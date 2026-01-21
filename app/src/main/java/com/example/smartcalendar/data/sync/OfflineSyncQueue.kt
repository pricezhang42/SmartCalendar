package com.example.smartcalendar.data.sync

import android.content.Context
import android.util.Log
import com.example.smartcalendar.data.model.SyncStatus
import com.example.smartcalendar.data.repository.LocalCalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages offline operations queue and processes them when back online.
 * Uses SyncStatus enum to track pending/synced/deleted states.
 */
class OfflineSyncQueue private constructor(context: Context) {

    private val localRepo = LocalCalendarRepository.getInstance(context)

    /**
     * Get count of pending calendar changes.
     */
    suspend fun getPendingCalendarCount(): Int = withContext(Dispatchers.IO) {
        try {
            val calendars = localRepo.getCalendars()
            calendars.count { it.syncStatus == SyncStatus.PENDING }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending calendar count: ${e.message}")
            0
        }
    }

    /**
     * Get count of pending event changes.
     */
    suspend fun getPendingEventCount(): Int = withContext(Dispatchers.IO) {
        try {
            val events = localRepo.getAllEventsIncludingDeleted()
            events.count { it.syncStatus == SyncStatus.PENDING || it.syncStatus == SyncStatus.DELETED }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending event count: ${e.message}")
            0
        }
    }

    /**
     * Get total count of pending changes.
     */
    suspend fun getTotalPendingCount(): Int {
        return getPendingCalendarCount() + getPendingEventCount()
    }

    /**
     * Check if there are any pending changes to sync.
     */
    suspend fun hasPendingChanges(): Boolean {
        return getTotalPendingCount() > 0
    }

    /**
     * Mark a calendar as pending sync.
     */
    suspend fun markCalendarPending(calendarId: String) = withContext(Dispatchers.IO) {
        try {
            val calendar = localRepo.getCalendar(calendarId)
            calendar?.let {
                localRepo.updateCalendar(it.copy(syncStatus = SyncStatus.PENDING))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking calendar pending: ${e.message}")
        }
    }

    /**
     * Mark an event as pending sync.
     */
    suspend fun markEventPending(eventUid: String) = withContext(Dispatchers.IO) {
        try {
            val event = localRepo.getEvent(eventUid)
            event?.let {
                localRepo.updateEvent(it.copy(syncStatus = SyncStatus.PENDING))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking event pending: ${e.message}")
        }
    }

    /**
     * Mark an event as deleted (soft delete for sync).
     */
    suspend fun markEventDeleted(eventUid: String) = withContext(Dispatchers.IO) {
        try {
            localRepo.deleteEvent(eventUid)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking event deleted: ${e.message}")
        }
    }

    /**
     * Mark a calendar as deleted (soft delete for sync).
     */
    suspend fun markCalendarDeleted(calendarId: String) = withContext(Dispatchers.IO) {
        try {
            val calendar = localRepo.getCalendar(calendarId)
            calendar?.let {
                localRepo.updateCalendar(it.copy(syncStatus = SyncStatus.DELETED))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking calendar deleted: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "OfflineSyncQueue"

        @Volatile
        private var INSTANCE: OfflineSyncQueue? = null

        fun getInstance(context: Context): OfflineSyncQueue {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineSyncQueue(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
