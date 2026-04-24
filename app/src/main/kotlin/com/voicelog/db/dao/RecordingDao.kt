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

    @Query("SELECT * FROM recordings WHERE status = 'pending' ORDER BY startedAt ASC")
    suspend fun getPendingRecordings(): List<Recording>

    @Query("UPDATE recordings SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("SELECT * FROM recordings ORDER BY startedAt DESC")
    fun getAllRecordings(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE status = 'done' AND startedAt < :cutoffMs")
    suspend fun getExpiredRecordings(cutoffMs: Long): List<Recording>

    @Query("DELETE FROM recordings WHERE status = 'done' AND startedAt < :cutoffMs")
    suspend fun deleteExpiredRecordings(cutoffMs: Long)

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Recording?
}
