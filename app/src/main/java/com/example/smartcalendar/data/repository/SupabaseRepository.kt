package com.example.smartcalendar.data.repository

import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.model.LocalCalendar
import com.example.smartcalendar.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Repository for syncing data with Supabase PostgreSQL.
 * Handles CRUD operations for calendars and events in the cloud.
 */
class SupabaseRepository private constructor() {

    private val client = SupabaseClient.client

    // ==================== Calendar Operations ====================

    suspend fun syncCalendars(calendars: List<LocalCalendar>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            calendars.forEach { calendar ->
                val dto = CalendarDto.fromLocal(calendar)

                // Upsert calendar (insert or update)
                client.from("calendars").upsert(dto)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCalendars(userId: String): Result<List<LocalCalendar>> = withContext(Dispatchers.IO) {
        try {
            val response = client.from("calendars")
                .select()
                .decodeList<CalendarDto>()

            val calendars = response.map { dto -> dto.toLocal() }
            Result.success(calendars)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertCalendar(calendar: LocalCalendar): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dto = CalendarDto.fromLocal(calendar)
            client.from("calendars").insert(dto)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCalendar(calendar: LocalCalendar): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dto = CalendarDto.fromLocal(calendar)
            client.from("calendars").update(dto) {
                select()
                filter {
                    CalendarDto::id eq calendar.id
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCalendar(calendarId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.from("calendars").delete {
                select()
                filter {
                    CalendarDto::id eq calendarId
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Event Operations ====================

    suspend fun syncEvents(events: List<ICalEvent>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            try {
                events.forEach { event ->
                    val dto = EventDto.fromLocal(event)
                    client.from("events").upsert(dto)
                }
            } catch (_: Exception) {
                // Fallback schema where event color column is event_color
                events.forEach { event ->
                    val dto = EventDtoV2.fromLocal(event)
                    client.from("events").upsert(dto)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchEvents(userId: String): Result<List<ICalEvent>> = withContext(Dispatchers.IO) {
        try {
            val events = try {
                val response = client.from("events")
                    .select()
                    .decodeList<EventDto>()
                response.map { dto -> dto.toLocal() }
            } catch (_: Exception) {
                val response = client.from("events")
                    .select()
                    .decodeList<EventDtoV2>()
                response.map { dto -> dto.toLocal() }
            }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertEvent(event: ICalEvent): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            try {
                val dto = EventDto.fromLocal(event)
                client.from("events").insert(dto)
            } catch (_: Exception) {
                val dto = EventDtoV2.fromLocal(event)
                client.from("events").insert(dto)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateEvent(event: ICalEvent): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            try {
                val dto = EventDto.fromLocal(event)
                client.from("events").update(dto) {
                    select()
                    filter {
                        EventDto::uid eq event.uid
                    }
                }
            } catch (_: Exception) {
                val dto = EventDtoV2.fromLocal(event)
                client.from("events").update(dto) {
                    select()
                    filter {
                        EventDtoV2::uid eq event.uid
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(eventUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.from("events").delete {
                select()
                filter {
                    EventDto::uid eq eventUid
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== DTOs for Supabase ====================

    @Serializable
    data class CalendarDto(
        val id: String,
        @SerialName("user_id") val userId: String,
        val name: String,
        val color: Int,
        @SerialName("is_default") val isDefault: Boolean,
        @SerialName("is_visible") val isVisible: Boolean,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    ) {
        fun toLocal(): LocalCalendar = LocalCalendar(
            id = id,
            userId = userId,
            name = name,
            color = color,
            isDefault = isDefault,
            isVisible = isVisible,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        companion object {
            fun fromLocal(calendar: LocalCalendar): CalendarDto = CalendarDto(
                id = calendar.id,
                userId = calendar.userId,
                name = calendar.name,
                color = calendar.color,
                isDefault = calendar.isDefault,
                isVisible = calendar.isVisible
            )
        }
    }

    @Serializable
    data class EventDto(
        val uid: String,
        @SerialName("user_id") val userId: String,
        @SerialName("calendar_id") val calendarId: String,
        val summary: String,
        val description: String = "",
        val location: String = "",
        @SerialName("dt_start") val dtStart: Long,
        @SerialName("dt_end") val dtEnd: Long,
        val duration: String? = null,
        @SerialName("all_day") val allDay: Boolean = false,
        val rrule: String? = null,
        val rdate: String? = null,
        val exdate: String? = null,
        val exrule: String? = null,
        val color: Int,
        @SerialName("last_modified") val lastModified: Long,
        @SerialName("original_id") val originalId: Long? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    ) {
        fun toLocal(): ICalEvent = ICalEvent(
            uid = uid,
            userId = userId,
            calendarId = calendarId,
            summary = summary,
            description = description,
            location = location,
            dtStart = dtStart,
            dtEnd = dtEnd,
            duration = duration,
            allDay = allDay,
            rrule = rrule,
            rdate = rdate,
            exdate = exdate,
            exrule = exrule,
            color = color,
            lastModified = lastModified,
            originalId = originalId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        companion object {
            fun fromLocal(event: ICalEvent): EventDto = EventDto(
                uid = event.uid,
                userId = event.userId,
                calendarId = event.calendarId,
                summary = event.summary,
                description = event.description,
                location = event.location,
                dtStart = event.dtStart,
                dtEnd = event.dtEnd,
                duration = event.duration,
                allDay = event.allDay,
                rrule = event.rrule,
                rdate = event.rdate,
                exdate = event.exdate,
                exrule = event.exrule,
                color = event.color,
                lastModified = event.lastModified,
                originalId = event.originalId
            )
        }
    }

    @Serializable
    data class EventDtoV2(
        val uid: String,
        @SerialName("user_id") val userId: String,
        @SerialName("calendar_id") val calendarId: String,
        val summary: String,
        val description: String = "",
        val location: String = "",
        @SerialName("dt_start") val dtStart: Long,
        @SerialName("dt_end") val dtEnd: Long,
        val duration: String? = null,
        @SerialName("all_day") val allDay: Boolean = false,
        val rrule: String? = null,
        val rdate: String? = null,
        val exdate: String? = null,
        val exrule: String? = null,
        @SerialName("event_color") val eventColor: Int,
        @SerialName("last_modified") val lastModified: Long,
        @SerialName("original_id") val originalId: Long? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    ) {
        fun toLocal(): ICalEvent = ICalEvent(
            uid = uid,
            userId = userId,
            calendarId = calendarId,
            summary = summary,
            description = description,
            location = location,
            dtStart = dtStart,
            dtEnd = dtEnd,
            duration = duration,
            allDay = allDay,
            rrule = rrule,
            rdate = rdate,
            exdate = exdate,
            exrule = exrule,
            color = eventColor,
            lastModified = lastModified,
            originalId = originalId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        companion object {
            fun fromLocal(event: ICalEvent): EventDtoV2 = EventDtoV2(
                uid = event.uid,
                userId = event.userId,
                calendarId = event.calendarId,
                summary = event.summary,
                description = event.description,
                location = event.location,
                dtStart = event.dtStart,
                dtEnd = event.dtEnd,
                duration = event.duration,
                allDay = event.allDay,
                rrule = event.rrule,
                rdate = event.rdate,
                exdate = event.exdate,
                exrule = event.exrule,
                eventColor = event.color,
                lastModified = event.lastModified,
                originalId = event.originalId
            )
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SupabaseRepository? = null

        fun getInstance(): SupabaseRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SupabaseRepository().also { INSTANCE = it }
            }
        }
    }
}
