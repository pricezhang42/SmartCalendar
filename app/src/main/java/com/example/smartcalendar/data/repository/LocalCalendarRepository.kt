package com.example.smartcalendar.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.smartcalendar.data.model.EventInstance
import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.util.InstanceGenerator
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Local calendar repository managing events with persistence.
 * Uses Map for O(1) event lookup by UID.
 */
class LocalCalendarRepository private constructor() {
    
    // Events stored by UID for efficient lookup
    private val events = mutableMapOf<String, ICalEvent>()
    
    // Cached instances for current view range
    private var cachedInstances = listOf<EventInstance>()
    private var cacheRangeStart = 0L
    private var cacheRangeEnd = 0L
    
    private var prefs: SharedPreferences? = null
    
    companion object {
        @Volatile
        private var instance: LocalCalendarRepository? = null
        private const val PREFS_NAME = "smartcalendar_events"
        private const val KEY_EVENTS = "events"
        
        fun getInstance(): LocalCalendarRepository {
            return instance ?: synchronized(this) {
                instance ?: LocalCalendarRepository().also { instance = it }
            }
        }
        
        /**
         * Initialize with context to enable persistence
         */
        fun init(context: Context) {
            getInstance().apply {
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                loadFromStorage()
            }
        }
    }
    
    /**
     * Get all events
     */
    fun getAllEvents(): List<ICalEvent> = events.values.toList()
    
    /**
     * Get event by UID
     */
    fun getEvent(uid: String): ICalEvent? = events[uid]
    
    /**
     * Add a new event
     */
    fun addEvent(event: ICalEvent): Boolean {
        events[event.uid] = event
        invalidateCache()
        saveToStorage()
        return true
    }
    
    /**
     * Update an existing event
     */
    fun updateEvent(event: ICalEvent): Boolean {
        if (!events.containsKey(event.uid)) return false
        events[event.uid] = event.copy(lastModified = System.currentTimeMillis())
        invalidateCache()
        saveToStorage()
        return true
    }
    
    /**
     * Delete an event by UID
     */
    fun deleteEvent(uid: String): Boolean {
        val removed = events.remove(uid) != null
        if (removed) {
            invalidateCache()
            saveToStorage()
        }
        return removed
    }
    
    /**
     * Add exception date to a recurring event (delete single instance)
     */
    fun addExceptionDate(uid: String, instanceTime: Long): Boolean {
        val event = events[uid] ?: return false
        if (!event.isRecurring) return false
        
        val dateFormat = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val exdateStr = dateFormat.format(Date(instanceTime))
        
        val newExdate = if (event.exdate.isNullOrEmpty()) {
            exdateStr
        } else {
            "${event.exdate},$exdateStr"
        }
        
        events[uid] = event.copy(exdate = newExdate, lastModified = System.currentTimeMillis())
        invalidateCache()
        saveToStorage()
        return true
    }
    
    /**
     * End recurring event at specific instance (delete this and following)
     */
    fun endRecurrenceAtInstance(uid: String, instanceTime: Long): Boolean {
        val event = events[uid] ?: return false
        if (!event.isRecurring) return false
        
        val untilTime = instanceTime - 1000
        val dateFormat = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val untilStr = dateFormat.format(Date(untilTime))
        
        val existingRrule = event.rrule ?: return false
        val newRrule = if (existingRrule.contains("UNTIL=") || existingRrule.contains("COUNT=")) {
            existingRrule
                .replace(Regex("UNTIL=[^;]*"), "UNTIL=$untilStr")
                .replace(Regex("COUNT=[^;]*"), "UNTIL=$untilStr")
        } else {
            "$existingRrule;UNTIL=$untilStr"
        }
        
        events[uid] = event.copy(rrule = newRrule, lastModified = System.currentTimeMillis())
        invalidateCache()
        saveToStorage()
        return true
    }
    
    /**
     * Get instances for display within a time range
     */
    fun getInstances(startTime: Long, endTime: Long): List<EventInstance> {
        // Use cache if range matches
        if (startTime == cacheRangeStart && endTime == cacheRangeEnd && cachedInstances.isNotEmpty()) {
            return cachedInstances
        }
        
        val allInstances = mutableListOf<EventInstance>()
        events.values.forEach { event ->
            allInstances.addAll(InstanceGenerator.generateInstances(event, startTime, endTime))
        }
        
        // Sort by start time
        cachedInstances = allInstances.sortedBy { it.startTime }
        cacheRangeStart = startTime
        cacheRangeEnd = endTime
        
        return cachedInstances
    }
    
    /**
     * Find event by original ID (used for import conflict detection)
     */
    fun findByOriginalId(originalId: Long): ICalEvent? {
        return events.values.find { it.originalId == originalId }
    }
    
    /**
     * Find event by original ID and title (conflict detection)
     */
    fun findByOriginalIdAndTitle(originalId: Long, title: String): ICalEvent? {
        return events.values.find { it.originalId == originalId && it.summary == title }
    }
    
    /**
     * Clear all events
     */
    fun clear() {
        events.clear()
        invalidateCache()
        saveToStorage()
    }
    
    /**
     * Get event count
     */
    fun getEventCount(): Int = events.size
    
    private fun invalidateCache() {
        cachedInstances = emptyList()
        cacheRangeStart = 0L
        cacheRangeEnd = 0L
    }
    
    // --- Persistence ---
    
    private fun saveToStorage() {
        val prefs = this.prefs ?: return
        val jsonArray = JSONArray()
        events.values.forEach { event ->
            jsonArray.put(eventToJson(event))
        }
        prefs.edit().putString(KEY_EVENTS, jsonArray.toString()).apply()
    }
    
    private fun loadFromStorage() {
        val prefs = this.prefs ?: return
        val jsonStr = prefs.getString(KEY_EVENTS, null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val event = jsonToEvent(jsonArray.getJSONObject(i))
                events[event.uid] = event
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun eventToJson(event: ICalEvent): JSONObject {
        return JSONObject().apply {
            put("uid", event.uid)
            put("summary", event.summary)
            put("description", event.description)
            put("location", event.location)
            put("dtStart", event.dtStart)
            put("dtEnd", event.dtEnd)
            put("duration", event.duration ?: "")
            put("allDay", event.allDay)
            put("rrule", event.rrule ?: "")
            put("rdate", event.rdate ?: "")
            put("exdate", event.exdate ?: "")
            put("exrule", event.exrule ?: "")
            put("color", event.color)
            put("lastModified", event.lastModified)
            put("originalId", event.originalId ?: 0L)
        }
    }
    
    private fun jsonToEvent(json: JSONObject): ICalEvent {
        return ICalEvent(
            uid = json.getString("uid"),
            summary = json.getString("summary"),
            description = json.optString("description", ""),
            location = json.optString("location", ""),
            dtStart = json.getLong("dtStart"),
            dtEnd = json.getLong("dtEnd"),
            duration = json.optString("duration", "").ifEmpty { null },
            allDay = json.optBoolean("allDay", false),
            rrule = json.optString("rrule", "").ifEmpty { null },
            rdate = json.optString("rdate", "").ifEmpty { null },
            exdate = json.optString("exdate", "").ifEmpty { null },
            exrule = json.optString("exrule", "").ifEmpty { null },
            color = json.optInt("color", -0x1A8CFF),
            lastModified = json.optLong("lastModified", System.currentTimeMillis()),
            originalId = json.optLong("originalId", 0L).takeIf { it != 0L }
        )
    }
}
