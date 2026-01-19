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
                }
                R.id.MineFragment -> {
                    supportActionBar?.title = getString(R.string.nav_mine)
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
        val calendars = importer.getImportableCalendars()
        if (calendars.isEmpty()) {
            Toast.makeText(this, "No calendars available for import", Toast.LENGTH_SHORT).show()
            return
        }

        val names = calendars.map { "${it.name} (${it.accountName})" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_calendar))
            .setItems(names) { _, which ->
                val selected = calendars[which]
                val count = importer.importFromCalendar(selected.id)
                Toast.makeText(this, getString(R.string.import_success, count), Toast.LENGTH_SHORT).show()
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                refreshCalendar()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showExportDialog() {
        if (localRepository.getEventCount() == 0) {
            Toast.makeText(this, "No events to export", Toast.LENGTH_SHORT).show()
            return
        }

        val calendars = exporter.getExportableCalendars()
        if (calendars.isEmpty()) {
            Toast.makeText(this, "No writable calendars available", Toast.LENGTH_SHORT).show()
            return
        }

        val names = calendars.map { "${it.name} (${it.accountName})" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_calendar))
            .setItems(names) { _, which ->
                val selected = calendars[which]
                val count = exporter.exportToCalendar(selected.id)
                Toast.makeText(this, getString(R.string.export_success, count), Toast.LENGTH_SHORT).show()
                binding.drawerLayout.closeDrawer(GravityCompat.START)
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