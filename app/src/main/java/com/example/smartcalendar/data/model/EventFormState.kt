package com.example.smartcalendar.data.model

/**
 * Represents the form state for event editing.
 * Used to track original vs current values for change detection.
 */
data class EventFormState(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis() + 3600000,
    val isAllDay: Boolean = false,
    val reminderMinutes: Int? = null,
    val repeatEnabled: Boolean = false,
    val frequency: String = Event.FREQ_WEEKLY,
    val interval: Int = 1,
    val daysOfWeek: Set<String> = emptySet(),
    val repeatEndType: RepeatEndType = RepeatEndType.REPEAT_COUNT,
    val occurrences: Int = 10,
    val timeZone: String = java.util.TimeZone.getDefault().id
) {
    /**
     * Check if recurrence settings have changed compared to another form state
     */
    fun hasRecurrenceChanged(other: EventFormState): Boolean {
        return repeatEnabled != other.repeatEnabled ||
               frequency != other.frequency ||
               interval != other.interval ||
               daysOfWeek != other.daysOfWeek ||
               repeatEndType != other.repeatEndType ||
               (repeatEndType == RepeatEndType.REPEAT_COUNT && occurrences != other.occurrences)
    }

    /**
     * Convert form state to an Event object
     */
    fun toEvent(eventId: Long = 0, calendarId: Long = 0, originalId: Long? = null, originalInstanceTime: Long? = null): Event {
        val rrule = if (repeatEnabled) {
            RecurrenceRule(
                frequency = frequency,
                interval = interval,
                daysOfWeek = if (frequency == Event.FREQ_WEEKLY) daysOfWeek else emptySet(),
                endType = repeatEndType,
                count = if (repeatEndType == RepeatEndType.REPEAT_COUNT) occurrences else null
            ).toRRule()
        } else null

        return Event(
            id = eventId,
            calendarId = calendarId,
            title = title,
            description = description,
            location = location,
            startTime = startTime,
            endTime = endTime,
            isAllDay = isAllDay,
            reminderMinutes = reminderMinutes,
            rrule = rrule,
            originalId = originalId,
            originalInstanceTime = originalInstanceTime,
            timeZone = timeZone
        )
    }

    companion object {
        /**
         * Create form state from an existing Event
         */
        fun fromEvent(event: Event): EventFormState {
            var frequency = Event.FREQ_WEEKLY
            var interval = 1
            var daysOfWeek = emptySet<String>()
            var repeatEnabled = false
            var repeatEndType = RepeatEndType.REPEAT_COUNT
            var occurrences = 10

            event.rrule?.let { rrule ->
                RecurrenceRule.fromRRule(rrule)?.let {
                    repeatEnabled = true
                    frequency = it.frequency
                    interval = it.interval
                    daysOfWeek = it.daysOfWeek
                    repeatEndType = it.endType
                    it.count?.let { count -> occurrences = count }
                }
            }

            return EventFormState(
                title = event.title,
                description = event.description,
                location = event.location,
                startTime = event.startTime,
                endTime = event.endTime,
                isAllDay = event.isAllDay,
                reminderMinutes = event.reminderMinutes,
                repeatEnabled = repeatEnabled,
                frequency = frequency,
                interval = interval,
                daysOfWeek = daysOfWeek,
                repeatEndType = repeatEndType,
                occurrences = occurrences,
                timeZone = event.timeZone
            )
        }
    }
}
