package com.voicelog.jni

class LlamaJNI {
    external fun init(modelPath: String, contextSize: Int, threadCount: Int): Long
    external fun generate(ctxPtr: Long, prompt: String, maxNewTokens: Int): String
    external fun getLastPromptEvalMs(ctxPtr: Long): Double
    external fun getLastDecodeMs(ctxPtr: Long): Double
    external fun getLastSamplingMs(ctxPtr: Long): Double
    external fun getLastTimeToFirstTokenMs(ctxPtr: Long): Double
    external fun getLastPromptTokenCount(ctxPtr: Long): Int
    external fun getLastGeneratedTokenCount(ctxPtr: Long): Int
    external fun free(ctxPtr: Long)

    companion object {
        init { System.loadLibrary("voicelog") }
    }
}
