package com.example.smartcalendar.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Status of a pending AI-extracted event.
 */
enum class PendingStatus {
    PENDING,    // Awaiting user review
    APPROVED,   // User approved and added to calendar
    REJECTED,   // User rejected
    MODIFIED    // User modified before approval
}

/**
 * Input type for AI processing.
 */
enum class InputType {
    TEXT,
    IMAGE,
    VOICE,
    DOCUMENT
}

/**
 * Type of operation requested by AI.
 */
enum class PendingOperation {
    CREATE,
    UPDATE,
    DELETE
}

/**
 * Represents an event extracted by AI, pending user review.
 */
@Entity(tableName = "pending_events")
@Serializable
data class PendingEvent(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,                      // Groups events from same AI request
    val userId: String,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val isAllDay: Boolean = false,
    val recurrenceRule: String? = null,
    val confidence: Float = 0.0f,               // AI confidence score (0.0 - 1.0)
    val status: PendingStatus = PendingStatus.PENDING,
    val suggestedCalendarId: String? = null,
    val suggestedColor: Int? = null,
    val sourceType: InputType = InputType.TEXT,
    val rawInput: String? = null,               // Original input text
    val operationType: PendingOperation = PendingOperation.CREATE,
    val targetEventId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Convert to ICalEvent for saving to calendar.
     */
    fun toICalEvent(calendarId: String, color: Int): ICalEvent {
        val now = System.currentTimeMillis()
        val start = startTime ?: now
        val end = endTime ?: (start + 3600000) // Default 1 hour

        return ICalEvent(
            uid = UUID.randomUUID().toString(),
            userId = userId,
            calendarId = calendarId,
            summary = title,
            description = description ?: "",
            location = location ?: "",
            dtStart = start,
            dtEnd = end,
            allDay = isAllDay,
            rrule = recurrenceRule,
            color = color,
            syncStatus = SyncStatus.PENDING
        )
    }
}
