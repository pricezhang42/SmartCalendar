package com.example.smartcalendar.data.model

/**
 * Data class representing a calendar account from Calendar Provider.
 */
data class CalendarAccount(
    val id: Long,
    val accountName: String,
    val displayName: String,
    val color: Int,
    val isVisible: Boolean = true,
    val isPrimary: Boolean = false
)
