package com.example.twinmindclone.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.twinmindclone.service.AudioRecordingService
import com.example.twinmindclone.service.RecordingState
import com.example.twinmindclone.ui.components.RecordingPulseAnimation
import com.example.twinmindclone.ui.viewmodel.RecordingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    meetingId: Long,
    onRecordingComplete: (Long) -> Unit,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var recordingService by remember { mutableStateOf<AudioRecordingService?>(null) }
    var serviceState by remember { mutableStateOf<RecordingState>(RecordingState.Idle) }
    var showStopDialog by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionsChecked by remember { mutableStateOf(false) }

    val liveTranscript by viewModel.liveTranscript.collectAsState()

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results[Manifest.permission.RECORD_AUDIO] == true
        permissionsChecked = true
    }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(
            buildList {
                add(Manifest.permission.RECORD_AUDIO)
                add(Manifest.permission.READ_PHONE_STATE)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
        )
    }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                recordingService = (binder as? AudioRecordingService.RecordingBinder)?.getService()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                recordingService = null
            }
        }
    }

    LaunchedEffect(meetingId, permissionsGranted) {
        if (!permissionsGranted) return@LaunchedEffect
        val intent = AudioRecordingService.startIntent(context, meetingId)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        context.bindService(
            Intent(context, AudioRecordingService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    LaunchedEffect(recordingService) {
        recordingService?.recordingState?.collect { state ->
            serviceState = state
            if (state is RecordingState.Idle && serviceState !is RecordingState.Idle) {
                onRecordingComplete(meetingId)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { context.unbindService(serviceConnection) } catch (e: Exception) { }
        }
    }

    BackHandler { showStopDialog = true }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Stop Recording?") },
            text = { Text("This will end the current recording and begin transcription.") },
            confirmButton = {
                TextButton(onClick = {
                    showStopDialog = false
                    recordingService?.stopRecordingAndFinalize()
                        ?: context.startService(
                            Intent(context, AudioRecordingService::class.java).apply {
                                action = AudioRecordingService.ACTION_STOP
                            }
                        )
                    onRecordingComplete(meetingId)
                }) { Text("Stop", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text("Continue") }
            }
        )
    }

    val isRecording = serviceState is RecordingState.Recording
    val isPaused = serviceState is RecordingState.Paused
    val elapsedSeconds = when (val s = serviceState) {
        is RecordingState.Recording -> s.elapsedSeconds
        is RecordingState.Paused -> s.elapsedSeconds
        else -> 0L
    }
    val statusText = when (val s = serviceState) {
        is RecordingState.Recording -> s.statusText
        is RecordingState.Paused -> s.reason
        else -> "Stopped"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording") },
                navigationIcon = {
                    IconButton(onClick = { showStopDialog = true }) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (permissionsChecked && !permissionsGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Microphone permission required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "TwinMind needs access to your microphone to record meetings. Please grant the permission in app settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }) {
                        Text("Open Settings")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1.2f))

                RecordingPulseAnimation(
                    isRecording = isRecording,
                    size = 140.dp
                )

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = formatTime(elapsedSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = when {
                        isRecording -> MaterialTheme.colorScheme.errorContainer
                        isPaused -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = CircleShape
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            isRecording -> MaterialTheme.colorScheme.onErrorContainer
                            isPaused -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1.5f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val pauseIntent = Intent(context, AudioRecordingService::class.java).apply {
                                action = if (isPaused) AudioRecordingService.ACTION_RESUME
                                else AudioRecordingService.ACTION_PAUSE
                            }
                            if (isPaused) {
                                recordingService?.resumeRecording() ?: context.startService(pauseIntent)
                            } else {
                                recordingService?.pauseRecording() ?: context.startService(pauseIntent)
                            }
                        },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Button(
                        onClick = { showStopDialog = true },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (liveTranscript.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Transcript (updates every ~30s)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val scrollState = rememberScrollState()
                            LaunchedEffect(liveTranscript) {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                            Text(
                                text = liveTranscript.takeLast(500),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .verticalScroll(scrollState),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}
