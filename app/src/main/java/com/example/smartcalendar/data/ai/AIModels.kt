package com.example.smartcalendar.data.ai

import kotlinx.serialization.Serializable

/**
 * Response from AI service after processing input.
 */
data class AIResponse(
    val events: List<ExtractedEvent>,
    val confidence: Float,
    val warnings: List<String> = emptyList(),
    val rawResponse: String = ""
)

/**
 * Event extracted by AI from user input.
 */
@Serializable
data class ExtractedEvent(
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val date: String? = null,           // YYYY-MM-DD format
    val startTime: String? = null,       // HH:MM format (24h)
    val endTime: String? = null,         // HH:MM format (24h)
    val isAllDay: Boolean? = null,
    val recurrence: String? = null,      // Natural language recurrence
    val recurrenceRule: String? = null,  // RRULE format if available
    val exceptionDates: List<String>? = null, // YYYY-MM-DD dates excluded
    val confidence: Float = 0.0f,
    val action: AIAction = AIAction.CREATE,
    val targetEventId: String? = null,
    val scope: AIRecurrenceScope? = null,
    val instanceDate: String? = null
)

@Serializable
enum class AIAction {
    CREATE,
    UPDATE,
    DELETE
}

@Serializable
enum class AIRecurrenceScope {
    THIS_INSTANCE,
    THIS_AND_FOLLOWING,
    ALL
}

@Serializable
data class CalendarContextEvent(
    val id: String,
    val title: String,
    val date: String,
    val startTime: String?,
    val endTime: String?,
    val isAllDay: Boolean,
    val recurrence: String? = null,
    val exdate: String? = null,
    val calendarName: String? = null
)

/**
 * Wrapper for Gemini JSON response.
 */
@Serializable
data class GeminiEventResponse(
    val events: List<ExtractedEvent> = emptyList()
)

/**
 * Processing result with success/error state.
 */
sealed class ProcessingResult {
    data class Success(val response: AIResponse) : ProcessingResult()
    data class Error(val message: String, val exception: Exception? = null) : ProcessingResult()
}
