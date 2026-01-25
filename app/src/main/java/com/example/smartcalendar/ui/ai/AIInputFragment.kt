package com.example.smartcalendar.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
}
