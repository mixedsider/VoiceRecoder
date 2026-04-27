package com.voicelog.inference

import com.voicelog.util.BackendResolution
import com.voicelog.worker.WhisperRuntime

class WhisperTranscriptionEngine : TranscriptionEngine {
    override suspend fun warmUp(
        modelPath: String,
        vocabPath: String,
        backendResolution: BackendResolution,
    ): EngineWarmUpResult =
        WhisperRuntime.warmUp(modelPath, vocabPath, backendResolution)

    override suspend fun transcribe(
        modelPath: String,
        vocabPath: String,
        audioData: FloatArray,
        timeoutMs: Int,
        backendResolution: BackendResolution,
    ): TranscriptionResult = WhisperRuntime.transcribe(
        modelPath = modelPath,
        vocabPath = vocabPath,
        audioData = audioData,
        timeoutMs = timeoutMs,
        backendResolution = backendResolution,
    )
}
