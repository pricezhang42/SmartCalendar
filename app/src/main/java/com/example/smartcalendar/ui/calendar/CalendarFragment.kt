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
            }
        }
        updateTitle()
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
            view.layoutManager = GridLayoutManager(parent.context, 7)
            view.isNestedScrollingEnabled = false
            return MonthViewHolder(view)
        }

        override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
            val offset = position - centerPosition
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, offset)
            
            val days = generateMonthDays(cal)
            val events = getEventsForMonth(cal)
            
            holder.recyclerView.adapter = MonthGridAdapter(days, events) { day ->
                currentCalendar.set(Calendar.DAY_OF_MONTH, day.dayOfMonth)
                currentCalendar.set(Calendar.MONTH, day.month)
                currentCalendar.set(Calendar.YEAR, day.year)
                viewMode = ViewMode.WEEK
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
            
            return repository.getEvents(start.timeInMillis, end.timeInMillis, selectedCalendarIds)
        }
    }

    // Week Pager Adapter
    inner class WeekPagerAdapter : RecyclerView.Adapter<WeekPagerAdapter.WeekViewHolder>() {
        
        inner class WeekViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val timeLabelsColumn: LinearLayout = itemView.findViewById(R.id.timeLabelsColumn)
            val weekDaysColumns: LinearLayout = itemView.findViewById(R.id.weekDaysColumns)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.page_week, parent, false)
            return WeekViewHolder(view)
        }

        override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
            val offset = position - centerPosition
            val cal = Calendar.getInstance()
            cal.add(Calendar.WEEK_OF_YEAR, offset)
            
            setupTimeLabels(holder.timeLabelsColumn)
            setupWeekColumns(holder.weekDaysColumns, cal)
        }

        override fun getItemCount(): Int = 1000

        private fun setupTimeLabels(container: LinearLayout) {
            container.removeAllViews()
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
                    setPadding(4, 0, 8, 0)
                }
                container.addView(label)
            }
        }

        private fun setupWeekColumns(container: LinearLayout, calendar: Calendar) {
            container.removeAllViews()
            val hourHeight = resources.getDimensionPixelSize(R.dimen.hour_height)
            val weekStart = getWeekStart(calendar)
            val weekEnd = getWeekEnd(calendar)
            
            val events = repository.getEvents(weekStart.timeInMillis, weekEnd.timeInMillis, selectedCalendarIds)
            val cal = weekStart.clone() as Calendar

            for (dayIndex in 0..6) {
                val dayColumn = FrameLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                // Draw hour grid lines
                val linesContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                for (hour in 0..23) {
                    val line = View(context).apply {
                        setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            hourHeight
                        )
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

                container.addView(dayColumn)
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
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    height
                ).apply {
                    topMargin = topOffset
                    marginStart = 2
                    marginEnd = 2
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
 * Adapter for month grid within each page
 */
class MonthGridAdapter(
    private val days: List<DayItem>,
    private val events: List<Event>,
    private val onDayClick: (DayItem) -> Unit
) : RecyclerView.Adapter<MonthGridAdapter.DayViewHolder>() {

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
            holder.eventsContainer.addView(chip)
        }

        holder.itemView.setOnClickListener {
            onDayClick(day)
        }
    }

    override fun getItemCount(): Int = days.size
}
