package com.example.smartcalendar.data.model

/**
 * Represents a single occurrence/instance of an event for display.
 * Generated from ICalEvent by expanding RRULE.
 */
data class EventInstance(
    val eventUid: String,
    val startTime: Long,
    val endTime: Long,
    val title: String,
    val description: String = "",
    val location: String = "",
    val color: Int,
    val allDay: Boolean = false,
    val isRecurring: Boolean = false
) {
    /**
     * Check if this instance overlaps with a time range
     */
    fun overlaps(rangeStart: Long, rangeEnd: Long): Boolean {
        return startTime < rangeEnd && endTime > rangeStart
    }
    
    /**
     * Check if this instance is on a specific day
     */
    fun isOnDay(dayStart: Long, dayEnd: Long): Boolean {
        return startTime < dayEnd && endTime > dayStart
    }
}
