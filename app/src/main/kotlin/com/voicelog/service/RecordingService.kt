package com.voicelog.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.voicelog.VoiceLogApp
import com.voicelog.db.AppDatabase
import com.voicelog.db.entity.Recording
import com.voicelog.ui.MainActivity
import com.voicelog.util.AudioUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class RecordingService : Service() {

    companion object {
        const val ACTION_START = "com.voicelog.START_RECORDING"
        const val ACTION_STOP = "com.voicelog.STOP_RECORDING"
        const val MAX_SEGMENT_DURATION_MS = 3 * 60 * 1000L
        const val SILENCE_THRESHOLD_MS = 1500L
        const val SAMPLE_RATE = VadDetector.SAMPLE_RATE
        const val FRAME_SIZE = VadDetector.FRAME_SIZE
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var recordingJob: Job? = null
    private lateinit var vad: VadDetector
    private lateinit var db: AppDatabase

    override fun onCreate() {
        super.onCreate()
        vad = VadDetector(this)
        db = AppDatabase.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(VoiceLogApp.NOTIFICATION_ID, buildNotification())
                startListening()
            }
            ACTION_STOP -> {
                stopListening()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startListening() {
        recordingJob = serviceScope.launch {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(FRAME_SIZE * 2)

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord.startRecording()
            vad.resetState()

            var isRecording = false
            var segmentStartMs = 0L
            var lastVoiceMs = 0L
            val segmentBuffer = mutableListOf<ShortArray>()
            val frame = ShortArray(FRAME_SIZE)

            while (isActive) {
                val read = audioRecord.read(frame, 0, FRAME_SIZE)
                if (read != FRAME_SIZE) continue

                val now = System.currentTimeMillis()
                val isVoice = vad.isVoice(frame)

                if (isVoice) {
                    lastVoiceMs = now
                    if (!isRecording) {
                        isRecording = true
                        segmentStartMs = now
                        segmentBuffer.clear()
                    }
                }

                if (isRecording) {
                    segmentBuffer.add(frame.copyOf())

                    val silenceDuration = now - lastVoiceMs
                    val segmentDuration = now - segmentStartMs

                    if (silenceDuration >= SILENCE_THRESHOLD_MS || segmentDuration >= MAX_SEGMENT_DURATION_MS) {
                        saveSegment(segmentBuffer.toList(), segmentStartMs, ((now - segmentStartMs) / 1000).toInt())
                        isRecording = false
                        segmentBuffer.clear()
                        vad.resetState()
                    }
                }
            }

            audioRecord.stop()
            audioRecord.release()
        }
    }

    private suspend fun saveSegment(frames: List<ShortArray>, startedAt: Long, durationSec: Int) {
        val combined = ShortArray(frames.sumOf { it.size })
        var offset = 0
        frames.forEach { frame ->
            frame.copyInto(combined, offset)
            offset += frame.size
        }

        val fileName = "rec_${AudioUtils.formatTimestamp(startedAt)}.wav"
        val dir = File(filesDir, "recordings").also { it.mkdirs() }
        val filePath = File(dir, fileName).absolutePath

        AudioUtils.writeWavFile(filePath, combined, SAMPLE_RATE)

        db.recordingDao().insert(
            Recording(
                filePath = filePath,
                startedAt = startedAt,
                durationSec = durationSec
            )
        )
    }

    private fun stopListening() {
        recordingJob?.cancel()
        recordingJob = null
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, VoiceLogApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("VoiceLog")
            .setContentText("음성 감지 중...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        stopListening()
        vad.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
