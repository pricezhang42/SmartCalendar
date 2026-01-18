package com.example.smartcalendar.data.model

import android.provider.CalendarContract

/**
 * Event data class compatible with Google Calendar Provider.
 * Maps to CalendarContract.Events fields.
 */
data class Event(
    val id: Long = 0,
    val calendarId: Long = 0,
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis() + 3600000, // Default 1 hour
    val isAllDay: Boolean = false,
    val color: Int = 0,
    val reminderMinutes: Int? = null, // null = no reminder
    val rrule: String? = null, // RFC 5545 recurrence rule
    val rdate: String? = null,
    val exdate: String? = null,
    val exrule: String? = null, // RFC 5545 exception rule
    val originalId: Long? = null, // ID of the recurring event this is an exception of
    val originalInstanceTime: Long? = null, // Original occurrence time that was modified
    val timeZone: String = java.util.TimeZone.getDefault().id,
    val hasAlarm: Boolean = false
) {
    companion object {
        // Reminder options in minutes
        val REMINDER_OPTIONS = listOf(
            null to "None",
            5 to "5 minutes before",
            10 to "10 minutes before",
            15 to "15 minutes before",
            30 to "30 minutes before",
            60 to "1 hour before",
            1440 to "1 day before"
        )

        // Frequency constants for RRULE
        const val FREQ_DAILY = "DAILY"
        const val FREQ_WEEKLY = "WEEKLY"
        const val FREQ_MONTHLY = "MONTHLY"
        const val FREQ_YEARLY = "YEARLY"

        // Day of week constants for BYDAY
        const val DAY_SU = "SU"
        const val DAY_MO = "MO"
        const val DAY_TU = "TU"
        const val DAY_WE = "WE"
        const val DAY_TH = "TH"
        const val DAY_FR = "FR"
        const val DAY_SA = "SA"
    }

    /**
     * Check if this event is recurring
     */
    fun isRecurring(): Boolean = rrule != null

    /**
     * Check if this is an exception to a recurring event
     */
    fun isException(): Boolean = originalId != null

    /**
     * Get the duration in milliseconds
     */
    fun getDurationMillis(): Long = endTime - startTime

    /**
     * Get a formatted duration string
     */
    fun getFormattedDuration(): String {
        val hours = getDurationMillis() / (1000 * 60 * 60)
        val minutes = (getDurationMillis() % (1000 * 60 * 60)) / (1000 * 60)
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
}
