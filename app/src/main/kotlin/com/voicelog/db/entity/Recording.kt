package com.voicelog.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val startedAt: Long,
    val durationSec: Int,
    val status: String = "pending",
    val summaryText: String? = null
)
