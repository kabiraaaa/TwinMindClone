package com.example.twinmindclone.data.local.dao

import androidx.room.*
import com.example.twinmindclone.data.local.entity.AudioChunkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioChunkDao {
    @Query("SELECT * FROM audio_chunks WHERE meetingId = :meetingId ORDER BY chunkIndex ASC")
    fun getChunksForMeeting(meetingId: Long): Flow<List<AudioChunkEntity>>

    @Query("SELECT * FROM audio_chunks WHERE meetingId = :meetingId ORDER BY chunkIndex ASC")
    suspend fun getChunksForMeetingSync(meetingId: Long): List<AudioChunkEntity>

    @Query("SELECT * FROM audio_chunks WHERE id = :id")
    suspend fun getChunkById(id: Long): AudioChunkEntity?

    @Query("SELECT * FROM audio_chunks WHERE status = 'PENDING'")
    suspend fun getPendingChunks(): List<AudioChunkEntity>

    @Query("SELECT * FROM audio_chunks WHERE meetingId = :meetingId AND status = 'PENDING' ORDER BY chunkIndex ASC")
    suspend fun getPendingChunksForMeeting(meetingId: Long): List<AudioChunkEntity>

    @Query("SELECT * FROM audio_chunks WHERE status = 'FAILED' AND retryCount < 3")
    suspend fun getRetryableChunks(): List<AudioChunkEntity>

    @Query("SELECT COUNT(*) FROM audio_chunks WHERE meetingId = :meetingId AND status IN ('PENDING', 'UPLOADING')")
    suspend fun countActiveChunksForMeeting(meetingId: Long): Int

    @Query("SELECT transcript FROM audio_chunks WHERE meetingId = :meetingId AND transcript IS NOT NULL ORDER BY chunkIndex ASC")
    suspend fun getTranscriptsForMeeting(meetingId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: AudioChunkEntity): Long

    @Update
    suspend fun updateChunk(chunk: AudioChunkEntity)

    @Query("UPDATE audio_chunks SET status = :status WHERE id = :chunkId")
    suspend fun updateChunkStatus(chunkId: Long, status: String)

    @Query("UPDATE audio_chunks SET transcript = :transcript, status = :status WHERE id = :chunkId")
    suspend fun updateChunkTranscript(chunkId: Long, transcript: String, status: String)

    @Query("UPDATE audio_chunks SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)
}
