package com.example.smartcalendar.ui.ai

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartcalendar.BuildConfig
import com.example.smartcalendar.R
import com.example.smartcalendar.data.ai.AICalendarAssistant
import com.example.smartcalendar.data.ai.WhisperTranscriber
import com.example.smartcalendar.data.ai.AIProcessingOutput
import com.example.smartcalendar.data.ai.StreamUpdate
import com.example.smartcalendar.data.repository.AuthRepository
import com.example.smartcalendar.databinding.FragmentAiInputBinding
import kotlinx.coroutines.launch

/**
 * Fragment for AI text input to extract calendar events.
 */
class AIInputFragment : Fragment() {

    private var _binding: FragmentAiInputBinding? = null
    private val binding get() = _binding!!

    private lateinit var aiAssistant: AICalendarAssistant
    private lateinit var chatAdapter: ChatMessageAdapter

    private val caliViewModel: CaliViewModel by activityViewModels()

    private var currentSessionId: String? = null
    private val messages = mutableListOf<ChatMessage>()
    private val attachments = mutableListOf<AttachmentItem>()

    private val attachmentPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        uris.forEach { uri ->
            addAttachment(uri)
        }
    }

    // Voice input via on-device Whisper
    private lateinit var whisperTranscriber: WhisperTranscriber

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            whisperTranscriber.startRecording()
        } else {
            Toast.makeText(requireContext(), R.string.voice_error_permission, Toast.LENGTH_SHORT).show()
        }
    }

    var onSessionCreated: ((String) -> Unit)? = null
    var onReviewRequested: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentSessionId = savedInstanceState?.getString("session_id")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        aiAssistant = AICalendarAssistant.getInstance(requireContext())

        // Show header only when hosted in AIAssistantActivity (not in bottom nav tab)
        if (activity is AIAssistantActivity) {
            binding.headerContainer.visibility = View.VISIBLE
        }

        setupChat()
        setupListeners()
        setupVoiceInput()
        binding.reviewButton.isEnabled = !currentSessionId.isNullOrBlank()
        checkApiKey()
    }

    private fun setupVoiceInput() {
        whisperTranscriber = WhisperTranscriber(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                whisperTranscriber.state.collect { state ->
                    handleVoiceState(state)
                }
            }
        }
    }

    private fun resetVoiceButton() {
        binding.voiceButton.setColorFilter(
            ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
        )
        binding.voiceButton.isEnabled = true
        binding.progressBar.visibility = View.GONE
        (parentFragment as? CaliFragment)?.hideAudioOverlay()
    }

    private fun handleVoiceState(state: WhisperTranscriber.State) {
        when (state) {
            is WhisperTranscriber.State.Idle -> resetVoiceButton()
            is WhisperTranscriber.State.Recording -> {
                binding.voiceButton.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.primary_blue)
                )
                (parentFragment as? CaliFragment)?.showAudioOverlay()
            }
            is WhisperTranscriber.State.Transcribing -> {
                binding.voiceButton.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE
                (parentFragment as? CaliFragment)?.hideAudioOverlay()
            }
            is WhisperTranscriber.State.Result -> {
                resetVoiceButton()
                val current = binding.textInput.text?.toString() ?: ""
                val newText = if (current.isEmpty()) state.text else "$current ${state.text}"
                binding.textInput.setText(newText)
                binding.textInput.setSelection(newText.length)
                whisperTranscriber.resetState()
            }
            is WhisperTranscriber.State.Error -> {
                resetVoiceButton()
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                whisperTranscriber.resetState()
            }
        }
    }

    private fun checkApiKey() {
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            binding.errorText.text = getString(R.string.ai_api_key_missing)
            binding.errorText.visibility = View.VISIBLE
            binding.processButton.isEnabled = false
            binding.reviewButton.isEnabled = false
        }
    }

    private fun setupListeners() {
        binding.closeButton.setOnClickListener {
            onClose?.invoke()
        }

        binding.attachButton.setOnClickListener {
            attachmentPicker.launch(ATTACHMENT_MIME_TYPES)
        }
        binding.clearAttachmentsButton.setOnClickListener {
            attachments.clear()
            renderAttachments()
        }

        binding.reviewButton.setOnClickListener {
            val sessionId = currentSessionId
            if (sessionId.isNullOrBlank()) {
                binding.errorText.text = getString(R.string.ai_no_events)
                binding.errorText.visibility = View.VISIBLE
            } else {
                onReviewRequested?.invoke(sessionId)
            }
        }

        binding.processButton.setOnClickListener {
            processInput()
        }

        binding.voiceButton.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        whisperTranscriber.startRecording()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (whisperTranscriber.isRecording()) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            whisperTranscriber.stopAndTranscribe()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupChat() {
        // Restore messages and session from ViewModel if available
        if (messages.isEmpty() && caliViewModel.messages.isNotEmpty()) {
            messages.addAll(caliViewModel.messages)
        }
        if (currentSessionId == null && caliViewModel.currentSessionId != null) {
            currentSessionId = caliViewModel.currentSessionId
        }

        chatAdapter = ChatMessageAdapter(messages)
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.chatRecyclerView.layoutManager = layoutManager
        binding.chatRecyclerView.adapter = chatAdapter
    }

    private fun processInput() {
        val text = binding.textInput.text.toString().trim()
        if (text.isEmpty() && attachments.isEmpty()) {
            binding.inputLayout.error = "Please enter some text or attach files"
            return
        }

        binding.inputLayout.error = null
        binding.textInput.setText("")
        if (text.isNotEmpty()) {
            addMessage(ChatMessage(ChatRole.USER, text))
        } else if (attachments.isNotEmpty()) {
            val label = "${attachments.size} file${if (attachments.size > 1) "s" else ""} attached"
            addMessage(ChatMessage(ChatRole.USER, "📎 $label"))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val userId = AuthRepository.getInstance().getCurrentUserId() ?: ""

            // Use streaming for text-only messages (new conversations or follow-ups without pending events)
            val useStreaming = text.isNotEmpty() && attachments.isEmpty()

            if (useStreaming) {
                // If session has pending events, try refinement first (non-streaming)
                if (currentSessionId != null) {
                    val refineResult = aiAssistant.refineSessionEvents(currentSessionId!!, text, userId)
                    if (refineResult.isSuccess) {
                        setLoading(false)
                        handleSuccess(refineResult.getOrThrow())
                        return@launch
                    }
                    // Refinement failed (no pending events) — fall through to streaming
                }

                // No spinner — dots in chat bubble serve as the indicator
                setLoading(true, showSpinner = false)
                addMessage(ChatMessage(ChatRole.ASSISTANT, ChatMessageAdapter.TYPING_INDICATOR))

                aiAssistant.processTextInputStreaming(text, userId).collect { update ->
                    when (update) {
                        is StreamUpdate.TextUpdate -> {
                            // Keep showing dots while streaming (don't show partial text)
                        }
                        is StreamUpdate.Done -> {
                            setLoading(false)
                            val finalText = update.output.message?.takeIf { it.isNotBlank() }
                                ?: getString(R.string.ai_review_ready)
                            chatAdapter.replaceLast(ChatMessage(ChatRole.ASSISTANT, finalText))
                            syncMessagesToViewModel()
                            handleSuccess(update.output, skipMessage = true)
                        }
                        is StreamUpdate.Error -> {
                            setLoading(false)
                            chatAdapter.replaceLast(ChatMessage(ChatRole.ASSISTANT, update.message, isError = true))
                            syncMessagesToViewModel()
                        }
                    }
                }
                return@launch
            }

            // Non-streaming paths (attachments only, or text+attachments)
            setLoading(true, showSpinner = false)
            addMessage(ChatMessage(ChatRole.ASSISTANT, ChatMessageAdapter.TYPING_INDICATOR))
            val result = when {
                text.isEmpty() && attachments.isNotEmpty() -> {
                    processAttachments(userId, null)
                }
                else -> {
                    // Text + attachments: process attachments directly (text provides context in prompt)
                    val sessionId = currentSessionId ?: java.util.UUID.randomUUID().toString()
                    processAttachments(userId, sessionId)
                }
            }

            setLoading(false)

            result.fold(
                onSuccess = { output ->
                    val finalText = output.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.ai_review_ready)
                    chatAdapter.replaceLast(ChatMessage(ChatRole.ASSISTANT, finalText))
                    syncMessagesToViewModel()
                    handleSuccess(output, skipMessage = true)
                },
                onFailure = { error ->
                    chatAdapter.replaceLast(
                        ChatMessage(
                            role = ChatRole.ASSISTANT,
                            text = error.message ?: getString(R.string.ai_error),
                            isError = true
                        )
                    )
                    syncMessagesToViewModel()
                }
            )
        }
    }

    private suspend fun processAttachments(
        userId: String,
        sessionId: String?
    ): Result<AIProcessingOutput> {
        val targetSessionId = sessionId ?: java.util.UUID.randomUUID().toString()
        var lastMessage: String? = null
        attachments.forEach { attachment ->
            val result = if (attachment.isImage) {
                aiAssistant.processImageIntoSession(
                    attachment.bytes,
                    attachment.mimeType,
                    userId,
                    attachment.displayName,
                    targetSessionId
                )
            } else {
                aiAssistant.processDocumentIntoSession(
                    attachment.bytes,
                    attachment.mimeType,
                    userId,
                    attachment.displayName,
                    targetSessionId
                )
            }

            if (result.isFailure) {
                addMessage(
                    ChatMessage(
                        role = ChatRole.ASSISTANT,
                        text = result.exceptionOrNull()?.message
                            ?: getString(R.string.ai_error),
                        isError = true
                    )
                )
            } else {
                lastMessage = result.getOrNull()?.message ?: lastMessage
            }
        }

        attachments.clear()
        renderAttachments()

        return Result.success(
            AIProcessingOutput(
                sessionId = targetSessionId,
                message = lastMessage,
                rawResponse = ""
            )
        )
    }

    private fun handleSuccess(output: AIProcessingOutput, skipMessage: Boolean = false) {
        if (currentSessionId == null) {
            currentSessionId = output.sessionId
            caliViewModel.currentSessionId = output.sessionId
            onSessionCreated?.invoke(output.sessionId)
        }

        binding.reviewButton.isEnabled = true
        if (!skipMessage) {
            val responseText = output.message?.takeIf { it.isNotBlank() }
                ?: getString(R.string.ai_review_ready)
            addMessage(ChatMessage(ChatRole.ASSISTANT, responseText))
        }
    }

    private fun addMessage(message: ChatMessage) {
        chatAdapter.addMessage(message)
        binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
        binding.errorText.visibility = View.GONE
        // Persist to ViewModel — skip typing indicator
        syncMessagesToViewModel()
    }

    private fun syncMessagesToViewModel() {
        caliViewModel.messages.clear()
        caliViewModel.messages.addAll(messages.filter { it.text != ChatMessageAdapter.TYPING_INDICATOR })
    }

    private fun setLoading(loading: Boolean, showSpinner: Boolean = true) {
        binding.processButton.isEnabled = !loading
        binding.processButton.text = if (loading) getString(R.string.ai_processing) else getString(R.string.ai_send)
        binding.progressBar.visibility = if (loading && showSpinner) View.VISIBLE else View.GONE
        binding.textInput.isEnabled = !loading
        binding.reviewButton.isEnabled = !loading && !currentSessionId.isNullOrBlank()
        binding.attachButton.isEnabled = !loading
        binding.clearAttachmentsButton.isEnabled = !loading
    }

    override fun onDestroyView() {
        whisperTranscriber.release()
        super.onDestroyView()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("session_id", currentSessionId)
        super.onSaveInstanceState(outState)
    }

    private fun getDisplayName(uri: android.net.Uri): String? {
        val cursor = requireContext().contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        ) ?: return null
        cursor.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return it.getString(index)
            }
        }
        return null
    }

    private fun addAttachment(uri: Uri) {
        val contentResolver = requireContext().contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val displayName = getDisplayName(uri) ?: getString(R.string.ai_attachment)
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            addMessage(
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    text = getString(R.string.ai_attachment_failed),
                    isError = true
                )
            )
            return
        }

        if (bytes.size > MAX_ATTACHMENT_BYTES) {
            addMessage(
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    text = getString(R.string.ai_attachment_too_large),
                    isError = true
                )
            )
            return
        }

        val isImage = mimeType.startsWith("image/")
        attachments.add(
            AttachmentItem(
                uri = uri,
                bytes = bytes,
                mimeType = mimeType,
                displayName = displayName,
                isImage = isImage
            )
        )
        renderAttachments()
    }

    private fun renderAttachments() {
        binding.attachmentsContainer.removeAllViews()
        if (attachments.isEmpty()) {
            binding.attachmentsContainer.visibility = View.GONE
            binding.clearAttachmentsButton.visibility = View.GONE
            return
        }
        binding.attachmentsContainer.visibility = View.VISIBLE
        binding.clearAttachmentsButton.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(requireContext())
        attachments.forEachIndexed { index, item ->
            val row = inflater.inflate(R.layout.item_attachment_row, binding.attachmentsContainer, false)
            val nameView = row.findViewById<TextView>(R.id.attachmentName)
            val removeButton = row.findViewById<ImageButton>(R.id.removeAttachment)
            val label = if (item.isImage) {
                getString(R.string.ai_attached_image, item.displayName)
            } else {
                getString(R.string.ai_attached_document, item.displayName)
            }
            nameView.text = label
            removeButton.setOnClickListener {
                attachments.removeAt(index)
                renderAttachments()
            }
            binding.attachmentsContainer.addView(row)
        }
    }

    companion object {
        private const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024
        private val ATTACHMENT_MIME_TYPES = arrayOf(
            "image/*",
            "application/pdf",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
    }

    private data class AttachmentItem(
        val uri: Uri,
        val bytes: ByteArray,
        val mimeType: String,
        val displayName: String,
        val isImage: Boolean
    )
}
