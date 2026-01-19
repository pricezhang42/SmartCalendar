package com.example.smartcalendar.data.model

import java.util.UUID

/**
 * Local calendar model for app-managed calendars.
 */
data class LocalCalendar(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Int,
    val isDefault: Boolean = false,
    val isVisible: Boolean = true
) {
    companion object {
        // Default calendar colors
        const val COLOR_BLUE = 0xFF4285F4.toInt()    // Personal - Google Blue
        const val COLOR_GREEN = 0xFF0F9D58.toInt()   // Work - Google Green
        const val COLOR_PURPLE = 0xFF9C27B0.toInt()
        const val COLOR_ORANGE = 0xFFFF9800.toInt()
        const val COLOR_RED = 0xFFF44336.toInt()
        const val COLOR_TEAL = 0xFF009688.toInt()
        
        val DEFAULT_COLORS = listOf(
            COLOR_BLUE, COLOR_GREEN, COLOR_PURPLE, 
            COLOR_ORANGE, COLOR_RED, COLOR_TEAL
        )
        
        fun createDefault(): List<LocalCalendar> = listOf(
            LocalCalendar(
                id = "personal",
                name = "Personal",
                color = COLOR_BLUE,
                isDefault = true
            ),
            LocalCalendar(
                id = "work",
                name = "Work",
                color = COLOR_GREEN,
                isDefault = true
            )
        )
    }
}
