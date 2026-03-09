package com.example.twinmindclone.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MeetingStatus {
    RECORDING, PAUSED, TRANSCRIBING, SUMMARIZING, COMPLETED, FAILED
}

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val startTime: Long,
    val endTime: Long? = null,
    val durationMs: Long = 0L,
    val status: MeetingStatus = MeetingStatus.RECORDING,
    val summary: String? = null,
    val actionItems: List<String> = emptyList(),
    val keyPoints: List<String> = emptyList()
)
