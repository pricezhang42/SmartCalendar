package com.example.smartcalendar.data.sync

import android.content.Context
import android.util.Log
import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.model.LocalCalendar
import com.example.smartcalendar.data.model.SyncStatus
import com.example.smartcalendar.data.repository.AuthRepository
import com.example.smartcalendar.data.repository.LocalCalendarRepository
import com.example.smartcalendar.data.repository.SupabaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages synchronization between local Room database and Supabase cloud.
 * Handles conflict resolution, sync status, and bidirectional sync.
 */
class SyncManager private constructor(private val context: Context) {

    private val localRepo = LocalCalendarRepository.getInstance(context)
    private val supabaseRepo = SupabaseRepository.getInstance()
    private val authRepo = AuthRepository.getInstance()
    private val networkMonitor = NetworkMonitor.getInstance(context)
    private val offlineQueue = OfflineSyncQueue.getInstance(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncStatus = MutableStateFlow(SyncState.IDLE)
    val syncStatus: StateFlow<SyncState> = _syncStatus

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime

    private val _pendingChangesCount = MutableStateFlow(0)
    val pendingChangesCount: StateFlow<Int> = _pendingChangesCount

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline

    private val isSyncing = AtomicBoolean(false)
    private var wasOffline = false

    enum class SyncState {
        IDLE,
        SYNCING,
        SUCCESS,
        ERROR,
        OFFLINE
    }

    /**
     * Start monitoring network and auto-sync when reconnecting.
     */
    fun startNetworkMonitoring() {
        networkMonitor.startMonitoring()

        scope.launch {
            networkMonitor.isOnline.collect { online ->
                _isOnline.value = online

                if (online) {
                    Log.d(TAG, "Network connected")
                    if (wasOffline) {
                        // Was offline, now online - trigger sync
                        Log.d(TAG, "Reconnected after being offline, triggering sync...")
                        syncIfNeeded()
                    }
                    wasOffline = false
                } else {
                    Log.d(TAG, "Network disconnected")
                    wasOffline = true
                    _syncStatus.value = SyncState.OFFLINE
                }
            }
        }

        // Update pending changes count periodically
        scope.launch {
            updatePendingCount()
        }
    }

    /**
     * Stop network monitoring.
     */
    fun stopNetworkMonitoring() {
        networkMonitor.stopMonitoring()
    }

    /**
     * Sync if there are pending changes and we're online.
     */
    suspend fun syncIfNeeded(): Result<Unit> {
        if (!networkMonitor.checkCurrentConnectivity()) {
            Log.d(TAG, "Offline - skipping sync")
            _syncStatus.value = SyncState.OFFLINE
            return Result.failure(Exception("No network connection"))
        }

        val hasPending = offlineQueue.hasPendingChanges()
        if (hasPending) {
            Log.d(TAG, "Has pending changes, syncing...")
            return sync()
        }

        Log.d(TAG, "No pending changes")
        return Result.success(Unit)
    }

    /**
     * Update the pending changes count.
     */
    private suspend fun updatePendingCount() {
        _pendingChangesCount.value = offlineQueue.getTotalPendingCount()
    }

    /**
     * Perform full bidirectional sync between local and cloud.
     */
    suspend fun sync(): Result<Unit> = withContext(Dispatchers.IO) {
        // Check network connectivity first
        if (!networkMonitor.checkCurrentConnectivity()) {
            Log.d(TAG, "No network - sync deferred")
            _syncStatus.value = SyncState.OFFLINE
            wasOffline = true
            return@withContext Result.failure(Exception("No network connection"))
        }

        if (!isSyncing.compareAndSet(false, true)) {
            return@withContext Result.failure(Exception("Sync already in progress"))
        }

        try {
            _syncStatus.value = SyncState.SYNCING
            Log.d(TAG, "Starting sync...")

            val userId = authRepo.getCurrentUserId()
            if (userId.isNullOrEmpty()) {
                throw Exception("User not authenticated")
            }

            // Step 1: Sync calendars
            syncCalendars(userId)

            // Step 2: Sync events
            syncEvents(userId)

            // Update pending count
            updatePendingCount()

            _lastSyncTime.value = System.currentTimeMillis()
            _syncStatus.value = SyncState.SUCCESS
            Log.d(TAG, "Sync completed successfully")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            _syncStatus.value = SyncState.ERROR
            Result.failure(e)
        } finally {
            isSyncing.set(false)
        }
    }

    /**
     * Sync calendars: Push local changes, then pull remote changes.
     */
    private suspend fun syncCalendars(userId: String) {
        // Push local pending calendars to cloud
        val localCalendars = localRepo.getCalendars()
        val pendingCalendars = localCalendars.filter { it.syncStatus == SyncStatus.PENDING }

        if (pendingCalendars.isNotEmpty()) {
            Log.d(TAG, "Pushing ${pendingCalendars.size} pending calendars to cloud")
            supabaseRepo.syncCalendars(pendingCalendars).getOrThrow()

            // Mark as synced
            pendingCalendars.forEach { calendar ->
                localRepo.updateCalendar(calendar.copy(syncStatus = SyncStatus.SYNCED))
            }
        }

        // Pull remote calendars
        Log.d(TAG, "Fetching calendars from cloud")
        val remoteCalendars = supabaseRepo.fetchCalendars(userId).getOrThrow()

        // Merge remote calendars into local
        remoteCalendars.forEach { remoteCalendar ->
            val localCalendar = localCalendars.find { it.id == remoteCalendar.id }

            if (localCalendar == null) {
                // New calendar from cloud - insert locally
                localRepo.addCalendar(remoteCalendar.copy(syncStatus = SyncStatus.SYNCED))
            } else {
                // Calendar exists - use last-write-wins based on updatedAt
                if (remoteCalendar.updatedAt > localCalendar.updatedAt) {
                    localRepo.updateCalendar(remoteCalendar.copy(syncStatus = SyncStatus.SYNCED))
                }
            }
        }

        Log.d(TAG, "Calendar sync completed")
    }

    /**
     * Sync events: Push local changes, then pull remote changes.
     */
    private suspend fun syncEvents(userId: String) {
        // Get all local events
        val localEvents = localRepo.getAllEvents()

        // Push local pending/deleted events to cloud
        val pendingEvents = localEvents.filter { it.syncStatus == SyncStatus.PENDING }
        val deletedEvents = localEvents.filter { it.syncStatus == SyncStatus.DELETED }

        if (pendingEvents.isNotEmpty()) {
            Log.d(TAG, "Pushing ${pendingEvents.size} pending events to cloud")
            supabaseRepo.syncEvents(pendingEvents).getOrThrow()

            // Mark as synced
            pendingEvents.forEach { event ->
                localRepo.updateEvent(event.copy(syncStatus = SyncStatus.SYNCED))
            }
        }

        if (deletedEvents.isNotEmpty()) {
            Log.d(TAG, "Deleting ${deletedEvents.size} events from cloud")
            deletedEvents.forEach { event ->
                supabaseRepo.deleteEvent(event.uid)
                localRepo.deleteEvent(event.uid)
            }
        }

        // Pull remote events
        Log.d(TAG, "Fetching events from cloud")
        val remoteEvents = supabaseRepo.fetchEvents(userId).getOrThrow()

        // Merge remote events into local
        remoteEvents.forEach { remoteEvent ->
            val localEvent = localEvents.find { it.uid == remoteEvent.uid }

            if (localEvent == null) {
                // New event from cloud - insert locally
                localRepo.addEvent(remoteEvent.copy(syncStatus = SyncStatus.SYNCED))
            } else if (localEvent.syncStatus != SyncStatus.DELETED) {
                // Event exists - check for conflicts
                if (remoteEvent.lastModified > localEvent.lastModified) {
                    // Remote is newer - update local
                    localRepo.updateEvent(remoteEvent.copy(syncStatus = SyncStatus.SYNCED))
                } else if (remoteEvent.lastModified < localEvent.lastModified &&
                           localEvent.syncStatus == SyncStatus.SYNCED) {
                    // Local is newer but already synced - conflict detected
                    // For now, use last-write-wins (keep local)
                    // TODO: Implement proper conflict resolution UI
                    Log.w(TAG, "Conflict detected for event ${localEvent.uid}, keeping local version")
                }
            }
        }

        Log.d(TAG, "Event sync completed")
    }

    /**
     * Push a single calendar to cloud immediately.
     */
    suspend fun pushCalendar(calendar: LocalCalendar): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabaseRepo.insertCalendar(calendar).getOrThrow()
            localRepo.updateCalendar(calendar.copy(syncStatus = SyncStatus.SYNCED))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push calendar: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Push a single event to cloud immediately.
     */
    suspend fun pushEvent(event: ICalEvent): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabaseRepo.insertEvent(event).getOrThrow()
            localRepo.updateEvent(event.copy(syncStatus = SyncStatus.SYNCED))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push event: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a calendar from cloud and local.
     */
    suspend fun deleteCalendar(calendarId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabaseRepo.deleteCalendar(calendarId).getOrThrow()
            localRepo.deleteCalendar(calendarId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete calendar: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete an event from cloud and local.
     */
    suspend fun deleteEvent(eventUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabaseRepo.deleteEvent(eventUid).getOrThrow()
            localRepo.deleteEvent(eventUid)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete event: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "SyncManager"

        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
