package com.voicelog.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.voicelog.db.entity.Transcript

@Dao
interface TranscriptDao {
    @Insert
    suspend fun insert(transcript: Transcript): Long

    @Query("DELETE FROM transcripts WHERE recordingId IN (:recordingIds)")
    suspend fun deleteByRecordingIds(recordingIds: List<Long>)

    @Query("""
        SELECT t.* FROM transcripts t
        INNER JOIN recordings r ON t.recordingId = r.id
        WHERE strftime('%Y-%m-%d', r.startedAt / 1000, 'unixepoch', 'localtime') = :date
        ORDER BY r.startedAt ASC
    """)
    suspend fun getTranscriptsByDate(date: String): List<Transcript>

    @Query("SELECT * FROM transcripts WHERE recordingId = :recordingId LIMIT 1")
    suspend fun getByRecordingId(recordingId: Long): Transcript?

    @Query("SELECT * FROM transcripts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Transcript?

    @Query("SELECT recordingId FROM transcripts WHERE instr(text, char(65533)) > 0")
    suspend fun getCorruptedRecordingIds(): List<Long>
}
