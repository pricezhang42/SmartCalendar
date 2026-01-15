package com.example.smartcalendar.ui.calendar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartcalendar.R
import com.example.smartcalendar.data.model.Event
import com.example.smartcalendar.data.repository.CalendarRepository
import com.example.smartcalendar.databinding.FragmentCalendarBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main calendar fragment with Month and Week views.
 */
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: CalendarRepository
    private var currentCalendar = Calendar.getInstance()
    private var events: List<Event> = emptyList()
    private var selectedCalendarIds: Set<Long> = emptySet()

    enum class ViewMode { MONTH, WEEK }
    var viewMode = ViewMode.MONTH
        set(value) {
            field = value
            updateViewMode()
        }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            loadCalendarData()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = CalendarRepository(requireContext())
        
        setupWeekdayHeaders()
        setupMonthGrid()
        checkPermissionsAndLoadData()
    }

    private fun checkPermissionsAndLoadData() {
        val readPermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_CALENDAR
        )
        val writePermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.WRITE_CALENDAR
        )

        if (readPermission == PackageManager.PERMISSION_GRANTED &&
            writePermission == PackageManager.PERMISSION_GRANTED) {
            loadCalendarData()
        } else {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            ))
        }
    }

    fun loadCalendarData() {
        // Get all calendars if none selected
        if (selectedCalendarIds.isEmpty()) {
            selectedCalendarIds = repository.getCalendars().map { it.id }.toSet()
        }

        // Calculate time range based on view mode
        val (start, end) = when (viewMode) {
            ViewMode.MONTH -> getMonthRange(currentCalendar)
            ViewMode.WEEK -> getWeekRange(currentCalendar)
        }

        events = repository.getEvents(start, end, selectedCalendarIds)
        updateView()
    }

    private fun getMonthRange(calendar: Calendar): Pair<Long, Long> {
        val start = calendar.clone() as Calendar
        start.set(Calendar.DAY_OF_MONTH, 1)
        start.set(Calendar.HOUR_OF_DAY, 0)
        start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)
        
        // Go back to start of week
        while (start.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            start.add(Calendar.DAY_OF_MONTH, -1)
        }

        val end = calendar.clone() as Calendar
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
        end.set(Calendar.HOUR_OF_DAY, 23)
        end.set(Calendar.MINUTE, 59)
        end.set(Calendar.SECOND, 59)
        
        // Go forward to end of week
        while (end.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            end.add(Calendar.DAY_OF_MONTH, 1)
        }

        return start.timeInMillis to end.timeInMillis
    }

    private fun getWeekRange(calendar: Calendar): Pair<Long, Long> {
        val start = calendar.clone() as Calendar
        start.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        start.set(Calendar.HOUR_OF_DAY, 0)
        start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)

        val end = calendar.clone() as Calendar
        end.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
        end.set(Calendar.HOUR_OF_DAY, 23)
        end.set(Calendar.MINUTE, 59)
        end.set(Calendar.SECOND, 59)

        return start.timeInMillis to end.timeInMillis
    }

    private fun setupWeekdayHeaders() {
        val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        binding.weekdayHeaderRow.removeAllViews()
        
        weekdays.forEach { day ->
            val textView = TextView(context).apply {
                text = day
                textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(if (day == "Sun" || day == "Sat") 
                    ContextCompat.getColor(requireContext(), R.color.weekend_text) 
                    else Color.GRAY)
            }
            binding.weekdayHeaderRow.addView(textView)
        }
    }

    private fun setupMonthGrid() {
        binding.monthRecyclerView.layoutManager = GridLayoutManager(context, 7)
        updateMonthView()
    }

    private fun updateView() {
        when (viewMode) {
            ViewMode.MONTH -> updateMonthView()
            ViewMode.WEEK -> updateWeekView()
        }
    }

    private fun updateViewMode() {
        when (viewMode) {
            ViewMode.MONTH -> {
                binding.monthViewContainer.visibility = View.VISIBLE
                binding.weekViewContainer.visibility = View.GONE
                binding.weekHeader.visibility = View.GONE
            }
            ViewMode.WEEK -> {
                binding.monthViewContainer.visibility = View.GONE
                binding.weekViewContainer.visibility = View.VISIBLE
                binding.weekHeader.visibility = View.VISIBLE
            }
        }
        loadCalendarData()
    }

    private fun updateMonthView() {
        val days = generateMonthDays()
        binding.monthRecyclerView.adapter = MonthAdapter(days, events, currentCalendar) { day ->
            // On day click, switch to that week
            currentCalendar.set(Calendar.DAY_OF_MONTH, day.dayOfMonth)
            currentCalendar.set(Calendar.MONTH, day.month)
            currentCalendar.set(Calendar.YEAR, day.year)
            viewMode = ViewMode.WEEK
        }
    }

    private fun generateMonthDays(): List<DayItem> {
        val days = mutableListOf<DayItem>()
        val (startTime, endTime) = getMonthRange(currentCalendar)
        
        val cal = Calendar.getInstance()
        cal.timeInMillis = startTime

        val endCal = Calendar.getInstance()
        endCal.timeInMillis = endTime

        while (cal.before(endCal) || cal.timeInMillis == endCal.timeInMillis) {
            days.add(DayItem(
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                month = cal.get(Calendar.MONTH),
                year = cal.get(Calendar.YEAR),
                isCurrentMonth = cal.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH),
                isToday = isToday(cal)
            ))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return days
    }

    private fun isToday(cal: Calendar): Boolean {
        val today = Calendar.getInstance()
        return cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
               cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }

    private fun updateWeekView() {
        setupWeekHeader()
        setupTimeLabels()
        setupWeekColumns()
    }

    private fun setupWeekHeader() {
        val weekNum = currentCalendar.get(Calendar.WEEK_OF_YEAR)
        binding.weekLabel.text = "Week\n$weekNum"

        binding.weekDaysContainer.removeAllViews()
        val (startTime, _) = getWeekRange(currentCalendar)
        val cal = Calendar.getInstance()
        cal.timeInMillis = startTime

        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dateFormat = SimpleDateFormat("d", Locale.getDefault())
        val today = Calendar.getInstance()

        for (i in 0..6) {
            val dayLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val isToday = cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                          cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)

            val dayLabel = TextView(context).apply {
                text = dayFormat.format(cal.time).uppercase().take(3)
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (isToday) ContextCompat.getColor(requireContext(), R.color.primary_blue) else Color.GRAY)
            }

            val dateLabel = TextView(context).apply {
                text = dateFormat.format(cal.time)
                textSize = 16f
                gravity = Gravity.CENTER
                if (isToday) {
                    setBackgroundResource(R.drawable.circle_button_background)
                    setTextColor(Color.WHITE)
                    setPadding(16, 8, 16, 8)
                } else {
                    setTextColor(Color.BLACK)
                }
            }

            dayLayout.addView(dayLabel)
            dayLayout.addView(dateLabel)
            binding.weekDaysContainer.addView(dayLayout)

            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun setupTimeLabels() {
        binding.timeLabelsColumn.removeAllViews()
        val hourHeight = resources.getDimensionPixelSize(R.dimen.hour_height)

        for (hour in 1..23) {
            val label = TextView(context).apply {
                text = String.format("%d %s", if (hour > 12) hour - 12 else hour, if (hour >= 12) "PM" else "AM")
                textSize = 10f
                gravity = Gravity.END or Gravity.TOP
                setTextColor(Color.GRAY)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    hourHeight
                )
                setPadding(4, 0, 8, 0)
            }
            binding.timeLabelsColumn.addView(label)
        }
    }

    private fun setupWeekColumns() {
        binding.weekDaysColumns.removeAllViews()
        val hourHeight = resources.getDimensionPixelSize(R.dimen.hour_height)
        val (startTime, _) = getWeekRange(currentCalendar)
        val cal = Calendar.getInstance()
        cal.timeInMillis = startTime

        for (dayIndex in 0..6) {
            val dayColumn = FrameLayoutCompat(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Draw hour lines
            val linesContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            for (hour in 1..23) {
                val line = View(context).apply {
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        hourHeight
                    ).apply {
                        topMargin = if (hour == 1) 0 else 0
                    }
                }
                linesContainer.addView(line)
            }
            dayColumn.addView(linesContainer)

            // Add events for this day
            val dayStart = cal.clone() as Calendar
            dayStart.set(Calendar.HOUR_OF_DAY, 0)
            dayStart.set(Calendar.MINUTE, 0)
            dayStart.set(Calendar.SECOND, 0)
            
            val dayEnd = cal.clone() as Calendar
            dayEnd.set(Calendar.HOUR_OF_DAY, 23)
            dayEnd.set(Calendar.MINUTE, 59)
            dayEnd.set(Calendar.SECOND, 59)

            val dayEvents = events.filter { event ->
                event.startTime >= dayStart.timeInMillis && event.startTime <= dayEnd.timeInMillis
            }

            dayEvents.forEach { event ->
                val eventView = createEventView(event, hourHeight)
                dayColumn.addView(eventView)
            }

            binding.weekDaysColumns.addView(dayColumn)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun createEventView(event: Event, hourHeight: Int): View {
        val eventCal = Calendar.getInstance()
        eventCal.timeInMillis = event.startTime
        val startHour = eventCal.get(Calendar.HOUR_OF_DAY)
        val startMinute = eventCal.get(Calendar.MINUTE)

        eventCal.timeInMillis = event.endTime
        val endHour = eventCal.get(Calendar.HOUR_OF_DAY)
        val endMinute = eventCal.get(Calendar.MINUTE)

        val topOffset = ((startHour - 1) * hourHeight + (startMinute * hourHeight / 60f)).toInt()
        val duration = ((endHour - startHour) * hourHeight + ((endMinute - startMinute) * hourHeight / 60f)).toInt()

        return TextView(context).apply {
            text = event.title
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(if (event.color != 0) event.color 
                else ContextCompat.getColor(requireContext(), R.color.event_purple))
            setPadding(8, 4, 8, 4)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                duration.coerceAtLeast(hourHeight / 2)
            ).apply {
                topMargin = topOffset.coerceAtLeast(0)
                marginStart = 2
                marginEnd = 2
            }
            setOnClickListener {
                (activity as? OnEventClickListener)?.onEventClick(event)
            }
        }
    }

    fun setSelectedCalendars(ids: Set<Long>) {
        selectedCalendarIds = ids
        loadCalendarData()
    }

    fun navigateMonth(offset: Int) {
        currentCalendar.add(Calendar.MONTH, offset)
        loadCalendarData()
    }

    fun navigateWeek(offset: Int) {
        currentCalendar.add(Calendar.WEEK_OF_YEAR, offset)
        loadCalendarData()
    }

    fun getTitle(): String {
        val format = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        return format.format(currentCalendar.time)
    }

    fun getCurrentTimeMillis(): Long = currentCalendar.timeInMillis

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    interface OnEventClickListener {
        fun onEventClick(event: Event)
    }

    // Simple FrameLayout that works with context
    inner class FrameLayoutCompat(context: android.content.Context?) : android.widget.FrameLayout(context!!)
}

