package com.voicelog.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcripts",
    foreignKeys = [ForeignKey(
        entity = Recording::class,
        parentColumns = ["id"],
        childColumns = ["recordingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recordingId")]
)
data class Transcript(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val text: String,
    val createdAt: Long
)
