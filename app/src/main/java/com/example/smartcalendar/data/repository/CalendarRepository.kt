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
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
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
                val ownerAccount = it.getString(it.getColumnIndexOrThrow(CalendarContract.Calendars.OWNER_ACCOUNT)) ?: ""
                val accessLevel = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL))
                
                calendars.add(CalendarAccount(id, accountName, displayName, color, visible, isPrimary, ownerAccount, accessLevel))
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
     * Get writable calendars (calendars that can receive events)
     */
    fun getWritableCalendars(): List<CalendarAccount> {
        return getCalendars().filter { it.isWritable() }
    }

    /**
     * Ensure SmartCalendar local calendars exist (Personal and Work)
     */
    fun ensureLocalCalendarsExist() {
        val calendars = getCalendars()
        val localCalendars = calendars.filter { it.accountName == CalendarAccount.SMARTCALENDAR_ACCOUNT }
        
        // Create Personal calendar if not exists
        if (localCalendars.none { it.displayName == "Personal" }) {
            createLocalCalendar("Personal", android.graphics.Color.parseColor("#4285F4")) // Google Blue
        }
        
        // Create Work calendar if not exists
        if (localCalendars.none { it.displayName == "Work" }) {
            createLocalCalendar("Work", android.graphics.Color.parseColor("#EA4335")) // Google Red
        }
    }

    /**
     * Create a local calendar owned by SmartCalendar
     */
    private fun createLocalCalendar(name: String, color: Int): Long {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, CalendarAccount.SMARTCALENDAR_ACCOUNT)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, name)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name)
            put(CalendarContract.Calendars.CALENDAR_COLOR, color)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, CalendarAccount.SMARTCALENDAR_ACCOUNT)
        }
        
        // Must use asSyncAdapter to create local calendars
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CalendarAccount.SMARTCALENDAR_ACCOUNT)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        
        val calendarUri = contentResolver.insert(uri, values)
        return calendarUri?.lastPathSegment?.toLongOrNull() ?: -1
    }

    /**
     * Move an event to a different calendar
     * @return true if successful
     */
    fun moveEvent(eventId: Long, targetCalendarId: Long): Boolean {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, targetCalendarId)
        }
        
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val rowsUpdated = contentResolver.update(uri, values, null, null)
        return rowsUpdated > 0
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
            CalendarContract.Events.EXRULE,
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
     * Get event instances within a time range from specified calendars.
     * This method uses CalendarContract.Instances to get EXPANDED occurrences
     * of recurring events, properly handling RRULE, RDATE, EXDATE, and EXRULE.
     */
    fun getEventInstances(
        startTime: Long,
        endTime: Long,
        calendarIds: Set<Long>? = null
    ): List<Event> {
        val events = mutableListOf<Event>()
        
        // Build URI with time range for Instances query
        val uri = CalendarContract.Instances.CONTENT_URI
            .buildUpon()
            .appendPath(startTime.toString())
            .appendPath(endTime.toString())
            .build()
        
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.RDATE,
            CalendarContract.Instances.EXDATE,
            CalendarContract.Instances.EXRULE,
            CalendarContract.Instances.ORIGINAL_ID,
            CalendarContract.Instances.ORIGINAL_INSTANCE_TIME,
            CalendarContract.Instances.EVENT_TIMEZONE,
            CalendarContract.Instances.HAS_ALARM
        )
        
        // Build selection for optional calendar filter
        var selection: String? = null
        var selectionArgs: Array<String>? = null
        
        if (calendarIds != null && calendarIds.isNotEmpty()) {
            val placeholders = calendarIds.joinToString(",") { "?" }
            selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
            selectionArgs = calendarIds.map { it.toString() }.toTypedArray()
        }
        
        val cursor: Cursor? = contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC"
        )
        
        cursor?.use {
            while (it.moveToNext()) {
                val event = instanceCursorToEvent(it)
                // Filter out instances that are past the UNTIL date in RRULE
                // (CalendarContract.Instances may not immediately respect RRULE changes)
                if (isInstanceValid(event)) {
                    events.add(event)
                }
            }
        }
        
        return events
    }

    /**
     * Check if an recurring event instance should be displayed based on its RRULE.
     * This filters out instances that are past the UNTIL date when Calendar Provider
     * hasn't synced the RRULE changes yet.
     */
    private fun isInstanceValid(event: Event): Boolean {
        val rrule = event.rrule ?: return true // Non-recurring events are always valid
        
        // Parse UNTIL from RRULE
        val untilMatch = Regex("UNTIL=([^;]+)").find(rrule)
        if (untilMatch != null) {
            val untilStr = untilMatch.groupValues[1]
            try {
                val dateFormat = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val untilDate = dateFormat.parse(untilStr)
                if (untilDate != null && event.startTime > untilDate.time) {
                    // Instance is past UNTIL date - filter it out
                    return false
                }
            } catch (e: Exception) {
                // If we can't parse UNTIL, don't filter
            }
        }
        
        return true
    }

    /**
     * Convert Instances cursor to Event object
     */
    private fun instanceCursorToEvent(cursor: Cursor): Event {
        // Get originalId - may be null for non-exception events
        val originalIdIndex = cursor.getColumnIndex(CalendarContract.Instances.ORIGINAL_ID)
        val originalId = if (originalIdIndex >= 0 && !cursor.isNull(originalIdIndex)) {
            cursor.getLong(originalIdIndex)
        } else null
        
        // Get originalInstanceTime - may be null for non-exception events
        val originalInstanceTimeIndex = cursor.getColumnIndex(CalendarContract.Instances.ORIGINAL_INSTANCE_TIME)
        val originalInstanceTime = if (originalInstanceTimeIndex >= 0 && !cursor.isNull(originalInstanceTimeIndex)) {
            cursor.getLong(originalInstanceTimeIndex)
        } else null
        
        return Event(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)),
            calendarId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)) ?: "",
            description = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)) ?: "",
            location = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)) ?: "",
            startTime = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)),
            endTime = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)),
            isAllDay = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)) == 1,
            color = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)),
            rrule = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.RRULE)),
            rdate = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.RDATE)),
            exdate = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EXDATE)),
            exrule = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EXRULE)),
            originalId = originalId,
            originalInstanceTime = originalInstanceTime,
            timeZone = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_TIMEZONE)) ?: TimeZone.getDefault().id,
            hasAlarm = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Instances.HAS_ALARM)) == 1
        )
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
            CalendarContract.Events.EXRULE,
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
            exrule = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EXRULE)),
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
            put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, event.timeZone)
            
            if (event.rrule != null) {
                // Recurring event: use DURATION instead of DTEND
                put(CalendarContract.Events.RRULE, event.rrule)
                val duration = "P${(event.endTime - event.startTime) / 1000}S"
                put(CalendarContract.Events.DURATION, duration)
                putNull(CalendarContract.Events.DTEND)
            } else {
                // Non-recurring event: use DTEND and clear DURATION
                putNull(CalendarContract.Events.RRULE)
                putNull(CalendarContract.Events.DURATION)
                put(CalendarContract.Events.DTEND, event.endTime)
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
     * Delete a single instance of a recurring event by inserting an exception.
     * Uses CONTENT_EXCEPTION_URI to properly create a cancelled exception.
     * @param originalEventId The ID of the parent recurring event
     * @param instanceTime The start time of the instance being deleted
     * @return true if successful
     */
    fun deleteRecurringEventInstance(originalEventId: Long, instanceTime: Long): Boolean {
        val originalEvent = getEvent(originalEventId) ?: return false
        
        // Insert a cancelled exception using CONTENT_EXCEPTION_URI
        val exceptionUri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_EXCEPTION_URI,
            originalEventId
        )
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, instanceTime)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
        }
        
        return try {
            val uri = contentResolver.insert(exceptionUri, values)
            uri != null
        } catch (e: Exception) {
            android.util.Log.e("CalendarRepository", "Error deleting recurring instance", e)
            false
        }
    }

    /**
     * Delete this and all following occurrences of a recurring event.
     * This updates the original event's UNTIL clause to end before instanceTime.
     * @param originalEventId The ID of the parent recurring event
     * @param instanceTime The start time from which to delete
     * @return true if successful
     */
    fun deleteRecurringEventFromInstance(originalEventId: Long, instanceTime: Long): Boolean {
        val originalEvent = getEvent(originalEventId) ?: return false
        
        // Set UNTIL to one second before instanceTime
        val untilTime = instanceTime - 1000
        val dateFormat = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val untilStr = dateFormat.format(java.util.Date(untilTime))
        
        // Modify existing RRULE to add UNTIL clause
        val existingRrule = originalEvent.rrule ?: return false
        val newRrule = if (existingRrule.contains("UNTIL=") || existingRrule.contains("COUNT=")) {
            // Replace existing UNTIL or COUNT with new UNTIL
            existingRrule
                .replace(Regex("UNTIL=[^;]*"), "UNTIL=$untilStr")
                .replace(Regex("COUNT=[^;]*"), "UNTIL=$untilStr")
        } else {
            "$existingRrule;UNTIL=$untilStr"
        }
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.RRULE, newRrule)
        }
        
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, originalEventId)
        val rowsUpdated = contentResolver.update(uri, values, null, null)
        return rowsUpdated > 0
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

    /**
     * Create an exception for a recurring event instance.
     * This creates a new event that overrides a single occurrence.
     * @param originalEventId The ID of the parent recurring event
     * @param instanceTime The start time of the instance being modified
     * @param updatedEvent The event data with updated fields
     * @return The ID of the newly created exception, or -1 if failed
     */
    fun createException(
        originalEventId: Long,
        instanceTime: Long,
        updatedEvent: Event
    ): Long {
        val calendarId = if (updatedEvent.calendarId > 0) updatedEvent.calendarId else getPrimaryCalendarId()
            ?: throw IllegalStateException("No calendar available")
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, updatedEvent.title)
            put(CalendarContract.Events.DESCRIPTION, updatedEvent.description)
            put(CalendarContract.Events.EVENT_LOCATION, updatedEvent.location)
            put(CalendarContract.Events.DTSTART, updatedEvent.startTime)
            put(CalendarContract.Events.DTEND, updatedEvent.endTime)
            put(CalendarContract.Events.ALL_DAY, if (updatedEvent.isAllDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, updatedEvent.timeZone)
            put(CalendarContract.Events.ORIGINAL_ID, originalEventId)
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, instanceTime)
            put(CalendarContract.Events.ORIGINAL_ALL_DAY, if (updatedEvent.isAllDay) 1 else 0)
            // Exceptions don't have their own RRULE
            putNull(CalendarContract.Events.RRULE)
        }
        
        val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val exceptionId = uri?.lastPathSegment?.toLongOrNull() ?: -1
        
        // Add reminder if specified
        if (exceptionId > 0 && updatedEvent.reminderMinutes != null) {
            addReminder(exceptionId, updatedEvent.reminderMinutes)
        }
        
        return exceptionId
    }

    /**
     * Split a recurring event series from a specific instance.
     * The original event ends before this instance, and a new event starts from this instance.
     * @param originalEventId The ID of the original recurring event
     * @param splitTime The start time of the instance to split from
     * @param newEventData The event data for the new series
     * @return The ID of the newly created recurring event, or -1 if failed
     */
    fun splitRecurringSeries(
        originalEventId: Long,
        splitTime: Long,
        newEventData: Event
    ): Long {
        // First, update the original event to end BEFORE the split time
        val originalEvent = getEvent(originalEventId) ?: return -1
        
        // Modify the RRULE to add UNTIL clause before split time
        val untilTime = splitTime - 1000 // 1 second before
        val sdf = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val untilString = sdf.format(java.util.Date(untilTime))
        
        val originalRrule = originalEvent.rrule ?: return -1
        // Remove any existing UNTIL or COUNT clause and add new UNTIL
        val modifiedRrule = originalRrule
            .split(";")
            .filter { !it.startsWith("UNTIL=") && !it.startsWith("COUNT=") }
            .joinToString(";") + ";UNTIL=$untilString"
        
        val updateValues = ContentValues().apply {
            put(CalendarContract.Events.RRULE, modifiedRrule)
        }
        val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, originalEventId)
        contentResolver.update(updateUri, updateValues, null, null)
        
        // Now create the new recurring event starting from splitTime
        return insertEvent(newEventData)
    }

    /**
     * Update only specific fields of a recurring event (master record).
     * This preserves exception-specific values for fields not being updated.
     * @param eventId The ID of the event to update
     * @param changedFields Map of field names to new values
     * @return true if successful
     */
    fun updateRecurringEventFields(eventId: Long, changedFields: ContentValues): Boolean {
        if (changedFields.size() == 0) return true
        
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val rowsUpdated = contentResolver.update(uri, changedFields, null, null)
        return rowsUpdated > 0
    }

    /**
     * Add an EXDATE to exclude a specific instance from a recurring event.
     * @param eventId The ID of the recurring event
     * @param instanceTime The time of the instance to exclude
     * @return true if successful
     */
    fun addExdateToEvent(eventId: Long, instanceTime: Long): Boolean {
        val event = getEvent(eventId) ?: return false
        
        val sdf = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val exdateString = sdf.format(java.util.Date(instanceTime))
        
        val newExdate = if (event.exdate.isNullOrBlank()) {
            exdateString
        } else {
            "${event.exdate},$exdateString"
        }
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.EXDATE, newExdate)
        }
        
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val rowsUpdated = contentResolver.update(uri, values, null, null)
        return rowsUpdated > 0
    }
}
