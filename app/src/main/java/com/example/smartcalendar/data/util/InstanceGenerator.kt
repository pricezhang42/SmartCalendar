package com.example.smartcalendar.data.util

import com.example.smartcalendar.data.model.EventInstance
import com.example.smartcalendar.data.model.ICalEvent
import java.util.*

/**
 * Generates event instances from ICalEvent by expanding RRULE.
 */
object InstanceGenerator {
    
    /**
     * Generate instances for an event within a time range
     */
    fun generateInstances(
        event: ICalEvent,
        rangeStart: Long,
        rangeEnd: Long,
        maxInstances: Int = 500
    ): List<EventInstance> {
        val instances = mutableListOf<EventInstance>()
        val duration = event.getDurationMs()
        val exdates = parseExdates(event.exdate)
        
        if (!event.isRecurring) {
            // Non-recurring: single instance
            if (event.dtStart < rangeEnd && event.dtEnd > rangeStart) {
                instances.add(createInstance(event, event.dtStart, event.dtEnd))
            }
            return instances
        }
        
        // Parse RRULE
        val rrule = parseRRule(event.rrule ?: return instances)
        val startCal = Calendar.getInstance().apply { timeInMillis = event.dtStart }
        
        var count = 0
        val maxCount = rrule.count ?: maxInstances
        val until = rrule.until ?: rangeEnd
        
        while (startCal.timeInMillis <= until && startCal.timeInMillis < rangeEnd && count < maxCount) {
            val instanceStart = startCal.timeInMillis
            val instanceEnd = instanceStart + duration
            
            // Check if not in exdates and within range
            if (!isExcluded(instanceStart, exdates) && instanceStart >= rangeStart - duration) {
                if (instanceStart < rangeEnd && instanceEnd > rangeStart) {
                    instances.add(createInstance(event, instanceStart, instanceEnd))
                }
            }
            
            // Advance based on frequency
            advanceCalendar(startCal, rrule)
            count++
        }
        
        return instances
    }
    
    private fun createInstance(event: ICalEvent, start: Long, end: Long): EventInstance {
        return EventInstance(
            eventUid = event.uid,
            startTime = start,
            endTime = end,
            title = event.summary,
            description = event.description,
            location = event.location,
            color = event.color,
            allDay = event.allDay,
            isRecurring = event.isRecurring
        )
    }
    
    private fun advanceCalendar(cal: Calendar, rrule: RRuleData) {
        val interval = rrule.interval
        when (rrule.freq) {
            "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, interval)
            "WEEKLY" -> {
                if (rrule.byDay.isNotEmpty()) {
                    advanceToNextByDay(cal, rrule.byDay, interval)
                } else {
                    cal.add(Calendar.WEEK_OF_YEAR, interval)
                }
            }
            "MONTHLY" -> cal.add(Calendar.MONTH, interval)
            "YEARLY" -> cal.add(Calendar.YEAR, interval)
            else -> cal.add(Calendar.DAY_OF_YEAR, interval)
        }
    }
    
    private fun advanceToNextByDay(cal: Calendar, byDay: List<String>, interval: Int) {
        val dayMap = mapOf(
            "SU" to Calendar.SUNDAY, "MO" to Calendar.MONDAY, "TU" to Calendar.TUESDAY,
            "WE" to Calendar.WEDNESDAY, "TH" to Calendar.THURSDAY, "FR" to Calendar.FRIDAY,
            "SA" to Calendar.SATURDAY
        )
        val targetDays = byDay.mapNotNull { dayMap[it] }.sorted()
        if (targetDays.isEmpty()) {
            cal.add(Calendar.WEEK_OF_YEAR, interval)
            return
        }
        
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val nextDay = targetDays.find { it > currentDay } ?: targetDays.first()
        
        if (nextDay > currentDay) {
            cal.add(Calendar.DAY_OF_YEAR, nextDay - currentDay)
        } else {
            // Move to next week
            cal.add(Calendar.WEEK_OF_YEAR, interval)
            cal.set(Calendar.DAY_OF_WEEK, nextDay)
        }
    }
    
    private data class ExdateSets(
        val localDays: Set<Long>,
        val utcDays: Set<Long>
    )

    private fun parseExdates(exdate: String?): ExdateSets {
        if (exdate.isNullOrEmpty()) return ExdateSets(emptySet(), emptySet())
        val localDays = mutableSetOf<Long>()
        val utcDays = mutableSetOf<Long>()
        exdate.split(",").forEach { raw ->
            val dateStr = raw.trim()
            val isUtc = dateStr.endsWith("Z")
            parseDateTime(dateStr)?.let { time ->
                if (isUtc) {
                    utcDays.add(normalizeToDayUtc(time))
                } else {
                    localDays.add(normalizeToDay(time))
                }
            }
        }
        return ExdateSets(localDays, utcDays)
    }
    
    private fun isExcluded(time: Long, exdates: ExdateSets): Boolean {
        return exdates.localDays.contains(normalizeToDay(time)) ||
            exdates.utcDays.contains(normalizeToDayUtc(time))
    }
    
    private fun normalizeToDay(time: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = time }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun normalizeToDayUtc(time: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = time }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    private fun parseDateTime(str: String): Long? {
        return try {
            val format = if (str.contains("T")) {
                java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            } else {
                java.text.SimpleDateFormat("yyyyMMdd", Locale.US)
            }
            format.parse(str)?.time
        } catch (e: Exception) { null }
    }
    
    data class RRuleData(
        val freq: String = "DAILY",
        val interval: Int = 1,
        val count: Int? = null,
        val until: Long? = null,
        val byDay: List<String> = emptyList()
    )
    
    private fun parseRRule(rrule: String): RRuleData {
        var freq = "DAILY"
        var interval = 1
        var count: Int? = null
        var until: Long? = null
        var byDay = listOf<String>()
        
        rrule.split(";").forEach { part ->
            val (key, value) = part.split("=").let { 
                if (it.size == 2) it[0] to it[1] else return@forEach 
            }
            when (key) {
                "FREQ" -> freq = value
                "INTERVAL" -> interval = value.toIntOrNull() ?: 1
                "COUNT" -> count = value.toIntOrNull()
                "UNTIL" -> until = parseDateTime(value)
                "BYDAY" -> byDay = value.split(",")
            }
        }
        
        return RRuleData(freq, interval, count, until, byDay)
    }
}
