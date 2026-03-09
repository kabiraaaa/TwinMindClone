package com.example.twinmindclone.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.twinmindclone.data.local.dao.AudioChunkDao
import com.example.twinmindclone.data.local.dao.MeetingDao
import com.example.twinmindclone.data.local.entity.MeetingStatus
import com.example.twinmindclone.data.remote.groq.GroqApiClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val meetingDao: MeetingDao,
    private val audioChunkDao: AudioChunkDao,
    private val groqApiClient: GroqApiClient
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MEETING_ID = "meeting_id"
        private const val DB_UPDATE_INTERVAL_MS = 500L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val meetingId = inputData.getLong(KEY_MEETING_ID, -1L)
        if (meetingId == -1L) return@withContext Result.failure()

        return@withContext try {
            val transcripts = audioChunkDao.getTranscriptsForMeeting(meetingId)
            val fullTranscript = transcripts.joinToString("\n\n").trim()

            if (fullTranscript.isBlank()) {
                meetingDao.updateMeetingStatus(meetingId, MeetingStatus.FAILED)
                return@withContext Result.failure()
            }

            meetingDao.updateMeetingStatus(meetingId, MeetingStatus.SUMMARIZING)

            val accumulated = StringBuilder()
            var lastDbWrite = System.currentTimeMillis()

            groqApiClient.streamSummary(fullTranscript).collect { token ->
                accumulated.append(token)
                val now = System.currentTimeMillis()
                if (now - lastDbWrite >= DB_UPDATE_INTERVAL_MS) {
                    meetingDao.updateMeetingSummary(
                        id = meetingId,
                        summary = accumulated.toString(),
                        actionItems = emptyList(),
                        keyPoints = emptyList()
                    )
                    lastDbWrite = now
                }
            }

            val parsed = groqApiClient.parseSummary(accumulated.toString())

            if (parsed.title.isNotBlank()) {
                meetingDao.updateMeetingTitle(meetingId, parsed.title)
            }

            meetingDao.updateMeetingSummary(
                id = meetingId,
                summary = parsed.summary.ifBlank { accumulated.toString() },
                actionItems = parsed.actionItems,
                keyPoints = parsed.keyPoints
            )
            meetingDao.updateMeetingStatus(meetingId, MeetingStatus.COMPLETED)

            Result.success()
        } catch (e: Exception) {
            meetingDao.updateMeetingStatus(meetingId, MeetingStatus.FAILED)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
