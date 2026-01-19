package com.example.smartcalendar.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Local calendar model for app-managed calendars.
 * Room entity with sync support.
 */
@Entity(tableName = "calendars")
@Serializable
data class LocalCalendar(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val name: String,
    val color: Int,
    val isDefault: Boolean = false,
    val isVisible: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING
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
        
        fun createDefault(userId: String): List<LocalCalendar> = listOf(
            LocalCalendar(
                id = "personal_$userId",
                userId = userId,
                name = "Personal",
                color = COLOR_BLUE,
                isDefault = true
            ),
            LocalCalendar(
                id = "work_$userId",
                userId = userId,
                name = "Work",
                color = COLOR_GREEN,
                isDefault = true
            )
        )
    }
}
