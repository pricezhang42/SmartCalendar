package com.example.smartcalendar.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.example.smartcalendar.data.model.CalendarAccount
import com.example.smartcalendar.data.model.Event
import java.util.TimeZone

/**
 * Repository for Calendar Provider API operations.
 * Handles CRUD operations for calendar events.
 */
class CalendarRepository(private val context: Context) {

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    /**
     * Get all available calendar accounts
     */
    fun getCalendars(): List<CalendarAccount> {
        val calendars = mutableListOf<CalendarAccount>()
        
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.IS_PRIMARY
        )
        
        val cursor: Cursor? = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null
        )
        
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                val accountName = it.getString(it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME))
                val displayName = it.getString(it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME))
                val color = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR))
                val visible = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)) == 1
                val isPrimary = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)) == 1
                
                calendars.add(CalendarAccount(id, accountName, displayName, color, visible, isPrimary))
            }
        }
        
        return calendars
    }

    /**
     * Get the primary calendar ID, or the first available calendar
     */
    fun getPrimaryCalendarId(): Long? {
        val calendars = getCalendars()
        return calendars.find { it.isPrimary }?.id ?: calendars.firstOrNull()?.id
    }

    /**
     * Get events within a time range from specified calendars
     */
    fun getEvents(
        startTime: Long,
        endTime: Long,
        calendarIds: Set<Long>? = null
    ): List<Event> {
        val events = mutableListOf<Event>()
        
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.DISPLAY_COLOR,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE,
            CalendarContract.Events.EXDATE,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.HAS_ALARM
        )
        
        // Build selection for time range and optional calendar filter
        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()
        
        // Events that overlap with the time range
        selectionParts.add("((${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?) OR (${CalendarContract.Events.DTEND} > ? AND ${CalendarContract.Events.DTEND} <= ?) OR (${CalendarContract.Events.DTSTART} < ? AND ${CalendarContract.Events.DTEND} > ?))")
        selectionArgs.addAll(listOf(
            startTime.toString(), endTime.toString(),
            startTime.toString(), endTime.toString(),
            startTime.toString(), endTime.toString()
        ))
        
        if (calendarIds != null && calendarIds.isNotEmpty()) {
            val placeholders = calendarIds.joinToString(",") { "?" }
            selectionParts.add("${CalendarContract.Events.CALENDAR_ID} IN ($placeholders)")
            selectionArgs.addAll(calendarIds.map { it.toString() })
        }
        
        val selection = selectionParts.joinToString(" AND ")
        
        val cursor: Cursor? = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs.toTypedArray(),
            "${CalendarContract.Events.DTSTART} ASC"
        )
        
        cursor?.use {
            while (it.moveToNext()) {
                val event = cursorToEvent(it)
                events.add(event)
            }
        }
        
        return events
    }

    /**
     * Get a single event by ID
     */
    fun getEvent(eventId: Long): Event? {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.DISPLAY_COLOR,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE,
            CalendarContract.Events.EXDATE,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.HAS_ALARM
        )
        
        val selection = "${CalendarContract.Events._ID} = ?"
        val selectionArgs = arrayOf(eventId.toString())
        
        val cursor: Cursor? = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )
        
        return cursor?.use {
            if (it.moveToFirst()) cursorToEvent(it) else null
        }
    }

    private fun cursorToEvent(cursor: Cursor): Event {
        return Event(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)),
            calendarId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: "",
            description = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)) ?: "",
            location = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)) ?: "",
            startTime = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)),
            endTime = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)),
            isAllDay = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1,
            color = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.DISPLAY_COLOR)),
            rrule = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.RRULE)),
            rdate = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.RDATE)),
            exdate = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EXDATE)),
            timeZone = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE)) ?: TimeZone.getDefault().id,
            hasAlarm = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.HAS_ALARM)) == 1
        )
    }

    /**
     * Insert a new event
     * @return The ID of the newly created event, or -1 if failed
     */
    fun insertEvent(event: Event): Long {
        val calendarId = if (event.calendarId > 0) event.calendarId else getPrimaryCalendarId()
            ?: throw IllegalStateException("No calendar available")
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DTSTART, event.startTime)
            put(CalendarContract.Events.DTEND, event.endTime)
            put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, event.timeZone)
            
            event.rrule?.let { put(CalendarContract.Events.RRULE, it) }
            event.rdate?.let { put(CalendarContract.Events.RDATE, it) }
            event.exdate?.let { put(CalendarContract.Events.EXDATE, it) }
        }
        
        val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = uri?.lastPathSegment?.toLongOrNull() ?: -1
        
        // Add reminder if specified
        if (eventId > 0 && event.reminderMinutes != null) {
            addReminder(eventId, event.reminderMinutes)
        }
        
        return eventId
    }

    /**
     * Update an existing event
     * @return true if successful
     */
    fun updateEvent(event: Event): Boolean {
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DTSTART, event.startTime)
            put(CalendarContract.Events.DTEND, event.endTime)
            put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, event.timeZone)
            
            if (event.rrule != null) {
                put(CalendarContract.Events.RRULE, event.rrule)
            } else {
                putNull(CalendarContract.Events.RRULE)
            }
        }
        
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
        val rowsUpdated = contentResolver.update(uri, values, null, null)
        
        // Update reminder
        if (rowsUpdated > 0) {
            deleteReminders(event.id)
            event.reminderMinutes?.let { addReminder(event.id, it) }
        }
        
        return rowsUpdated > 0
    }

    /**
     * Delete an event
     * @return true if successful
     */
    fun deleteEvent(eventId: Long): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val rowsDeleted = contentResolver.delete(uri, null, null)
        return rowsDeleted > 0
    }

    /**
     * Add a reminder to an event
     */
    private fun addReminder(eventId: Long, minutes: Int) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutes)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
    }

    /**
     * Delete all reminders for an event
     */
    private fun deleteReminders(eventId: Long) {
        contentResolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString())
        )
    }

    /**
     * Get reminder minutes for an event
     */
    fun getEventReminder(eventId: Long): Int? {
        val projection = arrayOf(CalendarContract.Reminders.MINUTES)
        val selection = "${CalendarContract.Reminders.EVENT_ID} = ?"
        val selectionArgs = arrayOf(eventId.toString())
        
        val cursor: Cursor? = contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )
        
        return cursor?.use {
            if (it.moveToFirst()) {
                it.getInt(it.getColumnIndexOrThrow(CalendarContract.Reminders.MINUTES))
            } else null
        }
    }
}
