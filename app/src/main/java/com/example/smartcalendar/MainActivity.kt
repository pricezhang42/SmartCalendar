package com.example.smartcalendar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.smartcalendar.data.model.CalendarAccount
import com.example.smartcalendar.data.model.Event
import com.example.smartcalendar.data.repository.CalendarRepository
import com.example.smartcalendar.databinding.ActivityMainBinding
import com.example.smartcalendar.ui.calendar.CalendarFragment
import com.example.smartcalendar.ui.event.EventModalFragment

class MainActivity : AppCompatActivity(), CalendarFragment.OnEventClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var repository: CalendarRepository
    
    private val calendarAccounts = mutableListOf<CalendarAccount>()
    private val selectedCalendarIds = mutableSetOf<Long>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            loadCalendars()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = getString(R.string.view_month)

        repository = CalendarRepository(this)

        setupNavigation()
        setupDrawer()
        setupFab()
        checkPermissionsAndLoad()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController

        // Setup bottom navigation
        binding.bottomNav.setupWithNavController(navController)

        // Update toolbar title based on destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.CalendarFragment -> {
                    val fragment = getCurrentCalendarFragment()
                    supportActionBar?.title = fragment?.getTitle() ?: getString(R.string.view_month)
                    // Register for title changes when swiping
                    fragment?.setOnTitleChangeListener { title ->
                        supportActionBar?.title = title
                    }
                }
                R.id.MineFragment -> {
                    supportActionBar?.title = getString(R.string.nav_mine)
                }
            }
        }
    }

    private fun setupDrawer() {
        // Toolbar navigation icon opens drawer
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // View mode options
        binding.navView.findViewById<LinearLayout>(R.id.weekOption)?.setOnClickListener {
            getCurrentCalendarFragment()?.let { fragment ->
                fragment.viewMode = CalendarFragment.ViewMode.WEEK
                supportActionBar?.title = fragment.getTitle()
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            updateViewModeSelection(isMonth = false)
        }

        binding.navView.findViewById<LinearLayout>(R.id.monthOption)?.setOnClickListener {
            getCurrentCalendarFragment()?.let { fragment ->
                fragment.viewMode = CalendarFragment.ViewMode.MONTH
                supportActionBar?.title = fragment.getTitle()
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            updateViewModeSelection(isMonth = true)
        }

        // Show All button
        binding.navView.findViewById<View>(R.id.showAllButton)?.setOnClickListener {
            selectedCalendarIds.clear()
            selectedCalendarIds.addAll(calendarAccounts.map { it.id })
            updateCalendarCheckboxes()
            getCurrentCalendarFragment()?.setSelectedCalendars(selectedCalendarIds)
        }

        // Refresh button
        binding.navView.findViewById<View>(R.id.refreshButton)?.setOnClickListener {
            loadCalendars()
        }
    }

    private fun updateViewModeSelection(isMonth: Boolean) {
        val monthOption = binding.navView.findViewById<LinearLayout>(R.id.monthOption)
        val weekOption = binding.navView.findViewById<LinearLayout>(R.id.weekOption)

        if (isMonth) {
            monthOption?.setBackgroundColor(ContextCompat.getColor(this, R.color.light_blue_bg))
            weekOption?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        } else {
            monthOption?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            weekOption?.setBackgroundColor(ContextCompat.getColor(this, R.color.light_blue_bg))
        }
    }

    private fun setupFab() {
        binding.fab.setOnClickListener {
            val fragment = getCurrentCalendarFragment()
            val initialTime = fragment?.getCurrentTimeMillis() ?: System.currentTimeMillis()
            showEventModal(null, initialTime)
        }
    }

    private fun checkPermissionsAndLoad() {
        val readPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CALENDAR
        )
        val writePermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.WRITE_CALENDAR
        )

        if (readPermission == PackageManager.PERMISSION_GRANTED &&
            writePermission == PackageManager.PERMISSION_GRANTED) {
            loadCalendars()
        } else {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            ))
        }
    }

    private fun loadCalendars() {
        calendarAccounts.clear()
        calendarAccounts.addAll(repository.getCalendars())
        
        // Select all by default
        if (selectedCalendarIds.isEmpty()) {
            selectedCalendarIds.addAll(calendarAccounts.map { it.id })
        }

        populateCalendarList()
    }

    private fun populateCalendarList() {
        val container = binding.navView.findViewById<LinearLayout>(R.id.calendarListContainer)
        container?.removeAllViews()

        calendarAccounts.forEach { account ->
            val itemView = layoutInflater.inflate(android.R.layout.simple_list_item_multiple_choice, container, false)
            
            // Create custom layout for calendar item
            val calendarItem = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 12)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val checkBox = CheckBox(this).apply {
                isChecked = selectedCalendarIds.contains(account.id)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedCalendarIds.add(account.id)
                    } else {
                        selectedCalendarIds.remove(account.id)
                    }
                    getCurrentCalendarFragment()?.setSelectedCalendars(selectedCalendarIds)
                }
            }

            val textContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(12, 0, 0, 0)
            }

            val nameText = TextView(this).apply {
                text = account.displayName
                textSize = 14f
                setTextColor(android.graphics.Color.BLACK)
            }

            val emailText = TextView(this).apply {
                text = account.accountName
                textSize = 12f
                setTextColor(android.graphics.Color.GRAY)
            }

            textContainer.addView(nameText)
            textContainer.addView(emailText)

            calendarItem.addView(checkBox)
            calendarItem.addView(textContainer)

            container?.addView(calendarItem)
        }
    }

    private fun updateCalendarCheckboxes() {
        val container = binding.navView.findViewById<LinearLayout>(R.id.calendarListContainer)
        for (i in 0 until (container?.childCount ?: 0)) {
            val child = container?.getChildAt(i) as? LinearLayout
            val checkBox = child?.getChildAt(0) as? CheckBox
            val account = calendarAccounts.getOrNull(i)
            if (checkBox != null && account != null) {
                checkBox.isChecked = selectedCalendarIds.contains(account.id)
            }
        }
    }

    private fun getCurrentCalendarFragment(): CalendarFragment? {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        return navHostFragment?.childFragmentManager?.fragments?.firstOrNull() as? CalendarFragment
    }

    private fun showEventModal(event: Event?, initialTime: Long) {
        val modal = EventModalFragment.newInstance(event, initialTime)
        modal.onSaveListener = { savedEvent ->
            saveEvent(savedEvent)
        }
        modal.onRecurringEditListener = { choice, updatedEvent, instanceTime ->
            // Refresh calendar view after recurring event edit with delay
            refreshCalendarWithDelay()
        }
        modal.onDeleteListener = { eventId ->
            // Refresh calendar view after delete with delay
            refreshCalendarWithDelay()
        }
        modal.show(supportFragmentManager, "EventModal")
    }

    private fun saveEvent(event: Event) {
        if (event.id > 0) {
            repository.updateEvent(event)
        } else {
            repository.insertEvent(event)
        }
        // Refresh calendar view with delay
        refreshCalendarWithDelay()
    }

    /**
     * Refresh calendar data with a small delay to allow Calendar Provider to process changes
     */
    private fun refreshCalendarWithDelay() {
        binding.root.postDelayed({
            getCurrentCalendarFragment()?.loadCalendarData()
        }, 300) // 300ms delay for Calendar Provider to sync
    }

    override fun onEventClick(event: Event) {
        showEventModal(event, event.startTime)
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}