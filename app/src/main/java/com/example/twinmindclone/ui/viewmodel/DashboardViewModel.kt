package com.example.twinmindclone.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmindclone.data.local.entity.MeetingEntity
import com.example.twinmindclone.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository
) : ViewModel() {

    val meetings: StateFlow<List<MeetingEntity>> = meetingRepository.getAllMeetings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createMeeting(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val title = "Meeting - ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())}"
            val meetingId = meetingRepository.createMeeting(title)
            onCreated(meetingId)
        }
    }
}
