package com.example.smartcalendar.data.model

/**
 * Represents the end type for recurring events
 */
enum class RepeatEndType {
    ENDLESSLY,      // No end date
    UNTIL_DATE,     // Ends on a specific date
    REPEAT_COUNT    // Ends after a specific number of occurrences
}
