package com.example.smartcalendar.ui.event

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.smartcalendar.R
import com.example.smartcalendar.data.model.Event
import com.example.smartcalendar.data.model.EventFormState
import com.example.smartcalendar.data.model.RecurrenceRule
import com.example.smartcalendar.data.model.RepeatEndType
import com.example.smartcalendar.data.repository.CalendarRepository
import com.example.smartcalendar.databinding.FragmentEventModalBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.*

/**
 * Bottom sheet dialog for creating and editing events.
 */
class EventModalFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentEventModalBinding? = null
    private val binding get() = _binding!!

    private var existingEvent: Event? = null
    private var instanceStartTime: Long? = null
    
    // Form state - all form data in one place
    private var originalForm: EventFormState? = null
    private var currentForm = EventFormState()
    
    // UI state helpers (not part of form data)
    private var selectedDate = Calendar.getInstance()
    private var startTime = Calendar.getInstance()
    private var endTime = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
    private val selectedDaysOfWeek = mutableSetOf<String>()

    private lateinit var repository: CalendarRepository

    var onSaveListener: ((Event) -> Unit)? = null
    var onDeleteListener: ((Long) -> Unit)? = null
    var onRecurringEditListener: ((RecurringEditChoice, Event, Long?) -> Unit)? = null

    enum class RecurringEditChoice {
        THIS_EVENT,
        THIS_AND_FOLLOWING,
        ALL_EVENTS
    }

    companion object {
        private const val ARG_EVENT_ID = "event_id"
        private const val ARG_EVENT_TITLE = "event_title"
        private const val ARG_EVENT_DESCRIPTION = "event_description"
        private const val ARG_EVENT_LOCATION = "event_location"
        private const val ARG_START_TIME = "start_time"
        private const val ARG_END_TIME = "end_time"
        private const val ARG_RRULE = "rrule"
        private const val ARG_ORIGINAL_ID = "original_id"
        private const val ARG_ORIGINAL_INSTANCE_TIME = "original_instance_time"
        private const val ARG_CALENDAR_ID = "calendar_id"
        private const val ARG_IS_ALL_DAY = "is_all_day"

        fun newInstance(event: Event? = null, initialTime: Long? = null): EventModalFragment {
            return EventModalFragment().apply {
                arguments = Bundle().apply {
                    event?.let {
                        putLong(ARG_EVENT_ID, it.id)
                        putString(ARG_EVENT_TITLE, it.title)
                        putString(ARG_EVENT_DESCRIPTION, it.description)
                        putString(ARG_EVENT_LOCATION, it.location)
                        putLong(ARG_START_TIME, it.startTime)
                        putLong(ARG_END_TIME, it.endTime)
                        putLong(ARG_CALENDAR_ID, it.calendarId)
                        putBoolean(ARG_IS_ALL_DAY, it.isAllDay)
                        it.rrule?.let { rrule -> putString(ARG_RRULE, rrule) }
                        it.originalId?.let { origId -> putLong(ARG_ORIGINAL_ID, origId) }
                        it.originalInstanceTime?.let { origTime -> putLong(ARG_ORIGINAL_INSTANCE_TIME, origTime) }
                    }
                    initialTime?.let {
                        putLong(ARG_START_TIME, it)
                        putLong(ARG_END_TIME, it + 3600000) // 1 hour duration
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventModalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        repository = CalendarRepository(requireContext())
        
        arguments?.let { args ->
            if (args.containsKey(ARG_EVENT_ID)) {
                val rrule = args.getString(ARG_RRULE)
                val originalId = if (args.containsKey(ARG_ORIGINAL_ID)) args.getLong(ARG_ORIGINAL_ID) else null
                val originalInstanceTime = if (args.containsKey(ARG_ORIGINAL_INSTANCE_TIME)) args.getLong(ARG_ORIGINAL_INSTANCE_TIME) else null
                
                existingEvent = Event(
                    id = args.getLong(ARG_EVENT_ID),
                    calendarId = args.getLong(ARG_CALENDAR_ID, 0),
                    title = args.getString(ARG_EVENT_TITLE) ?: "",
                    description = args.getString(ARG_EVENT_DESCRIPTION) ?: "",
                    location = args.getString(ARG_EVENT_LOCATION) ?: "",
                    startTime = args.getLong(ARG_START_TIME),
                    endTime = args.getLong(ARG_END_TIME),
                    isAllDay = args.getBoolean(ARG_IS_ALL_DAY, false),
                    rrule = rrule,
                    originalId = originalId,
                    originalInstanceTime = originalInstanceTime
                )
                
                instanceStartTime = args.getLong(ARG_START_TIME)  // Store the instance time
            }
            if (args.containsKey(ARG_START_TIME)) {
                startTime.timeInMillis = args.getLong(ARG_START_TIME)
                endTime.timeInMillis = args.getLong(ARG_END_TIME)
                selectedDate.timeInMillis = startTime.timeInMillis
            }
        }

        setupViews()
        setupListeners()
        updateUI()
    }

    private fun setupViews() {
        existingEvent?.let { event ->
            // Initialize form from event
            currentForm = EventFormState.fromEvent(event)
            originalForm = currentForm.copy()
            
            // Populate UI fields
            binding.titleEditText.setText(currentForm.title)
            binding.descriptionEditText.setText(currentForm.description)
            binding.locationEditText.setText(currentForm.location)
            startTime.timeInMillis = currentForm.startTime
            endTime.timeInMillis = currentForm.endTime
            selectedDate.timeInMillis = currentForm.startTime
            
            // Sync day of week selection
            selectedDaysOfWeek.clear()
            selectedDaysOfWeek.addAll(currentForm.daysOfWeek)
            
            // Show delete button for existing events
            binding.deleteButton.visibility = View.VISIBLE
        }

        binding.allDaySwitch.isChecked = currentForm.isAllDay
        binding.repeatSwitch.isChecked = currentForm.repeatEnabled
        binding.repeatOptionsContainer.visibility = if (currentForm.repeatEnabled) View.VISIBLE else View.GONE

        // Setup day of week chips
        setupDayOfWeekChips()
    }

    private fun setupDayOfWeekChips() {
        val days = listOf("S" to "SU", "M" to "MO", "T" to "TU", "W" to "WE", "T" to "TH", "F" to "FR", "S" to "SA")
        binding.daysOfWeekContainer.removeAllViews()

        days.forEachIndexed { index, (label, code) ->
            val chip = TextView(context).apply {
                text = label
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 8, 0, 8)
                layoutParams = android.widget.LinearLayout.LayoutParams(36, 36).apply {
                    marginEnd = 8
                }
                isSelected = selectedDaysOfWeek.contains(code)
                updateDayChipStyle(this)

                setOnClickListener {
                    if (selectedDaysOfWeek.contains(code)) {
                        selectedDaysOfWeek.remove(code)
                    } else {
                        selectedDaysOfWeek.add(code)
                    }
                    isSelected = selectedDaysOfWeek.contains(code)
                    updateDayChipStyle(this)
                }
            }
            binding.daysOfWeekContainer.addView(chip)
        }
    }

    private fun updateDayChipStyle(chip: TextView) {
        if (chip.isSelected) {
            chip.setBackgroundResource(R.drawable.circle_button_background)
            chip.setTextColor(android.graphics.Color.WHITE)
        } else {
            chip.setBackgroundResource(R.drawable.day_chip_background)
            chip.setTextColor(android.graphics.Color.BLACK)
        }
    }

    private fun setupListeners() {
        binding.dateValue.setOnClickListener { showDatePicker() }
        binding.startTimeValue.setOnClickListener { showTimePicker(true) }
        binding.endTimeValue.setOnClickListener { showTimePicker(false) }
        
        binding.allDaySwitch.setOnCheckedChangeListener { _, isChecked ->
            currentForm = currentForm.copy(isAllDay = isChecked)
            updateTimeVisibility()
        }

        binding.repeatSwitch.setOnCheckedChangeListener { _, isChecked ->
            currentForm = currentForm.copy(repeatEnabled = isChecked)
            binding.repeatOptionsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.reminderRow.setOnClickListener { showReminderPicker() }
        binding.repeatEndType.setOnClickListener { showRepeatEndTypePicker() }

        // Frequency chip selection
        binding.frequencyChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val frequency = when (checkedIds.firstOrNull()) {
                R.id.chipDaily -> Event.FREQ_DAILY
                R.id.chipWeekly -> Event.FREQ_WEEKLY
                R.id.chipMonthly -> Event.FREQ_MONTHLY
                R.id.chipYearly -> Event.FREQ_YEARLY
                else -> Event.FREQ_WEEKLY
            }
            currentForm = currentForm.copy(frequency = frequency)
            updateIntervalLabel()
            
            // Show/hide repeat on days (only for weekly)
            binding.repeatOnContainer.visibility = 
                if (frequency == Event.FREQ_WEEKLY) View.VISIBLE else View.GONE
        }

        binding.decreaseOccurrences.setOnClickListener {
            if (currentForm.occurrences > 1) {
                currentForm = currentForm.copy(occurrences = currentForm.occurrences - 1)
                binding.occurrencesValue.text = currentForm.occurrences.toString()
            }
        }

        binding.increaseOccurrences.setOnClickListener {
            currentForm = currentForm.copy(occurrences = currentForm.occurrences + 1)
            binding.occurrencesValue.text = currentForm.occurrences.toString()
        }

        binding.cancelButton.setOnClickListener { dismiss() }
        binding.saveButton.setOnClickListener { saveEvent() }
        binding.deleteButton.setOnClickListener { deleteEvent() }
    }

    private fun updateUI() {
        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        binding.dateValue.text = dateFormat.format(selectedDate.time)
        binding.startTimeValue.text = timeFormat.format(startTime.time)
        binding.endTimeValue.text = timeFormat.format(endTime.time)

        updateTimeVisibility()
        updateReminderText()
        updateIntervalLabel()

        // Set initial chip selection
        when (currentForm.frequency) {
            Event.FREQ_DAILY -> binding.chipDaily.isChecked = true
            Event.FREQ_WEEKLY -> binding.chipWeekly.isChecked = true
            Event.FREQ_MONTHLY -> binding.chipMonthly.isChecked = true
            Event.FREQ_YEARLY -> binding.chipYearly.isChecked = true
        }

        binding.intervalEditText.setText(currentForm.interval.toString())
        binding.occurrencesValue.text = currentForm.occurrences.toString()
        
        // Show/hide repeat on days (only for weekly)
        binding.repeatOnContainer.visibility = 
            if (currentForm.frequency == Event.FREQ_WEEKLY) View.VISIBLE else View.GONE
        
        updateRepeatEndTypeUI()
    }

    private fun updateTimeVisibility() {
        val visibility = if (currentForm.isAllDay) View.GONE else View.VISIBLE
        binding.startTimeRow.visibility = visibility
        binding.endTimeRow.visibility = visibility
    }

    private fun updateReminderText() {
        binding.reminderValue.text = when (currentForm.reminderMinutes) {
            null -> getString(R.string.reminder_none)
            5 -> getString(R.string.reminder_5min)
            10 -> getString(R.string.reminder_10min)
            15 -> getString(R.string.reminder_15min)
            30 -> getString(R.string.reminder_30min)
            60 -> getString(R.string.reminder_1hour)
            1440 -> getString(R.string.reminder_1day)
            else -> "${currentForm.reminderMinutes} minutes before"
        }
    }

    private fun updateIntervalLabel() {
        binding.intervalUnitLabel.text = when (currentForm.frequency) {
            Event.FREQ_DAILY -> getString(R.string.unit_day)
            Event.FREQ_WEEKLY -> getString(R.string.unit_week)
            Event.FREQ_MONTHLY -> getString(R.string.unit_month)
            Event.FREQ_YEARLY -> getString(R.string.unit_year)
            else -> getString(R.string.unit_week)
        }
    }

    private fun updateRepeatEndTypeUI() {
        binding.repeatEndType.text = when (currentForm.repeatEndType) {
            RepeatEndType.ENDLESSLY -> getString(R.string.repeat_endlessly)
            RepeatEndType.UNTIL_DATE -> getString(R.string.repeat_until_date)
            RepeatEndType.REPEAT_COUNT -> getString(R.string.repeat_count)
        }
        binding.occurrencesContainer.visibility = 
            if (currentForm.repeatEndType == RepeatEndType.REPEAT_COUNT) View.VISIBLE else View.GONE
    }

    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDate.set(year, month, day)
                startTime.set(Calendar.YEAR, year)
                startTime.set(Calendar.MONTH, month)
                startTime.set(Calendar.DAY_OF_MONTH, day)
                endTime.set(Calendar.YEAR, year)
                endTime.set(Calendar.MONTH, month)
                endTime.set(Calendar.DAY_OF_MONTH, day)
                updateUI()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker(isStart: Boolean) {
        val calendar = if (isStart) startTime else endTime
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                
                // Ensure end time is after start time
                if (isStart && endTime.before(startTime)) {
                    endTime.timeInMillis = startTime.timeInMillis
                    endTime.add(Calendar.HOUR_OF_DAY, 1)
                }
                updateUI()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun showReminderPicker() {
        val popup = PopupMenu(requireContext(), binding.reminderRow)
        popup.menu.add(0, 0, 0, getString(R.string.reminder_none))
        popup.menu.add(0, 5, 1, getString(R.string.reminder_5min))
        popup.menu.add(0, 10, 2, getString(R.string.reminder_10min))
        popup.menu.add(0, 15, 3, getString(R.string.reminder_15min))
        popup.menu.add(0, 30, 4, getString(R.string.reminder_30min))
        popup.menu.add(0, 60, 5, getString(R.string.reminder_1hour))
        popup.menu.add(0, 1440, 6, getString(R.string.reminder_1day))

        popup.setOnMenuItemClickListener { item ->
            currentForm = currentForm.copy(reminderMinutes = if (item.itemId == 0) null else item.itemId)
            updateReminderText()
            true
        }
        popup.show()
    }

    private fun showRepeatEndTypePicker() {
        val popup = PopupMenu(requireContext(), binding.repeatEndType)
        popup.menu.add(0, 0, 0, getString(R.string.repeat_endlessly))
        popup.menu.add(0, 1, 1, getString(R.string.repeat_until_date))
        popup.menu.add(0, 2, 2, getString(R.string.repeat_count))

        popup.setOnMenuItemClickListener { item ->
            val newEndType = when (item.itemId) {
                0 -> RepeatEndType.ENDLESSLY
                1 -> RepeatEndType.UNTIL_DATE
                else -> RepeatEndType.REPEAT_COUNT
            }
            currentForm = currentForm.copy(repeatEndType = newEndType)
            updateRepeatEndTypeUI()
            true
        }
        popup.show()
    }

    private fun saveEvent() {
        val title = binding.titleEditText.text.toString().trim()
        if (title.isEmpty()) {
            binding.titleEditText.error = "Title is required"
            return
        }

        // Build final form state from UI
        val finalForm = currentForm.copy(
            title = title,
            description = binding.descriptionEditText.text.toString().trim(),
            location = binding.locationEditText.text.toString().trim(),
            startTime = startTime.timeInMillis,
            endTime = endTime.timeInMillis,
            interval = binding.intervalEditText.text.toString().toIntOrNull() ?: 1,
            daysOfWeek = selectedDaysOfWeek.toSet()
        )

        // Convert form to event
        val event = finalForm.toEvent(
            eventId = existingEvent?.id ?: 0,
            calendarId = existingEvent?.calendarId ?: 0,
            originalId = existingEvent?.originalId,
            originalInstanceTime = existingEvent?.originalInstanceTime
        )

        // Check if this is a recurring event being edited
        val isRecurringEvent = existingEvent?.rrule != null || existingEvent?.originalId != null
        
        if (isRecurringEvent && existingEvent != null && originalForm != null) {
            // Special case: User turned OFF repeat for a recurring event
            // This automatically ends the series and creates a normal event
            if (originalForm!!.repeatEnabled && !finalForm.repeatEnabled) {
                handleRepeatTurnedOff(event)
                return
            }
            
            // Use form comparison to detect recurrence changes
            val recurrenceChanged = finalForm.hasRecurrenceChanged(originalForm!!)
            showRecurringEditDialog(event, recurrenceChanged)
        } else {
            // Normal save for non-recurring events
            onSaveListener?.invoke(event)
            dismiss()
        }
    }

    /**
     * Handle the special case where user turns off repeat for a recurring event.
     * - If first occurrence (no originalId): Convert the master event to non-recurring
     * - If later occurrence: End the series and create a new non-recurring event
     */
    private fun handleRepeatTurnedOff(event: Event) {
        val instanceTime = instanceStartTime ?: return
        val calendarId = existingEvent?.calendarId ?: return
        
        try {
            // If no originalId, this is the master event itself (first occurrence)
            if (existingEvent?.originalId == null) {
                val masterEventId = existingEvent?.id ?: return
                // First occurrence: Just update the master event to remove recurrence
                val updatedEvent = event.copy(id = masterEventId, calendarId = calendarId)
                repository.updateEvent(updatedEvent)
                onRecurringEditListener?.invoke(RecurringEditChoice.ALL_EVENTS, updatedEvent, instanceTime)
            } else {
                // Later occurrence: End the original series and create new non-recurring event
                val masterEventId = existingEvent?.originalId ?: return
                repository.splitRecurringSeries(masterEventId, instanceTime, event.copy(calendarId = calendarId))
                onRecurringEditListener?.invoke(RecurringEditChoice.THIS_AND_FOLLOWING, event, instanceTime)
            }
        } catch (e: Exception) {
            android.util.Log.e("EventModalFragment", "Error turning off repeat", e)
            android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
        dismiss()
    }

    private fun showRecurringEditDialog(event: Event, rruleChanged: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_recurring_edit_choice, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.editChoiceGroup)
        val optionThisEvent = dialogView.findViewById<View>(R.id.optionThisEvent)
        val optionThisAndFollowing = dialogView.findViewById<View>(R.id.optionThisAndFollowing)
        val optionAllEvents = dialogView.findViewById<View>(R.id.optionAllEvents)

        // If RRULE changed, hide "This event" option
        if (rruleChanged) {
            optionThisEvent.visibility = View.GONE
            // Check "All events" by default when RRULE changed
            radioGroup.check(R.id.optionAllEvents)
        } else {
            // Check "This event" by default
            radioGroup.check(R.id.optionThisEvent)
        }

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val choice = when (radioGroup.checkedRadioButtonId) {
                    R.id.optionThisEvent -> RecurringEditChoice.THIS_EVENT
                    R.id.optionThisAndFollowing -> RecurringEditChoice.THIS_AND_FOLLOWING
                    R.id.optionAllEvents -> RecurringEditChoice.ALL_EVENTS
                    else -> RecurringEditChoice.ALL_EVENTS
                }
                handleRecurringEditChoice(choice, event)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleRecurringEditChoice(choice: RecurringEditChoice, event: Event) {
        val originalId = existingEvent?.originalId ?: existingEvent?.id ?: return
        val instanceTime = instanceStartTime ?: return

        when (choice) {
            RecurringEditChoice.THIS_EVENT -> {
                // Create an exception for this instance only
                val exceptionId = repository.createException(originalId, instanceTime, event)
                if (exceptionId > 0) {
                    // Notify listener with the updated event
                    onRecurringEditListener?.invoke(choice, event.copy(id = exceptionId), instanceTime)
                }
            }
            RecurringEditChoice.THIS_AND_FOLLOWING -> {
                // Split the series from this instance
                val newSeriesId = repository.splitRecurringSeries(originalId, instanceTime, event)
                if (newSeriesId > 0) {
                    onRecurringEditListener?.invoke(choice, event.copy(id = newSeriesId), instanceTime)
                }
            }
            RecurringEditChoice.ALL_EVENTS -> {
                // Update the master event with changed fields only
                val changedFields = getChangedFields(event)
                if (changedFields.size() > 0) {
                    repository.updateRecurringEventFields(originalId, changedFields)
                }
                onRecurringEditListener?.invoke(choice, event, instanceTime)
            }
        }
        dismiss()
    }

    private fun deleteEvent() {
        val event = existingEvent ?: return
        val isRecurringEvent = event.rrule != null || event.originalId != null
        
        if (isRecurringEvent) {
            showRecurringDeleteDialog()
        } else {
            // Simple confirmation for non-recurring events
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Event")
                .setMessage("Are you sure you want to delete this event?")
                .setPositiveButton("Delete") { _, _ ->
                    repository.deleteEvent(event.id)
                    onDeleteListener?.invoke(event.id)
                    dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showRecurringDeleteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_recurring_edit_choice, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.editChoiceGroup)
        val optionThisEvent = dialogView.findViewById<View>(R.id.optionThisEvent)
        val optionThisAndFollowing = dialogView.findViewById<View>(R.id.optionThisAndFollowing)
        val optionAllEvents = dialogView.findViewById<View>(R.id.optionAllEvents)

        // All options are available for delete
        optionThisEvent.visibility = View.VISIBLE
        optionThisAndFollowing.visibility = View.VISIBLE
        optionAllEvents.visibility = View.VISIBLE
        radioGroup.check(R.id.optionThisEvent)

        AlertDialog.Builder(requireContext())
            .setTitle("Delete Recurring Event")
            .setView(dialogView)
            .setPositiveButton("Delete") { _, _ ->
                val choice = when (radioGroup.checkedRadioButtonId) {
                    R.id.optionThisEvent -> RecurringEditChoice.THIS_EVENT
                    R.id.optionThisAndFollowing -> RecurringEditChoice.THIS_AND_FOLLOWING
                    R.id.optionAllEvents -> RecurringEditChoice.ALL_EVENTS
                    else -> RecurringEditChoice.THIS_EVENT
                }
                handleRecurringDeleteChoice(choice)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleRecurringDeleteChoice(choice: RecurringEditChoice) {
        val event = existingEvent ?: return
        val instanceTime = instanceStartTime ?: return
        
        // For instances from CalendarContract.Instances, EVENT_ID is the master event ID
        // originalId is only set for exception events
        val masterEventId = event.originalId ?: event.id
        
        // Check if this is the first occurrence by comparing with master event start time
        val masterEvent = repository.getEvent(masterEventId)
        val isFirstOccurrence = masterEvent?.startTime == instanceTime

        try {
            when (choice) {
                RecurringEditChoice.THIS_EVENT -> {
                    // Delete only this instance using CONTENT_EXCEPTION_URI
                    repository.deleteRecurringEventInstance(masterEventId, instanceTime)
                }
                RecurringEditChoice.THIS_AND_FOLLOWING -> {
                    if (isFirstOccurrence) {
                        // First occurrence + this and following = delete entire event
                        repository.deleteEvent(masterEventId)
                    } else {
                        // End the series from this instance
                        repository.deleteRecurringEventFromInstance(masterEventId, instanceTime)
                    }
                }
                RecurringEditChoice.ALL_EVENTS -> {
                    // Delete the entire recurring event
                    repository.deleteEvent(masterEventId)
                }
            }
            onDeleteListener?.invoke(event.id)
        } catch (e: Exception) {
            android.util.Log.e("EventModalFragment", "Error deleting recurring event", e)
            android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
        dismiss()
    }

    private fun getChangedFields(newEvent: Event): ContentValues {
        val values = ContentValues()
        val original = originalForm ?: return values

        if (newEvent.title != original.title) {
            values.put(CalendarContract.Events.TITLE, newEvent.title)
        }
        if (newEvent.description != original.description) {
            values.put(CalendarContract.Events.DESCRIPTION, newEvent.description)
        }
        if (newEvent.location != original.location) {
            values.put(CalendarContract.Events.EVENT_LOCATION, newEvent.location)
        }
        if (newEvent.isAllDay != original.isAllDay) {
            values.put(CalendarContract.Events.ALL_DAY, if (newEvent.isAllDay) 1 else 0)
        }
        // Compare rrule by checking if recurrence settings changed
        val originalEventFromForm = original.toEvent()
        if (newEvent.rrule != originalEventFromForm.rrule) {
            if (newEvent.rrule != null) {
                values.put(CalendarContract.Events.RRULE, newEvent.rrule)
            } else {
                values.putNull(CalendarContract.Events.RRULE)
            }
        }
        // Note: startTime/endTime changes for "All events" would affect all instances
        // which may not be the desired behavior, so we skip those for recurring updates

        return values
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
