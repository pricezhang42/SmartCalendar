package com.example.smartcalendar.data.sync

import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.model.LocalCalendar
import com.example.smartcalendar.data.model.SyncStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for sync-related data models and utilities.
 */
class SyncManagerTest {

    @Test
    fun `SyncStatus enum has all required states`() {
        val states = SyncStatus.values()
        assertEquals(4, states.size)
        assertTrue(states.contains(SyncStatus.SYNCED))
        assertTrue(states.contains(SyncStatus.PENDING))
        assertTrue(states.contains(SyncStatus.CONFLICT))
        assertTrue(states.contains(SyncStatus.DELETED))
    }

    @Test
    fun `LocalCalendar default sync status is PENDING`() {
        val calendar = LocalCalendar(
            id = "test-id",
            userId = "user-123",
            name = "Test Calendar",
            color = 0xFF0000
        )
        assertEquals(SyncStatus.PENDING, calendar.syncStatus)
    }

    @Test
    fun `ICalEvent default sync status is PENDING`() {
        val event = ICalEvent(
            uid = UUID.randomUUID().toString(),
            calendarId = "cal-123",
            summary = "Test Event",
            dtStart = System.currentTimeMillis(),
            dtEnd = System.currentTimeMillis() + 3600000,
            color = 0xFF0000
        )
        assertEquals(SyncStatus.PENDING, event.syncStatus)
    }

    @Test
    fun `LocalCalendar copy preserves all fields`() {
        val original = LocalCalendar(
            id = "test-id",
            userId = "user-123",
            name = "Test Calendar",
            color = 0xFF0000,
            isDefault = true,
            isVisible = true,
            syncStatus = SyncStatus.PENDING
        )

        val copy = original.copy(syncStatus = SyncStatus.SYNCED)

        assertEquals(original.id, copy.id)
        assertEquals(original.userId, copy.userId)
        assertEquals(original.name, copy.name)
        assertEquals(original.color, copy.color)
        assertEquals(original.isDefault, copy.isDefault)
        assertEquals(original.isVisible, copy.isVisible)
        assertEquals(SyncStatus.SYNCED, copy.syncStatus)
    }

    @Test
    fun `ICalEvent copy preserves all fields`() {
        val original = ICalEvent(
            uid = "event-123",
            userId = "user-123",
            calendarId = "cal-123",
            summary = "Test Event",
            description = "Test Description",
            location = "Test Location",
            dtStart = 1000L,
            dtEnd = 2000L,
            allDay = false,
            rrule = "FREQ=DAILY",
            color = 0xFF0000,
            syncStatus = SyncStatus.PENDING
        )

        val copy = original.copy(syncStatus = SyncStatus.SYNCED)

        assertEquals(original.uid, copy.uid)
        assertEquals(original.userId, copy.userId)
        assertEquals(original.calendarId, copy.calendarId)
        assertEquals(original.summary, copy.summary)
        assertEquals(original.description, copy.description)
        assertEquals(original.location, copy.location)
        assertEquals(original.dtStart, copy.dtStart)
        assertEquals(original.dtEnd, copy.dtEnd)
        assertEquals(original.allDay, copy.allDay)
        assertEquals(original.rrule, copy.rrule)
        assertEquals(original.color, copy.color)
        assertEquals(SyncStatus.SYNCED, copy.syncStatus)
    }

    @Test
    fun `ICalEvent isRecurring returns true when rrule is set`() {
        val recurringEvent = ICalEvent(
            uid = "event-123",
            calendarId = "cal-123",
            summary = "Recurring Event",
            dtStart = 1000L,
            dtEnd = 2000L,
            rrule = "FREQ=WEEKLY;BYDAY=MO,WE,FR",
            color = 0xFF0000
        )

        assertTrue(recurringEvent.isRecurring)
    }

    @Test
    fun `ICalEvent isRecurring returns false when rrule is null`() {
        val singleEvent = ICalEvent(
            uid = "event-123",
            calendarId = "cal-123",
            summary = "Single Event",
            dtStart = 1000L,
            dtEnd = 2000L,
            color = 0xFF0000
        )

        assertFalse(singleEvent.isRecurring)
    }

    @Test
    fun `LocalCalendar createDefault generates correct calendars`() {
        val userId = "test-user-123"
        val calendars = LocalCalendar.createDefault(userId)

        assertEquals(2, calendars.size)

        val personal = calendars.find { it.name == "Personal" }
        assertNotNull(personal)
        assertEquals("personal_$userId", personal?.id)
        assertEquals(userId, personal?.userId)
        assertTrue(personal?.isDefault == true)

        val work = calendars.find { it.name == "Work" }
        assertNotNull(work)
        assertEquals("work_$userId", work?.id)
        assertEquals(userId, work?.userId)
        assertTrue(work?.isDefault == true) // Both calendars are marked as default
    }

    @Test
    fun `SyncState enum has all required states`() {
        val states = SyncManager.SyncState.values()
        assertEquals(5, states.size)
        assertTrue(states.contains(SyncManager.SyncState.IDLE))
        assertTrue(states.contains(SyncManager.SyncState.SYNCING))
        assertTrue(states.contains(SyncManager.SyncState.SUCCESS))
        assertTrue(states.contains(SyncManager.SyncState.ERROR))
        assertTrue(states.contains(SyncManager.SyncState.OFFLINE))
    }
}
