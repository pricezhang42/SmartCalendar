package com.example.smartcalendar.ui.ai

import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartcalendar.BuildConfig
import com.example.smartcalendar.R
import com.example.smartcalendar.data.ai.AICalendarAssistant
import com.example.smartcalendar.data.ai.AIProcessingOutput
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

    private var currentSessionId: String? = null
    private val messages = mutableListOf<ChatMessage>()

    private val attachmentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
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
            return@registerForActivityResult
        }

        if (bytes.size > MAX_ATTACHMENT_BYTES) {
            addMessage(
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    text = getString(R.string.ai_attachment_too_large),
                    isError = true
                )
            )
            return@registerForActivityResult
        }

        val isImage = mimeType.startsWith("image/")
        val label = if (isImage) {
            getString(R.string.ai_attached_image, displayName)
        } else {
            getString(R.string.ai_attached_document, displayName)
        }
        addMessage(ChatMessage(ChatRole.USER, label))
        processAttachment(bytes, mimeType, displayName, isImage)
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

        setupChat()
        setupListeners()
        binding.reviewButton.isEnabled = !currentSessionId.isNullOrBlank()
        checkApiKey()
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
    }

    private fun setupChat() {
        chatAdapter = ChatMessageAdapter(messages)
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.chatRecyclerView.layoutManager = layoutManager
        binding.chatRecyclerView.adapter = chatAdapter
    }

    private fun processInput() {
        val text = binding.textInput.text.toString().trim()
        if (text.isEmpty()) {
            binding.inputLayout.error = "Please enter some text"
            return
        }

        binding.inputLayout.error = null
        binding.textInput.setText("")
        setLoading(true)
        addMessage(ChatMessage(ChatRole.USER, text))

        lifecycleScope.launch {
            val userId = AuthRepository.getInstance().getCurrentUserId() ?: ""

            val result = if (currentSessionId == null) {
                aiAssistant.processTextInput(text, userId)
            } else {
                aiAssistant.refineSessionEvents(currentSessionId!!, text, userId)
            }

            setLoading(false)

            result.fold(
                onSuccess = { output ->
                    handleSuccess(output)
                },
                onFailure = { error ->
                    addMessage(
                        ChatMessage(
                            role = ChatRole.ASSISTANT,
                            text = error.message ?: getString(R.string.ai_error),
                            isError = true
                        )
                    )
                }
            )
        }
    }

    private fun processAttachment(
        bytes: ByteArray,
        mimeType: String,
        displayName: String,
        isImage: Boolean
    ) {
        binding.inputLayout.error = null
        setLoading(true)

        lifecycleScope.launch {
            val userId = AuthRepository.getInstance().getCurrentUserId() ?: ""
            currentSessionId = null

            val result = if (isImage) {
                aiAssistant.processImageInput(bytes, mimeType, userId, displayName)
            } else {
                aiAssistant.processDocumentInput(bytes, mimeType, userId, displayName)
            }

            setLoading(false)

            result.fold(
                onSuccess = { output ->
                    handleSuccess(output)
                },
                onFailure = { error ->
                    addMessage(
                        ChatMessage(
                            role = ChatRole.ASSISTANT,
                            text = error.message ?: getString(R.string.ai_error),
                            isError = true
                        )
                    )
                }
            )
        }
    }

    private fun handleSuccess(output: AIProcessingOutput) {
        if (currentSessionId == null) {
            currentSessionId = output.sessionId
            onSessionCreated?.invoke(output.sessionId)
        }

        binding.reviewButton.isEnabled = true
        val responseText = output.message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.ai_review_ready)
        addMessage(ChatMessage(ChatRole.ASSISTANT, responseText))
    }

    private fun addMessage(message: ChatMessage) {
        chatAdapter.addMessage(message)
        binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
        binding.errorText.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        binding.processButton.isEnabled = !loading
        binding.processButton.text = if (loading) getString(R.string.ai_processing) else getString(R.string.ai_send)
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.textInput.isEnabled = !loading
        binding.reviewButton.isEnabled = !loading && !currentSessionId.isNullOrBlank()
    }

    override fun onDestroyView() {
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
}
