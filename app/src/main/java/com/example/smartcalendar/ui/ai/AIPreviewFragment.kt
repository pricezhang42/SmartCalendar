package com.example.smartcalendar.ui.ai

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartcalendar.R
import com.example.smartcalendar.data.ai.AICalendarAssistant
import com.example.smartcalendar.data.model.LocalCalendar
import com.example.smartcalendar.data.model.PendingEvent
import com.example.smartcalendar.data.repository.AuthRepository
import com.example.smartcalendar.data.repository.LocalCalendarRepository
import com.example.smartcalendar.databinding.FragmentAiPreviewBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragment for reviewing and approving AI-extracted events.
 */
class AIPreviewFragment : Fragment() {

    private var _binding: FragmentAiPreviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var aiAssistant: AICalendarAssistant
    private lateinit var localRepository: LocalCalendarRepository
    private lateinit var adapter: PendingEventAdapter

    private var sessionId: String = ""
    private var selectedCalendar: LocalCalendar? = null
    private var calendars: List<LocalCalendar> = emptyList()

    var onEventClick: ((PendingEvent) -> Unit)? = null
    var onComplete: (() -> Unit)? = null
    var onBack: (() -> Unit)? = null

    companion object {
        private const val ARG_SESSION_ID = "session_id"

        fun newInstance(sessionId: String): AIPreviewFragment {
            return AIPreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SESSION_ID, sessionId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionId = arguments?.getString(ARG_SESSION_ID) ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        aiAssistant = AICalendarAssistant.getInstance(requireContext())
        localRepository = LocalCalendarRepository.getInstance(requireContext())
        AuthRepository.getInstance().getCurrentUserId()?.let { userId ->
            if (userId.isNotEmpty()) {
                localRepository.setUserId(userId)
            }
        }

        setupRecyclerView()
        setupListeners()
        loadCalendars()
        observeEvents()
    }

    private fun setupRecyclerView() {
        adapter = PendingEventAdapter { event ->
            onEventClick?.invoke(event)
        }

        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.eventsRecyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener {
            onBack?.invoke()
        }

        // Calendar selection
        binding.calendarName.setOnClickListener { showCalendarPicker() }
        binding.calendarColorDot.setOnClickListener { showCalendarPicker() }

        binding.approveButton.setOnClickListener {
            approveAll()
        }

        binding.rejectButton.setOnClickListener {
            rejectAll()
        }
    }

    private fun loadCalendars() {
        lifecycleScope.launch {
            calendars = localRepository.getCalendars()
            if (calendars.isNotEmpty()) {
                selectedCalendar = calendars.firstOrNull { it.isDefault } ?: calendars.first()
                updateCalendarDisplay()
            }
        }
    }

    private fun updateCalendarDisplay() {
        selectedCalendar?.let { cal ->
            binding.calendarName.text = cal.name

            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cal.color)
            }
            binding.calendarColorDot.background = drawable
        }
    }

    private fun showCalendarPicker() {
        if (calendars.isEmpty()) return

        val names = calendars.map { it.name }.toTypedArray()
        val currentIndex = calendars.indexOfFirst { it.id == selectedCalendar?.id }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_calendar)
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                selectedCalendar = calendars[which]
                updateCalendarDisplay()
                dialog.dismiss()
            }
            .show()
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            aiAssistant.getPendingEvents(sessionId).collectLatest { events ->
                adapter.submitList(events)

                val count = events.size
                binding.eventCount.text = "$count event${if (count != 1) "s" else ""}"

                if (events.isEmpty()) {
                    binding.eventsRecyclerView.visibility = View.GONE
                    binding.emptyText.visibility = View.VISIBLE
                } else {
                    binding.eventsRecyclerView.visibility = View.VISIBLE
                    binding.emptyText.visibility = View.GONE
                }
            }
        }
    }

    private fun approveAll() {
        val calendar = selectedCalendar ?: return

        lifecycleScope.launch {
            val result = aiAssistant.approveAllEvents(
                sessionId = sessionId,
                calendarId = calendar.id,
                color = calendar.color
            )

            result.fold(
                onSuccess = { count ->
                    Toast.makeText(
                        context,
                        getString(R.string.ai_events_approved, count),
                        Toast.LENGTH_SHORT
                    ).show()
                    onComplete?.invoke()
                },
                onFailure = { error ->
                    Toast.makeText(
                        context,
                        error.message ?: getString(R.string.ai_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    private fun rejectAll() {
        lifecycleScope.launch {
            aiAssistant.rejectAllEvents(sessionId)
            onComplete?.invoke()
        }
    }

    /**
     * Called when a pending event was modified in EventModalFragment.
     * Updates the event in the database.
     */
    fun updateEvent(event: PendingEvent) {
        lifecycleScope.launch {
            aiAssistant.updatePendingEvent(event)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
