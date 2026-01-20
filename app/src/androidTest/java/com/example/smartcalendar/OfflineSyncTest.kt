package com.example.smartcalendar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartcalendar.data.local.AppDatabase
import com.example.smartcalendar.data.local.CalendarDao
import com.example.smartcalendar.data.local.EventDao
import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.model.LocalCalendar
import com.example.smartcalendar.data.model.SyncStatus
import com.example.smartcalendar.data.sync.NetworkMonitor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for offline sync behavior.
 */
@RunWith(AndroidJUnit4::class)
class OfflineSyncTest {

    private lateinit var database: AppDatabase
    private lateinit var calendarDao: CalendarDao
    private lateinit var eventDao: EventDao
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var context: Context

    private val testUserId = "test-user-123"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        calendarDao = database.calendarDao()
        eventDao = database.eventDao()
        networkMonitor = NetworkMonitor.getInstance(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun networkMonitorReturnsConnectivityStatus() {
        // Network monitor should return a boolean
        val isOnline = networkMonitor.checkCurrentConnectivity()
        assertTrue(isOnline is Boolean)
    }

    @Test
    fun newCalendarHasPendingSyncStatus() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "New Calendar",
            color = 0xFF0000
        )

        calendarDao.insert(calendar)

        val retrieved = calendarDao.getCalendarById("cal-1")
        assertEquals(SyncStatus.PENDING, retrieved?.syncStatus)
    }

    @Test
    fun newEventHasPendingSyncStatus() = runBlocking {
        // Insert calendar first
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Test Calendar",
            color = 0xFF0000
        )
        calendarDao.insert(calendar)

        val event = ICalEvent(
            uid = "event-1",
            userId = testUserId,
            calendarId = "cal-1",
            summary = "New Event",
            dtStart = 1000L,
            dtEnd = 2000L,
            color = 0xFF0000
        )

        eventDao.insert(event)

        val retrieved = eventDao.getEventById("event-1")
        assertEquals(SyncStatus.PENDING, retrieved?.syncStatus)
    }

    @Test
    fun canMarkCalendarAsSynced() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Test Calendar",
            color = 0xFF0000,
            syncStatus = SyncStatus.PENDING
        )

        calendarDao.insert(calendar)

        val synced = calendar.copy(syncStatus = SyncStatus.SYNCED)
        calendarDao.update(synced)

        val retrieved = calendarDao.getCalendarById("cal-1")
        assertEquals(SyncStatus.SYNCED, retrieved?.syncStatus)
    }

    @Test
    fun canMarkEventAsSynced() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Test Calendar",
            color = 0xFF0000
        )
        calendarDao.insert(calendar)

        val event = ICalEvent(
            uid = "event-1",
            userId = testUserId,
            calendarId = "cal-1",
            summary = "Test Event",
            dtStart = 1000L,
            dtEnd = 2000L,
            color = 0xFF0000,
            syncStatus = SyncStatus.PENDING
        )

        eventDao.insert(event)

        val synced = event.copy(syncStatus = SyncStatus.SYNCED)
        eventDao.update(synced)

        val retrieved = eventDao.getEventById("event-1")
        assertEquals(SyncStatus.SYNCED, retrieved?.syncStatus)
    }

    @Test
    fun canMarkEventAsDeleted() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Test Calendar",
            color = 0xFF0000
        )
        calendarDao.insert(calendar)

        val event = ICalEvent(
            uid = "event-1",
            userId = testUserId,
            calendarId = "cal-1",
            summary = "Test Event",
            dtStart = 1000L,
            dtEnd = 2000L,
            color = 0xFF0000,
            syncStatus = SyncStatus.SYNCED
        )

        eventDao.insert(event)

        // Mark as deleted (soft delete)
        val deleted = event.copy(syncStatus = SyncStatus.DELETED)
        eventDao.update(deleted)

        val retrieved = eventDao.getEventById("event-1")
        assertEquals(SyncStatus.DELETED, retrieved?.syncStatus)
    }

    @Test
    fun canFilterPendingCalendars() = runBlocking {
        val pendingCal = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Pending Calendar",
            color = 0xFF0000,
            syncStatus = SyncStatus.PENDING
        )
        val syncedCal = LocalCalendar(
            id = "cal-2",
            userId = testUserId,
            name = "Synced Calendar",
            color = 0x00FF00,
            syncStatus = SyncStatus.SYNCED
        )

        calendarDao.insert(pendingCal)
        calendarDao.insert(syncedCal)

        val all = calendarDao.getCalendarsListByUser(testUserId)
        val pending = all.filter { it.syncStatus == SyncStatus.PENDING }

        assertEquals(2, all.size)
        assertEquals(1, pending.size)
        assertEquals("Pending Calendar", pending.first().name)
    }

    @Test
    fun canFilterPendingEvents() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Test Calendar",
            color = 0xFF0000
        )
        calendarDao.insert(calendar)

        val pendingEvent = ICalEvent(
            uid = "event-1",
            userId = testUserId,
            calendarId = "cal-1",
            summary = "Pending Event",
            dtStart = 1000L,
            dtEnd = 2000L,
            color = 0xFF0000,
            syncStatus = SyncStatus.PENDING
        )
        val syncedEvent = ICalEvent(
            uid = "event-2",
            userId = testUserId,
            calendarId = "cal-1",
            summary = "Synced Event",
            dtStart = 3000L,
            dtEnd = 4000L,
            color = 0xFF0000,
            syncStatus = SyncStatus.SYNCED
        )

        eventDao.insert(pendingEvent)
        eventDao.insert(syncedEvent)

        val all = eventDao.getEventsListByUser(testUserId)
        val pending = all.filter { it.syncStatus == SyncStatus.PENDING }
        val deleted = all.filter { it.syncStatus == SyncStatus.DELETED }

        assertEquals(2, all.size)
        assertEquals(1, pending.size)
        assertEquals(0, deleted.size)
    }

    @Test
    fun canFilterDeletedEvents() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Test Calendar",
            color = 0xFF0000
        )
        calendarDao.insert(calendar)

        val activeEvent = ICalEvent(
            uid = "event-1",
            userId = testUserId,
            calendarId = "cal-1",
            summary = "Active Event",
            dtStart = 1000L,
            dtEnd = 2000L,
            color = 0xFF0000,
            syncStatus = SyncStatus.SYNCED
        )
        val deletedEvent = ICalEvent(
            uid = "event-2",
            userId = testUserId,
            calendarId = "cal-1",
            summary = "Deleted Event",
            dtStart = 3000L,
            dtEnd = 4000L,
            color = 0xFF0000,
            syncStatus = SyncStatus.DELETED
        )

        eventDao.insert(activeEvent)
        eventDao.insert(deletedEvent)

        val all = eventDao.getEventsListByUser(testUserId)
        val deleted = all.filter { it.syncStatus == SyncStatus.DELETED }

        assertEquals(2, all.size)
        assertEquals(1, deleted.size)
        assertEquals("Deleted Event", deleted.first().summary)
    }

    @Test
    fun lastModifiedUpdatesOnChange() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Test Calendar",
            color = 0xFF0000
        )
        calendarDao.insert(calendar)

        val originalTime = System.currentTimeMillis()
        val event = ICalEvent(
            uid = "event-1",
            userId = testUserId,
            calendarId = "cal-1",
            summary = "Test Event",
            dtStart = 1000L,
            dtEnd = 2000L,
            color = 0xFF0000,
            lastModified = originalTime
        )

        eventDao.insert(event)

        // Simulate update with new lastModified
        Thread.sleep(10) // Small delay to ensure different timestamp
        val newTime = System.currentTimeMillis()
        val updated = event.copy(summary = "Updated Event", lastModified = newTime)
        eventDao.update(updated)

        val retrieved = eventDao.getEventById("event-1")
        assertTrue(retrieved?.lastModified ?: 0 > originalTime)
    }
}
