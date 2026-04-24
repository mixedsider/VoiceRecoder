package com.voicelog.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.voicelog.db.AppDatabase
import com.voicelog.db.entity.Summary
import com.voicelog.db.entity.Transcript
import com.voicelog.jni.LlamaJNI
import com.voicelog.jni.WhisperJNI
import com.voicelog.util.ModelUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ProcessingWorker"
        private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val db = AppDatabase.getInstance(ctx)

        if (!ModelUtils.isWhisperModelReady(ctx) || !ModelUtils.isLlamaModelReady(ctx)) {
            Log.e(TAG, "Model files not ready")
            return Result.failure()
        }

        val pending = db.recordingDao().getPendingRecordings()
        if (pending.isEmpty()) {
            Log.i(TAG, "No pending recordings")
            return Result.success()
        }

        val whisper = WhisperJNI()
        val whisperCtx = whisper.init(ModelUtils.getWhisperModelPath(ctx))
        if (whisperCtx == 0L) {
            Log.e(TAG, "Failed to initialize Whisper")
            return Result.failure()
        }

        val processedDates = mutableSetOf<String>()

        try {
            for (recording in pending) {
                db.recordingDao().updateStatus(recording.id, "transcribing")
                val audioFloats = loadWavAsFloat(recording.filePath)
                if (audioFloats == null) {
                    Log.e(TAG, "Failed to load WAV: ${recording.filePath}")
                    db.recordingDao().updateStatus(recording.id, "pending")
                    continue
                }

                val text = whisper.transcribe(whisperCtx, audioFloats)
                db.transcriptDao().insert(
                    Transcript(
                        recordingId = recording.id,
                        text = text,
                        createdAt = System.currentTimeMillis()
                    )
                )
                db.recordingDao().updateStatus(recording.id, "summarizing")
                processedDates.add(dateFmt.format(Date(recording.startedAt)))
            }
        } finally {
            whisper.free(whisperCtx)
        }

        val llama = LlamaJNI()
        val llamaCtx = llama.init(ModelUtils.getLlamaModelPath(ctx))
        if (llamaCtx == 0L) {
            Log.e(TAG, "Failed to initialize LLaMA")
            return Result.failure()
        }

        try {
            for (date in processedDates) {
                val transcripts = db.transcriptDao().getTranscriptsByDate(date)
                if (transcripts.isEmpty()) continue

                val combined = transcripts.joinToString("\n\n") { it.text.trim() }
                val prompt = "다음 내용을 핵심만 한국어로 요약해줘:\n\n$combined"
                val summary = llama.generate(llamaCtx, prompt)

                db.summaryDao().insertOrReplace(
                    Summary(
                        date = date,
                        summaryText = summary,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            for (recording in pending) {
                db.recordingDao().updateStatus(recording.id, "done")
            }
        } finally {
            llama.free(llamaCtx)
        }

        val retentionDays = ctx.getSharedPreferences("voicelog_prefs", Context.MODE_PRIVATE)
            .getInt("retention_days", 30)
        val cutoffMs = System.currentTimeMillis() - retentionDays * 86_400_000L
        db.recordingDao().deleteExpiredRecordings(cutoffMs)

        Log.i(TAG, "Processing complete")
        return Result.success()
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
            Log.e(TAG, "Error loading WAV: ${e.message}")
            null
        }
    }
}
