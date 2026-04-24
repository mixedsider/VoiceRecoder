package com.voicelog.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "summaries")
data class Summary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val summaryText: String,
    val createdAt: Long
)
