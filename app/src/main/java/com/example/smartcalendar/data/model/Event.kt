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

        // Repeat frequency constants
        const val FREQ_DAILY = "DAILY"
        const val FREQ_WEEKLY = "WEEKLY"
        const val FREQ_MONTHLY = "MONTHLY"
        const val FREQ_YEARLY = "YEARLY"

        // Days of week for weekly recurrence
        val DAYS_OF_WEEK = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")
    }
}

/**
 * Enum for repeat end type
 */
enum class RepeatEndType {
    ENDLESSLY,
    UNTIL_DATE,
    REPEAT_COUNT
}

/**
 * Data class for recurrence rule configuration
 */
data class RecurrenceRule(
    val frequency: String = Event.FREQ_WEEKLY,
    val interval: Int = 1,
    val daysOfWeek: Set<String> = emptySet(), // For weekly: SU, MO, TU, etc.
    val endType: RepeatEndType = RepeatEndType.ENDLESSLY,
    val untilDate: Long? = null,
    val count: Int? = null
) {
    /**
     * Convert to RFC 5545 RRULE string
     */
    fun toRRule(): String {
        val parts = mutableListOf("FREQ=$frequency")
        
        if (interval > 1) {
            parts.add("INTERVAL=$interval")
        }
        
        if (frequency == Event.FREQ_WEEKLY && daysOfWeek.isNotEmpty()) {
            parts.add("BYDAY=${daysOfWeek.joinToString(",")}")
        }
        
        when (endType) {
            RepeatEndType.UNTIL_DATE -> {
                untilDate?.let {
                    val sdf = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    parts.add("UNTIL=${sdf.format(java.util.Date(it))}")
                }
            }
            RepeatEndType.REPEAT_COUNT -> {
                count?.let { parts.add("COUNT=$it") }
            }
            RepeatEndType.ENDLESSLY -> { /* No end clause */ }
        }
        
        return parts.joinToString(";")
    }

    companion object {
        /**
         * Parse an RRULE string into RecurrenceRule
         */
        fun fromRRule(rrule: String?): RecurrenceRule? {
            if (rrule.isNullOrBlank()) return null
            
            val parts = rrule.split(";").associate {
                val (key, value) = it.split("=", limit = 2)
                key to value
            }
            
            val frequency = parts["FREQ"] ?: return null
            val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
            val daysOfWeek = parts["BYDAY"]?.split(",")?.toSet() ?: emptySet()
            
            val (endType, untilDate, count) = when {
                parts.containsKey("UNTIL") -> {
                    val sdf = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val date = try { sdf.parse(parts["UNTIL"]!!)?.time } catch (e: Exception) { null }
                    Triple(RepeatEndType.UNTIL_DATE, date, null)
                }
                parts.containsKey("COUNT") -> {
                    Triple(RepeatEndType.REPEAT_COUNT, null, parts["COUNT"]?.toIntOrNull())
                }
                else -> Triple(RepeatEndType.ENDLESSLY, null, null)
            }
            
            return RecurrenceRule(frequency, interval, daysOfWeek, endType, untilDate, count)
        }
    }
}
