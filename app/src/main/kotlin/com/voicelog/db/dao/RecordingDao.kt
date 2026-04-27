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
    suspend fun getPendingTranscriptionRecordings(): List<Recording>

    @Query("SELECT * FROM recordings WHERE status = 'transcript_ready' ORDER BY startedAt ASC")
    suspend fun getPendingSummaryRecordings(): List<Recording>

    @Query(
        """
        UPDATE recordings
        SET status = CASE
            WHEN status IN ('queued', 'transcribing') THEN 'pending'
            WHEN status = 'summarizing' THEN 'transcript_ready'
            ELSE status
        END
        WHERE status IN ('queued', 'transcribing', 'summarizing')
        """
    )
    suspend fun resetInFlightStatuses()

    @Query("UPDATE recordings SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE recordings SET summaryText = :summaryText WHERE id = :id")
    suspend fun updateSummaryText(id: Long, summaryText: String?)

    @Query("UPDATE recordings SET status = 'pending' WHERE id IN (:ids)")
    suspend fun resetToPending(ids: List<Long>)

    @Query("UPDATE recordings SET status = 'queued' WHERE status = 'pending'")
    suspend fun markPendingAsQueued(): Int

    @Query("SELECT COUNT(*) > 0 FROM recordings WHERE status IN (:statuses)")
    suspend fun hasAnyWithStatuses(statuses: List<String>): Boolean

    @Query("SELECT * FROM recordings ORDER BY startedAt DESC")
    fun getAllRecordings(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE status = 'done' AND startedAt < :cutoffMs")
    suspend fun getExpiredRecordings(cutoffMs: Long): List<Recording>

    @Query("DELETE FROM recordings WHERE status = 'done' AND startedAt < :cutoffMs")
    suspend fun deleteExpiredRecordings(cutoffMs: Long)

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Recording?

    @Query("""
        SELECT * FROM recordings
        WHERE strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime') = :date
        ORDER BY startedAt ASC
    """)
    suspend fun getRecordingsByDate(date: String): List<Recording>
}
