package com.example.smartcalendar.data.sync

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.repository.LocalCalendarRepository

/**
 * Exports events from local repository to Android Calendar Provider.
 */
class CalendarExporter(private val context: Context) {

    private val contentResolver = context.contentResolver

    data class ExportableCalendar(
        val id: Long,
        val name: String,
        val accountName: String,
        val color: Int
    )

    /**
     * Get list of local calendars available for export
     */
    fun getExportableCalendars(): List<ExportableCalendar> {
        val calendars = mutableListOf<ExportableCalendar>()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )

        // Only writable calendars
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        val cursor = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(1) ?: ""
                val accountName = it.getString(2) ?: ""
                // Exclude holiday calendars
                if (!name.contains("holiday", ignoreCase = true) &&
                    !accountName.contains("holiday", ignoreCase = true)) {
                    calendars.add(ExportableCalendar(
                        id = it.getLong(0),
                        name = name,
                        accountName = accountName,
                        color = it.getInt(3)
                    ))
                }
            }
        }

        return calendars
    }

    /**
     * Export events from a local app calendar to a system calendar
     * @param calendarId The system calendar to export to
     * @param sourceCalendarId The local app calendar to export from (null = all)
     * @return number of events exported
     */
    suspend fun exportToCalendar(calendarId: Long, sourceCalendarId: String? = null): Int {
        val repository = LocalCalendarRepository.getInstance()
        val allEvents = repository.getAllEvents()
        val events = if (sourceCalendarId != null) {
            allEvents.filter { it.calendarId == sourceCalendarId }
        } else {
            allEvents
        }
        var exportedCount = 0

        events.forEach { event ->
            val existingEventId = findExistingEvent(calendarId, event)

            if (existingEventId != null) {
                // Update existing event
                updateEvent(existingEventId, event)
            } else {
                // Insert new event
                insertEvent(calendarId, event)
            }
            exportedCount++
        }

        return exportedCount
    }

    /**
     * Find existing event by originalId and title
     */
    private fun findExistingEvent(calendarId: Long, event: ICalEvent): Long? {
        if (event.originalId == null) return null

        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                "${CalendarContract.Events._ID} = ? AND " +
                "${CalendarContract.Events.TITLE} = ?"
        val selectionArgs = arrayOf(
            calendarId.toString(),
            event.originalId.toString(),
            event.summary
        )

        val cursor = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        return cursor?.use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    private fun insertEvent(calendarId: Long, event: ICalEvent): Long {
        val values = eventToContentValues(calendarId, event)
        val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri?.lastPathSegment?.toLongOrNull() ?: -1
    }

    private fun updateEvent(eventId: Long, event: ICalEvent) {
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, event.summary)
            put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DTSTART, event.dtStart)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)

            if (event.isRecurring) {
                put(CalendarContract.Events.RRULE, event.rrule)
                put(CalendarContract.Events.DURATION, event.duration ?: ICalEvent.toDurationString(event.getDurationMs()))
                putNull(CalendarContract.Events.DTEND)
            } else {
                put(CalendarContract.Events.DTEND, event.dtEnd)
                putNull(CalendarContract.Events.RRULE)
                putNull(CalendarContract.Events.DURATION)
            }

            event.exdate?.let { put(CalendarContract.Events.EXDATE, it) }
        }

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        contentResolver.update(uri, values, null, null)
    }

    private fun eventToContentValues(calendarId: Long, event: ICalEvent): ContentValues {
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.summary)
            put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DTSTART, event.dtStart)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)

            if (event.isRecurring) {
                put(CalendarContract.Events.RRULE, event.rrule)
                put(CalendarContract.Events.DURATION, event.duration ?: ICalEvent.toDurationString(event.getDurationMs()))
            } else {
                put(CalendarContract.Events.DTEND, event.dtEnd)
            }

            event.rdate?.let { put(CalendarContract.Events.RDATE, it) }
            event.exdate?.let { put(CalendarContract.Events.EXDATE, it) }
            event.exrule?.let { put(CalendarContract.Events.EXRULE, it) }
        }
    }
}
