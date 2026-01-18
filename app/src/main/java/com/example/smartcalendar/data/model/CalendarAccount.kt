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
    val isPrimary: Boolean = false,
    val ownerAccount: String = "",
    val accessLevel: Int = 0
) {
    /**
     * Check if this calendar can receive new events
     */
    fun isWritable(): Boolean {
        // ACCESS_OWNER (700), ACCESS_CONTRIBUTOR (500) can create events
        return accessLevel >= 500
    }
    
    /**
     * Check if this is a SmartCalendar-owned local calendar
     */
    fun isLocalCalendar(): Boolean {
        return accountName == SMARTCALENDAR_ACCOUNT
    }
    
    companion object {
        const val SMARTCALENDAR_ACCOUNT = "SmartCalendar"
    }
}
