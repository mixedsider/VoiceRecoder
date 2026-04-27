package com.voicelog.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InferenceTelemetryTest {

    @Test
    fun summaryMetrics_computesPromptTokensPerSecond() {
        val metrics = SummaryMetrics(
            totalMs = 1200L,
            promptEvalMs = 200.0,
            decodeMs = 500.0,
            samplingMs = 10.0,
            timeToFirstTokenMs = 50.0,
            promptTokenCount = 100,
            generatedTokenCount = 20,
        )

        assertEquals(500.0, metrics.promptTokensPerSecond!!, 0.0001)
    }

    @Test
    fun summaryMetrics_computesDecodeTokensPerSecond() {
        val metrics = SummaryMetrics(
            totalMs = 1200L,
            promptEvalMs = 200.0,
            decodeMs = 400.0,
            samplingMs = 10.0,
            timeToFirstTokenMs = 50.0,
            promptTokenCount = 100,
            generatedTokenCount = 20,
        )

        assertEquals(50.0, metrics.decodeTokensPerSecond!!, 0.0001)
    }

    @Test
    fun summaryMetrics_returnsNullRatesWhenDurationOrTokenCountMissing() {
        val metrics = SummaryMetrics(
            totalMs = 100L,
            promptEvalMs = 0.0,
            decodeMs = 0.0,
            samplingMs = 0.0,
            timeToFirstTokenMs = 0.0,
            promptTokenCount = 0,
            generatedTokenCount = 0,
        )

        assertNull(metrics.promptTokensPerSecond)
        assertNull(metrics.decodeTokensPerSecond)
    }
}
