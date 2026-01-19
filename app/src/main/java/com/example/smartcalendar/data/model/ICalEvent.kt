package com.example.smartcalendar.data.model

import java.util.UUID

/**
 * iCalendar format event model.
 * Represents a calendar event with full RFC 5545 recurrence support.
 */
data class ICalEvent(
    val uid: String = UUID.randomUUID().toString(),
    val calendarId: String = "personal", // Reference to LocalCalendar
    val summary: String,
    val description: String = "",
    val location: String = "",
    val dtStart: Long,
    val dtEnd: Long,
    val duration: String? = null, // For recurring events (e.g., "PT1H")
    val allDay: Boolean = false,
    val rrule: String? = null,
    val rdate: String? = null,
    val exdate: String? = null,
    val exrule: String? = null,
    val color: Int = -0x1A8CFF, // Default blue
    val lastModified: Long = System.currentTimeMillis(),
    val originalId: Long? = null // For tracking imported events
) {
    val isRecurring: Boolean
        get() = !rrule.isNullOrEmpty()
    
    /**
     * Get event duration in milliseconds
     */
    fun getDurationMs(): Long {
        return if (duration != null) {
            parseDuration(duration)
        } else {
            dtEnd - dtStart
        }
    }
    
    companion object {
        /**
         * Parse ISO 8601 duration string (e.g., "PT1H30M", "P1D")
         */
        fun parseDuration(duration: String): Long {
            var millis = 0L
            val regex = Regex("P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
            val match = regex.find(duration) ?: return 3600000L // Default 1 hour
            
            match.groupValues[1].toIntOrNull()?.let { millis += it * 86400000L } // Days
            match.groupValues[2].toIntOrNull()?.let { millis += it * 3600000L }  // Hours
            match.groupValues[3].toIntOrNull()?.let { millis += it * 60000L }    // Minutes
            match.groupValues[4].toIntOrNull()?.let { millis += it * 1000L }     // Seconds
            
            return if (millis > 0) millis else 3600000L
        }
        
        /**
         * Convert milliseconds to ISO 8601 duration
         */
        fun toDurationString(millis: Long): String {
            val hours = millis / 3600000
            val minutes = (millis % 3600000) / 60000
            return if (minutes > 0) "PT${hours}H${minutes}M" else "PT${hours}H"
        }
    }
}
