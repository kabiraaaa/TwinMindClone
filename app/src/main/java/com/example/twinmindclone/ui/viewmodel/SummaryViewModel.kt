package com.example.twinmindclone.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmindclone.data.local.entity.MeetingEntity
import com.example.twinmindclone.data.local.entity.MeetingStatus
import com.example.twinmindclone.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SummaryUiState {
    object Loading : SummaryUiState()
    data class Transcribing(val chunksRemaining: Int = 0, val transcript: String = "") : SummaryUiState()
    data class Summarizing(val partialText: String = "") : SummaryUiState()
    data class Success(val meeting: MeetingEntity) : SummaryUiState()
    data class Error(val message: String, val canRetry: Boolean = true, val transcript: String? = null) : SummaryUiState()
}

@HiltViewModel
class SummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val meetingRepository: MeetingRepository
) : ViewModel() {

    val meetingId: Long = savedStateHandle.get<Long>("meetingId") ?: -1L

    val uiState: StateFlow<SummaryUiState> = combine(
        meetingRepository.getMeetingById(meetingId),
        meetingRepository.getLiveTranscript(meetingId)
    ) { meeting, transcript ->
        when {
            meeting == null -> SummaryUiState.Error("Meeting not found", false)
            meeting.status == MeetingStatus.RECORDING || meeting.status == MeetingStatus.PAUSED ->
                SummaryUiState.Loading
            meeting.status == MeetingStatus.TRANSCRIBING ->
                SummaryUiState.Transcribing(transcript = transcript)
            meeting.status == MeetingStatus.SUMMARIZING ->
                SummaryUiState.Summarizing(partialText = meeting.summary ?: "")
            meeting.status == MeetingStatus.COMPLETED ->
                SummaryUiState.Success(meeting)
            meeting.status == MeetingStatus.FAILED ->
                SummaryUiState.Error(
                    message = "Summary generation failed.",
                    transcript = transcript.takeIf { it.isNotBlank() }
                )
            else -> SummaryUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SummaryUiState.Loading)

    fun retrySummary() {
        viewModelScope.launch {
            meetingRepository.updateMeetingStatus(meetingId, MeetingStatus.TRANSCRIBING)
            meetingRepository.enqueueSummary(meetingId)
        }
    }
}
