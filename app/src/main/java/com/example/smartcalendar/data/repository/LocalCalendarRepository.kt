package com.example.smartcalendar.data.repository

import android.content.Context
import com.example.smartcalendar.data.local.AppDatabase
import com.example.smartcalendar.data.local.CalendarDao
import com.example.smartcalendar.data.local.EventDao
import com.example.smartcalendar.data.model.EventInstance
import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.model.LocalCalendar
import com.example.smartcalendar.data.model.SyncStatus
import com.example.smartcalendar.data.util.InstanceGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Local calendar repository using Room database.
 * Manages calendars and events with sync support.
 */
class LocalCalendarRepository private constructor(
    private val calendarDao: CalendarDao,
    private val eventDao: EventDao
) {

    // Cached instances for current view range
    private var cachedInstances = listOf<EventInstance>()
    private var cacheRangeStart = 0L
    private var cacheRangeEnd = 0L

    // Current user ID - set after authentication
    private var currentUserId: String = ""

    fun setUserId(userId: String) {
        if (currentUserId != userId) {
            currentUserId = userId
            invalidateCache()
        }
    }

    fun getUserId(): String = currentUserId

    // ==================== Calendar Methods ====================

    fun observeCalendars(): Flow<List<LocalCalendar>> = calendarDao.getCalendarsByUser(currentUserId)

    suspend fun getCalendars(): List<LocalCalendar> = withContext(Dispatchers.IO) {
        calendarDao.getCalendarsListByUser(currentUserId)
    }

    suspend fun getCalendar(id: String): LocalCalendar? = withContext(Dispatchers.IO) {
        calendarDao.getCalendarById(id)
    }

    suspend fun getVisibleCalendars(): List<LocalCalendar> = withContext(Dispatchers.IO) {
        calendarDao.getVisibleCalendars(currentUserId)
    }

    suspend fun getVisibleCalendarIds(): Set<String> = withContext(Dispatchers.IO) {
        calendarDao.getVisibleCalendars(currentUserId).map { it.id }.toSet()
    }

    suspend fun addCalendar(calendar: LocalCalendar): Boolean = withContext(Dispatchers.IO) {
        val calendarWithUser = calendar.copy(
            userId = currentUserId,
            syncStatus = SyncStatus.PENDING
        )
        calendarDao.insert(calendarWithUser)
        true
    }

    suspend fun updateCalendar(calendar: LocalCalendar): Boolean = withContext(Dispatchers.IO) {
        val existing = calendarDao.getCalendarById(calendar.id) ?: return@withContext false
        val updated = calendar.copy(
            userId = existing.userId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        calendarDao.update(updated)
        invalidateCache()
        true
    }

    suspend fun deleteCalendar(id: String): Boolean = withContext(Dispatchers.IO) {
        val calendar = calendarDao.getCalendarById(id) ?: return@withContext false
        if (calendar.isDefault) return@withContext false // Cannot delete default calendars

        // Events are deleted automatically via CASCADE
        calendarDao.deleteById(id)
        invalidateCache()
        true
    }

    suspend fun setCalendarVisible(id: String, visible: Boolean): Boolean = withContext(Dispatchers.IO) {
        calendarDao.setVisibility(id, visible)
        invalidateCache()
        true
    }

    suspend fun ensureDefaultCalendars() = withContext(Dispatchers.IO) {
        if (currentUserId.isEmpty()) return@withContext

        // First, clean up any duplicate calendars
        removeDuplicateCalendars()

        val existingCalendars = calendarDao.getCalendarsListByUser(currentUserId)
        val existingNames = existingCalendars.map { it.name }.toSet()

        // Only create default calendars if they don't already exist by name
        LocalCalendar.createDefault(currentUserId).forEach { defaultCalendar ->
            if (defaultCalendar.name !in existingNames) {
                calendarDao.insert(defaultCalendar)
            }
        }
    }

    private suspend fun removeDuplicateCalendars() = withContext(Dispatchers.IO) {
        val calendars = calendarDao.getCalendarsListByUser(currentUserId)
        val seen = mutableSetOf<String>()
        val toDelete = mutableListOf<String>()

        calendars.forEach { calendar ->
            if (calendar.name in seen) {
                // Mark duplicate for deletion
                toDelete.add(calendar.id)
            } else {
                seen.add(calendar.name)
            }
        }

        // Delete duplicates
        toDelete.forEach { id ->
            calendarDao.deleteById(id)
        }

        if (toDelete.isNotEmpty()) {
            invalidateCache()
        }
    }

    // ==================== Event Methods ====================

    fun observeEvents(): Flow<List<ICalEvent>> = eventDao.getEventsByUser(currentUserId)

    suspend fun getAllEvents(): List<ICalEvent> = withContext(Dispatchers.IO) {
        eventDao.getEventsListByUser(currentUserId)
    }

    suspend fun getEvent(uid: String): ICalEvent? = withContext(Dispatchers.IO) {
        eventDao.getEventById(uid)
    }

    suspend fun getEventsByCalendar(calendarId: String): List<ICalEvent> = withContext(Dispatchers.IO) {
        eventDao.getEventsByCalendar(calendarId)
    }

    suspend fun addEvent(event: ICalEvent): Boolean = withContext(Dispatchers.IO) {
        val eventWithUser = event.copy(
            userId = currentUserId,
            syncStatus = SyncStatus.PENDING
        )
        eventDao.insert(eventWithUser)
        invalidateCache()
        true
    }

    suspend fun updateEvent(event: ICalEvent): Boolean = withContext(Dispatchers.IO) {
        val existing = eventDao.getEventById(event.uid) ?: return@withContext false
        val updated = event.copy(
            userId = existing.userId,
            lastModified = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        eventDao.update(updated)
        invalidateCache()
        true
    }

    suspend fun deleteEvent(uid: String): Boolean = withContext(Dispatchers.IO) {
        val event = eventDao.getEventById(uid) ?: return@withContext false
        eventDao.delete(event)
        invalidateCache()
        true
    }

    suspend fun addExceptionDate(uid: String, instanceTime: Long): Boolean = withContext(Dispatchers.IO) {
        val event = eventDao.getEventById(uid) ?: return@withContext false
        if (!event.isRecurring) return@withContext false

        val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val exdateStr = dateFormat.format(Date(instanceTime))

        val newExdate = if (event.exdate.isNullOrEmpty()) exdateStr else "${event.exdate},$exdateStr"

        val updated = event.copy(
            exdate = newExdate,
            lastModified = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        eventDao.update(updated)
        invalidateCache()
        true
    }

    suspend fun endRecurrenceAtInstance(uid: String, instanceTime: Long): Boolean = withContext(Dispatchers.IO) {
        val event = eventDao.getEventById(uid) ?: return@withContext false
        if (!event.isRecurring) return@withContext false

        val untilTime = instanceTime - 1000
        val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val untilStr = dateFormat.format(Date(untilTime))

        val existingRrule = event.rrule ?: return@withContext false
        val newRrule = if (existingRrule.contains("UNTIL=") || existingRrule.contains("COUNT=")) {
            existingRrule
                .replace(Regex("UNTIL=[^;]*"), "UNTIL=$untilStr")
                .replace(Regex("COUNT=[^;]*"), "UNTIL=$untilStr")
        } else {
            "$existingRrule;UNTIL=$untilStr"
        }

        val updated = event.copy(
            rrule = newRrule,
            lastModified = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        eventDao.update(updated)
        invalidateCache()
        true
    }

    /**
     * Get instances filtered by visible calendars
     */
    suspend fun getInstances(startTime: Long, endTime: Long): List<EventInstance> = withContext(Dispatchers.IO) {
        if (startTime == cacheRangeStart && endTime == cacheRangeEnd && cachedInstances.isNotEmpty()) {
            return@withContext cachedInstances
        }

        val visibleIds = getVisibleCalendarIds().toList()
        if (visibleIds.isEmpty()) {
            cachedInstances = emptyList()
            return@withContext cachedInstances
        }

        val allInstances = mutableListOf<EventInstance>()
        val events = eventDao.getVisibleEvents(currentUserId, visibleIds)

        events.forEach { event ->
            allInstances.addAll(InstanceGenerator.generateInstances(event, startTime, endTime))
        }

        cachedInstances = allInstances.sortedBy { it.startTime }
        cacheRangeStart = startTime
        cacheRangeEnd = endTime

        cachedInstances
    }

    suspend fun findByOriginalId(originalId: Long): ICalEvent? = withContext(Dispatchers.IO) {
        eventDao.getEventsListByUser(currentUserId).find { it.originalId == originalId }
    }

    suspend fun findByOriginalIdAndTitle(originalId: Long, title: String): ICalEvent? = withContext(Dispatchers.IO) {
        eventDao.getEventsListByUser(currentUserId).find {
            it.originalId == originalId && it.summary == title
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        eventDao.deleteAllForUser(currentUserId)
        invalidateCache()
    }

    suspend fun getEventCount(): Int = withContext(Dispatchers.IO) {
        eventDao.getEventCount(currentUserId)
    }

    // ==================== Sync Methods ====================

    suspend fun getPendingCalendars(): List<LocalCalendar> = withContext(Dispatchers.IO) {
        calendarDao.getPendingSync()
    }

    suspend fun getPendingEvents(): List<ICalEvent> = withContext(Dispatchers.IO) {
        eventDao.getPendingSync()
    }

    suspend fun markCalendarSynced(id: String) = withContext(Dispatchers.IO) {
        calendarDao.updateSyncStatus(id, SyncStatus.SYNCED)
    }

    suspend fun markEventSynced(uid: String) = withContext(Dispatchers.IO) {
        eventDao.updateSyncStatus(uid, SyncStatus.SYNCED)
    }

    // ==================== Cache Management ====================

    fun invalidateCache() {
        cachedInstances = emptyList()
        cacheRangeStart = 0L
        cacheRangeEnd = 0L
    }

    companion object {
        @Volatile
        private var instance: LocalCalendarRepository? = null

        fun getInstance(context: Context): LocalCalendarRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val database = AppDatabase.getInstance(context)
                    LocalCalendarRepository(
                        database.calendarDao(),
                        database.eventDao()
                    ).also { instance = it }
                }
            }
        }

        fun getInstance(): LocalCalendarRepository {
            return instance ?: throw IllegalStateException(
                "LocalCalendarRepository not initialized. Call getInstance(context) first."
            )
        }
    }
}
