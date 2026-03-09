package com.example.twinmindclone.service

sealed class RecordingState {
    object Idle : RecordingState()

    data class Recording(
        val meetingId: Long,
        val elapsedSeconds: Long,
        val statusText: String = "Recording..."
    ) : RecordingState()

    data class Paused(
        val meetingId: Long,
        val elapsedSeconds: Long,
        val reason: String
    ) : RecordingState()
}
