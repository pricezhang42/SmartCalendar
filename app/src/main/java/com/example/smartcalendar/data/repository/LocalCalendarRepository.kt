package com.example.smartcalendar.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.smartcalendar.data.model.EventInstance
import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.model.LocalCalendar
import com.example.smartcalendar.data.util.InstanceGenerator
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Local calendar repository managing calendars and events with persistence.
 */
class LocalCalendarRepository private constructor() {
    
    // Calendars stored by ID
    private val calendars = mutableMapOf<String, LocalCalendar>()
    
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
        private const val PREFS_NAME = "smartcalendar_data"
        private const val KEY_EVENTS = "events"
        private const val KEY_CALENDARS = "calendars"
        
        fun getInstance(): LocalCalendarRepository {
            return instance ?: synchronized(this) {
                instance ?: LocalCalendarRepository().also { instance = it }
            }
        }
        
        fun init(context: Context) {
            getInstance().apply {
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                loadFromStorage()
                ensureDefaultCalendars()
            }
        }
    }
    
    // ==================== Calendar Methods ====================
    
    fun getCalendars(): List<LocalCalendar> = calendars.values.toList()
    
    fun getCalendar(id: String): LocalCalendar? = calendars[id]
    
    fun getVisibleCalendars(): List<LocalCalendar> = calendars.values.filter { it.isVisible }
    
    fun getVisibleCalendarIds(): Set<String> = calendars.values.filter { it.isVisible }.map { it.id }.toSet()
    
    fun addCalendar(calendar: LocalCalendar): Boolean {
        calendars[calendar.id] = calendar
        saveToStorage()
        return true
    }
    
    fun updateCalendar(calendar: LocalCalendar): Boolean {
        if (!calendars.containsKey(calendar.id)) return false
        calendars[calendar.id] = calendar
        invalidateCache()
        saveToStorage()
        return true
    }
    
    fun deleteCalendar(id: String): Boolean {
        val calendar = calendars[id] ?: return false
        if (calendar.isDefault) return false // Cannot delete default calendars
        
        // Delete all events in this calendar
        events.values.filter { it.calendarId == id }.forEach { events.remove(it.uid) }
        calendars.remove(id)
        invalidateCache()
        saveToStorage()
        return true
    }
    
    fun setCalendarVisible(id: String, visible: Boolean): Boolean {
        val calendar = calendars[id] ?: return false
        calendars[id] = calendar.copy(isVisible = visible)
        invalidateCache()
        saveToStorage()
        return true
    }
    
    private fun ensureDefaultCalendars() {
        if (calendars.isEmpty()) {
            LocalCalendar.createDefault().forEach { calendars[it.id] = it }
            saveToStorage()
        }
    }
    
    // ==================== Event Methods ====================
    
    fun getAllEvents(): List<ICalEvent> = events.values.toList()
    
    fun getEvent(uid: String): ICalEvent? = events[uid]
    
    fun addEvent(event: ICalEvent): Boolean {
        events[event.uid] = event
        invalidateCache()
        saveToStorage()
        return true
    }
    
    fun updateEvent(event: ICalEvent): Boolean {
        if (!events.containsKey(event.uid)) return false
        events[event.uid] = event.copy(lastModified = System.currentTimeMillis())
        invalidateCache()
        saveToStorage()
        return true
    }
    
    fun deleteEvent(uid: String): Boolean {
        val removed = events.remove(uid) != null
        if (removed) {
            invalidateCache()
            saveToStorage()
        }
        return removed
    }
    
    fun addExceptionDate(uid: String, instanceTime: Long): Boolean {
        val event = events[uid] ?: return false
        if (!event.isRecurring) return false
        
        val dateFormat = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val exdateStr = dateFormat.format(Date(instanceTime))
        
        val newExdate = if (event.exdate.isNullOrEmpty()) exdateStr else "${event.exdate},$exdateStr"
        
        events[uid] = event.copy(exdate = newExdate, lastModified = System.currentTimeMillis())
        invalidateCache()
        saveToStorage()
        return true
    }
    
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
     * Get instances filtered by visible calendars
     */
    fun getInstances(startTime: Long, endTime: Long): List<EventInstance> {
        if (startTime == cacheRangeStart && endTime == cacheRangeEnd && cachedInstances.isNotEmpty()) {
            return cachedInstances
        }
        
        val visibleIds = getVisibleCalendarIds()
        val allInstances = mutableListOf<EventInstance>()
        
        events.values
            .filter { visibleIds.contains(it.calendarId) }
            .forEach { event ->
                allInstances.addAll(InstanceGenerator.generateInstances(event, startTime, endTime))
            }
        
        cachedInstances = allInstances.sortedBy { it.startTime }
        cacheRangeStart = startTime
        cacheRangeEnd = endTime
        
        return cachedInstances
    }
    
    fun findByOriginalId(originalId: Long): ICalEvent? = events.values.find { it.originalId == originalId }
    
    fun findByOriginalIdAndTitle(originalId: Long, title: String): ICalEvent? =
        events.values.find { it.originalId == originalId && it.summary == title }
    
    fun clear() {
        events.clear()
        invalidateCache()
        saveToStorage()
    }
    
    fun getEventCount(): Int = events.size
    
    private fun invalidateCache() {
        cachedInstances = emptyList()
        cacheRangeStart = 0L
        cacheRangeEnd = 0L
    }
    
    // ==================== Persistence ====================
    
    private fun saveToStorage() {
        val prefs = this.prefs ?: return
        
        // Save calendars
        val calendarsJson = JSONArray()
        calendars.values.forEach { calendarsJson.put(calendarToJson(it)) }
        
        // Save events
        val eventsJson = JSONArray()
        events.values.forEach { eventsJson.put(eventToJson(it)) }
        
        prefs.edit()
            .putString(KEY_CALENDARS, calendarsJson.toString())
            .putString(KEY_EVENTS, eventsJson.toString())
            .apply()
    }
    
    private fun loadFromStorage() {
        val prefs = this.prefs ?: return
        
        // Load calendars
        prefs.getString(KEY_CALENDARS, null)?.let { jsonStr ->
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val cal = jsonToCalendar(jsonArray.getJSONObject(i))
                    calendars[cal.id] = cal
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        
        // Load events
        prefs.getString(KEY_EVENTS, null)?.let { jsonStr ->
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val event = jsonToEvent(jsonArray.getJSONObject(i))
                    events[event.uid] = event
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    private fun calendarToJson(cal: LocalCalendar) = JSONObject().apply {
        put("id", cal.id)
        put("name", cal.name)
        put("color", cal.color)
        put("isDefault", cal.isDefault)
        put("isVisible", cal.isVisible)
    }
    
    private fun jsonToCalendar(json: JSONObject) = LocalCalendar(
        id = json.getString("id"),
        name = json.getString("name"),
        color = json.getInt("color"),
        isDefault = json.optBoolean("isDefault", false),
        isVisible = json.optBoolean("isVisible", true)
    )
    
    private fun eventToJson(event: ICalEvent) = JSONObject().apply {
        put("uid", event.uid)
        put("calendarId", event.calendarId)
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
    
    private fun jsonToEvent(json: JSONObject) = ICalEvent(
        uid = json.getString("uid"),
        calendarId = json.optString("calendarId", "personal"),
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
