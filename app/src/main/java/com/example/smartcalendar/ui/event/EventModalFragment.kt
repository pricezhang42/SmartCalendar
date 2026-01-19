package com.example.smartcalendar.ui.event

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import com.example.smartcalendar.R
import com.example.smartcalendar.data.model.EventInstance
import com.example.smartcalendar.data.model.ICalEvent
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

    private var existingEvent: ICalEvent? = null
    private var instanceStartTime: Long? = null
    
    // UI state
    private var selectedDate = Calendar.getInstance()
    private var startTime = Calendar.getInstance()
    private var endTime = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
    private val selectedDaysOfWeek = mutableSetOf<String>()
    private var isAllDay = false
    private var repeatEnabled = false
    private var repeatFrequency = "WEEKLY"
    private var repeatInterval = 1
    private var reminderMinutes: Int? = null
    
    // Repeat end options
    private var repeatEndType = "NEVER" // NEVER, COUNT, UNTIL
    private var repeatCount = 10
    private var repeatUntil: Calendar? = null

    var onSaveListener: ((ICalEvent) -> Unit)? = null
    var onDeleteListener: ((String) -> Unit)? = null
    var onDeleteInstanceListener: ((String, Long) -> Unit)? = null
    var onDeleteFromInstanceListener: ((String, Long) -> Unit)? = null

    companion object {
        private const val ARG_EVENT_UID = "event_uid"
        private const val ARG_INSTANCE_TIME = "instance_time"
        private const val ARG_INITIAL_TIME = "initial_time"

        fun newInstance(event: ICalEvent? = null, instance: EventInstance? = null, initialTime: Long? = null): EventModalFragment {
            return EventModalFragment().apply {
                arguments = Bundle().apply {
                    event?.uid?.let { putString(ARG_EVENT_UID, it) }
                    instance?.startTime?.let { putLong(ARG_INSTANCE_TIME, it) }
                    initialTime?.let { putLong(ARG_INITIAL_TIME, it) }
                }
                existingEvent = event
                instanceStartTime = instance?.startTime
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventModalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews()
        setupListeners()
        updateUI()
    }

    private fun setupViews() {
        existingEvent?.let { event ->
            binding.titleEditText.setText(event.summary)
            binding.descriptionEditText.setText(event.description)
            binding.locationEditText.setText(event.location)
            
            // For recurring events, use the instance time, not master event time
            if (event.isRecurring && instanceStartTime != null) {
                val duration = event.getDurationMs()
                startTime.timeInMillis = instanceStartTime!!
                endTime.timeInMillis = instanceStartTime!! + duration
                selectedDate.timeInMillis = instanceStartTime!!
            } else {
                startTime.timeInMillis = event.dtStart
                endTime.timeInMillis = event.dtEnd
                selectedDate.timeInMillis = event.dtStart
            }
            
            isAllDay = event.allDay
            repeatEnabled = event.isRecurring
            
            // Parse RRULE for repeat options
            event.rrule?.let { parseRRule(it) }
            
            binding.deleteButton.visibility = View.VISIBLE
        } ?: run {
            // New event - use initial time from arguments
            arguments?.getLong(ARG_INITIAL_TIME)?.takeIf { it > 0 }?.let {
                startTime.timeInMillis = it
                endTime.timeInMillis = it + 3600000
                selectedDate.timeInMillis = it
            }
        }
        
        // Calendar picker - only "Personal" for now
        binding.calendarValue.text = "Personal"
        binding.calendarColor.setBackgroundColor(android.graphics.Color.parseColor("#4285F4"))

        binding.allDaySwitch.isChecked = isAllDay
        binding.repeatSwitch.isChecked = repeatEnabled
        binding.repeatOptionsContainer.visibility = if (repeatEnabled) View.VISIBLE else View.GONE

        // Auto-select current day of week for new events
        if (selectedDaysOfWeek.isEmpty()) {
            val dayCode = when (startTime.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> "SU"
                Calendar.MONDAY -> "MO"
                Calendar.TUESDAY -> "TU"
                Calendar.WEDNESDAY -> "WE"
                Calendar.THURSDAY -> "TH"
                Calendar.FRIDAY -> "FR"
                Calendar.SATURDAY -> "SA"
                else -> "SU"
            }
            selectedDaysOfWeek.add(dayCode)
        }

        setupDayOfWeekChips()
        updateRepeatEndUI()
    }

    private fun parseRRule(rrule: String) {
        rrule.split(";").forEach { part ->
            val parts = part.split("=")
            if (parts.size == 2) {
                when (parts[0]) {
                    "FREQ" -> repeatFrequency = parts[1]
                    "INTERVAL" -> repeatInterval = parts[1].toIntOrNull() ?: 1
                    "COUNT" -> {
                        repeatEndType = "COUNT"
                        repeatCount = parts[1].toIntOrNull() ?: 10
                    }
                    "UNTIL" -> {
                        repeatEndType = "UNTIL"
                        repeatUntil = parseUntilDate(parts[1])
                    }
                    "BYDAY" -> {
                        selectedDaysOfWeek.clear()
                        selectedDaysOfWeek.addAll(parts[1].split(","))
                    }
                }
            }
        }
    }
    
    private fun parseUntilDate(dateStr: String): Calendar? {
        return try {
            val format = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = format.parse(dateStr)
            Calendar.getInstance().apply { time = date }
        } catch (e: Exception) {
            null
        }
    }

    private fun setupDayOfWeekChips() {
        val days = listOf("S" to "SU", "M" to "MO", "T" to "TU", "W" to "WE", "T" to "TH", "F" to "FR", "S" to "SA")
        binding.daysOfWeekContainer.removeAllViews()

        days.forEach { (label, code) ->
            val chip = TextView(context).apply {
                text = label
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                val chipHeight = (40 * resources.displayMetrics.density).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(0, chipHeight, 1f).apply {
                    marginEnd = (4 * resources.displayMetrics.density).toInt()
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
        
        // Repeat frequency chips
        binding.chipDaily.setOnClickListener { setRepeatFrequency("DAILY") }
        binding.chipWeekly.setOnClickListener { setRepeatFrequency("WEEKLY") }
        binding.chipMonthly.setOnClickListener { setRepeatFrequency("MONTHLY") }
        binding.chipYearly.setOnClickListener { setRepeatFrequency("YEARLY") }

        // Repeat ends
        binding.repeatEndType.setOnClickListener { showRepeatEndPicker() }
        binding.occurrencesEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                repeatCount = s.toString().toIntOrNull() ?: 1
            }
        })

        binding.cancelButton.setOnClickListener { dismiss() }
        binding.saveButton.setOnClickListener { saveEvent() }
        binding.deleteButton.setOnClickListener { deleteEvent() }
    }
    
    private fun showRepeatEndPicker() {
        val popup = PopupMenu(requireContext(), binding.repeatEndType)
        popup.menu.add(0, 0, 0, getString(R.string.repeat_never))
        popup.menu.add(0, 1, 1, getString(R.string.repeat_count))
        popup.menu.add(0, 2, 2, getString(R.string.repeat_until))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> repeatEndType = "NEVER"
                1 -> repeatEndType = "COUNT"
                2 -> {
                    repeatEndType = "UNTIL"
                    showRepeatUntilDatePicker()
                }
            }
            updateRepeatEndUI()
            true
        }
        popup.show()
    }
    
    private fun showRepeatUntilDatePicker() {
        val cal = repeatUntil ?: Calendar.getInstance().apply { add(Calendar.MONTH, 1) }
        DatePickerDialog(requireContext(), { _, year, month, day ->
            repeatUntil = Calendar.getInstance().apply { set(year, month, day) }
            updateRepeatEndUI()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    
    private fun updateRepeatEndUI() {
        when (repeatEndType) {
            "NEVER" -> {
                binding.repeatEndType.text = getString(R.string.repeat_never)
                binding.occurrencesContainer.visibility = View.GONE
            }
            "COUNT" -> {
                binding.repeatEndType.text = getString(R.string.repeat_count)
                binding.occurrencesContainer.visibility = View.VISIBLE
                binding.occurrencesEditText.setText(repeatCount.toString())
            }
            "UNTIL" -> {
                val format = SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                val dateStr = repeatUntil?.let { format.format(it.time) } ?: getString(R.string.repeat_until)
                binding.repeatEndType.text = dateStr
                binding.occurrencesContainer.visibility = View.GONE
            }
        }
    }

    private fun setRepeatFrequency(freq: String) {
        repeatFrequency = freq
        updateFrequencyUI()
    }

    private fun updateFrequencyUI() {
        binding.chipDaily.isChecked = repeatFrequency == "DAILY"
        binding.chipWeekly.isChecked = repeatFrequency == "WEEKLY"
        binding.chipMonthly.isChecked = repeatFrequency == "MONTHLY"
        binding.chipYearly.isChecked = repeatFrequency == "YEARLY"
        // Show/hide days of week container
        binding.repeatOnContainer.visibility = if (repeatFrequency == "WEEKLY") View.VISIBLE else View.GONE
    }

    private fun updateUI() {
        val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        
        binding.dateValue.text = dateFormat.format(selectedDate.time)
        binding.startTimeValue.text = timeFormat.format(startTime.time)
        binding.endTimeValue.text = timeFormat.format(endTime.time)
        
        updateTimeVisibility()
        updateFrequencyUI()
        updateReminderText()
    }

    private fun updateTimeVisibility() {
        val visibility = if (isAllDay) View.GONE else View.VISIBLE
        binding.startTimeRow.visibility = visibility
        binding.endTimeRow.visibility = visibility
    }

    private fun updateReminderText() {
        binding.reminderValue.text = when (reminderMinutes) {
            null, 0 -> getString(R.string.reminder_none)
            5 -> getString(R.string.reminder_5min)
            10 -> getString(R.string.reminder_10min)
            15 -> getString(R.string.reminder_15min)
            30 -> getString(R.string.reminder_30min)
            60 -> getString(R.string.reminder_1hour)
            1440 -> getString(R.string.reminder_1day)
            else -> "$reminderMinutes min"
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(requireContext(), { _, year, month, day ->
            selectedDate.set(year, month, day)
            startTime.set(Calendar.YEAR, year)
            startTime.set(Calendar.MONTH, month)
            startTime.set(Calendar.DAY_OF_MONTH, day)
            endTime.set(Calendar.YEAR, year)
            endTime.set(Calendar.MONTH, month)
            endTime.set(Calendar.DAY_OF_MONTH, day)
            updateWeekdayForStartTime()
            updateUI()
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
    }
    
    private fun updateWeekdayForStartTime() {
        val dayCode = when (startTime.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "SU"
            Calendar.MONDAY -> "MO"
            Calendar.TUESDAY -> "TU"
            Calendar.WEDNESDAY -> "WE"
            Calendar.THURSDAY -> "TH"
            Calendar.FRIDAY -> "FR"
            Calendar.SATURDAY -> "SA"
            else -> "SU"
        }
        selectedDaysOfWeek.clear()
        selectedDaysOfWeek.add(dayCode)
        setupDayOfWeekChips()
    }

    private fun showTimePicker(isStart: Boolean) {
        val calendar = if (isStart) startTime else endTime
        TimePickerDialog(requireContext(), { _, hour, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            if (isStart && endTime.before(startTime)) {
                endTime.timeInMillis = startTime.timeInMillis
                endTime.add(Calendar.HOUR_OF_DAY, 1)
            }
            updateUI()
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }

    private fun showReminderPicker() {
        val popup = PopupMenu(requireContext(), binding.reminderRow)
        popup.menu.add(0, 0, 0, getString(R.string.reminder_none))
        popup.menu.add(0, 5, 1, getString(R.string.reminder_5min))
        popup.menu.add(0, 15, 2, getString(R.string.reminder_15min))
        popup.menu.add(0, 30, 3, getString(R.string.reminder_30min))
        popup.menu.add(0, 60, 4, getString(R.string.reminder_1hour))
        popup.setOnMenuItemClickListener { item ->
            reminderMinutes = if (item.itemId == 0) null else item.itemId
            updateReminderText()
            true
        }
        popup.show()
    }

    private fun saveEvent() {
        val title = binding.titleEditText.text.toString().trim()
        if (title.isEmpty()) {
            binding.titleEditText.error = "Title required"
            return
        }

        val rrule = if (repeatEnabled) buildRRule() else null
        val duration = if (repeatEnabled) ICalEvent.toDurationString(endTime.timeInMillis - startTime.timeInMillis) else null

        val event = ICalEvent(
            uid = existingEvent?.uid ?: UUID.randomUUID().toString(),
            summary = title,
            description = binding.descriptionEditText.text.toString().trim(),
            location = binding.locationEditText.text.toString().trim(),
            dtStart = startTime.timeInMillis,
            dtEnd = endTime.timeInMillis,
            duration = duration,
            allDay = isAllDay,
            rrule = rrule,
            exdate = existingEvent?.exdate,
            color = existingEvent?.color ?: android.graphics.Color.parseColor("#4285F4"),
            originalId = existingEvent?.originalId
        )

        // For recurring events being edited, show options
        if (existingEvent?.isRecurring == true && instanceStartTime != null) {
            showRecurringEditDialog(event)
        } else {
            onSaveListener?.invoke(event)
            dismiss()
        }
    }

    private fun buildRRule(): String {
        val sb = StringBuilder("FREQ=$repeatFrequency")
        if (repeatInterval > 1) {
            sb.append(";INTERVAL=$repeatInterval")
        }
        if (repeatFrequency == "WEEKLY" && selectedDaysOfWeek.isNotEmpty()) {
            sb.append(";BYDAY=${selectedDaysOfWeek.joinToString(",")}")
        }
        // Add repeat end
        when (repeatEndType) {
            "COUNT" -> sb.append(";COUNT=$repeatCount")
            "UNTIL" -> repeatUntil?.let { until ->
                val format = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sb.append(";UNTIL=${format.format(until.time)}")
            }
        }
        return sb.toString()
    }

    private fun showRecurringEditDialog(event: ICalEvent) {
        val options = arrayOf("This event", "This and following events", "All events")
        AlertDialog.Builder(requireContext())
            .setTitle("Edit recurring event")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // This event only - add exception and create new single event
                        onDeleteInstanceListener?.invoke(existingEvent!!.uid, instanceStartTime!!)
                        onSaveListener?.invoke(event.copy(uid = UUID.randomUUID().toString(), rrule = null, duration = null))
                    }
                    1 -> {
                        // This and following - end original series and save new
                        onDeleteFromInstanceListener?.invoke(existingEvent!!.uid, instanceStartTime!!)
                        onSaveListener?.invoke(event.copy(uid = UUID.randomUUID().toString()))
                    }
                    2 -> {
                        // All events - update the original
                        onSaveListener?.invoke(event)
                    }
                }
                dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteEvent() {
        val event = existingEvent ?: return
        
        if (event.isRecurring && instanceStartTime != null) {
            showRecurringDeleteDialog()
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete event")
                .setMessage("Are you sure you want to delete this event?")
                .setPositiveButton("Delete") { _, _ ->
                    onDeleteListener?.invoke(event.uid)
                    dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showRecurringDeleteDialog() {
        val options = arrayOf("This event", "This and following events", "All events")
        AlertDialog.Builder(requireContext())
            .setTitle("Delete recurring event")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onDeleteInstanceListener?.invoke(existingEvent!!.uid, instanceStartTime!!)
                    1 -> onDeleteFromInstanceListener?.invoke(existingEvent!!.uid, instanceStartTime!!)
                    2 -> onDeleteListener?.invoke(existingEvent!!.uid)
                }
                dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
