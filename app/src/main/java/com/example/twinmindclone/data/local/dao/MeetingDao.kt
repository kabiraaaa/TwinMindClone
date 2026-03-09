package com.example.twinmindclone.data.local.dao

import androidx.room.*
import com.example.twinmindclone.data.local.entity.MeetingEntity
import com.example.twinmindclone.data.local.entity.MeetingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meetings ORDER BY startTime DESC")
    fun getAllMeetings(): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE id = :id LIMIT 1")
    fun getMeetingById(id: Long): Flow<MeetingEntity?>

    @Query("SELECT * FROM meetings WHERE id = :id LIMIT 1")
    suspend fun getMeetingByIdSync(id: Long): MeetingEntity?

    @Query("SELECT * FROM meetings WHERE status IN ('RECORDING','PAUSED') ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveMeeting(): MeetingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeeting(meeting: MeetingEntity): Long

    @Update
    suspend fun updateMeeting(meeting: MeetingEntity)

    @Delete
    suspend fun deleteMeeting(meeting: MeetingEntity)

    @Query("UPDATE meetings SET status = :status WHERE id = :id")
    suspend fun updateMeetingStatus(id: Long, status: MeetingStatus)

    @Query("UPDATE meetings SET durationMs = :durationMs, endTime = :endTime WHERE id = :id")
    suspend fun updateMeetingDuration(id: Long, durationMs: Long, endTime: Long)

    @Query("UPDATE meetings SET summary = :summary, actionItems = :actionItems, keyPoints = :keyPoints WHERE id = :id")
    suspend fun updateMeetingSummary(id: Long, summary: String, actionItems: List<String>, keyPoints: List<String>)

    @Query("UPDATE meetings SET title = :title WHERE id = :id")
    suspend fun updateMeetingTitle(id: Long, title: String)
}
