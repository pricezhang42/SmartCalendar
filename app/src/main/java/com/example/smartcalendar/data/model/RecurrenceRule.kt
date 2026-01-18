package com.example.smartcalendar.data.model

/**
 * Represents a recurrence rule for repeating events.
 * Based on RFC 5545 RRULE specification.
 */
data class RecurrenceRule(
    val frequency: String = Event.FREQ_WEEKLY,
    val interval: Int = 1,
    val daysOfWeek: Set<String> = emptySet(),
    val endType: RepeatEndType = RepeatEndType.REPEAT_COUNT,
    val count: Int? = null,
    val until: Long? = null
) {
    /**
     * Convert to RFC 5545 RRULE string format
     */
    fun toRRule(): String {
        val parts = mutableListOf<String>()
        parts.add("FREQ=$frequency")
        
        if (interval > 1) {
            parts.add("INTERVAL=$interval")
        }
        
        if (daysOfWeek.isNotEmpty() && frequency == Event.FREQ_WEEKLY) {
            parts.add("BYDAY=${daysOfWeek.joinToString(",")}")
        }
        
        when (endType) {
            RepeatEndType.REPEAT_COUNT -> {
                count?.let { parts.add("COUNT=$it") }
            }
            RepeatEndType.UNTIL_DATE -> {
                until?.let {
                    val dateFormat = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    parts.add("UNTIL=${dateFormat.format(java.util.Date(it))}")
                }
            }
            RepeatEndType.ENDLESSLY -> {
                // No end clause needed
            }
        }
        
        parts.add("WKST=MO")
        
        return parts.joinToString(";")
    }

    companion object {
        /**
         * Parse an RRULE string into a RecurrenceRule object
         */
        fun fromRRule(rrule: String): RecurrenceRule? {
            if (rrule.isBlank()) return null
            
            val parts = rrule.split(";").associate { part ->
                val (key, value) = part.split("=", limit = 2).let {
                    if (it.size == 2) it[0] to it[1] else it[0] to ""
                }
                key to value
            }
            
            val frequency = parts["FREQ"] ?: return null
            val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
            val daysOfWeek = parts["BYDAY"]?.split(",")?.toSet() ?: emptySet()
            val count = parts["COUNT"]?.toIntOrNull()
            
            val endType = when {
                count != null -> RepeatEndType.REPEAT_COUNT
                parts.containsKey("UNTIL") -> RepeatEndType.UNTIL_DATE
                else -> RepeatEndType.ENDLESSLY
            }
            
            return RecurrenceRule(
                frequency = frequency,
                interval = interval,
                daysOfWeek = daysOfWeek,
                endType = endType,
                count = count
            )
        }
    }
}
