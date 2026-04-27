package com.voicelog.inference

interface SummaryEngine : AutoCloseable {
    fun generate(prompt: String, maxNewTokens: Int = 512): SummaryResult
}
