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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Instrumented tests for Room database operations.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var calendarDao: CalendarDao
    private lateinit var eventDao: EventDao

    private val testUserId = "test-user-123"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        calendarDao = database.calendarDao()
        eventDao = database.eventDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Calendar Tests ====================

    @Test
    fun insertAndRetrieveCalendar() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Test Calendar",
            color = 0xFF0000,
            isDefault = true,
            isVisible = true,
            syncStatus = SyncStatus.PENDING
        )

        calendarDao.insert(calendar)

        val retrieved = calendarDao.getCalendarById("cal-1")
        assertNotNull(retrieved)
        assertEquals("Test Calendar", retrieved?.name)
        assertEquals(testUserId, retrieved?.userId)
    }

    @Test
    fun getCalendarsByUser() = runBlocking {
        val calendar1 = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Calendar 1",
            color = 0xFF0000
        )
        val calendar2 = LocalCalendar(
            id = "cal-2",
            userId = testUserId,
            name = "Calendar 2",
            color = 0x00FF00
        )
        val calendar3 = LocalCalendar(
            id = "cal-3",
            userId = "other-user",
            name = "Other User Calendar",
            color = 0x0000FF
        )

        calendarDao.insert(calendar1)
        calendarDao.insert(calendar2)
        calendarDao.insert(calendar3)

        val calendars = calendarDao.getCalendarsListByUser(testUserId)
        assertEquals(2, calendars.size)
        assertTrue(calendars.all { it.userId == testUserId })
    }

    @Test
    fun updateCalendar() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Original Name",
            color = 0xFF0000
        )

        calendarDao.insert(calendar)

        val updated = calendar.copy(name = "Updated Name", syncStatus = SyncStatus.SYNCED)
        calendarDao.update(updated)

        val retrieved = calendarDao.getCalendarById("cal-1")
        assertEquals("Updated Name", retrieved?.name)
        assertEquals(SyncStatus.SYNCED, retrieved?.syncStatus)
    }

    @Test
    fun deleteCalendar() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "To Delete",
            color = 0xFF0000
        )

        calendarDao.insert(calendar)
        calendarDao.deleteById("cal-1")

        val retrieved = calendarDao.getCalendarById("cal-1")
        assertNull(retrieved)
    }

    @Test
    fun getVisibleCalendars() = runBlocking {
        val visible = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Visible",
            color = 0xFF0000,
            isVisible = true
        )
        val hidden = LocalCalendar(
            id = "cal-2",
            userId = testUserId,
            name = "Hidden",
            color = 0x00FF00,
            isVisible = false
        )

        calendarDao.insert(visible)
        calendarDao.insert(hidden)

        val visibleCalendars = calendarDao.getVisibleCalendars(testUserId)
        assertEquals(1, visibleCalendars.size)
        assertEquals("Visible", visibleCalendars.first().name)
    }

    @Test
    fun setCalendarVisibility() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Test",
            color = 0xFF0000,
            isVisible = true
        )

        calendarDao.insert(calendar)
        calendarDao.setVisibility("cal-1", false)

        val retrieved = calendarDao.getCalendarById("cal-1")
        assertFalse(retrieved?.isVisible ?: true)
    }

    // ==================== Event Tests ====================

    @Test
    fun insertAndRetrieveEvent() = runBlocking {
        // First insert a calendar (foreign key constraint)
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
            dtStart = System.currentTimeMillis(),
            dtEnd = System.currentTimeMillis() + 3600000,
            color = 0xFF0000
        )

        eventDao.insert(event)

        val retrieved = eventDao.getEventById("event-1")
        assertNotNull(retrieved)
        assertEquals("Test Event", retrieved?.summary)
    }

    @Test
    fun getEventsByCalendar() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Test Calendar",
            color = 0xFF0000
        )
        calendarDao.insert(calendar)

        val event1 = ICalEvent(
            uid = "event-1",
            userId = testUserId,
            calendarId = "cal-1",
            summary = "Event 1",
            dtStart = 1000L,
            dtEnd = 2000L,
            color = 0xFF0000
        )
        val event2 = ICalEvent(
            uid = "event-2",
            userId = testUserId,
            calendarId = "cal-1",
            summary = "Event 2",
            dtStart = 3000L,
            dtEnd = 4000L,
            color = 0xFF0000
        )

        eventDao.insert(event1)
        eventDao.insert(event2)

        val events = eventDao.getEventsByCalendar("cal-1")
        assertEquals(2, events.size)
    }

    @Test
    fun updateEvent() = runBlocking {
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
            summary = "Original",
            dtStart = 1000L,
            dtEnd = 2000L,
            color = 0xFF0000
        )

        eventDao.insert(event)

        val updated = event.copy(summary = "Updated", syncStatus = SyncStatus.SYNCED)
        eventDao.update(updated)

        val retrieved = eventDao.getEventById("event-1")
        assertEquals("Updated", retrieved?.summary)
        assertEquals(SyncStatus.SYNCED, retrieved?.syncStatus)
    }

    @Test
    fun deleteEvent() = runBlocking {
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
            summary = "To Delete",
            dtStart = 1000L,
            dtEnd = 2000L,
            color = 0xFF0000
        )

        eventDao.insert(event)
        eventDao.deleteById("event-1")

        val retrieved = eventDao.getEventById("event-1")
        assertNull(retrieved)
    }

    @Test
    fun getEventsInDateRange() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Test Calendar",
            color = 0xFF0000
        )
        calendarDao.insert(calendar)

        val baseTime = System.currentTimeMillis()

        val event1 = ICalEvent(
            uid = "event-1",
            userId = testUserId,
            calendarId = "cal-1",
            summary = "Event 1",
            dtStart = baseTime,
            dtEnd = baseTime + 3600000,
            color = 0xFF0000
        )
        val event2 = ICalEvent(
            uid = "event-2",
            userId = testUserId,
            calendarId = "cal-1",
            summary = "Event 2",
            dtStart = baseTime + 86400000, // +1 day
            dtEnd = baseTime + 90000000,
            color = 0xFF0000
        )
        val event3 = ICalEvent(
            uid = "event-3",
            userId = testUserId,
            calendarId = "cal-1",
            summary = "Event 3",
            dtStart = baseTime + 172800000, // +2 days
            dtEnd = baseTime + 176400000,
            color = 0xFF0000
        )

        eventDao.insert(event1)
        eventDao.insert(event2)
        eventDao.insert(event3)

        // Get events in first day only (endTime is exclusive, so use -1 to exclude boundary)
        val events = eventDao.getEventsInRange(
            userId = testUserId,
            startTime = baseTime,
            endTime = baseTime + 86400000 - 1
        )

        assertEquals(1, events.size)
        assertEquals("Event 1", events.first().summary)
    }

    @Test
    fun cascadeDeleteEventsWhenCalendarDeleted() = runBlocking {
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
            color = 0xFF0000
        )
        eventDao.insert(event)

        // Delete calendar - events should be cascade deleted
        calendarDao.deleteById("cal-1")

        val retrievedEvent = eventDao.getEventById("event-1")
        assertNull(retrievedEvent)
    }

    @Test
    fun flowUpdatesOnChange() = runBlocking {
        val calendar = LocalCalendar(
            id = "cal-1",
            userId = testUserId,
            name = "Test Calendar",
            color = 0xFF0000
        )
        calendarDao.insert(calendar)

        // Get initial flow value
        val initialCalendars = calendarDao.getCalendarsByUser(testUserId).first()
        assertEquals(1, initialCalendars.size)

        // Insert another calendar
        val calendar2 = LocalCalendar(
            id = "cal-2",
            userId = testUserId,
            name = "Test Calendar 2",
            color = 0x00FF00
        )
        calendarDao.insert(calendar2)

        // Get updated flow value
        val updatedCalendars = calendarDao.getCalendarsByUser(testUserId).first()
        assertEquals(2, updatedCalendars.size)
    }
}
