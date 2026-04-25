package com.voicelog.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.voicelog.db.entity.Recording
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Insert
    suspend fun insert(recording: Recording): Long

    @Query("SELECT * FROM recordings WHERE status IN ('pending', 'queued') ORDER BY startedAt ASC")
    suspend fun getPendingRecordings(): List<Recording>

    @Query("UPDATE recordings SET status = 'pending' WHERE status IN ('queued', 'transcribing', 'summarizing')")
    suspend fun resetInFlightStatuses()

    @Query("UPDATE recordings SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE recordings SET status = 'pending' WHERE id IN (:ids)")
    suspend fun resetToPending(ids: List<Long>)

    @Query("UPDATE recordings SET status = 'queued' WHERE status = 'pending'")
    suspend fun markPendingAsQueued(): Int

    @Query("SELECT * FROM recordings ORDER BY startedAt DESC")
    fun getAllRecordings(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE status = 'done' AND startedAt < :cutoffMs")
    suspend fun getExpiredRecordings(cutoffMs: Long): List<Recording>

    @Query("DELETE FROM recordings WHERE status = 'done' AND startedAt < :cutoffMs")
    suspend fun deleteExpiredRecordings(cutoffMs: Long)

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Recording?
}
