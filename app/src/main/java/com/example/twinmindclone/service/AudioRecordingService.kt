package com.example.twinmindclone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.twinmindclone.MainActivity
import com.example.twinmindclone.data.local.dao.AudioChunkDao
import com.example.twinmindclone.data.local.datastore.SessionDataStore
import com.example.twinmindclone.data.local.entity.AudioChunkEntity
import com.example.twinmindclone.domain.repository.MeetingRepository
import com.example.twinmindclone.worker.SessionFinalizationWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class AudioRecordingService : LifecycleService() {

    @Inject
    lateinit var meetingRepository: MeetingRepository
    @Inject
    lateinit var audioChunkDao: AudioChunkDao
    @Inject
    lateinit var sessionDataStore: SessionDataStore

    private val binder = RecordingBinder()

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private var currentMeetingId: Long = -1L
    private var chunkIndex: Int = 0
    private var elapsedSeconds: Long = 0L
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var isPaused: Boolean = false
    private var pendingPauseReason: String? = null
    private var lastOverlapBytes: ByteArray = ByteArray(0)

    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var telephonyManager: TelephonyManager

    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null

    private var audioFocusRequest: AudioFocusRequest? = null

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra("state", -1)
            val name = intent.getStringExtra("name") ?: "Headset"
            val message = if (state == 1) "Connected: $name" else "Disconnected: $name"
            updateNotificationText(message)
        }
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (!isPaused) pauseRecording("Paused - Audio focus lost")
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isPaused && _recordingState.value is RecordingState.Paused &&
                    (_recordingState.value as RecordingState.Paused).reason == "Paused - Audio focus lost"
                ) {
                    resumeRecording()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                stopRecordingAndFinalize()
            }
        }
    }

    inner class RecordingBinder : Binder() {
        fun getService(): AudioRecordingService = this@AudioRecordingService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        createNotificationChannel()
        registerHeadsetReceiver()
        registerPhoneStateListener()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val meetingId = intent.getLongExtra(EXTRA_MEETING_ID, -1L)
                if (meetingId != -1L) startRecording(meetingId)
            }

            ACTION_PAUSE -> pauseRecording("Paused")
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecordingAndFinalize()
            else -> {
                lifecycleScope.launch {
                    val activeMeetingId = sessionDataStore.getActiveMeetingId()
                    if (activeMeetingId != null) {
                        SessionFinalizationWorker.enqueue(applicationContext, activeMeetingId)
                        sessionDataStore.clearActiveMeetingId()
                    }
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    fun startRecording(meetingId: Long) {
        currentMeetingId = meetingId
        chunkIndex = 0
        elapsedSeconds = 0L
        isPaused = false
        lastOverlapBytes = ByteArray(0)

        lifecycleScope.launch { sessionDataStore.saveActiveMeetingId(meetingId) }

        val notification = buildNotification(elapsedSeconds, false, "Recording...")
        startForeground(NOTIFICATION_ID, notification)

        requestAudioFocus()
        startTimerJob()
        startChunkRecordingJob()

        _recordingState.value = RecordingState.Recording(meetingId, 0L, "Recording...")
    }

    fun pauseRecording(reason: String = "Paused") {
        if (isPaused) return
        isPaused = true
        pendingPauseReason = reason
        recordingJob?.cancel()
        _recordingState.value = RecordingState.Paused(currentMeetingId, elapsedSeconds, reason)
        updateNotification(elapsedSeconds, true, reason)
    }

    fun resumeRecording() {
        if (!isPaused) return
        isPaused = false
        pendingPauseReason = null
        startChunkRecordingJob()
        _recordingState.value =
            RecordingState.Recording(currentMeetingId, elapsedSeconds, "Recording...")
        updateNotification(elapsedSeconds, false, "Recording...")
    }

    fun stopRecordingAndFinalize() {
        recordingJob?.cancel()
        timerJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        abandonAudioFocus()

        val meetingId = currentMeetingId
        val duration = elapsedSeconds * 1000L
        val endTime = System.currentTimeMillis()

        lifecycleScope.launch {
            if (meetingId != -1L) {
                meetingRepository.finalizeMeeting(meetingId, duration, endTime)
                sessionDataStore.clearActiveMeetingId()
            }
        }

        _recordingState.value = RecordingState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startTimerJob() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                elapsedSeconds++
                val currentState = _recordingState.value
                if (currentState is RecordingState.Recording) {
                    _recordingState.value = currentState.copy(elapsedSeconds = elapsedSeconds)
                    updateNotification(elapsedSeconds, false, currentState.statusText)
                }
                if (elapsedSeconds % 10L == 0L) checkSilenceWarning()
            }
        }
    }

    private var consecutiveSilentChecks = 0
    private var lastMaxAmplitude = 0

    private fun checkSilenceWarning() {
    }

    private fun startChunkRecordingJob() {
        recordingJob?.cancel()
        recordingJob = lifecycleScope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
            val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBufferSize * 4

            if (!hasEnoughStorage()) {
                withContext(Dispatchers.Main) {
                    updateNotificationText("Recording stopped - Low storage")
                    stopRecordingAndFinalize()
                }
                return@launch
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioRecord?.startRecording()

            val chunkDurationMs = 30_000L
            val overlapDurationMs = 2_000L
            val bytesPerMs = sampleRate * 2 / 1000
            val chunkBytes = (chunkDurationMs * bytesPerMs).toInt()
            val overlapBytes = (overlapDurationMs * bytesPerMs).toInt()

            var currentChunkStream = ByteArrayOutputStream()
            if (lastOverlapBytes.isNotEmpty()) {
                currentChunkStream.write(lastOverlapBytes)
            }

            val readBuffer = ByteArray(bufferSize)
            var bytesWritten = currentChunkStream.size()
            var silentFrameCount = 0
            val silenceThresholdRms = 100.0
            val silenceCheckInterval = 10 * sampleRate * 2 // bytes in 10 seconds
            var bytesSinceLastSilenceCheck = 0
            var hasSilenceWarning = false

            while (isActive && !isPaused) {
                val read = audioRecord?.read(readBuffer, 0, bufferSize) ?: break
                if (read > 0) {
                    currentChunkStream.write(readBuffer, 0, read)
                    bytesWritten += read
                    bytesSinceLastSilenceCheck += read

                    if (bytesSinceLastSilenceCheck >= silenceCheckInterval) {
                        val rms = calculateRms(readBuffer, read)
                        bytesSinceLastSilenceCheck = 0
                        if (rms < silenceThresholdRms) {
                            silentFrameCount++
                            if (silentFrameCount >= 1 && !hasSilenceWarning) {
                                hasSilenceWarning = true
                                withContext(Dispatchers.Main) {
                                    updateNotificationText("No audio detected - Check microphone")
                                }
                            }
                        } else {
                            silentFrameCount = 0
                            if (hasSilenceWarning) {
                                hasSilenceWarning = false
                                withContext(Dispatchers.Main) {
                                    updateNotificationText("Recording...")
                                }
                            }
                        }
                    }

                    if (bytesWritten % (chunkBytes / 4) < bufferSize && !hasEnoughStorage()) {
                        withContext(Dispatchers.Main) {
                            updateNotificationText("Recording stopped - Low storage")
                            stopRecordingAndFinalize()
                        }
                        return@launch
                    }

                    if (bytesWritten >= chunkBytes) {
                        val pcmData = currentChunkStream.toByteArray()

                        lastOverlapBytes = if (pcmData.size > overlapBytes) {
                            pcmData.copyOfRange(pcmData.size - overlapBytes, pcmData.size)
                        } else {
                            pcmData.copyOf()
                        }

                        saveChunkAndEnqueue(pcmData, currentMeetingId, chunkIndex)
                        chunkIndex++

                        currentChunkStream = ByteArrayOutputStream()
                        currentChunkStream.write(lastOverlapBytes)
                        bytesWritten = lastOverlapBytes.size
                    }
                }
            }

            withContext(NonCancellable) {
                val finalData = currentChunkStream.toByteArray()
                if (finalData.size > overlapBytes * 2) {
                    saveChunkAndEnqueue(finalData, currentMeetingId, chunkIndex)
                }

                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord?.stop()
                }
                audioRecord?.release()
                audioRecord = null
            }
        }
    }

    private suspend fun saveChunkAndEnqueue(pcmData: ByteArray, meetingId: Long, index: Int) {
        val fileName = "chunk_${meetingId}_${index}.wav"
        val chunkFile = File(applicationContext.filesDir, fileName)
        WavWriter.writePcmToWav(pcmData, chunkFile)

        val entity = AudioChunkEntity(
            meetingId = meetingId,
            filePath = chunkFile.absolutePath,
            chunkIndex = index,
            durationMs = 30_000L,
            createdAt = System.currentTimeMillis()
        )
        val chunkId = audioChunkDao.insertChunk(entity)

        withContext(Dispatchers.Main) {
            meetingRepository.enqueueTranscription(chunkId, meetingId)
        }
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Double {
        var sum = 0.0
        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i].toInt() or (buffer[i + 1].toInt() shl 8)).toShort().toDouble()
            sum += sample * sample
            i += 2
        }
        val samples = length / 2
        return if (samples > 0) Math.sqrt(sum / samples) else 0.0
    }

    private fun hasEnoughStorage(): Boolean {
        return try {
            val stat = StatFs(applicationContext.filesDir.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes > 50 * 1024 * 1024
        } catch (e: Exception) {
            true
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    private fun registerPhoneStateListener() {
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback =
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            handleCallState(state)
                        }
                    }
                telephonyManager.registerTelephonyCallback(mainExecutor, telephonyCallback!!)
            } else {
                phoneStateListener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state)
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: SecurityException) {
        }
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!isPaused) pauseRecording("Paused - Phone call")
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (isPaused && _recordingState.value is RecordingState.Paused &&
                    (_recordingState.value as RecordingState.Paused).reason == "Paused - Phone call"
                ) {
                    resumeRecording()
                }
            }
        }
    }

    private fun registerHeadsetReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
        }
        registerReceiver(headsetReceiver, filter)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Audio recording in progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        elapsedSeconds: Long,
        isPaused: Boolean,
        statusText: String
    ): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = if (isPaused) {
            PendingIntent.getService(
                this, 1,
                Intent(this, AudioRecordingService::class.java).apply { action = ACTION_RESUME },
                PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this, 1,
                Intent(this, AudioRecordingService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, AudioRecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val timeStr = formatTime(elapsedSeconds)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TwinMind - $statusText")
            .setContentText(timeStr)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                pauseResumeIntent
            )
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification(elapsedSeconds: Long, isPaused: Boolean, statusText: String) {
        val notification = buildNotification(elapsedSeconds, isPaused, statusText)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationText(text: String) {
        val isPaused = this.isPaused
        val notification = buildNotification(elapsedSeconds, isPaused, text)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatTime(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingJob?.cancel()
        timerJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        abandonAudioFocus()
        unregisterReceiver(headsetReceiver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_MEETING_ID = "meeting_id"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_CHANNEL_ID = "recording_channel"

        fun startIntent(context: Context, meetingId: Long): Intent {
            return Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MEETING_ID, meetingId)
            }
        }
    }
}
