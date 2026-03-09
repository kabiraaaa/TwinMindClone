package com.example.twinmindclone.domain.repository

import com.example.twinmindclone.data.local.entity.MeetingEntity
import com.example.twinmindclone.data.local.entity.MeetingStatus
import kotlinx.coroutines.flow.Flow

interface MeetingRepository {
    fun getAllMeetings(): Flow<List<MeetingEntity>>
    fun getMeetingById(id: Long): Flow<MeetingEntity?>
    suspend fun getMeetingByIdSync(id: Long): MeetingEntity?
    suspend fun createMeeting(title: String): Long
    suspend fun updateMeetingStatus(id: Long, status: MeetingStatus)
    suspend fun finalizeMeeting(id: Long, durationMs: Long, endTime: Long)
    fun getLiveTranscript(meetingId: Long): Flow<String>
    suspend fun enqueueTranscription(chunkId: Long, meetingId: Long)
    suspend fun enqueueSummary(meetingId: Long)
}
