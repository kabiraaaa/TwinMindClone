package com.example.twinmindclone.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.twinmindclone.data.local.dao.AudioChunkDao
import com.example.twinmindclone.data.remote.groq.GroqApiClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val groqApiClient: GroqApiClient,
    private val audioChunkDao: AudioChunkDao,
    private val workManager: WorkManager
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_CHUNK_ID = "chunk_id"
        const val KEY_MEETING_ID = "meeting_id"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val chunkId = inputData.getLong(KEY_CHUNK_ID, -1L)
        val meetingId = inputData.getLong(KEY_MEETING_ID, -1L)

        if (chunkId == -1L || meetingId == -1L) return@withContext Result.failure()

        val chunk = audioChunkDao.getChunkById(chunkId) ?: return@withContext Result.failure()

        audioChunkDao.updateChunkStatus(chunkId, "UPLOADING")

        val audioFile = File(chunk.filePath)
        if (!audioFile.exists()) {
            audioChunkDao.updateChunkStatus(chunkId, "FAILED")
            audioChunkDao.incrementRetryCount(chunkId)
            return@withContext if (runAttemptCount < 3) {
                Result.retry()
            } else {
                enqueueSummaryIfAllDone(meetingId)
                Result.failure()
            }
        }

        return@withContext try {
            val audioBytes = audioFile.readBytes()
            val transcript = groqApiClient.transcribeAudio(audioBytes, "audio/wav")
            audioChunkDao.updateChunkTranscript(chunkId, transcript, "TRANSCRIBED")
            audioFile.delete()
            enqueueSummaryIfAllDone(meetingId)
            Result.success()
        } catch (e: Exception) {
            audioChunkDao.incrementRetryCount(chunkId)
            audioChunkDao.updateChunkStatus(chunkId, "FAILED")
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                enqueueSummaryIfAllDone(meetingId)
                Result.failure()
            }
        }
    }

    private suspend fun enqueueSummaryIfAllDone(meetingId: Long) {
        if (audioChunkDao.countActiveChunksForMeeting(meetingId) == 0) {
            val request = OneTimeWorkRequestBuilder<SummaryWorker>()
                .setInputData(workDataOf(SummaryWorker.KEY_MEETING_ID to meetingId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("summary_$meetingId")
                .build()
            workManager.enqueueUniqueWork(
                "summary_$meetingId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
