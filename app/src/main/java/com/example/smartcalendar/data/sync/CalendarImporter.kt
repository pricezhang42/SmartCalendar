package com.example.smartcalendar.data.sync

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.repository.LocalCalendarRepository
import java.util.*

/**
 * Imports events from Android Calendar Provider to local repository.
 */
class CalendarImporter(private val context: Context) {
    
    private val contentResolver = context.contentResolver
    
    data class ImportableCalendar(
        val id: Long,
        val name: String,
        val accountName: String,
        val color: Int
    )
    
    /**
     * Get list of calendars available for import (excluding holidays)
     */
    fun getImportableCalendars(): List<ImportableCalendar> {
        val calendars = mutableListOf<ImportableCalendar>()
        
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR
        )
        
        // Exclude holiday calendars
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} NOT LIKE ?"
        val selectionArgs = arrayOf("%holiday%")
        
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
                // Double-check to exclude holiday calendars
                if (!name.contains("holiday", ignoreCase = true) && 
                    !accountName.contains("holiday", ignoreCase = true)) {
                    calendars.add(ImportableCalendar(
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
     * Import all events from a calendar
     * @return number of events imported
     */
    fun importFromCalendar(calendarId: Long): Int {
        val repository = LocalCalendarRepository.getInstance()
        var importedCount = 0
        
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE,
            CalendarContract.Events.EXDATE,
            CalendarContract.Events.EXRULE,
            CalendarContract.Events.DISPLAY_COLOR,
            CalendarContract.Events.LAST_DATE
        )
        
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0"
        val selectionArgs = arrayOf(calendarId.toString())
        
        val cursor = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )
        
        cursor?.use {
            while (it.moveToNext()) {
                val event = cursorToICalEvent(it)
                if (event != null) {
                    // Check for conflict: same originalId and title
                    val existing = repository.findByOriginalIdAndTitle(event.originalId ?: 0, event.summary)
                    if (existing != null) {
                        // Update existing event
                        repository.updateEvent(event.copy(uid = existing.uid))
                    } else {
                        // Add new event
                        repository.addEvent(event)
                    }
                    importedCount++
                }
            }
        }
        
        return importedCount
    }
    
    private fun cursorToICalEvent(cursor: Cursor): ICalEvent? {
        val id = cursor.getLong(0)
        val title = cursor.getString(1) ?: return null
        val dtStart = cursor.getLong(4)
        if (dtStart == 0L) return null
        
        var dtEnd = cursor.getLong(5)
        val duration = cursor.getString(6)
        val rrule = cursor.getString(8)
        
        // For recurring events, DTEND is null - get from first instance
        if (dtEnd == 0L && !rrule.isNullOrEmpty()) {
            dtEnd = getFirstInstanceEnd(id, dtStart)
        }
        
        // If still no dtEnd, calculate from duration or default to 1 hour
        if (dtEnd == 0L) {
            dtEnd = if (!duration.isNullOrEmpty()) {
                dtStart + ICalEvent.parseDuration(duration)
            } else {
                dtStart + 3600000L
            }
        }
        
        return ICalEvent(
            uid = UUID.randomUUID().toString(),
            summary = title,
            description = cursor.getString(2) ?: "",
            location = cursor.getString(3) ?: "",
            dtStart = dtStart,
            dtEnd = dtEnd,
            duration = duration,
            allDay = cursor.getInt(7) == 1,
            rrule = rrule,
            rdate = cursor.getString(9),
            exdate = cursor.getString(10),
            exrule = cursor.getString(11),
            color = cursor.getInt(12),
            originalId = id
        )
    }
    
    /**
     * Get DTEND from first instance for recurring events
     */
    private fun getFirstInstanceEnd(eventId: Long, dtStart: Long): Long {
        val projection = arrayOf(
            CalendarContract.Instances.END
        )
        
        // Query a reasonable range from dtStart
        val rangeEnd = dtStart + (365L * 24 * 60 * 60 * 1000) // 1 year
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(dtStart.toString())
            .appendPath(rangeEnd.toString())
            .build()
        
        val selection = "${CalendarContract.Instances.EVENT_ID} = ?"
        val selectionArgs = arrayOf(eventId.toString())
        
        val cursor = contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC LIMIT 1"
        )
        
        return cursor?.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        } ?: 0L
    }
}
