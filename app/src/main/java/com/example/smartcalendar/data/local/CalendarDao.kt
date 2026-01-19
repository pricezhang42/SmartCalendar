package com.example.smartcalendar.data.local

import androidx.room.*
import com.example.smartcalendar.data.model.LocalCalendar
import com.example.smartcalendar.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for calendar operations.
 */
@Dao
interface CalendarDao {
    
    // ===== Queries =====
    
    @Query("SELECT * FROM calendars WHERE userId = :userId ORDER BY name")
    fun getCalendarsByUser(userId: String): Flow<List<LocalCalendar>>
    
    @Query("SELECT * FROM calendars WHERE userId = :userId")
    suspend fun getCalendarsListByUser(userId: String): List<LocalCalendar>
    
    @Query("SELECT * FROM calendars WHERE id = :id")
    suspend fun getCalendarById(id: String): LocalCalendar?
    
    @Query("SELECT * FROM calendars WHERE userId = :userId AND isVisible = 1")
    suspend fun getVisibleCalendars(userId: String): List<LocalCalendar>
    
    @Query("SELECT * FROM calendars WHERE syncStatus != :synced")
    suspend fun getPendingSync(synced: SyncStatus = SyncStatus.SYNCED): List<LocalCalendar>
    
    @Query("SELECT COUNT(*) FROM calendars WHERE userId = :userId")
    suspend fun getCalendarCount(userId: String): Int
    
    // ===== Insert/Update =====
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calendar: LocalCalendar)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(calendars: List<LocalCalendar>)
    
    @Update
    suspend fun update(calendar: LocalCalendar)
    
    @Query("UPDATE calendars SET isVisible = :visible, updatedAt = :now, syncStatus = :pending WHERE id = :id")
    suspend fun setVisibility(
        id: String, 
        visible: Boolean, 
        now: Long = System.currentTimeMillis(),
        pending: SyncStatus = SyncStatus.PENDING
    )
    
    @Query("UPDATE calendars SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)
    
    // ===== Delete =====
    
    @Delete
    suspend fun delete(calendar: LocalCalendar)
    
    @Query("DELETE FROM calendars WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM calendars WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
