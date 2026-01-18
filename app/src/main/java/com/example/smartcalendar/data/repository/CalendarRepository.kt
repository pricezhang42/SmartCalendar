package com.example.smartcalendar.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.example.smartcalendar.data.model.CalendarAccount
import com.example.smartcalendar.data.model.Event
import com.example.smartcalendar.data.model.RecurrenceRule
import com.example.smartcalendar.data.model.RepeatEndType
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
            CalendarContract.Events.EXRULE,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.HAS_ALARM,
            // Additional columns for debugging
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ACCESS_LEVEL,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.EVENT_COLOR,
            CalendarContract.Events.EVENT_COLOR_KEY,
            CalendarContract.Events.EVENT_END_TIMEZONE,
            CalendarContract.Events.GUESTS_CAN_INVITE_OTHERS,
            CalendarContract.Events.GUESTS_CAN_MODIFY,
            CalendarContract.Events.GUESTS_CAN_SEE_GUESTS,
            CalendarContract.Events.HAS_ATTENDEE_DATA,
            CalendarContract.Events.HAS_EXTENDED_PROPERTIES,
            CalendarContract.Events.IS_ORGANIZER,
            CalendarContract.Events.LAST_DATE,
            CalendarContract.Events.LAST_SYNCED,
            CalendarContract.Events.ORGANIZER,
            CalendarContract.Events.ORIGINAL_ALL_DAY,
            CalendarContract.Events.ORIGINAL_ID,
            CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
            CalendarContract.Events.ORIGINAL_SYNC_ID,
            CalendarContract.Events.SELF_ATTENDEE_STATUS,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.UID_2445,
            CalendarContract.Events.DIRTY,
            CalendarContract.Events.DELETED
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
                events.add(event)
            }
        }
        
        // Filter out orphaned exceptions (events with originalId pointing to non-existent master)
        val masterEventIds = mutableSetOf<Long>()
        events.forEach { if (it.originalId == null) masterEventIds.add(it.id) }
        
        return events.filter { event ->
            // Keep events without originalId (normal events and master events)
            // Only keep exceptions if their master event exists
            event.originalId == null || masterEventIds.contains(event.originalId) || getEvent(event.originalId) != null
        }
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
            CalendarContract.Events.HAS_ALARM,
            // Additional columns for debugging
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ACCESS_LEVEL,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.EVENT_COLOR,
            CalendarContract.Events.EVENT_COLOR_KEY,
            CalendarContract.Events.EVENT_END_TIMEZONE,
            CalendarContract.Events.GUESTS_CAN_INVITE_OTHERS,
            CalendarContract.Events.GUESTS_CAN_MODIFY,
            CalendarContract.Events.GUESTS_CAN_SEE_GUESTS,
            CalendarContract.Events.HAS_ATTENDEE_DATA,
            CalendarContract.Events.HAS_EXTENDED_PROPERTIES,
            CalendarContract.Events.IS_ORGANIZER,
            CalendarContract.Events.LAST_DATE,
            CalendarContract.Events.LAST_SYNCED,
            CalendarContract.Events.ORGANIZER,
            CalendarContract.Events.ORIGINAL_ALL_DAY,
            CalendarContract.Events.ORIGINAL_ID,
            CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
            CalendarContract.Events.ORIGINAL_SYNC_ID,
            CalendarContract.Events.SELF_ATTENDEE_STATUS,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.UID_2445,
            CalendarContract.Events.DIRTY,
            CalendarContract.Events.DELETED
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
        // Helper function to safely get nullable int
        fun getIntOrNull(column: String): Int? {
            val idx = cursor.getColumnIndex(column)
            return if (idx >= 0 && !cursor.isNull(idx)) cursor.getInt(idx) else null
        }
        
        // Helper function to safely get nullable long
        fun getLongOrNull(column: String): Long? {
            val idx = cursor.getColumnIndex(column)
            return if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else null
        }
        
        // Helper function to safely get nullable string
        fun getStringOrNull(column: String): String? {
            val idx = cursor.getColumnIndex(column)
            return if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else null
        }
        
        // Helper function to safely get boolean (default false)
        fun getBoolean(column: String, default: Boolean = false): Boolean {
            val idx = cursor.getColumnIndex(column)
            return if (idx >= 0 && !cursor.isNull(idx)) cursor.getInt(idx) == 1 else default
        }

        return Event(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)),
            calendarId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: "",
            description = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)) ?: "",
            location = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)) ?: "",
            startTime = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)),
            endTime = getLongOrNull(CalendarContract.Events.DTEND) ?: cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)),
            isAllDay = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1,
            color = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.DISPLAY_COLOR)),
            rrule = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.RRULE)),
            rdate = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.RDATE)),
            exdate = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EXDATE)),
            exrule = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EXRULE)),
            timeZone = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE)) ?: TimeZone.getDefault().id,
            hasAlarm = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.HAS_ALARM)) == 1,
            // Additional fields
            duration = getStringOrNull(CalendarContract.Events.DURATION),
            accessLevel = getIntOrNull(CalendarContract.Events.ACCESS_LEVEL),
            availability = getIntOrNull(CalendarContract.Events.AVAILABILITY),
            eventColor = getIntOrNull(CalendarContract.Events.EVENT_COLOR),
            eventColorKey = getStringOrNull(CalendarContract.Events.EVENT_COLOR_KEY),
            eventEndTimezone = getStringOrNull(CalendarContract.Events.EVENT_END_TIMEZONE),
            guestsCanInviteOthers = getBoolean(CalendarContract.Events.GUESTS_CAN_INVITE_OTHERS, true),
            guestsCanModify = getBoolean(CalendarContract.Events.GUESTS_CAN_MODIFY),
            guestsCanSeeGuests = getBoolean(CalendarContract.Events.GUESTS_CAN_SEE_GUESTS, true),
            hasAttendeeData = getBoolean(CalendarContract.Events.HAS_ATTENDEE_DATA),
            hasExtendedProperties = getBoolean(CalendarContract.Events.HAS_EXTENDED_PROPERTIES),
            isOrganizer = getBoolean(CalendarContract.Events.IS_ORGANIZER, true),
            lastDate = getLongOrNull(CalendarContract.Events.LAST_DATE),
            lastSynced = getBoolean(CalendarContract.Events.LAST_SYNCED),
            organizer = getStringOrNull(CalendarContract.Events.ORGANIZER),
            originalAllDay = getIntOrNull(CalendarContract.Events.ORIGINAL_ALL_DAY)?.let { it == 1 },
            originalId = getLongOrNull(CalendarContract.Events.ORIGINAL_ID),
            originalInstanceTime = getLongOrNull(CalendarContract.Events.ORIGINAL_INSTANCE_TIME),
            originalSyncId = getStringOrNull(CalendarContract.Events.ORIGINAL_SYNC_ID),
            selfAttendeeStatus = getIntOrNull(CalendarContract.Events.SELF_ATTENDEE_STATUS),
            status = getIntOrNull(CalendarContract.Events.STATUS),
            uid2445 = getStringOrNull(CalendarContract.Events.UID_2445),
            dirty = getBoolean(CalendarContract.Events.DIRTY),
            deleted = getBoolean(CalendarContract.Events.DELETED)
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
     * Delete an event and all its exceptions (for recurring events)
     * @return true if successful
     */
    fun deleteEvent(eventId: Long): Boolean {
        // First, delete any exceptions that reference this event as their original
        val exceptionUri = CalendarContract.Events.CONTENT_URI
        contentResolver.delete(
            exceptionUri,
            "${CalendarContract.Events.ORIGINAL_ID} = ?",
            arrayOf(eventId.toString())
        )
        
        // Then delete the event itself
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
     * The original event ends before this instance, and optionally a new event starts from this instance.
     * @param originalEventId The ID of the original recurring event
     * @param splitTime The start time of the instance to split from
     * @param newEventData The event data for the new series (null = just end series)
     * @return The ID of the newly created event, 0 if no new event, or -1 if failed
     */
    fun splitRecurringSeries(
        originalEventId: Long,
        splitTime: Long,
        newEventData: Event?
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
        
        // Create the new event starting from splitTime (if provided)
        // For delete "this and following", newEventData is null - we just end the series
        return if (newEventData != null) {
            insertEvent(newEventData)
        } else {
            0L // No new event created
        }
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
        
        android.util.Log.d("CalendarRepository", "Adding EXDATE: $exdateString for instanceTime: $instanceTime to event $eventId")
        
        val newExdate = if (event.exdate.isNullOrBlank()) {
            exdateString
        } else {
            "${event.exdate},$exdateString"
        }
        
        // Important: Preserve RRULE when updating EXDATE
        // Only update EXDATE and RRULE - don't touch DURATION
        val values = ContentValues().apply {
            put(CalendarContract.Events.EXDATE, newExdate)
            // Preserve the recurrence rule
            if (event.rrule != null) {
                put(CalendarContract.Events.RRULE, event.rrule)
            }
        }
        
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val rowsUpdated = contentResolver.update(uri, values, null, null)
        android.util.Log.d("CalendarRepository", "EXDATE update result: $rowsUpdated rows updated, newExdate: $newExdate, rrule preserved: ${event.rrule}")
        return rowsUpdated > 0
    }

    /**
     * Shift the start time of a recurring event to skip the first occurrence.
     * This is used when deleting the first occurrence since EXDATE doesn't work for DTSTART.
     * @param eventId The ID of the recurring event
     * @return true if successful
     */
    fun shiftRecurrenceStart(eventId: Long): Boolean {
        val event = getEvent(eventId) ?: return false
        val rrule = event.rrule ?: return false
        
        // Parse the recurrence rule to calculate the next occurrence
        val recurrenceRule = RecurrenceRule.fromRRule(rrule) ?: return false
        
        // Calculate the shift based on frequency
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = event.startTime
        
        when (recurrenceRule.frequency) {
            Event.FREQ_DAILY -> calendar.add(java.util.Calendar.DAY_OF_YEAR, recurrenceRule.interval)
            Event.FREQ_WEEKLY -> calendar.add(java.util.Calendar.WEEK_OF_YEAR, recurrenceRule.interval)
            Event.FREQ_MONTHLY -> calendar.add(java.util.Calendar.MONTH, recurrenceRule.interval)
            Event.FREQ_YEARLY -> calendar.add(java.util.Calendar.YEAR, recurrenceRule.interval)
        }
        
        val newStartTime = calendar.timeInMillis
        val duration = event.endTime - event.startTime
        val newEndTime = newStartTime + duration
        
        android.util.Log.d("CalendarRepository", "Shifting DTSTART from ${event.startTime} to $newStartTime for event $eventId")
        
        // Update the event with new start/end times
        // Also decrement COUNT if applicable
        var updatedRrule = rrule
        if (recurrenceRule.endType == RepeatEndType.REPEAT_COUNT && recurrenceRule.count != null) {
            val newCount = recurrenceRule.count - 1
            if (newCount <= 0) {
                // No more occurrences left
                return deleteEvent(eventId)
            }
            updatedRrule = recurrenceRule.copy(count = newCount).toRRule()
        }
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, newStartTime)
            // Use DURATION for recurring events
            val durationSecs = duration / 1000
            put(CalendarContract.Events.DURATION, "P${durationSecs}S")
            putNull(CalendarContract.Events.DTEND)
            put(CalendarContract.Events.RRULE, updatedRrule)
        }
        
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val rowsUpdated = contentResolver.update(uri, values, null, null)
        return rowsUpdated > 0
    }
}
