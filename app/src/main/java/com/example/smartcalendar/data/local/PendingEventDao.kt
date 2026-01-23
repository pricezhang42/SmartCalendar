package com.example.smartcalendar.data.local

import androidx.room.*
import com.example.smartcalendar.data.model.PendingEvent
import com.example.smartcalendar.data.model.PendingStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO for pending AI-extracted events.
 */
@Dao
interface PendingEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PendingEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<PendingEvent>)

    @Update
    suspend fun update(event: PendingEvent)

    @Delete
    suspend fun delete(event: PendingEvent)

    @Query("DELETE FROM pending_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pending_events WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("DELETE FROM pending_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM pending_events WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)

    @Query("SELECT * FROM pending_events WHERE id = :id")
    suspend fun getById(id: String): PendingEvent?

    @Query("SELECT * FROM pending_events WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getBySession(sessionId: String): Flow<List<PendingEvent>>

    @Query("SELECT * FROM pending_events WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getBySessionList(sessionId: String): List<PendingEvent>

    @Query("SELECT * FROM pending_events WHERE userId = :userId AND status = :status ORDER BY createdAt DESC")
    fun getByUserAndStatus(userId: String, status: PendingStatus): Flow<List<PendingEvent>>

    @Query("SELECT * FROM pending_events WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllByUser(userId: String): Flow<List<PendingEvent>>

    @Query("SELECT * FROM pending_events WHERE userId = :userId AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingByUser(userId: String): Flow<List<PendingEvent>>

    @Query("UPDATE pending_events SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: PendingStatus)

    @Query("UPDATE pending_events SET status = :status WHERE sessionId = :sessionId")
    suspend fun updateSessionStatus(sessionId: String, status: PendingStatus)

    @Query("SELECT COUNT(*) FROM pending_events WHERE userId = :userId AND status = 'PENDING'")
    suspend fun countPending(userId: String): Int
}
