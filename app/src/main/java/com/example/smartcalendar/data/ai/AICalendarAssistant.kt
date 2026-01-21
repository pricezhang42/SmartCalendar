package com.example.smartcalendar.data.ai

import android.content.Context
import android.util.Log
import com.example.smartcalendar.data.local.AppDatabase
import com.example.smartcalendar.data.model.InputType
import com.example.smartcalendar.data.model.PendingEvent
import com.example.smartcalendar.data.model.PendingStatus
import com.example.smartcalendar.data.repository.LocalCalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Orchestrates AI processing for calendar event extraction.
 * Manages the flow from user input to pending events ready for review.
 */
class AICalendarAssistant private constructor(
    private val context: Context,
    private val aiService: AIService = GeminiProvider()
) {
    companion object {
        private const val TAG = "AICalendarAssistant"

        @Volatile
        private var INSTANCE: AICalendarAssistant? = null

        fun getInstance(context: Context): AICalendarAssistant {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AICalendarAssistant(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val database = AppDatabase.getInstance(context)
    private val pendingEventDao = database.pendingEventDao()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

    /**
     * Process text input and create pending events for review.
     * @param text User's natural language input
     * @param userId Current user ID
     * @return Session ID for the created pending events, or error message
     */
    suspend fun processTextInput(text: String, userId: String): Result<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Processing text input: $text")

        val currentDate = dateFormat.format(Date())
        val timezone = TimeZone.getDefault().id

        when (val result = aiService.parseText(text, currentDate, timezone)) {
            is ProcessingResult.Success -> {
                if (result.response.events.isEmpty()) {
                    return@withContext Result.failure(Exception("No events found in the input"))
                }

                val sessionId = UUID.randomUUID().toString()
                val pendingEvents = result.response.events.map { extracted ->
                    convertToPendingEvent(extracted, sessionId, userId, text, InputType.TEXT)
                }

                pendingEventDao.insertAll(pendingEvents)
                Log.d(TAG, "Created ${pendingEvents.size} pending events with session: $sessionId")

                Result.success(sessionId)
            }
            is ProcessingResult.Error -> {
                Log.e(TAG, "AI processing error: ${result.message}")
                Result.failure(Exception(result.message))
            }
        }
    }

    /**
     * Get pending events for a session.
     */
    fun getPendingEvents(sessionId: String): Flow<List<PendingEvent>> {
        return pendingEventDao.getBySession(sessionId)
    }

    /**
     * Get all pending events for a user.
     */
    fun getAllPendingEvents(userId: String): Flow<List<PendingEvent>> {
        return pendingEventDao.getPendingByUser(userId)
    }

    /**
     * Update a pending event (user made modifications).
     */
    suspend fun updatePendingEvent(event: PendingEvent) = withContext(Dispatchers.IO) {
        pendingEventDao.update(event.copy(status = PendingStatus.MODIFIED))
    }

    /**
     * Approve a single pending event and add to calendar.
     * @return The created ICalEvent UID
     */
    suspend fun approveEvent(
        event: PendingEvent,
        calendarId: String,
        color: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val calendarRepository = LocalCalendarRepository.getInstance(context)
            calendarRepository.setUserId(event.userId)
            val iCalEvent = event.toICalEvent(calendarId, color)

            calendarRepository.addEvent(iCalEvent)
            pendingEventDao.updateStatus(event.id, PendingStatus.APPROVED)

            Log.d(TAG, "Approved event: ${event.title} -> ${iCalEvent.uid}")
            Result.success(iCalEvent.uid)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to approve event", e)
            Result.failure(e)
        }
    }

    /**
     * Approve all pending events in a session.
     */
    suspend fun approveAllEvents(
        sessionId: String,
        calendarId: String,
        color: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val events = pendingEventDao.getBySessionList(sessionId)
                .filter { it.status == PendingStatus.PENDING || it.status == PendingStatus.MODIFIED }

            if (events.isEmpty()) {
                return@withContext Result.failure(Exception("No pending events to approve"))
            }

            val userId = events.first().userId
            val calendarRepository = LocalCalendarRepository.getInstance(context)
            calendarRepository.setUserId(userId)

            events.forEach { event ->
                val iCalEvent = event.toICalEvent(calendarId, color)
                calendarRepository.addEvent(iCalEvent)
            }

            pendingEventDao.updateSessionStatus(sessionId, PendingStatus.APPROVED)
            Log.d(TAG, "Approved ${events.size} events")

            Result.success(events.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to approve all events", e)
            Result.failure(e)
        }
    }

    /**
     * Reject a pending event.
     */
    suspend fun rejectEvent(eventId: String) = withContext(Dispatchers.IO) {
        pendingEventDao.updateStatus(eventId, PendingStatus.REJECTED)
    }

    /**
     * Reject all events in a session.
     */
    suspend fun rejectAllEvents(sessionId: String) = withContext(Dispatchers.IO) {
        pendingEventDao.updateSessionStatus(sessionId, PendingStatus.REJECTED)
    }

    /**
     * Delete a pending event.
     */
    suspend fun deletePendingEvent(eventId: String) = withContext(Dispatchers.IO) {
        pendingEventDao.deleteById(eventId)
    }

    /**
     * Clear all pending events for a session.
     */
    suspend fun clearSession(sessionId: String) = withContext(Dispatchers.IO) {
        pendingEventDao.deleteBySession(sessionId)
    }

    /**
     * Convert AI-extracted event to pending event model.
     */
    private fun convertToPendingEvent(
        extracted: ExtractedEvent,
        sessionId: String,
        userId: String,
        rawInput: String,
        inputType: InputType
    ): PendingEvent {
        val (startTime, endTime) = parseEventTimes(extracted)

        return PendingEvent(
            sessionId = sessionId,
            userId = userId,
            title = extracted.title,
            description = extracted.description,
            location = extracted.location,
            startTime = startTime,
            endTime = endTime,
            isAllDay = extracted.isAllDay,
            recurrenceRule = parseRecurrenceToRRule(extracted.recurrence),
            confidence = extracted.confidence,
            sourceType = inputType,
            rawInput = rawInput
        )
    }

    /**
     * Parse date and time strings to timestamps.
     */
    private fun parseEventTimes(extracted: ExtractedEvent): Pair<Long?, Long?> {
        if (extracted.date == null) {
            return Pair(null, null)
        }

        return try {
            val baseDate = dateFormat.parse(extracted.date) ?: return Pair(null, null)
            val calendar = Calendar.getInstance().apply {
                time = baseDate
            }

            val startTime = if (extracted.startTime != null) {
                val timeParts = extracted.startTime.split(":")
                calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                calendar.set(Calendar.MINUTE, timeParts.getOrNull(1)?.toInt() ?: 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            } else if (extracted.isAllDay) {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            } else {
                null
            }

            val endTime = when {
                extracted.endTime != null && startTime != null -> {
                    val timeParts = extracted.endTime.split(":")
                    calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                    calendar.set(Calendar.MINUTE, timeParts.getOrNull(1)?.toInt() ?: 0)
                    calendar.timeInMillis
                }
                extracted.isAllDay && startTime != null -> {
                    // All day events end at 23:59:59
                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    calendar.timeInMillis
                }
                startTime != null -> {
                    // Default 1 hour duration
                    startTime + 3600000L
                }
                else -> null
            }

            Pair(startTime, endTime)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse event times", e)
            Pair(null, null)
        }
    }

    /**
     * Convert natural language recurrence to RRULE format.
     */
    private fun parseRecurrenceToRRule(recurrence: String?): String? {
        if (recurrence.isNullOrBlank()) return null

        val lower = recurrence.lowercase()
        return when {
            lower.contains("daily") || lower.contains("every day") -> "FREQ=DAILY"
            lower.contains("weekly") || lower.contains("every week") -> {
                val days = mutableListOf<String>()
                if (lower.contains("monday") || lower.contains("mon")) days.add("MO")
                if (lower.contains("tuesday") || lower.contains("tue")) days.add("TU")
                if (lower.contains("wednesday") || lower.contains("wed")) days.add("WE")
                if (lower.contains("thursday") || lower.contains("thu")) days.add("TH")
                if (lower.contains("friday") || lower.contains("fri")) days.add("FR")
                if (lower.contains("saturday") || lower.contains("sat")) days.add("SA")
                if (lower.contains("sunday") || lower.contains("sun")) days.add("SU")

                if (days.isNotEmpty()) {
                    "FREQ=WEEKLY;BYDAY=${days.joinToString(",")}"
                } else {
                    "FREQ=WEEKLY"
                }
            }
            lower.contains("monthly") || lower.contains("every month") -> "FREQ=MONTHLY"
            lower.contains("yearly") || lower.contains("every year") || lower.contains("annually") -> "FREQ=YEARLY"
            lower.contains("every monday") -> "FREQ=WEEKLY;BYDAY=MO"
            lower.contains("every tuesday") -> "FREQ=WEEKLY;BYDAY=TU"
            lower.contains("every wednesday") -> "FREQ=WEEKLY;BYDAY=WE"
            lower.contains("every thursday") -> "FREQ=WEEKLY;BYDAY=TH"
            lower.contains("every friday") -> "FREQ=WEEKLY;BYDAY=FR"
            lower.contains("every saturday") -> "FREQ=WEEKLY;BYDAY=SA"
            lower.contains("every sunday") -> "FREQ=WEEKLY;BYDAY=SU"
            lower.contains("weekday") || lower.contains("weekdays") -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
            lower.contains("weekend") -> "FREQ=WEEKLY;BYDAY=SA,SU"
            else -> null
        }
    }
}
