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
import com.voicelog.inference.EngineWarmUpMetrics
import com.voicelog.inference.SummaryMetrics
import com.voicelog.inference.SummaryEngineFactory
import com.voicelog.inference.SummaryPromptBuilder
import com.voicelog.inference.TranscriptionMetrics
import com.voicelog.inference.TranscriptionEngine
import com.voicelog.inference.WhisperTranscriptionEngine
import com.voicelog.util.BackendResolution
import com.voicelog.util.InferencePreferences
import com.voicelog.util.InferenceRuntime
import com.voicelog.util.ModelUtils
import com.voicelog.util.ProcessingPreferences
import com.voicelog.util.SummaryTextFormatter
import android.os.SystemClock
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
        private const val STATUS_TRANSCRIBING = "transcribing"
        private const val STATUS_TRANSCRIPT_READY = "transcript_ready"
        private const val STATUS_SUMMARIZING = "summarizing"
        private const val STATUS_DONE = "done"
        private const val STATUS_FAILED = "failed"

        private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val db = AppDatabase.getInstance(ctx)
        val canRunStt = ProcessingPreferences.shouldRunSttNow(ctx)
        val canRunSummary = ProcessingPreferences.shouldRunSummaryNow(ctx)

        Log.i(TAG, "Processing worker started")
        setForeground(createForegroundInfo("Preparing models"))

        if (!canRunStt && !canRunSummary) {
            Log.i(TAG, "Skipping work because current policies do not allow processing right now")
            return Result.success()
        }

        val pendingTranscriptions = if (canRunStt) {
            db.recordingDao().getPendingTranscriptionRecordings()
        } else {
            emptyList()
        }
        val pendingSummaries = if (canRunSummary) {
            db.recordingDao().getPendingSummaryRecordings()
        } else {
            emptyList()
        }

        val needsWhisper = pendingTranscriptions.isNotEmpty()
        val needsLlama = canRunSummary &&
            (pendingTranscriptions.isNotEmpty() || pendingSummaries.isNotEmpty())
        val sttResolution = InferenceRuntime.resolveStt(ctx)
        val selectedLlmModel = InferencePreferences.getSelectedLlmModel(ctx)
        val readyLlmModel = if (needsLlama) {
            InferencePreferences.getReadyLlmModelOrFallback(ctx)
        } else {
            selectedLlmModel
        }
        val llmResolution = InferenceRuntime.resolveLlm(ctx, readyLlmModel ?: selectedLlmModel)

        if (needsWhisper && !InferencePreferences.isSelectedSttModelReady(ctx)) {
            Log.e(TAG, "Whisper model files not ready")
            return Result.failure()
        }
        if (needsLlama && readyLlmModel == null) {
            Log.e(TAG, "No ready LLM model files are available for summary")
            return Result.failure()
        }
        if (needsLlama && readyLlmModel?.id != selectedLlmModel.id) {
            Log.w(
                TAG,
                "Selected LLM model ${selectedLlmModel.displayName} is not ready; " +
                    "using ready fallback ${readyLlmModel?.displayName}"
            )
        }

        Log.i(
            TAG,
            "Pending transcriptions: ${pendingTranscriptions.size}, pending summaries: ${pendingSummaries.size}"
        )
        if (needsWhisper) {
            logBackendResolution("STT", sttResolution)
        }
        if (needsLlama) {
            logBackendResolution("LLM", llmResolution)
        }
        if (!needsWhisper && !needsLlama) {
            Log.i(TAG, "No pending recordings")
            return Result.success()
        }

        val summaryCandidates = mutableListOf<Recording>()
        val transcriptionEngine: TranscriptionEngine = WhisperTranscriptionEngine()

        if (needsWhisper) {
            setForeground(createForegroundInfo("Loading Whisper model"))
            Log.i(TAG, "Initializing Whisper")
            val whisperModelPath = InferencePreferences.getSelectedSttModelPath(ctx)
            val whisperVocabPath = ModelUtils.getWhisperVocabPath(ctx)
            val warmUpResult = transcriptionEngine.warmUp(
                whisperModelPath,
                whisperVocabPath,
                sttResolution,
            )
            logWhisperWarmUpMetrics(warmUpResult.metrics)
            if (!warmUpResult.success) {
                Log.e(TAG, "Failed to initialize Whisper")
                return Result.retry()
            }
            Log.i(TAG, "Whisper initialized")

            for ((index, recording) in pendingTranscriptions.withIndex()) {
                db.recordingDao().updateStatus(recording.id, STATUS_TRANSCRIBING)
                setForeground(
                    createForegroundInfo(
                        message = "Transcribing ${index + 1}/${pendingTranscriptions.size}",
                        current = index + 1,
                        total = pendingTranscriptions.size
                    )
                )
                Log.i(TAG, "Transcribing recording ${recording.id}")

                try {
                    val audioLoadStartedAt = SystemClock.elapsedRealtime()
                    val audioFloats = loadWavAsFloat(recording.filePath)
                    val audioLoadMs = SystemClock.elapsedRealtime() - audioLoadStartedAt
                    if (audioFloats == null) {
                        Log.e(TAG, "Failed to load WAV: ${recording.filePath}")
                        db.recordingDao().updateStatus(recording.id, STATUS_FAILED)
                        continue
                    }

                    val transcription = transcriptionEngine.transcribe(
                        whisperModelPath,
                        whisperVocabPath,
                        audioFloats,
                        WHISPER_TRANSCRIBE_TIMEOUT_MS,
                        sttResolution,
                    )
                    logTranscriptionMetrics(
                        recording = recording,
                        audioLoadMs = audioLoadMs,
                        audioSampleCount = audioFloats.size,
                        metrics = transcription.metrics,
                    )
                    if (transcription.text.isBlank()) {
                        Log.w(
                            TAG,
                            "Transcription timed out or returned empty text for recording ${recording.id}"
                        )
                        db.recordingDao().updateStatus(recording.id, STATUS_FAILED)
                        continue
                    }

                    db.transcriptDao().insert(
                        Transcript(
                            recordingId = recording.id,
                            text = transcription.text,
                            createdAt = System.currentTimeMillis()
                        )
                    )

                    if (canRunSummary) {
                        db.recordingDao().updateStatus(recording.id, STATUS_SUMMARIZING)
                        summaryCandidates += recording
                    } else {
                        db.recordingDao().updateStatus(recording.id, STATUS_TRANSCRIPT_READY)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Transcription failed for recording ${recording.id}", e)
                    db.recordingDao().updateStatus(recording.id, STATUS_FAILED)
                }
            }
        }

        if (canRunSummary) {
            summaryCandidates += pendingSummaries
        }

        if (summaryCandidates.isEmpty()) {
            cleanupExpiredRecordings(ctx, db)
            Log.i(TAG, "Processing complete without summary work")
            return Result.success()
        }

        val summaryActiveIds = mutableSetOf<Long>()
        val processedDates = linkedSetOf<String>()
        val existingSummaryIds = pendingSummaries.mapTo(mutableSetOf()) { it.id }
        val llmModel = readyLlmModel ?: selectedLlmModel

        setForeground(createForegroundInfo("Loading summary model"))
        Log.i(TAG, "Initializing summary engine")
        val summaryEngineInitStartedAt = SystemClock.elapsedRealtime()
        val summaryEngineHandle = SummaryEngineFactory.create(
            context = ctx,
            model = llmModel,
            resolution = llmResolution,
        )
        val summaryEngineInitMs = SystemClock.elapsedRealtime() - summaryEngineInitStartedAt
        Log.i(TAG, "Summary engine init took ${summaryEngineInitMs}ms")
        if (summaryEngineHandle == null) {
            Log.e(TAG, "Failed to initialize summary engine")
            resetStatuses(db, summaryCandidates.map { it.id }.toSet(), STATUS_TRANSCRIPT_READY)
            return Result.retry()
        }
        val summaryEngine = summaryEngineHandle.engine
        if (!summaryEngineHandle.fallbackReason.isNullOrBlank()) {
            Log.w(TAG, summaryEngineHandle.fallbackReason)
        }
        Log.i(
            TAG,
            "Summary engine initialized with model=${summaryEngineHandle.model.displayName}, " +
                "backend=${summaryEngineHandle.effectiveBackend.displayName}"
        )

        return try {
            try {
                for ((index, recording) in summaryCandidates.withIndex()) {
                    summaryActiveIds += recording.id
                    if (existingSummaryIds.contains(recording.id)) {
                        db.recordingDao().updateStatus(recording.id, STATUS_SUMMARIZING)
                    }

                    setForeground(
                        createForegroundInfo(
                            message = "Summarizing recording ${index + 1}/${summaryCandidates.size}",
                            current = index + 1,
                            total = summaryCandidates.size
                        )
                    )

                    val transcript = db.transcriptDao().getByRecordingId(recording.id)
                    val transcriptText = transcript?.text?.trim().orEmpty()
                    if (transcriptText.isBlank()) {
                        Log.w(TAG, "Missing transcript for recording ${recording.id}")
                        db.recordingDao().updateStatus(recording.id, STATUS_FAILED)
                        summaryActiveIds -= recording.id
                        continue
                    }

                    val summaryPrompt = SummaryPromptBuilder.recordingPrompt(transcriptText)
                    val summaryResult = summaryEngine.generate(
                        summaryPrompt,
                        SummaryEngineFactory.RECORDING_MAX_TOKENS,
                    )
                    logSummaryMetrics(
                        scope = "recording:${recording.id}",
                        promptLength = summaryPrompt.length,
                        metrics = summaryResult.metrics,
                    )
                    val summary = SummaryTextFormatter.normalize(summaryResult.text)
                    db.recordingDao().updateSummaryText(recording.id, summary)
                    processedDates += dateFmt.format(Date(recording.startedAt))
                }

                for ((index, date) in processedDates.withIndex()) {
                    setForeground(
                        createForegroundInfo(
                            message = "Summarizing ${index + 1}/${processedDates.size}",
                            current = index + 1,
                            total = processedDates.size
                        )
                    )
                    Log.i(TAG, "Summarizing date $date")

                    val recordingsForDate = db.recordingDao().getRecordingsByDate(date)
                    val recordingSummaries = recordingsForDate.mapNotNull { it.summaryText }
                    val transcripts = db.transcriptDao().getTranscriptsByDate(date)
                    if (transcripts.isEmpty()) {
                        continue
                    }

                    val prompt = SummaryPromptBuilder.dailyPrompt(
                        recordingSummaries = recordingSummaries,
                        fallbackTranscripts = transcripts.map { it.text },
                    )
                    val summaryResult = summaryEngine.generate(
                        prompt,
                        SummaryEngineFactory.DAILY_MAX_TOKENS,
                    )
                    logSummaryMetrics(
                        scope = "daily:$date",
                        promptLength = prompt.length,
                        metrics = summaryResult.metrics,
                    )
                    val summary = SummaryTextFormatter.normalize(summaryResult.text)

                    db.summaryDao().insertOrReplace(
                        Summary(
                            date = date,
                            summaryText = summary,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                for (recording in summaryCandidates) {
                    if (summaryActiveIds.contains(recording.id)) {
                        db.recordingDao().updateStatus(recording.id, STATUS_DONE)
                        summaryActiveIds -= recording.id
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Summarization failed", e)
                resetStatuses(db, summaryActiveIds, STATUS_TRANSCRIPT_READY)
                return Result.retry()
            }

            cleanupExpiredRecordings(ctx, db)
            Log.i(TAG, "Processing complete")
            Result.success()
        } finally {
            summaryEngine.close()
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

    private fun logWhisperWarmUpMetrics(metrics: EngineWarmUpMetrics) {
        Log.i(
            TAG,
            "Whisper warm-up: total=${metrics.totalMs}ms, modelLoad=${metrics.modelLoadMs}ms, " +
                "auxLoad=${metrics.auxiliaryLoadMs}ms, delegateInit=${metrics.delegateInitMs}ms, " +
                "requested=${metrics.requestedBackend.displayName}, " +
                "effective=${metrics.effectiveBackend.displayName}, reused=${metrics.reused}, " +
                "fallback=${metrics.fallbackReason ?: "none"}"
        )
    }

    private fun logTranscriptionMetrics(
        recording: Recording,
        audioLoadMs: Long,
        audioSampleCount: Int,
        metrics: TranscriptionMetrics,
    ) {
        Log.i(
            TAG,
            "Transcription metrics recording=${recording.id}: audioLoad=${audioLoadMs}ms, " +
                "samples=$audioSampleCount, preprocess=${metrics.preprocessingMs}ms, " +
                "infer=${metrics.inferenceMs}ms, decode=${metrics.decodeMs}ms, " +
                "total=${metrics.totalMs}ms, timedOut=${metrics.timedOut}, " +
                "requested=${metrics.requestedBackend.displayName}, " +
                "effective=${metrics.effectiveBackend.displayName}, " +
                "delegateInit=${metrics.delegateInitMs}ms, fallback=${metrics.fallbackReason ?: "none"}"
        )
    }

    private fun logSummaryMetrics(
        scope: String,
        promptLength: Int,
        metrics: SummaryMetrics,
    ) {
        Log.i(
            TAG,
            "Summary metrics $scope: promptChars=$promptLength, promptTokens=${metrics.promptTokenCount}, " +
                "generatedTokens=${metrics.generatedTokenCount}, ttft=${formatMs(metrics.timeToFirstTokenMs)}, " +
                "prefill=${formatMs(metrics.promptEvalMs)}, decode=${formatMs(metrics.decodeMs)}, " +
                "sampling=${formatMs(metrics.samplingMs)}, total=${metrics.totalMs}ms, " +
                "prefillTps=${formatRate(metrics.promptTokensPerSecond)}, " +
                "decodeTps=${formatRate(metrics.decodeTokensPerSecond)}"
        )
    }

    private fun logBackendResolution(label: String, resolution: BackendResolution) {
        val fallbackPart = if (resolution.fallbackReason.isNullOrBlank()) {
            ""
        } else {
            ", fallbackReason=${resolution.fallbackReason}"
        }
        Log.i(
            TAG,
            "$label backend requested=${resolution.requestedBackend.displayName}, " +
                "effective=${resolution.effectiveBackend.displayName}$fallbackPart"
        )
    }

    private fun formatMs(value: Double): String = String.format(Locale.US, "%.2fms", value)

    private fun formatRate(value: Double?): String {
        return if (value == null) {
            "n/a"
        } else {
            String.format(Locale.US, "%.2f", value)
        }
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
