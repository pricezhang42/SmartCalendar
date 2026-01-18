package com.example.smartcalendar.ui.calendar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.smartcalendar.R
import com.example.smartcalendar.data.model.Event
import com.example.smartcalendar.data.repository.CalendarRepository
import com.example.smartcalendar.databinding.FragmentCalendarBinding
import com.example.smartcalendar.databinding.ItemCalendarDayBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main calendar fragment with Month and Week views supporting swipe navigation.
 */
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: CalendarRepository
    private var currentCalendar = Calendar.getInstance()
    private var selectedCalendarIds: Set<Long> = emptySet()

    // ViewPager position constants - 500 is "center" position representing current month/week
    private val centerPosition = 500

    enum class ViewMode { MONTH, WEEK }
    var viewMode = ViewMode.MONTH
        set(value) {
            field = value
            updateViewMode()
        }

    private var onTitleChangeListener: ((String) -> Unit)? = null

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
        setupMonthViewPager()
        setupWeekViewPager()
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
        if (selectedCalendarIds.isEmpty()) {
            selectedCalendarIds = repository.getCalendars().map { it.id }.toSet()
        }
        
        // Refresh only the current visible page for efficiency
        val monthPosition = binding.monthViewPager.currentItem
        val weekPosition = binding.weekViewPager.currentItem
        
        // Use targeted notifications instead of notifyDataSetChanged()
        binding.monthViewPager.adapter?.notifyItemChanged(monthPosition)
        binding.weekViewPager.adapter?.notifyItemChanged(weekPosition)
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
                    else Color.DKGRAY)
            }
            binding.weekdayHeaderRow.addView(textView)
        }
    }

    private fun setupMonthViewPager() {
        binding.monthViewPager.adapter = MonthPagerAdapter()
        binding.monthViewPager.setCurrentItem(centerPosition, false)
        
        binding.monthViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val offset = position - centerPosition
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, offset)
                currentCalendar = cal
                updateTitle()
            }
        })
    }

    private fun setupWeekViewPager() {
        binding.weekViewPager.adapter = WeekPagerAdapter()
        binding.weekViewPager.setCurrentItem(centerPosition, false)
        
        binding.weekViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val offset = position - centerPosition
                val cal = Calendar.getInstance()
                cal.add(Calendar.WEEK_OF_YEAR, offset)
                currentCalendar = cal
                updateWeekHeader(cal)
                updateTitle()
            }
        })
    }

    private fun updateViewMode() {
        when (viewMode) {
            ViewMode.MONTH -> {
                binding.monthViewContainer.visibility = View.VISIBLE
                binding.weekViewContainer.visibility = View.GONE
            }
            ViewMode.WEEK -> {
                binding.monthViewContainer.visibility = View.GONE
                binding.weekViewContainer.visibility = View.VISIBLE
                updateWeekHeader(currentCalendar)
                setupFixedTimeLabels()
            }
        }
        updateTitle()
    }

    private fun setupFixedTimeLabels() {
        binding.fixedTimeLabelsColumn.removeAllViews()
        val hourHeight = resources.getDimensionPixelSize(R.dimen.hour_height)

        for (hour in 0..23) {
            val label = TextView(context).apply {
                text = when (hour) {
                    0 -> "12 AM"
                    12 -> "12 PM"
                    in 1..11 -> "$hour AM"
                    else -> "${hour - 12} PM"
                }
                textSize = 10f
                gravity = Gravity.END or Gravity.TOP
                setTextColor(Color.GRAY)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    hourHeight
                )
                setPadding(4, 0, 4, 0)
            }
            binding.fixedTimeLabelsColumn.addView(label)
        }
    }

    private fun updateWeekHeader(calendar: Calendar) {
        val weekNum = calendar.get(Calendar.WEEK_OF_YEAR)
        binding.weekLabel.text = "Week\n$weekNum"

        binding.weekDaysContainer.removeAllViews()
        val weekStart = getWeekStart(calendar)
        val cal = weekStart.clone() as Calendar

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

                // 1. Define a fixed size (e.g., 40dp)
                val sizeInDp = 33
                val sizeInPx = (sizeInDp * resources.displayMetrics.density).toInt()

                // 2. Force the layout params to be a square
                layoutParams = LinearLayout.LayoutParams(sizeInPx, sizeInPx).apply {
                    gravity = Gravity.CENTER_HORIZONTAL // Center the square in the column
                }

                if (isToday) {
                    setBackgroundResource(R.drawable.circle_button_background)
                    setTextColor(Color.WHITE)
                } else {
                    setTextColor(Color.BLACK)
                    background = null // Clear background for other days
                }
            }

            dayLayout.addView(dayLabel)
            dayLayout.addView(dateLabel)
            binding.weekDaysContainer.addView(dayLayout)

            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun getWeekStart(calendar: Calendar): Calendar {
        val start = calendar.clone() as Calendar
        start.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        start.set(Calendar.HOUR_OF_DAY, 0)
        start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)
        return start
    }

    private fun getWeekEnd(calendar: Calendar): Calendar {
        val end = calendar.clone() as Calendar
        end.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
        end.set(Calendar.HOUR_OF_DAY, 23)
        end.set(Calendar.MINUTE, 59)
        end.set(Calendar.SECOND, 59)
        return end
    }

    private fun updateTitle() {
        val format = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        onTitleChangeListener?.invoke(format.format(currentCalendar.time))
    }

    fun setOnTitleChangeListener(listener: (String) -> Unit) {
        onTitleChangeListener = listener
        updateTitle()
    }

    fun setSelectedCalendars(ids: Set<Long>) {
        selectedCalendarIds = ids
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

    // Month Pager Adapter
    inner class MonthPagerAdapter : RecyclerView.Adapter<MonthPagerAdapter.MonthViewHolder>() {
        
        inner class MonthViewHolder(val recyclerView: RecyclerView) : RecyclerView.ViewHolder(recyclerView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.page_month, parent, false) as RecyclerView

            // FORCE the RecyclerView to fill the entire ViewPager container
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            view.layoutManager = GridLayoutManager(parent.context, 7)
            return MonthViewHolder(view)
        }

        override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
            val offset = position - centerPosition
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, offset)
            
            val days = generateMonthDays(cal)
            val events = getEventsForMonth(cal)
            
            // Use post to ensure RecyclerView is measured before calculating row height
            holder.recyclerView.post {
                val parentHeight = holder.recyclerView.height
                val numRows = (days.size + 6) / 7 // Ceiling division
                
                holder.recyclerView.adapter = MonthGridAdapter(days, events, parentHeight, numRows) { event ->
                    (activity as? OnEventClickListener)?.onEventClick(event)
                }
            }
        }

        override fun getItemCount(): Int = 1000 // Large number for "infinite" scroll

        private fun generateMonthDays(calendar: Calendar): List<DayItem> {
            val days = mutableListOf<DayItem>()
            
            val monthStart = calendar.clone() as Calendar
            monthStart.set(Calendar.DAY_OF_MONTH, 1)
            monthStart.set(Calendar.HOUR_OF_DAY, 0)
            monthStart.set(Calendar.MINUTE, 0)
            
            // Go back to Sunday
            while (monthStart.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                monthStart.add(Calendar.DAY_OF_MONTH, -1)
            }

            val monthEnd = calendar.clone() as Calendar
            monthEnd.set(Calendar.DAY_OF_MONTH, monthEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
            
            // Go forward to Saturday
            while (monthEnd.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                monthEnd.add(Calendar.DAY_OF_MONTH, 1)
            }

            val cal = monthStart.clone() as Calendar
            val today = Calendar.getInstance()
            
            while (!cal.after(monthEnd)) {
                days.add(DayItem(
                    dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                    month = cal.get(Calendar.MONTH),
                    year = cal.get(Calendar.YEAR),
                    isCurrentMonth = cal.get(Calendar.MONTH) == calendar.get(Calendar.MONTH),
                    isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                              cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                ))
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            return days
        }

        private fun getEventsForMonth(calendar: Calendar): List<Event> {
            val start = calendar.clone() as Calendar
            start.set(Calendar.DAY_OF_MONTH, 1)
            start.add(Calendar.DAY_OF_MONTH, -7)
            start.set(Calendar.HOUR_OF_DAY, 0)
            start.set(Calendar.MINUTE, 0)
            
            val end = calendar.clone() as Calendar
            end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
            end.add(Calendar.DAY_OF_MONTH, 7)
            end.set(Calendar.HOUR_OF_DAY, 23)
            end.set(Calendar.MINUTE, 59)

            val events_ = repository.getEvents(start.timeInMillis, end.timeInMillis, selectedCalendarIds)
            val events = repository.getEventInstances(start.timeInMillis, end.timeInMillis, selectedCalendarIds)
            return events
        }
    }

    // Week Pager Adapter
    inner class WeekPagerAdapter : RecyclerView.Adapter<WeekPagerAdapter.WeekViewHolder>() {
        
        inner class WeekViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val weekDaysColumns: LinearLayout = itemView.findViewById(R.id.weekDaysColumns)
            val scrollView: ScrollView = itemView as ScrollView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.page_week, parent, false)
            return WeekViewHolder(view)
        }

        override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
            val offset = position - centerPosition
            val cal = Calendar.getInstance()
            cal.add(Calendar.WEEK_OF_YEAR, offset)
            
            setupWeekColumns(holder.weekDaysColumns, cal)
            
            // Sync scroll between fixed time labels and this page
            holder.scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                binding.fixedTimeLabelsScroll.scrollTo(0, scrollY)
            }
        }

        override fun getItemCount(): Int = 1000

        private fun setupWeekColumns(container: LinearLayout, calendar: Calendar) {
            container.removeAllViews()
            val hourHeight = resources.getDimensionPixelSize(R.dimen.hour_height)
            val weekStart = getWeekStart(calendar)
            val weekEnd = getWeekEnd(calendar)
            
            val events = repository.getEventInstances(weekStart.timeInMillis, weekEnd.timeInMillis, selectedCalendarIds)
            val cal = weekStart.clone() as Calendar

            for (dayIndex in 0..6) {
                // Wrap day column in a horizontal layout to add vertical divider
                val dayWrapper = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                // Add vertical divider line (for all days to create grid)
                val verticalDivider = View(requireContext()).apply {
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))
                    layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
                }
                dayWrapper.addView(verticalDivider)

                val dayColumn = FrameLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                // Draw hour grid lines with white background and bottom border
                val linesContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Color.WHITE)
                }
                for (hour in 0..23) {
                    val hourCell = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            hourHeight
                        )
                    }
                    // Add a thin line at the top of each hour
                    val line = View(context).apply {
                        setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1 // 1px thin line
                        )
                    }
                    hourCell.addView(line)
                    linesContainer.addView(hourCell)
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
                }.sortedBy { it.startTime }

                // Calculate layout positions for overlapping events
                val eventLayouts = calculateEventLayouts(dayEvents)
                
                eventLayouts.forEach { layout ->
                    val eventView = createEventView(layout.event, hourHeight, layout.column, layout.totalColumns)
                    dayColumn.addView(eventView)
                }

                // Add dayColumn to wrapper and wrapper to container
                dayWrapper.addView(dayColumn)
                container.addView(dayWrapper)
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        /**
         * Calculate layout positions for overlapping events.
         * Returns a list of EventLayout with column position and total columns for each event.
         */
        private fun calculateEventLayouts(events: List<Event>): List<EventLayout> {
            if (events.isEmpty()) return emptyList()
            
            val layouts = mutableListOf<EventLayout>()
            val activeEvents = mutableListOf<EventLayout>()
            
            events.forEach { event ->
                // Remove events that have ended before this one starts
                activeEvents.removeAll { it.event.endTime <= event.startTime }
                
                // Find the first available column
                val usedColumns = activeEvents.map { it.column }.toSet()
                var column = 0
                while (column in usedColumns) {
                    column++
                }
                
                val layout = EventLayout(event, column, 1)
                activeEvents.add(layout)
                layouts.add(layout)
            }
            
            // Second pass: calculate total columns for each group of overlapping events
            layouts.forEach { layout ->
                val overlapping = layouts.filter { other ->
                    layout.event.startTime < other.event.endTime && 
                    layout.event.endTime > other.event.startTime
                }
                layout.totalColumns = (overlapping.maxOfOrNull { it.column } ?: 0) + 1
            }
            
            return layouts
        }

        private fun createEventView(event: Event, hourHeight: Int, column: Int, totalColumns: Int): View {
            val eventCal = Calendar.getInstance()
            eventCal.timeInMillis = event.startTime
            val startHour = eventCal.get(Calendar.HOUR_OF_DAY)
            val startMinute = eventCal.get(Calendar.MINUTE)

            eventCal.timeInMillis = event.endTime
            val endHour = eventCal.get(Calendar.HOUR_OF_DAY)
            val endMinute = eventCal.get(Calendar.MINUTE)

            val topOffset = (startHour * hourHeight + (startMinute * hourHeight / 60f)).toInt()
            val durationHours = (event.endTime - event.startTime) / 3600000f
            val height = (durationHours * hourHeight).toInt().coerceAtLeast(hourHeight / 2)

            return TextView(context).apply {
                text = event.title
                textSize = 10f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.event_chip_background)
                background.setTint(
                    if (event.color != 0) event.color 
                    else ContextCompat.getColor(requireContext(), R.color.event_purple)
                )
                setPadding(8, 4, 8, 4)
                
                // Calculate width and horizontal position for overlapping events
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    height
                ).apply {
                    topMargin = topOffset
                    marginStart = 2
                    marginEnd = 2
                    
                    // If there are multiple columns, divide the width
                    if (totalColumns > 1) {
                        // Use weight-like calculation: each event gets 1/totalColumns of the width
                        // Position is based on column index
                        width = 0 // Will be set programmatically
                    }
                }
                
                // For overlapping events, we need to set width and position after layout
                if (totalColumns > 1) {
                    post {
                        val parentWidth = (parent as? View)?.width ?: 0
                        if (parentWidth > 0) {
                            val gap = 1 // 1px gap between events
                            val totalGaps = totalColumns - 1
                            val availableWidth = parentWidth - (totalGaps * gap)
                            val eventWidth = availableWidth / totalColumns
                            val leftPosition = column * (eventWidth + gap)
                            
                            layoutParams = FrameLayout.LayoutParams(eventWidth, height).apply {
                                topMargin = topOffset
                                marginStart = leftPosition
                            }
                        }
                    }
                }
                
                setOnClickListener {
                    (activity as? OnEventClickListener)?.onEventClick(event)
                }
            }
        }
    }
}

