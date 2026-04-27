package com.voicelog.inference

import com.voicelog.util.BackendResolution

interface TranscriptionEngine {
    suspend fun warmUp(
        modelPath: String,
        vocabPath: String,
        backendResolution: BackendResolution,
    ): EngineWarmUpResult

    suspend fun transcribe(
        modelPath: String,
        vocabPath: String,
        audioData: FloatArray,
        timeoutMs: Int,
        backendResolution: BackendResolution,
    ): TranscriptionResult
}
