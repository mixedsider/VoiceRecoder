package com.voicelog.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voicelog.db.entity.Summary
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(summary: Summary)

    @Query("SELECT * FROM summaries ORDER BY date DESC")
    fun getAllSummaries(): Flow<List<Summary>>

    @Query("SELECT * FROM summaries WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): Summary?

    @Query("DELETE FROM summaries WHERE date = :date")
    suspend fun deleteByDate(date: String)
}