data class DayItem(
    val dayOfMonth: Int,
    val month: Int,
    val year: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean
)

/**
 * Helper class for event layout calculation in week view
 */
data class EventLayout(
    val event: Event,
    val column: Int,
    var totalColumns: Int
)

/**
 * Adapter for month grid within each page
 */
class MonthGridAdapter(
    private val days: List<DayItem>,
    private val events: List<Event>,
    private val parentHeight: Int,
    private val numRows: Int,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<MonthGridAdapter.DayViewHolder>() {

    private val rowHeight: Int = if (numRows > 0 && parentHeight > 0) parentHeight / numRows else 0

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayNumber: TextView = view.findViewById(R.id.dayNumber)
        val eventsContainer: LinearLayout = view.findViewById(R.id.eventsContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val binding = ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DayViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = days[position]
        val context = holder.itemView.context

        // Apply calculated row height to fill the grid
        if (rowHeight > 0) {
            holder.itemView.layoutParams.height = rowHeight
        }

        holder.dayNumber.text = day.dayOfMonth.toString()

        // Style based on day type
        when {
            day.isToday -> {
                holder.dayNumber.setBackgroundResource(R.drawable.circle_button_background)
                holder.dayNumber.setTextColor(Color.WHITE)
            }
            !day.isCurrentMonth -> {
                // Use 0 or null to remove the background resource entirely
                holder.dayNumber.background = null
                holder.dayNumber.setTextColor(Color.LTGRAY)
            }
            else -> {
                holder.dayNumber.background = null
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
        }

        dayEvents.forEach { event ->
            val chip = LayoutInflater.from(context)
                .inflate(R.layout.item_event_chip, holder.eventsContainer, false) as TextView
            chip.text = event.title
            if (event.color != 0) {
                chip.background.setTint(event.color)
            }
            // Make event chips clickable for editing
            chip.setOnClickListener {
                onEventClick(event)
            }
            holder.eventsContainer.addView(chip)
        }
    }

    override fun getItemCount(): Int = days.size
}
