package com.example.twinmindclone.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

enum class ChunkStatus {
    PENDING, UPLOADING, TRANSCRIBED, FAILED
}

@Entity(
    tableName = "audio_chunks",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("meetingId")]
)
data class AudioChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val meetingId: Long,
    val filePath: String,
    val chunkIndex: Int,
    val status: ChunkStatus = ChunkStatus.PENDING,
    val transcript: String? = null,
    val durationMs: Long = 30_000L,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