data class DayItem(
    val dayOfMonth: Int,
    val month: Int,
    val year: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean
)

/**
 * Adapter for month view grid
 */
class MonthAdapter(
    private val days: List<DayItem>,
    private val events: List<Event>,
    private val currentCalendar: Calendar,
    private val onDayClick: (DayItem) -> Unit
) : RecyclerView.Adapter<MonthAdapter.DayViewHolder>() {

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayNumber: TextView = view.findViewById(R.id.dayNumber)
        val eventsContainer: LinearLayout = view.findViewById(R.id.eventsContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = days[position]
        val context = holder.itemView.context

        holder.dayNumber.text = day.dayOfMonth.toString()

        // Style based on day type
        when {
            day.isToday -> {
                holder.dayNumber.setBackgroundResource(R.drawable.circle_button_background)
                holder.dayNumber.setTextColor(Color.WHITE)
            }
            !day.isCurrentMonth -> {
                holder.dayNumber.setBackgroundColor(Color.TRANSPARENT)
                holder.dayNumber.setTextColor(Color.LTGRAY)
            }
            else -> {
                holder.dayNumber.setBackgroundColor(Color.TRANSPARENT)
                holder.dayNumber.setTextColor(Color.BLACK)
            }
        }

        // Add events for this day
        holder.eventsContainer.removeAllViews()
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, day.year)
            set(Calendar.MONTH, day.month)
            set(Calendar.DAY_OF_MONTH, day.dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val dayEnd = dayStart.clone() as Calendar
        dayEnd.set(Calendar.HOUR_OF_DAY, 23)
        dayEnd.set(Calendar.MINUTE, 59)
        dayEnd.set(Calendar.SECOND, 59)

        val dayEvents = events.filter { event ->
            event.startTime >= dayStart.timeInMillis && event.startTime <= dayEnd.timeInMillis
        }.take(3) // Show max 3 events per day in month view

        dayEvents.forEach { event ->
            val chip = LayoutInflater.from(context)
                .inflate(R.layout.item_event_chip, holder.eventsContainer, false) as TextView
            chip.text = event.title
            if (event.color != 0) {
                chip.background.setTint(event.color)
            }
            holder.eventsContainer.addView(chip)
        }

        holder.itemView.setOnClickListener {
            onDayClick(day)
        }
    }

    override fun getItemCount(): Int = days.size
}
