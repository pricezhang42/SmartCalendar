package com.example.smartcalendar

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.smartcalendar.data.model.EventInstance
import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.repository.AuthRepository
import com.example.smartcalendar.data.repository.LocalCalendarRepository
import com.example.smartcalendar.data.sync.CalendarExporter
import com.example.smartcalendar.data.sync.CalendarImporter
import com.example.smartcalendar.data.sync.SyncManager
import com.example.smartcalendar.data.sync.RealtimeSync
import com.example.smartcalendar.databinding.ActivityMainBinding
import com.example.smartcalendar.ui.ai.AIAssistantActivity
import com.example.smartcalendar.ui.auth.LoginActivity
import com.example.smartcalendar.ui.calendar.CalendarFragment
import com.example.smartcalendar.ui.event.EventModalFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), CalendarFragment.OnEventClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var localRepository: LocalCalendarRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var importer: CalendarImporter
    private lateinit var exporter: CalendarExporter
    private lateinit var syncManager: SyncManager
    private lateinit var realtimeSync: RealtimeSync

    private var syncMenuItem: android.view.MenuItem? = null

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

        // Check authentication first
        authRepository = AuthRepository.getInstance()
        if (!authRepository.isSignedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = getString(R.string.view_month)

        // Initialize repository with context and user ID
        localRepository = LocalCalendarRepository.getInstance(this)
        val userId = authRepository.getCurrentUserId() ?: ""
        localRepository.setUserId(userId)

        importer = CalendarImporter(this)
        exporter = CalendarExporter(this)

        // Initialize sync managers
        syncManager = SyncManager.getInstance(this)
        realtimeSync = RealtimeSync.getInstance(this)

        // Start network monitoring for auto-sync on reconnect
        syncManager.startNetworkMonitoring()

        // Ensure default calendars exist for user
        lifecycleScope.launch {
            localRepository.ensureDefaultCalendars()
            setupCalendarList()

            // Start real-time sync
            realtimeSync.startListening()

            // Perform initial sync (will handle offline gracefully)
            performSync()
        }

        setupNavigation()
        setupDrawer()
        setupFab()
        setupSyncStatusObserver()
        setupNetworkStatusObserver()
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

        lifecycleScope.launch {
            val calendars = localRepository.getCalendars()
            calendars.forEach { calendar ->
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
                            lifecycleScope.launch {
                                localRepository.setCalendarVisible(calendar.id, checked)
                                getCurrentCalendarFragment()?.loadCalendarData()
                            }
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

        // Long press on FAB opens AI Assistant
        binding.fab.setOnLongClickListener {
            openAIAssistant()
            true
        }
    }

    private fun openAIAssistant() {
        startActivity(Intent(this, AIAssistantActivity::class.java))
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
        lifecycleScope.launch {
            val localCalendars = localRepository.getCalendars()
            val localNames = localCalendars.map { it.name }.toTypedArray()

            // First, select local app calendar to import INTO
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Import to which calendar?")
                .setItems(localNames) { _, localWhich ->
                    val targetCalendarId = localCalendars[localWhich].id

                    // Then, select system calendar to import FROM
                    val systemCalendars = importer.getImportableCalendars()
                    if (systemCalendars.isEmpty()) {
                        Toast.makeText(this@MainActivity, "No system calendars available for import", Toast.LENGTH_SHORT).show()
                        return@setItems
                    }

                    val systemNames = systemCalendars.map { "${it.name} (${it.accountName})" }.toTypedArray()

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Import from which calendar?")
                        .setItems(systemNames) { _, systemWhich ->
                            lifecycleScope.launch {
                                val selected = systemCalendars[systemWhich]
                                val count = importer.importFromCalendar(selected.id, targetCalendarId)
                                Toast.makeText(this@MainActivity, getString(R.string.import_success, count), Toast.LENGTH_SHORT).show()
                                binding.drawerLayout.closeDrawer(GravityCompat.START)
                                refreshCalendar()
                                setupCalendarList()
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun showExportDialog() {
        lifecycleScope.launch {
            val localCalendars = localRepository.getCalendars()
            val localNames = localCalendars.map { it.name }.toTypedArray()

            // First, select local app calendar to export FROM
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Export from which calendar?")
                .setItems(localNames) { _, localWhich ->
                    lifecycleScope.launch {
                        val sourceCalendarId = localCalendars[localWhich].id
                        val allEvents = localRepository.getAllEvents()
                        val eventCount = allEvents.count { it.calendarId == sourceCalendarId }

                        if (eventCount == 0) {
                            Toast.makeText(this@MainActivity, "No events in ${localCalendars[localWhich].name}", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        // Then, select system calendar to export TO
                        val systemCalendars = exporter.getExportableCalendars()
                        if (systemCalendars.isEmpty()) {
                            Toast.makeText(this@MainActivity, "No writable system calendars available", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val systemNames = systemCalendars.map { "${it.name} (${it.accountName})" }.toTypedArray()

                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Export to which calendar?")
                            .setItems(systemNames) { _, systemWhich ->
                                lifecycleScope.launch {
                                    val selected = systemCalendars[systemWhich]
                                    val count = exporter.exportToCalendar(selected.id, sourceCalendarId)
                                    Toast.makeText(this@MainActivity, getString(R.string.export_success, count), Toast.LENGTH_SHORT).show()
                                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                                }
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun getCurrentCalendarFragment(): CalendarFragment? {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        return navHostFragment?.childFragmentManager?.fragments?.firstOrNull() as? CalendarFragment
    }

    private fun showEventModal(instance: EventInstance?, initialTime: Long) {
        lifecycleScope.launch {
            val event = instance?.let { localRepository.getEvent(it.eventUid) }
            val modal = EventModalFragment.newInstance(event, instance, initialTime)

            modal.onSaveListener = { savedEvent ->
                lifecycleScope.launch {
                    if (localRepository.getEvent(savedEvent.uid) != null) {
                        localRepository.updateEvent(savedEvent)
                    } else {
                        localRepository.addEvent(savedEvent)
                    }
                    refreshCalendar()
                }
            }

            modal.onDeleteListener = { eventUid ->
                lifecycleScope.launch {
                    localRepository.deleteEvent(eventUid)
                    refreshCalendar()
                }
            }

            modal.onDeleteInstanceListener = { eventUid, instanceTime ->
                lifecycleScope.launch {
                    localRepository.addExceptionDate(eventUid, instanceTime)
                    refreshCalendar()
                }
            }

            modal.onDeleteFromInstanceListener = { eventUid, instanceTime ->
                lifecycleScope.launch {
                    localRepository.endRecurrenceAtInstance(eventUid, instanceTime)
                    refreshCalendar()
                }
            }

            modal.show(supportFragmentManager, "EventModal")
        }
    }

    private fun refreshCalendar() {
        getCurrentCalendarFragment()?.loadCalendarData()
    }

    override fun onEventClick(instance: EventInstance) {
        showEventModal(instance, instance.startTime)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        syncMenuItem = menu.findItem(R.id.action_sync)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_ai -> {
                openAIAssistant()
                true
            }
            R.id.action_sync -> {
                performSync()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun performSync() {
        lifecycleScope.launch {
            val result = syncManager.sync()
            result.onSuccess {
                Toast.makeText(this@MainActivity, R.string.sync_success, Toast.LENGTH_SHORT).show()
                refreshCalendar()
            }.onFailure { error ->
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.sync_error) + ": ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupSyncStatusObserver() {
        lifecycleScope.launch {
            syncManager.syncStatus.collect { status ->
                when (status) {
                    SyncManager.SyncState.SYNCING -> {
                        syncMenuItem?.isEnabled = false
                        supportActionBar?.subtitle = getString(R.string.syncing)
                    }
                    SyncManager.SyncState.SUCCESS -> {
                        syncMenuItem?.isEnabled = true
                        supportActionBar?.subtitle = null
                        refreshCalendar()
                    }
                    SyncManager.SyncState.ERROR -> {
                        syncMenuItem?.isEnabled = true
                        supportActionBar?.subtitle = null
                    }
                    SyncManager.SyncState.IDLE -> {
                        syncMenuItem?.isEnabled = true
                        supportActionBar?.subtitle = null
                    }
                    SyncManager.SyncState.OFFLINE -> {
                        syncMenuItem?.isEnabled = true
                        supportActionBar?.subtitle = getString(R.string.offline_mode)
                    }
                }
            }
        }
    }

    private fun setupNetworkStatusObserver() {
        lifecycleScope.launch {
            syncManager.isOnline.collect { online ->
                if (online) {
                    // Back online - subtitle will be cleared by sync status observer
                } else {
                    supportActionBar?.subtitle = getString(R.string.offline_mode)
                }
            }
        }

        // Also observe pending changes count
        lifecycleScope.launch {
            syncManager.pendingChangesCount.collect { count ->
                if (count > 0 && !syncManager.isOnline.value) {
                    supportActionBar?.subtitle = getString(R.string.offline_pending, count)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncManager.stopNetworkMonitoring()
        lifecycleScope.launch {
            realtimeSync.stopListening()
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
