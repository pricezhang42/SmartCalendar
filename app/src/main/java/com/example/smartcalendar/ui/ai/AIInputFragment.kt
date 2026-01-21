package com.example.smartcalendar.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.smartcalendar.BuildConfig
import com.example.smartcalendar.R
import com.example.smartcalendar.data.ai.AICalendarAssistant
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

    var onSessionCreated: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        aiAssistant = AICalendarAssistant.getInstance(requireContext())

        setupListeners()
        checkApiKey()
    }

    private fun checkApiKey() {
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            binding.errorText.text = getString(R.string.ai_api_key_missing)
            binding.errorText.visibility = View.VISIBLE
            binding.processButton.isEnabled = false
        }
    }

    private fun setupListeners() {
        binding.closeButton.setOnClickListener {
            onClose?.invoke()
        }

        binding.processButton.setOnClickListener {
            processInput()
        }

        // Example chips
        binding.chipExample1.setOnClickListener {
            binding.textInput.setText("Meeting tomorrow at 2pm")
        }

        binding.chipExample2.setOnClickListener {
            binding.textInput.setText("Weekly standup every Monday 9am")
        }

        binding.chipExample3.setOnClickListener {
            binding.textInput.setText("Lunch with Sarah Friday noon")
        }
    }

    private fun processInput() {
        val text = binding.textInput.text.toString().trim()
        if (text.isEmpty()) {
            binding.inputLayout.error = "Please enter some text"
            return
        }

        binding.inputLayout.error = null
        setLoading(true)

        lifecycleScope.launch {
            val userId = AuthRepository.getInstance().getCurrentUserId() ?: ""

            val result = aiAssistant.processTextInput(text, userId)

            setLoading(false)

            result.fold(
                onSuccess = { sessionId ->
                    onSessionCreated?.invoke(sessionId)
                },
                onFailure = { error ->
                    binding.errorText.text = error.message ?: getString(R.string.ai_error)
                    binding.errorText.visibility = View.VISIBLE
                }
            )
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.processButton.isEnabled = !loading
        binding.processButton.text = if (loading) getString(R.string.ai_processing) else getString(R.string.ai_process)
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.errorText.visibility = View.GONE
        binding.textInput.isEnabled = !loading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
