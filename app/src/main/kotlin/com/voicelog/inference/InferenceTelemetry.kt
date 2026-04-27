package com.voicelog.inference

import com.voicelog.util.InferenceBackend

data class EngineWarmUpMetrics(
    val totalMs: Long,
    val modelLoadMs: Long,
    val auxiliaryLoadMs: Long,
    val reused: Boolean,
    val requestedBackend: InferenceBackend = InferenceBackend.CPU,
    val effectiveBackend: InferenceBackend = InferenceBackend.CPU,
    val delegateInitMs: Long = 0L,
    val fallbackReason: String? = null,
)

data class EngineWarmUpResult(
    val success: Boolean,
    val metrics: EngineWarmUpMetrics,
)

data class TranscriptionMetrics(
    val totalMs: Long,
    val preprocessingMs: Long,
    val inferenceMs: Long,
    val decodeMs: Long,
    val timedOut: Boolean,
    val requestedBackend: InferenceBackend = InferenceBackend.CPU,
    val effectiveBackend: InferenceBackend = InferenceBackend.CPU,
    val delegateInitMs: Long = 0L,
    val fallbackReason: String? = null,
)

data class TranscriptionResult(
    val text: String,
    val metrics: TranscriptionMetrics,
)

data class SummaryMetrics(
    val totalMs: Long,
    val promptEvalMs: Double,
    val decodeMs: Double,
    val samplingMs: Double,
    val timeToFirstTokenMs: Double,
    val promptTokenCount: Int,
    val generatedTokenCount: Int,
) {
    val promptTokensPerSecond: Double?
        get() = if (promptEvalMs > 0.0 && promptTokenCount > 0) {
            promptTokenCount / (promptEvalMs / 1000.0)
        } else {
            null
        }

    val decodeTokensPerSecond: Double?
        get() = if (decodeMs > 0.0 && generatedTokenCount > 0) {
            generatedTokenCount / (decodeMs / 1000.0)
        } else {
            null
        }
}

data class SummaryResult(
    val text: String,
    val metrics: SummaryMetrics,
)
