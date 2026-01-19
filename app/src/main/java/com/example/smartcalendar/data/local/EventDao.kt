package com.example.smartcalendar.data.local

import androidx.room.*
import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for event operations.
 */
@Dao
interface EventDao {
    
    // ===== Queries =====
    
    @Query("SELECT * FROM events WHERE userId = :userId ORDER BY dtStart")
    fun getEventsByUser(userId: String): Flow<List<ICalEvent>>
    
    @Query("SELECT * FROM events WHERE userId = :userId")
    suspend fun getEventsListByUser(userId: String): List<ICalEvent>
    
    @Query("SELECT * FROM events WHERE uid = :uid")
    suspend fun getEventById(uid: String): ICalEvent?
    
    @Query("SELECT * FROM events WHERE calendarId = :calendarId ORDER BY dtStart")
    suspend fun getEventsByCalendar(calendarId: String): List<ICalEvent>
    
    @Query("SELECT * FROM events WHERE userId = :userId AND calendarId IN (:visibleCalendarIds) ORDER BY dtStart")
    suspend fun getVisibleEvents(userId: String, visibleCalendarIds: List<String>): List<ICalEvent>
    
    @Query("SELECT * FROM events WHERE syncStatus != :synced")
    suspend fun getPendingSync(synced: SyncStatus = SyncStatus.SYNCED): List<ICalEvent>
    
    @Query("SELECT COUNT(*) FROM events WHERE userId = :userId")
    suspend fun getEventCount(userId: String): Int
    
    @Query("SELECT * FROM events WHERE userId = :userId AND dtStart >= :startTime AND dtStart <= :endTime")
    suspend fun getEventsInRange(userId: String, startTime: Long, endTime: Long): List<ICalEvent>
    
    // ===== Insert/Update =====
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ICalEvent)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<ICalEvent>)
    
    @Update
    suspend fun update(event: ICalEvent)
    
    @Query("UPDATE events SET syncStatus = :status WHERE uid = :uid")
    suspend fun updateSyncStatus(uid: String, status: SyncStatus)
    
    // ===== Delete =====
    
    @Delete
    suspend fun delete(event: ICalEvent)
    
    @Query("DELETE FROM events WHERE uid = :uid")
    suspend fun deleteById(uid: String)
    
    @Query("DELETE FROM events WHERE calendarId = :calendarId")
    suspend fun deleteByCalendar(calendarId: String)
    
    @Query("DELETE FROM events WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
