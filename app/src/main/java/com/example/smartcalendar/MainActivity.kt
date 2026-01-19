package com.example.smartcalendar

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.smartcalendar.data.model.EventInstance
import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.repository.LocalCalendarRepository
import com.example.smartcalendar.data.sync.CalendarExporter
import com.example.smartcalendar.data.sync.CalendarImporter
import com.example.smartcalendar.databinding.ActivityMainBinding
import com.example.smartcalendar.ui.calendar.CalendarFragment
import com.example.smartcalendar.ui.event.EventModalFragment

class MainActivity : AppCompatActivity(), CalendarFragment.OnEventClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var localRepository: LocalCalendarRepository
    private lateinit var importer: CalendarImporter
    private lateinit var exporter: CalendarExporter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Permissions granted - ready for import/export
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = getString(R.string.view_month)

        // Initialize repository with context for persistence
        LocalCalendarRepository.init(this)
        localRepository = LocalCalendarRepository.getInstance()
        importer = CalendarImporter(this)
        exporter = CalendarExporter(this)

        setupNavigation()
        setupDrawer()
        setupFab()
        checkPermissions()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.CalendarFragment -> {
                    val fragment = getCurrentCalendarFragment()
                    supportActionBar?.title = fragment?.getTitle() ?: getString(R.string.view_month)
                    fragment?.setOnTitleChangeListener { title ->
                        supportActionBar?.title = title
                    }
                    binding.fab.visibility = android.view.View.VISIBLE
                }
                R.id.MineFragment -> {
                    supportActionBar?.title = getString(R.string.nav_mine)
                    binding.fab.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun setupDrawer() {
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // Week view option
        binding.navView.findViewById<LinearLayout>(R.id.weekOption)?.setOnClickListener {
            getCurrentCalendarFragment()?.let { fragment ->
                fragment.viewMode = CalendarFragment.ViewMode.WEEK
                supportActionBar?.title = fragment.getTitle()
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            updateViewModeSelection(isMonth = false)
        }

        // Month view option
        binding.navView.findViewById<LinearLayout>(R.id.monthOption)?.setOnClickListener {
            getCurrentCalendarFragment()?.let { fragment ->
                fragment.viewMode = CalendarFragment.ViewMode.MONTH
                supportActionBar?.title = fragment.getTitle()
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            updateViewModeSelection(isMonth = true)
        }

        // Import button
        binding.navView.findViewById<android.view.View>(R.id.importButton)?.setOnClickListener {
            showImportDialog()
        }

        // Export button
        binding.navView.findViewById<android.view.View>(R.id.exportButton)?.setOnClickListener {
            showExportDialog()
        }
        
        // Populate calendar checkboxes
        setupCalendarList()
    }
    
    private fun setupCalendarList() {
        val container = binding.navView.findViewById<android.widget.LinearLayout>(R.id.calendarsContainer) ?: return
        container.removeAllViews()
        
        localRepository.getCalendars().forEach { calendar ->
            val row = layoutInflater.inflate(android.R.layout.simple_list_item_multiple_choice, container, false) as android.widget.CheckedTextView
            row.apply {
                text = calendar.name
                isChecked = calendar.isVisible
                checkMarkDrawable = null // We'll use custom checkbox
                setPadding(0, 16, 0, 16)
                
                // Create custom row with color dot
                val customRow = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 12, 0, 12)
                    
                    // Color dot
                    val colorDot = android.view.View(context).apply {
                        val size = (24 * resources.displayMetrics.density).toInt()
                        layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                            marginEnd = (12 * resources.displayMetrics.density).toInt()
                        }
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(calendar.color)
                        }
                    }
                    addView(colorDot)
                    
                    // Calendar name
                    val nameText = android.widget.TextView(context).apply {
                        text = calendar.name
                        textSize = 14f
                        setTextColor(android.graphics.Color.BLACK)
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    addView(nameText)
                    
                    // Checkbox
                    val checkbox = android.widget.CheckBox(context).apply {
                        isChecked = calendar.isVisible
                        buttonTintList = android.content.res.ColorStateList.valueOf(calendar.color)
                        setOnCheckedChangeListener { _, checked ->
                            localRepository.setCalendarVisible(calendar.id, checked)
                            getCurrentCalendarFragment()?.loadCalendarData()
                        }
                    }
                    addView(checkbox)
                }
                container.addView(customRow)
            }
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

    private fun checkPermissions() {
        val readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
        val writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)

        if (readPermission != PackageManager.PERMISSION_GRANTED ||
            writePermission != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            ))
        }
    }

    private fun showImportDialog() {
        val localCalendars = localRepository.getCalendars()
        val localNames = localCalendars.map { it.name }.toTypedArray()
        
        // First, select local app calendar to import INTO
        AlertDialog.Builder(this)
            .setTitle("Import to which calendar?")
            .setItems(localNames) { _, localWhich ->
                val targetCalendarId = localCalendars[localWhich].id
                
                // Then, select system calendar to import FROM
                val systemCalendars = importer.getImportableCalendars()
                if (systemCalendars.isEmpty()) {
                    Toast.makeText(this, "No system calendars available for import", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                
                val systemNames = systemCalendars.map { "${it.name} (${it.accountName})" }.toTypedArray()
                
                AlertDialog.Builder(this)
                    .setTitle("Import from which calendar?")
                    .setItems(systemNames) { _, systemWhich ->
                        val selected = systemCalendars[systemWhich]
                        val count = importer.importFromCalendar(selected.id, targetCalendarId)
                        Toast.makeText(this, getString(R.string.import_success, count), Toast.LENGTH_SHORT).show()
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                        refreshCalendar()
                        setupCalendarList()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showExportDialog() {
        val localCalendars = localRepository.getCalendars()
        val localNames = localCalendars.map { it.name }.toTypedArray()
        
        // First, select local app calendar to export FROM
        AlertDialog.Builder(this)
            .setTitle("Export from which calendar?")
            .setItems(localNames) { _, localWhich ->
                val sourceCalendarId = localCalendars[localWhich].id
                val eventCount = localRepository.getAllEvents().count { it.calendarId == sourceCalendarId }
                
                if (eventCount == 0) {
                    Toast.makeText(this, "No events in ${localCalendars[localWhich].name}", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                
                // Then, select system calendar to export TO
                val systemCalendars = exporter.getExportableCalendars()
                if (systemCalendars.isEmpty()) {
                    Toast.makeText(this, "No writable system calendars available", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                
                val systemNames = systemCalendars.map { "${it.name} (${it.accountName})" }.toTypedArray()
                
                AlertDialog.Builder(this)
                    .setTitle("Export to which calendar?")
                    .setItems(systemNames) { _, systemWhich ->
                        val selected = systemCalendars[systemWhich]
                        val count = exporter.exportToCalendar(selected.id, sourceCalendarId)
                        Toast.makeText(this, getString(R.string.export_success, count), Toast.LENGTH_SHORT).show()
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getCurrentCalendarFragment(): CalendarFragment? {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        return navHostFragment?.childFragmentManager?.fragments?.firstOrNull() as? CalendarFragment
    }

    private fun showEventModal(instance: EventInstance?, initialTime: Long) {
        val event = instance?.let { localRepository.getEvent(it.eventUid) }
        val modal = EventModalFragment.newInstance(event, instance, initialTime)
        
        modal.onSaveListener = { savedEvent ->
            if (localRepository.getEvent(savedEvent.uid) != null) {
                localRepository.updateEvent(savedEvent)
            } else {
                localRepository.addEvent(savedEvent)
            }
            refreshCalendar()
        }
        
        modal.onDeleteListener = { eventUid ->
            localRepository.deleteEvent(eventUid)
            refreshCalendar()
        }
        
        modal.onDeleteInstanceListener = { eventUid, instanceTime ->
            localRepository.addExceptionDate(eventUid, instanceTime)
            refreshCalendar()
        }
        
        modal.onDeleteFromInstanceListener = { eventUid, instanceTime ->
            localRepository.endRecurrenceAtInstance(eventUid, instanceTime)
            refreshCalendar()
        }
        
        modal.show(supportFragmentManager, "EventModal")
    }

    private fun refreshCalendar() {
        getCurrentCalendarFragment()?.loadCalendarData()
    }

    override fun onEventClick(instance: EventInstance) {
        showEventModal(instance, instance.startTime)
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}