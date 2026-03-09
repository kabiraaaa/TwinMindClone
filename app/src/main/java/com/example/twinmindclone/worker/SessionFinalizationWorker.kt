package com.example.twinmindclone.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.twinmindclone.data.local.dao.AudioChunkDao
import com.example.twinmindclone.data.local.dao.MeetingDao
import com.example.twinmindclone.data.local.entity.MeetingStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class SessionFinalizationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val meetingDao: MeetingDao,
    private val audioChunkDao: AudioChunkDao,
    private val workManager: WorkManager
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MEETING_ID = "meeting_id"

        fun enqueue(context: Context, meetingId: Long) {
            val request = OneTimeWorkRequestBuilder<SessionFinalizationWorker>()
                .setInputData(workDataOf(KEY_MEETING_ID to meetingId))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val meetingId = inputData.getLong(KEY_MEETING_ID, -1L)
        if (meetingId == -1L) return@withContext Result.failure()

        val meeting = meetingDao.getMeetingByIdSync(meetingId) ?: return@withContext Result.failure()

        val now = System.currentTimeMillis()
        val duration = now - meeting.startTime
        meetingDao.updateMeetingDuration(meetingId, duration, now)
        meetingDao.updateMeetingStatus(meetingId, MeetingStatus.TRANSCRIBING)

        val pendingChunks = audioChunkDao.getPendingChunksForMeeting(meetingId)
        val retryableChunks = audioChunkDao.getRetryableChunks().filter { it.meetingId == meetingId }
        val chunksToProcess = (pendingChunks + retryableChunks).distinctBy { it.id }

        chunksToProcess.forEach { chunk ->
            val transcriptionRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(
                    workDataOf(
                        TranscriptionWorker.KEY_CHUNK_ID to chunk.id,
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
            workManager.enqueue(transcriptionRequest)
        }

        if (chunksToProcess.isEmpty()) {
            val summaryRequest = OneTimeWorkRequestBuilder<SummaryWorker>()
                .setInputData(workDataOf(SummaryWorker.KEY_MEETING_ID to meetingId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("summary_$meetingId")
                .build()
            workManager.enqueueUniqueWork("summary_$meetingId", ExistingWorkPolicy.KEEP, summaryRequest)
        }

        Result.success()
    }
}
