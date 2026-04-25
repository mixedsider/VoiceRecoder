package com.voicelog.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.voicelog.R
import com.voicelog.VoiceLogApp
import com.voicelog.db.AppDatabase
import com.voicelog.db.entity.Recording
import com.voicelog.db.entity.Summary
import com.voicelog.db.entity.Transcript
import com.voicelog.jni.LlamaJNI
import com.voicelog.util.ModelUtils
import com.voicelog.util.SummaryTextFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "voice_processing"

        private const val TAG = "ProcessingWorker"
        private const val WHISPER_TRANSCRIBE_TIMEOUT_MS = 30_000
        private const val STATUS_PENDING = "pending"
        private const val STATUS_TRANSCRIBING = "transcribing"
        private const val STATUS_SUMMARIZING = "summarizing"
        private const val STATUS_DONE = "done"
        private const val STATUS_FAILED = "failed"

        private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val db = AppDatabase.getInstance(ctx)

        Log.i(TAG, "Processing worker started")
        setForeground(createForegroundInfo("Preparing models"))

        if (!ModelUtils.isWhisperModelReady(ctx) || !ModelUtils.isLlamaModelReady(ctx)) {
            Log.e(TAG, "Model files not ready")
            return Result.failure()
        }

        val pending = db.recordingDao().getPendingRecordings()
        Log.i(TAG, "Pending recordings: ${pending.size}")
        if (pending.isEmpty()) {
            Log.i(TAG, "No pending recordings")
            return Result.success()
        }

        val activeIds = mutableSetOf<Long>()
        val transcribedRecordings = mutableListOf<Recording>()
        val processedDates = linkedSetOf<String>()

        setForeground(createForegroundInfo("Loading Whisper model"))
        Log.i(TAG, "Initializing Whisper")
        val whisperModelPath = ModelUtils.getWhisperModelPath(ctx)
        val whisperVocabPath = ModelUtils.getWhisperVocabPath(ctx)
        val whisperReady = WhisperRuntime.warmUp(whisperModelPath, whisperVocabPath)
        if (!whisperReady) {
            Log.e(TAG, "Failed to initialize Whisper")
            return Result.retry()
        }
        Log.i(TAG, "Whisper initialized")

        for ((index, recording) in pending.withIndex()) {
            activeIds += recording.id
            db.recordingDao().updateStatus(recording.id, STATUS_TRANSCRIBING)
            setForeground(
                createForegroundInfo(
                    message = "Transcribing ${index + 1}/${pending.size}",
                    current = index + 1,
                    total = pending.size
                )
            )
            Log.i(TAG, "Transcribing recording ${recording.id}")

            try {
                val audioFloats = loadWavAsFloat(recording.filePath)
                if (audioFloats == null) {
                    Log.e(TAG, "Failed to load WAV: ${recording.filePath}")
                    db.recordingDao().updateStatus(recording.id, STATUS_FAILED)
                    activeIds -= recording.id
                    continue
                }

                val text = WhisperRuntime.transcribe(
                    whisperModelPath,
                    whisperVocabPath,
                    audioFloats,
                    WHISPER_TRANSCRIBE_TIMEOUT_MS
                )
                if (text.isBlank()) {
                    Log.w(
                        TAG,
                        "Transcription timed out or returned empty text for recording ${recording.id}"
                    )
                    db.recordingDao().updateStatus(recording.id, STATUS_FAILED)
                    activeIds -= recording.id
                    continue
                }
                db.transcriptDao().insert(
                    Transcript(
                        recordingId = recording.id,
                        text = text,
                        createdAt = System.currentTimeMillis()
                    )
                )
                db.recordingDao().updateStatus(recording.id, STATUS_SUMMARIZING)
                transcribedRecordings += recording
                processedDates += dateFmt.format(Date(recording.startedAt))
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed for recording ${recording.id}", e)
                db.recordingDao().updateStatus(recording.id, STATUS_FAILED)
                activeIds -= recording.id
            }
        }

        if (transcribedRecordings.isEmpty()) {
            Log.i(TAG, "Nothing to summarize")
            return Result.success()
        }

        val llama = LlamaJNI()
        setForeground(createForegroundInfo("Loading summary model"))
        Log.i(TAG, "Initializing LLaMA")
        val llamaCtx = llama.init(ModelUtils.getLlamaModelPath(ctx))
        if (llamaCtx == 0L) {
            Log.e(TAG, "Failed to initialize LLaMA")
            resetStatuses(db, activeIds, STATUS_PENDING)
            return Result.retry()
        }
        Log.i(TAG, "LLaMA initialized")

        return try {
            try {
                for ((index, date) in processedDates.withIndex()) {
                    setForeground(
                        createForegroundInfo(
                            message = "Summarizing ${index + 1}/${processedDates.size}",
                            current = index + 1,
                            total = processedDates.size
                        )
                    )
                    Log.i(TAG, "Summarizing date $date")

                    val transcripts = db.transcriptDao().getTranscriptsByDate(date)
                    if (transcripts.isEmpty()) {
                        continue
                    }

                    val combined = transcripts.joinToString("\n\n") { it.text.trim() }
                    val prompt = "Summarize the following daily transcript in concise Korean:\n\n$combined"
                    val summary = SummaryTextFormatter.normalize(
                        llama.generate(llamaCtx, prompt)
                    )

                    db.summaryDao().insertOrReplace(
                        Summary(
                            date = date,
                            summaryText = summary,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                for (recording in transcribedRecordings) {
                    db.recordingDao().updateStatus(recording.id, STATUS_DONE)
                    activeIds -= recording.id
                }
            } catch (e: Exception) {
                Log.e(TAG, "Summarization failed", e)
                resetStatuses(db, activeIds, STATUS_PENDING)
                return Result.retry()
            }

            cleanupExpiredRecordings(ctx, db)
            Log.i(TAG, "Processing complete")
            Result.success()
        } finally {
            llama.free(llamaCtx)
        }
    }

    private fun createForegroundInfo(
        message: String,
        current: Int = 0,
        total: Int = 0
    ): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, VoiceLogApp.PROCESSING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("VoiceLog processing")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(total, current, total <= 0)
            .build()

        return ForegroundInfo(
            VoiceLogApp.PROCESSING_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private suspend fun resetStatuses(db: AppDatabase, ids: Set<Long>, status: String) {
        for (id in ids) {
            db.recordingDao().updateStatus(id, status)
        }
    }

    private suspend fun cleanupExpiredRecordings(ctx: Context, db: AppDatabase) {
        val retentionDays = ctx.getSharedPreferences("voicelog_prefs", Context.MODE_PRIVATE)
            .getInt("retention_days", 30)
        val cutoffMs = System.currentTimeMillis() - retentionDays * 86_400_000L
        val expired = db.recordingDao().getExpiredRecordings(cutoffMs)
        expired.forEach { rec ->
            try {
                File(rec.filePath).delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete file: ${rec.filePath}", e)
            }
        }
        db.recordingDao().deleteExpiredRecordings(cutoffMs)
    }

    internal fun loadWavAsFloat(filePath: String): FloatArray? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            if (bytes.size <= 44) return null
            val pcmOffset = 44
            val numSamples = (bytes.size - pcmOffset) / 2
            FloatArray(numSamples) { i ->
                val lo = bytes[pcmOffset + i * 2].toInt() and 0xFF
                val hi = bytes[pcmOffset + i * 2 + 1].toInt()
                ((hi shl 8) or lo).toShort() / 32768f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading WAV: ${e.message}", e)
            null
        }
    }
}
