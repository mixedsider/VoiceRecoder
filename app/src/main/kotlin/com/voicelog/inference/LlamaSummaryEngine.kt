package com.voicelog.inference

import android.os.SystemClock
import com.voicelog.jni.LlamaJNI

class LlamaSummaryEngine private constructor(
    private val llama: LlamaJNI,
    private val ctxPtr: Long,
) : SummaryEngine {

    override fun generate(prompt: String, maxNewTokens: Int): SummaryResult {
        val startedAt = SystemClock.elapsedRealtime()
        val text = llama.generate(ctxPtr, prompt, maxNewTokens)
        val totalMs = SystemClock.elapsedRealtime() - startedAt
        return SummaryResult(
            text = text,
            metrics = SummaryMetrics(
                totalMs = totalMs,
                promptEvalMs = llama.getLastPromptEvalMs(ctxPtr),
                decodeMs = llama.getLastDecodeMs(ctxPtr),
                samplingMs = llama.getLastSamplingMs(ctxPtr),
                timeToFirstTokenMs = llama.getLastTimeToFirstTokenMs(ctxPtr),
                promptTokenCount = llama.getLastPromptTokenCount(ctxPtr),
                generatedTokenCount = llama.getLastGeneratedTokenCount(ctxPtr),
            ),
        )
    }

    override fun close() {
        llama.free(ctxPtr)
    }

    companion object {
        fun create(
            modelPath: String,
            contextSize: Int = 2048,
            threadCount: Int = autoThreadCount(),
        ): LlamaSummaryEngine? {
            val llama = LlamaJNI()
            val ctxPtr = llama.init(modelPath, contextSize, threadCount)
            return if (ctxPtr == 0L) {
                null
            } else {
                LlamaSummaryEngine(llama, ctxPtr)
            }
        }

        private fun autoThreadCount(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return cores.coerceIn(1, 4)
        }
    }
}
