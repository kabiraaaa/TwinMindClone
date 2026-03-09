package com.example.twinmindclone.data.repository

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.twinmindclone.data.local.dao.AudioChunkDao
import com.example.twinmindclone.data.local.dao.MeetingDao
import com.example.twinmindclone.data.local.entity.MeetingEntity
import com.example.twinmindclone.data.local.entity.MeetingStatus
import com.example.twinmindclone.domain.repository.MeetingRepository
import com.example.twinmindclone.worker.SummaryWorker
import com.example.twinmindclone.worker.TranscriptionWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingRepositoryImpl @Inject constructor(
    private val meetingDao: MeetingDao,
    private val audioChunkDao: AudioChunkDao,
    private val workManager: WorkManager
) : MeetingRepository {

    override fun getAllMeetings(): Flow<List<MeetingEntity>> = meetingDao.getAllMeetings()

    override fun getMeetingById(id: Long): Flow<MeetingEntity?> = meetingDao.getMeetingById(id)

    override suspend fun getMeetingByIdSync(id: Long): MeetingEntity? = meetingDao.getMeetingByIdSync(id)

    override suspend fun createMeeting(title: String): Long {
        val entity = MeetingEntity(
            title = title,
            startTime = System.currentTimeMillis(),
            status = MeetingStatus.RECORDING
        )
        return meetingDao.insertMeeting(entity)
    }

    override suspend fun updateMeetingStatus(id: Long, status: MeetingStatus) {
        meetingDao.updateMeetingStatus(id, status)
    }

    override suspend fun finalizeMeeting(id: Long, durationMs: Long, endTime: Long) {
        meetingDao.updateMeetingDuration(id, durationMs, endTime)
        meetingDao.updateMeetingStatus(id, MeetingStatus.TRANSCRIBING)
    }

    override fun getLiveTranscript(meetingId: Long): Flow<String> {
        return audioChunkDao.getChunksForMeeting(meetingId).map { chunks ->
            chunks
                .filter { it.transcript != null }
                .sortedBy { it.chunkIndex }
                .joinToString(" ") { it.transcript!! }
        }
    }

    override suspend fun enqueueTranscription(chunkId: Long, meetingId: Long) {
        val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(
                workDataOf(
                    TranscriptionWorker.KEY_CHUNK_ID to chunkId,
                    TranscriptionWorker.KEY_MEETING_ID to meetingId
                )
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("transcription_$meetingId")
            .build()
        workManager.enqueue(request)
    }

    override suspend fun enqueueSummary(meetingId: Long) {
        val request = OneTimeWorkRequestBuilder<SummaryWorker>()
            .setInputData(workDataOf(SummaryWorker.KEY_MEETING_ID to meetingId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("summary_$meetingId")
            .build()
        workManager.enqueue(request)
    }
}
