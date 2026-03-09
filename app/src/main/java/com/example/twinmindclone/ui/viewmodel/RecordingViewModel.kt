package com.example.twinmindclone.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmindclone.data.local.entity.MeetingEntity
import com.example.twinmindclone.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val meetingRepository: MeetingRepository
) : ViewModel() {

    private val meetingId: Long = savedStateHandle.get<Long>("meetingId") ?: -1L

    val liveTranscript: StateFlow<String> = meetingRepository.getLiveTranscript(meetingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val meeting: StateFlow<MeetingEntity?> = meetingRepository.getMeetingById(meetingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
