package com.voicelog.worker

import android.util.Log
import com.voicelog.stt.WhisperTfliteEngine
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
    private var executor = Executors.newSingleThreadExecutor()

    suspend fun warmUp(modelPath: String, vocabPath: String): Boolean = mutex.withLock {
        ensureEngineLocked(modelPath, vocabPath) != null
    }

    suspend fun transcribe(
        modelPath: String,
        vocabPath: String,
        audioData: FloatArray,
        timeoutMs: Int
    ): String = mutex.withLock {
        val whisperEngine = ensureEngineLocked(modelPath, vocabPath) ?: return ""
        val future = executor.submit<String> { whisperEngine.transcribe(audioData) }

        try {
            future.get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            Log.w(TAG, "Whisper transcription timed out after ${timeoutMs}ms")
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Whisper transcription failed", e)
            future.cancel(true)
            releaseLocked()
            ""
        }
    }

    suspend fun reset() = mutex.withLock {
        releaseLocked()
    }

    private fun ensureEngineLocked(
        modelPath: String,
        vocabPath: String
    ): WhisperTfliteEngine? {
        if (engine != null && loadedModelPath == modelPath && loadedVocabPath == vocabPath) {
            Log.i(TAG, "Reusing Whisper context")
            return engine
        }

        releaseLocked()

        Log.i(TAG, "Loading Whisper context")
        val newEngine = WhisperTfliteEngine()
        val ready = try {
            newEngine.initialize(modelPath, vocabPath, true)
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
    }
}
