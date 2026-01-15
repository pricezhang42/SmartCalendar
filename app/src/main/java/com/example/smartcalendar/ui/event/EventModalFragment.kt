package com.example.smartcalendar.ui.event

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.smartcalendar.R
import com.example.smartcalendar.data.model.Event
import com.example.smartcalendar.data.model.RecurrenceRule
import com.example.smartcalendar.data.model.RepeatEndType
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
    private var selectedDate = Calendar.getInstance()
    private var startTime = Calendar.getInstance()
    private var endTime = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
    private var isAllDay = false
    private var reminderMinutes: Int? = null
    private var repeatEnabled = false
    private var recurrenceRule = RecurrenceRule()
    private val selectedDaysOfWeek = mutableSetOf<String>()
    private var repeatEndType = RepeatEndType.REPEAT_COUNT
    private var occurrences = 10

    var onSaveListener: ((Event) -> Unit)? = null
    var onDeleteListener: ((Long) -> Unit)? = null

    companion object {
        private const val ARG_EVENT_ID = "event_id"
        private const val ARG_EVENT_TITLE = "event_title"
        private const val ARG_START_TIME = "start_time"
        private const val ARG_END_TIME = "end_time"

        fun newInstance(event: Event? = null, initialTime: Long? = null): EventModalFragment {
            return EventModalFragment().apply {
                arguments = Bundle().apply {
                    event?.let {
                        putLong(ARG_EVENT_ID, it.id)
                        putString(ARG_EVENT_TITLE, it.title)
                        putLong(ARG_START_TIME, it.startTime)
                        putLong(ARG_END_TIME, it.endTime)
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
        
        arguments?.let { args ->
            if (args.containsKey(ARG_EVENT_ID)) {
                existingEvent = Event(
                    id = args.getLong(ARG_EVENT_ID),
                    title = args.getString(ARG_EVENT_TITLE) ?: "",
                    startTime = args.getLong(ARG_START_TIME),
                    endTime = args.getLong(ARG_END_TIME)
                )
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
            binding.titleEditText.setText(event.title)
            binding.descriptionEditText.setText(event.description)
            binding.locationEditText.setText(event.location)
            startTime.timeInMillis = event.startTime
            endTime.timeInMillis = event.endTime
            selectedDate.timeInMillis = event.startTime
            isAllDay = event.isAllDay
            
            // Parse recurrence rule if present
            event.rrule?.let { rrule ->
                RecurrenceRule.fromRRule(rrule)?.let {
                    repeatEnabled = true
                    recurrenceRule = it
                    selectedDaysOfWeek.clear()
                    selectedDaysOfWeek.addAll(it.daysOfWeek)
                    repeatEndType = it.endType
                    it.count?.let { count -> occurrences = count }
                }
            }
        }

        binding.allDaySwitch.isChecked = isAllDay
        binding.repeatSwitch.isChecked = repeatEnabled

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
            isAllDay = isChecked
            updateTimeVisibility()
        }

        binding.repeatSwitch.setOnCheckedChangeListener { _, isChecked ->
            repeatEnabled = isChecked
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
            recurrenceRule = recurrenceRule.copy(frequency = frequency)
            updateIntervalLabel()
            
            // Show/hide repeat on days (only for weekly)
            binding.repeatOnContainer.visibility = 
                if (frequency == Event.FREQ_WEEKLY) View.VISIBLE else View.GONE
        }

        binding.decreaseOccurrences.setOnClickListener {
            if (occurrences > 1) {
                occurrences--
                binding.occurrencesValue.text = occurrences.toString()
            }
        }

        binding.increaseOccurrences.setOnClickListener {
            occurrences++
            binding.occurrencesValue.text = occurrences.toString()
        }

        binding.cancelButton.setOnClickListener { dismiss() }
        binding.saveButton.setOnClickListener { saveEvent() }
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
        when (recurrenceRule.frequency) {
            Event.FREQ_DAILY -> binding.chipDaily.isChecked = true
            Event.FREQ_WEEKLY -> binding.chipWeekly.isChecked = true
            Event.FREQ_MONTHLY -> binding.chipMonthly.isChecked = true
            Event.FREQ_YEARLY -> binding.chipYearly.isChecked = true
        }

        binding.intervalEditText.setText(recurrenceRule.interval.toString())
        binding.occurrencesValue.text = occurrences.toString()
        
        updateRepeatEndTypeUI()
    }

    private fun updateTimeVisibility() {
        val visibility = if (isAllDay) View.GONE else View.VISIBLE
        binding.startTimeRow.visibility = visibility
        binding.endTimeRow.visibility = visibility
    }

    private fun updateReminderText() {
        binding.reminderValue.text = when (reminderMinutes) {
            null -> getString(R.string.reminder_none)
            5 -> getString(R.string.reminder_5min)
            10 -> getString(R.string.reminder_10min)
            15 -> getString(R.string.reminder_15min)
            30 -> getString(R.string.reminder_30min)
            60 -> getString(R.string.reminder_1hour)
            1440 -> getString(R.string.reminder_1day)
            else -> "$reminderMinutes minutes before"
        }
    }

    private fun updateIntervalLabel() {
        binding.intervalUnitLabel.text = when (recurrenceRule.frequency) {
            Event.FREQ_DAILY -> getString(R.string.unit_day)
            Event.FREQ_WEEKLY -> getString(R.string.unit_week)
            Event.FREQ_MONTHLY -> getString(R.string.unit_month)
            Event.FREQ_YEARLY -> getString(R.string.unit_year)
            else -> getString(R.string.unit_week)
        }
    }

    private fun updateRepeatEndTypeUI() {
        binding.repeatEndType.text = when (repeatEndType) {
            RepeatEndType.ENDLESSLY -> getString(R.string.repeat_endlessly)
            RepeatEndType.UNTIL_DATE -> getString(R.string.repeat_until_date)
            RepeatEndType.REPEAT_COUNT -> getString(R.string.repeat_count)
        }
        binding.occurrencesContainer.visibility = 
            if (repeatEndType == RepeatEndType.REPEAT_COUNT) View.VISIBLE else View.GONE
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
            reminderMinutes = if (item.itemId == 0) null else item.itemId
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
            repeatEndType = when (item.itemId) {
                0 -> RepeatEndType.ENDLESSLY
                1 -> RepeatEndType.UNTIL_DATE
                else -> RepeatEndType.REPEAT_COUNT
            }
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

        val rrule = if (repeatEnabled) {
            val interval = binding.intervalEditText.text.toString().toIntOrNull() ?: 1
            RecurrenceRule(
                frequency = recurrenceRule.frequency,
                interval = interval,
                daysOfWeek = if (recurrenceRule.frequency == Event.FREQ_WEEKLY) selectedDaysOfWeek else emptySet(),
                endType = repeatEndType,
                count = if (repeatEndType == RepeatEndType.REPEAT_COUNT) occurrences else null
            ).toRRule()
        } else null

        val event = Event(
            id = existingEvent?.id ?: 0,
            calendarId = existingEvent?.calendarId ?: 0,
            title = title,
            description = binding.descriptionEditText.text.toString().trim(),
            location = binding.locationEditText.text.toString().trim(),
            startTime = startTime.timeInMillis,
            endTime = endTime.timeInMillis,
            isAllDay = isAllDay,
            reminderMinutes = reminderMinutes,
            rrule = rrule
        )

        onSaveListener?.invoke(event)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
