package com.example.twinmindclone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.twinmindclone.ui.viewmodel.SummaryUiState
import com.example.twinmindclone.ui.viewmodel.SummaryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    meetingId: Long,
    onNavigateBack: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meeting Summary") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is SummaryUiState.Error) {
                        IconButton(onClick = { viewModel.retrySummary() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {

            is SummaryUiState.Loading -> CenteredProgress(
                paddingValues = paddingValues,
                message = "Processing recording..."
            )

            is SummaryUiState.Transcribing -> {
                if (state.transcript.isBlank()) {
                    CenteredProgress(
                        paddingValues = paddingValues,
                        message = "Transcribing audio...",
                        subMessage = "This may take a few moments"
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    "Transcribing audio...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        item { TranscriptCard(transcript = state.transcript) }
                    }
                }
            }

            is SummaryUiState.Summarizing -> {
                if (state.partialText.isBlank()) {
                    CenteredProgress(
                        paddingValues = paddingValues,
                        message = "Generating summary..."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    "Generating summary...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        item {
                            Text(
                                text = state.partialText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is SummaryUiState.Success -> {
                val meeting = state.meeting
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = meeting.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!meeting.summary.isNullOrBlank()) {
                        item {
                            SummarySection(title = "Summary") {
                                Text(
                                    text = meeting.summary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    if (meeting.actionItems.isNotEmpty()) {
                        item {
                            SummarySection(title = "Action Items") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    meeting.actionItems.forEach { item ->
                                        BulletItem(text = item)
                                    }
                                }
                            }
                        }
                    }

                    if (meeting.keyPoints.isNotEmpty()) {
                        item {
                            SummarySection(title = "Key Points") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    meeting.keyPoints.forEach { point ->
                                        BulletItem(text = point)
                                    }
                                }
                            }
                        }
                    }

                    if (meeting.summary.isNullOrBlank() &&
                        meeting.actionItems.isEmpty() &&
                        meeting.keyPoints.isEmpty()
                    ) {
                        item {
                            Text(
                                "No summary available.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is SummaryUiState.Error -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (state.canRetry) {
                        item {
                            Button(onClick = { viewModel.retrySummary() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry Summary")
                            }
                        }
                    }
                    if (!state.transcript.isNullOrBlank()) {
                        item {
                            Text(
                                text = "Transcript is available below",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        item { TranscriptCard(transcript = state.transcript) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredProgress(
    paddingValues: PaddingValues,
    message: String,
    subMessage: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator()
            Text(message, style = MaterialTheme.typography.titleMedium)
            if (!subMessage.isNullOrBlank()) {
                Text(
                    subMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummarySection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun TranscriptCard(transcript: String) {
    SummarySection(title = "Transcript") {
        Text(
            text = transcript,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BulletItem(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
