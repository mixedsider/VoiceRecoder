package com.voicelog.worker

import android.util.Log
import android.os.SystemClock
import com.voicelog.inference.EngineWarmUpMetrics
import com.voicelog.inference.EngineWarmUpResult
import com.voicelog.inference.TranscriptionMetrics
import com.voicelog.inference.TranscriptionResult
import com.voicelog.stt.WhisperTfliteEngine
import com.voicelog.util.BackendResolution
import com.voicelog.util.InferenceBackend
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object WhisperRuntime {

    private const val TAG = "WhisperRuntime"

    private val mutex = Mutex()

    private var engine: WhisperTfliteEngine? = null
    private var loadedModelPath: String? = null
    private var loadedVocabPath: String? = null
    private var loadedBackend: InferenceBackend? = null
    private var executor = Executors.newSingleThreadExecutor()

    suspend fun warmUp(
        modelPath: String,
        vocabPath: String,
        backendResolution: BackendResolution,
    ): EngineWarmUpResult = mutex.withLock {
        val startedAt = SystemClock.elapsedRealtime()
        val existingEngine = engine
        val reused = existingEngine != null &&
            loadedModelPath == modelPath &&
            loadedVocabPath == vocabPath &&
            loadedBackend == backendResolution.effectiveBackend
        val whisperEngine = ensureEngineLocked(modelPath, vocabPath, backendResolution.effectiveBackend)
        EngineWarmUpResult(
            success = whisperEngine != null,
            metrics = if (whisperEngine == null) {
                EngineWarmUpMetrics(
                    totalMs = SystemClock.elapsedRealtime() - startedAt,
                    modelLoadMs = 0L,
                    auxiliaryLoadMs = 0L,
                    reused = false,
                )
            } else {
                EngineWarmUpMetrics(
                    totalMs = SystemClock.elapsedRealtime() - startedAt,
                    modelLoadMs = whisperEngine.lastModelLoadMs,
                    auxiliaryLoadMs = whisperEngine.lastVocabLoadMs,
                    reused = reused,
                    requestedBackend = backendResolution.requestedBackend,
                    effectiveBackend = whisperEngine.effectiveBackend ?: backendResolution.effectiveBackend,
                    delegateInitMs = whisperEngine.lastDelegateInitMs,
                    fallbackReason = whisperEngine.backendFallbackReason ?: backendResolution.fallbackReason,
                )
            },
        )
    }

    suspend fun transcribe(
        modelPath: String,
        vocabPath: String,
        audioData: FloatArray,
        timeoutMs: Int,
        backendResolution: BackendResolution,
    ): TranscriptionResult = mutex.withLock {
        val whisperEngine = ensureEngineLocked(modelPath, vocabPath, backendResolution.effectiveBackend)
            ?: return TranscriptionResult(
                text = "",
                metrics = TranscriptionMetrics(
                    totalMs = 0L,
                    preprocessingMs = 0L,
                    inferenceMs = 0L,
                    decodeMs = 0L,
                    timedOut = false,
                    requestedBackend = backendResolution.requestedBackend,
                    effectiveBackend = InferenceBackend.CPU,
                    fallbackReason = backendResolution.fallbackReason,
                ),
            )
        val startedAt = SystemClock.elapsedRealtime()
        val future = executor.submit<String> { whisperEngine.transcribe(audioData) }

        try {
            val text = future.get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            TranscriptionResult(
                text = text,
                metrics = TranscriptionMetrics(
                    totalMs = SystemClock.elapsedRealtime() - startedAt,
                    preprocessingMs = whisperEngine.lastPreprocessingMs,
                    inferenceMs = whisperEngine.lastInferenceMs,
                    decodeMs = whisperEngine.lastDecodeMs,
                    timedOut = false,
                    requestedBackend = backendResolution.requestedBackend,
                    effectiveBackend = whisperEngine.effectiveBackend ?: backendResolution.effectiveBackend,
                    delegateInitMs = whisperEngine.lastDelegateInitMs,
                    fallbackReason = whisperEngine.backendFallbackReason ?: backendResolution.fallbackReason,
                ),
            )
        } catch (e: TimeoutException) {
            Log.w(TAG, "Whisper transcription timed out after ${timeoutMs}ms")
            future.cancel(true)
            releaseLocked()
            TranscriptionResult(
                text = "",
                metrics = TranscriptionMetrics(
                    totalMs = SystemClock.elapsedRealtime() - startedAt,
                    preprocessingMs = 0L,
                    inferenceMs = 0L,
                    decodeMs = 0L,
                    timedOut = true,
                    requestedBackend = backendResolution.requestedBackend,
                    effectiveBackend = backendResolution.effectiveBackend,
                    fallbackReason = backendResolution.fallbackReason,
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Whisper transcription failed", e)
            future.cancel(true)
            releaseLocked()
            TranscriptionResult(
                text = "",
                metrics = TranscriptionMetrics(
                    totalMs = SystemClock.elapsedRealtime() - startedAt,
                    preprocessingMs = 0L,
                    inferenceMs = 0L,
                    decodeMs = 0L,
                    timedOut = false,
                    requestedBackend = backendResolution.requestedBackend,
                    effectiveBackend = backendResolution.effectiveBackend,
                    fallbackReason = backendResolution.fallbackReason,
                ),
            )
        }
    }

    suspend fun reset() = mutex.withLock {
        releaseLocked()
    }

    private fun ensureEngineLocked(
        modelPath: String,
        vocabPath: String,
        backend: InferenceBackend,
    ): WhisperTfliteEngine? {
        if (
            engine != null &&
            loadedModelPath == modelPath &&
            loadedVocabPath == vocabPath &&
            loadedBackend == backend
        ) {
            Log.i(TAG, "Reusing Whisper context")
            return engine
        }

        releaseLocked()

        Log.i(TAG, "Loading Whisper context")
        val newEngine = WhisperTfliteEngine()
        val ready = try {
            newEngine.initialize(modelPath, vocabPath, true, backend)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Whisper context", e)
            false
        }

        if (!ready) {
            Log.e(TAG, "Failed to load Whisper context")
            newEngine.close()
            return null
        }

        engine = newEngine
        loadedModelPath = modelPath
        loadedVocabPath = vocabPath
        loadedBackend = backend
        Log.i(TAG, "Whisper context ready")
        return engine
    }

    private fun releaseLocked() {
        engine?.close()
        engine = null
        if (!executor.isShutdown) {
            executor.shutdownNow()
        }
        executor = Executors.newSingleThreadExecutor()
        loadedModelPath = null
        loadedVocabPath = null
        loadedBackend = null
    }
}
