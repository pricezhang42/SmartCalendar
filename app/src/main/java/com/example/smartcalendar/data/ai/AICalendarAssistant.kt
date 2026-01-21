package com.example.smartcalendar.data.ai

import android.content.Context
import android.util.Log
import com.example.smartcalendar.data.local.AppDatabase
import com.example.smartcalendar.data.model.InputType
import com.example.smartcalendar.data.model.PendingEvent
import com.example.smartcalendar.data.model.PendingOperation
import com.example.smartcalendar.data.model.PendingRecurrenceScope
import com.example.smartcalendar.data.model.PendingStatus
import com.example.smartcalendar.data.model.ICalEvent
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
        val calendarRepository = LocalCalendarRepository.getInstance(context)
        calendarRepository.setUserId(userId)

        val calendarContext = if (shouldIncludeCalendarContext(text)) {
            buildCalendarContext(calendarRepository)
        } else {
            null
        }

        when (val result = aiService.parseText(text, currentDate, timezone, calendarContext)) {
            is ProcessingResult.Success -> {
                if (result.response.events.isEmpty()) {
                    return@withContext Result.failure(Exception("No events found in the input"))
                }

                val sessionId = UUID.randomUUID().toString()
                val pendingEvents = result.response.events.mapNotNull { extracted ->
                    val action = extracted.action
                    if (action == AIAction.UPDATE || action == AIAction.DELETE) {
                        val targetId = extracted.targetEventId
                            ?: return@mapNotNull null
                        val existing = calendarRepository.getEvent(targetId)
                            ?: return@mapNotNull null
                        val scope = determineRecurrenceScope(extracted, existing)
                        val normalized = normalizeExtractedForScope(extracted, scope)
                        val merged = mergeWithExisting(normalized, existing)
                        val operation = if (action == AIAction.DELETE) {
                            PendingOperation.DELETE
                        } else {
                            PendingOperation.UPDATE
                        }
                        val instanceStartTime = resolveInstanceStartTime(
                            normalized,
                            existing,
                            scope
                        )
                        convertToPendingEvent(
                            merged,
                            sessionId,
                            userId,
                            text,
                            InputType.TEXT,
                            operation,
                            existing.uid,
                            existing.calendarId,
                            scope,
                            instanceStartTime
                        )
                    } else {
                        convertToPendingEvent(
                            extracted,
                            sessionId,
                            userId,
                            text,
                            InputType.TEXT,
                            PendingOperation.CREATE,
                            null,
                            null,
                            null,
                            null
                        )
                    }
                }

                if (pendingEvents.isEmpty()) {
                    return@withContext Result.failure(Exception("No matching events found to update or delete"))
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
            when (event.operationType) {
                PendingOperation.CREATE -> {
                    val iCalEvent = event.toICalEvent(calendarId, color)
                    calendarRepository.addEvent(iCalEvent)
                    pendingEventDao.updateStatus(event.id, PendingStatus.APPROVED)
                    Log.d(TAG, "Approved new event: ${event.title} -> ${iCalEvent.uid}")
                    Result.success(iCalEvent.uid)
                }
                PendingOperation.UPDATE -> {
                    val targetId = event.targetEventId
                        ?: return@withContext Result.failure(Exception("Missing target event id"))
                    val existing = calendarRepository.getEvent(targetId)
                        ?: return@withContext Result.failure(Exception("Target event not found"))
                    val resultUid = applyRecurringUpdate(calendarRepository, existing, event, calendarId, color)
                    pendingEventDao.updateStatus(event.id, PendingStatus.APPROVED)
                    Result.success(resultUid)
                }
                PendingOperation.DELETE -> {
                    val targetId = event.targetEventId
                        ?: return@withContext Result.failure(Exception("Missing target event id"))
                    val existing = calendarRepository.getEvent(targetId)
                        ?: return@withContext Result.failure(Exception("Target event not found"))
                    applyRecurringDelete(calendarRepository, existing, event)
                    pendingEventDao.updateStatus(event.id, PendingStatus.APPROVED)
                    Result.success(targetId)
                }
            }
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
                when (event.operationType) {
                    PendingOperation.CREATE -> {
                        val iCalEvent = event.toICalEvent(calendarId, color)
                        calendarRepository.addEvent(iCalEvent)
                    }
                    PendingOperation.UPDATE -> {
                        val targetId = event.targetEventId ?: return@forEach
                        val existing = calendarRepository.getEvent(targetId) ?: return@forEach
                        applyRecurringUpdate(calendarRepository, existing, event, calendarId, color)
                    }
                    PendingOperation.DELETE -> {
                        val targetId = event.targetEventId ?: return@forEach
                        val existing = calendarRepository.getEvent(targetId) ?: return@forEach
                        applyRecurringDelete(calendarRepository, existing, event)
                    }
                }
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
        inputType: InputType,
        operationType: PendingOperation,
        targetEventId: String?,
        suggestedCalendarId: String?,
        recurrenceScope: PendingRecurrenceScope?,
        instanceStartTime: Long?
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
            isAllDay = extracted.isAllDay ?: false,
            recurrenceRule = parseRecurrenceToRRule(extracted.recurrence),
            confidence = extracted.confidence,
            sourceType = inputType,
            rawInput = rawInput,
            operationType = operationType,
            targetEventId = targetEventId,
            suggestedCalendarId = suggestedCalendarId,
            recurrenceScope = recurrenceScope,
            instanceStartTime = instanceStartTime
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

            val isAllDay = extracted.isAllDay == true
            val startTime = if (!extracted.startTime.isNullOrBlank()) {
                val timeParts = extracted.startTime.split(":")
                calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                calendar.set(Calendar.MINUTE, timeParts.getOrNull(1)?.toInt() ?: 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            } else if (isAllDay) {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            } else {
                null
            }

            val endTime = when {
                !extracted.endTime.isNullOrBlank() && startTime != null -> {
                    val timeParts = extracted.endTime.split(":")
                    calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                    calendar.set(Calendar.MINUTE, timeParts.getOrNull(1)?.toInt() ?: 0)
                    calendar.timeInMillis
                }
                isAllDay && startTime != null -> {
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

    private fun shouldIncludeCalendarContext(text: String): Boolean {
        val lower = text.lowercase()
        val keywords = listOf(
            "postpone",
            "delay",
            "move",
            "reschedule",
            "shift",
            "update",
            "change",
            "edit",
            "cancel",
            "delete",
            "remove"
        )
        return keywords.any { lower.contains(it) }
    }

    private suspend fun buildCalendarContext(
        calendarRepository: LocalCalendarRepository
    ): List<CalendarContextEvent> {
        val calendars = calendarRepository.getCalendars().associateBy { it.id }
        val events = calendarRepository.getAllEvents()
        val now = System.currentTimeMillis()
        val rangeStart = now - 7 * 24 * 60 * 60 * 1000L
        val rangeEnd = now + 30 * 24 * 60 * 60 * 1000L

        return events
            .filter { it.dtStart in rangeStart..rangeEnd }
            .map { event ->
                CalendarContextEvent(
                    id = event.uid,
                    title = event.summary,
                    date = dateFormat.format(Date(event.dtStart)),
                    startTime = if (event.allDay) null else timeFormat.format(Date(event.dtStart)),
                    endTime = if (event.allDay) null else timeFormat.format(Date(event.dtEnd)),
                    isAllDay = event.allDay,
                    recurrence = event.rrule,
                    exdate = event.exdate,
                    calendarName = calendars[event.calendarId]?.name
                )
            }
    }

    private fun mergeWithExisting(extracted: ExtractedEvent, existing: ICalEvent): ExtractedEvent {
        val existingDate = dateFormat.format(Date(existing.dtStart))
        val existingStart = if (existing.allDay) null else timeFormat.format(Date(existing.dtStart))
        val existingEnd = if (existing.allDay) null else timeFormat.format(Date(existing.dtEnd))
        val isAllDay = extracted.isAllDay ?: existing.allDay

        return extracted.copy(
            title = if (extracted.title.isBlank()) existing.summary else extracted.title,
            description = extracted.description ?: existing.description.ifBlank { null },
            location = extracted.location ?: existing.location.ifBlank { null },
            date = extracted.date ?: existingDate,
            startTime = extracted.startTime ?: if (isAllDay) null else existingStart,
            endTime = extracted.endTime ?: if (isAllDay) null else existingEnd,
            isAllDay = isAllDay,
            recurrence = extracted.recurrence ?: existing.rrule,
            targetEventId = existing.uid
        )
    }

    private fun determineRecurrenceScope(
        extracted: ExtractedEvent,
        existing: ICalEvent
    ): PendingRecurrenceScope? {
        if (!existing.isRecurring) return null
        extracted.scope?.let { scope ->
            return when (scope) {
                AIRecurrenceScope.THIS_INSTANCE -> PendingRecurrenceScope.THIS_INSTANCE
                AIRecurrenceScope.THIS_AND_FOLLOWING -> PendingRecurrenceScope.THIS_AND_FOLLOWING
                AIRecurrenceScope.ALL -> PendingRecurrenceScope.ALL
            }
        }
        if (!extracted.recurrence.isNullOrBlank()) {
            return PendingRecurrenceScope.ALL
        }
        if (!extracted.instanceDate.isNullOrBlank() ||
            !extracted.date.isNullOrBlank() ||
            !extracted.startTime.isNullOrBlank() ||
            !extracted.endTime.isNullOrBlank()
        ) {
            return PendingRecurrenceScope.THIS_INSTANCE
        }
        return PendingRecurrenceScope.ALL
    }

    private fun normalizeExtractedForScope(
        extracted: ExtractedEvent,
        scope: PendingRecurrenceScope?
    ): ExtractedEvent {
        if (scope == null) return extracted
        return when (scope) {
            PendingRecurrenceScope.THIS_INSTANCE -> extracted.copy(recurrence = null)
            PendingRecurrenceScope.THIS_AND_FOLLOWING -> extracted
            PendingRecurrenceScope.ALL -> extracted
        }
    }

    private fun resolveInstanceStartTime(
        extracted: ExtractedEvent,
        existing: ICalEvent,
        scope: PendingRecurrenceScope?
    ): Long? {
        if (scope == null || scope == PendingRecurrenceScope.ALL) return null
        val instanceDate = extracted.instanceDate ?: extracted.date
        if (instanceDate.isNullOrBlank()) {
            return existing.dtStart
        }
        val dateWithInstance = extracted.copy(date = instanceDate)
        val (startTime, _) = parseEventTimes(dateWithInstance)
        return startTime ?: existing.dtStart
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

    private fun mergePendingIntoExisting(
        existing: ICalEvent,
        pending: PendingEvent,
        fallbackCalendarId: String,
        fallbackColor: Int
    ): ICalEvent {
        val calendarId = pending.suggestedCalendarId
            ?: existing.calendarId.ifBlank { fallbackCalendarId }
        val color = pending.suggestedColor
            ?: existing.color.takeIf { it != 0 } ?: fallbackColor
        val start = pending.startTime ?: existing.dtStart
        val end = pending.endTime ?: existing.dtEnd

        return existing.copy(
            calendarId = calendarId,
            summary = pending.title,
            description = pending.description ?: "",
            location = pending.location ?: "",
            dtStart = start,
            dtEnd = end,
            allDay = pending.isAllDay,
            rrule = pending.recurrenceRule,
            color = color,
            lastModified = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = com.example.smartcalendar.data.model.SyncStatus.PENDING
        )
    }

    private suspend fun applyRecurringUpdate(
        calendarRepository: LocalCalendarRepository,
        existing: ICalEvent,
        pending: PendingEvent,
        calendarId: String,
        color: Int
    ): String {
        if (!existing.isRecurring || pending.recurrenceScope == null || pending.recurrenceScope == PendingRecurrenceScope.ALL) {
            val updated = mergePendingIntoExisting(existing, pending, calendarId, color)
            calendarRepository.updateEvent(updated)
            Log.d(TAG, "Updated event: ${updated.summary} -> ${updated.uid}")
            return updated.uid
        }

        val instanceTime = pending.instanceStartTime ?: existing.dtStart
        return when (pending.recurrenceScope) {
            PendingRecurrenceScope.THIS_INSTANCE -> {
                calendarRepository.addExceptionDate(existing.uid, instanceTime)
                val single = pending.toICalEvent(calendarId, color).copy(rrule = null, duration = null)
                calendarRepository.addEvent(single)
                Log.d(TAG, "Updated single instance: ${single.summary} -> ${single.uid}")
                single.uid
            }
            PendingRecurrenceScope.THIS_AND_FOLLOWING -> {
                calendarRepository.endRecurrenceAtInstance(existing.uid, instanceTime)
                val duration = ICalEvent.toDurationString(
                    (pending.endTime ?: (pending.startTime ?: instanceTime + 3600000L)) -
                        (pending.startTime ?: instanceTime)
                )
                val newSeries = pending.toICalEvent(calendarId, color).copy(
                    rrule = pending.recurrenceRule ?: existing.rrule,
                    duration = duration
                )
                calendarRepository.addEvent(newSeries)
                Log.d(TAG, "Updated series from instance: ${newSeries.summary} -> ${newSeries.uid}")
                newSeries.uid
            }
            PendingRecurrenceScope.ALL -> {
                val updated = mergePendingIntoExisting(existing, pending, calendarId, color)
                calendarRepository.updateEvent(updated)
                Log.d(TAG, "Updated event: ${updated.summary} -> ${updated.uid}")
                updated.uid
            }
        }
    }

    private suspend fun applyRecurringDelete(
        calendarRepository: LocalCalendarRepository,
        existing: ICalEvent,
        pending: PendingEvent
    ) {
        if (!existing.isRecurring || pending.recurrenceScope == null || pending.recurrenceScope == PendingRecurrenceScope.ALL) {
            calendarRepository.deleteEvent(existing.uid)
            Log.d(TAG, "Deleted event: ${existing.uid}")
            return
        }

        val instanceTime = pending.instanceStartTime ?: existing.dtStart
        when (pending.recurrenceScope) {
            PendingRecurrenceScope.THIS_INSTANCE -> {
                calendarRepository.addExceptionDate(existing.uid, instanceTime)
                Log.d(TAG, "Deleted single instance for event: ${existing.uid}")
            }
            PendingRecurrenceScope.THIS_AND_FOLLOWING -> {
                calendarRepository.endRecurrenceAtInstance(existing.uid, instanceTime)
                Log.d(TAG, "Deleted instances from date for event: ${existing.uid}")
            }
            PendingRecurrenceScope.ALL -> {
                calendarRepository.deleteEvent(existing.uid)
                Log.d(TAG, "Deleted event: ${existing.uid}")
            }
        }
    }
}
