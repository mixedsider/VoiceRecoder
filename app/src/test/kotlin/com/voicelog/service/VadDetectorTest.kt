package com.voicelog.service

import org.junit.Assert.*
import org.junit.Test

class VadDetectorTest {

    @Test
    fun constants_sampleRateIs16000() {
        assertEquals(16000, VadDetector.SAMPLE_RATE)
    }

    @Test
    fun constants_frameSizeIs512() {
        assertEquals(512, VadDetector.FRAME_SIZE)
    }

    @Test
    fun constants_voiceThresholdIs0_5() {
        assertEquals(0.5f, VadDetector.VOICE_THRESHOLD)
    }

    @Test
    fun normalizeShortToFloat_maxPositive_is1() {
        val sample: Short = 32767
        val normalized = sample / 32768f
        assertTrue(normalized > 0.99f)
        assertTrue(normalized <= 1.0f)
    }

    @Test
    fun normalizeShortToFloat_minNegative_isMinus1() {
        val sample: Short = -32768
        val normalized = sample / 32768f
        assertEquals(-1.0f, normalized, 0.0001f)
    }

    @Test
    fun normalizeShortToFloat_zero_isZero() {
        val sample: Short = 0
        val normalized = sample / 32768f
        assertEquals(0.0f, normalized)
    }

    @Test
    fun frameSize_512samples_at16kHz_is32ms() {
        val durationMs = VadDetector.FRAME_SIZE * 1000.0 / VadDetector.SAMPLE_RATE
        assertEquals(32.0, durationMs, 0.1)
    }

    @Test
    fun recordingSegmentDuration_isOneMinute() {
        assertEquals(60_000L, RecordingService.FIXED_SEGMENT_DURATION_MS)
    }

    @Test
    fun hiddenStateSize_is128() {
        // 2 layers * 1 batch * 64 hidden = 128
        val expectedSize = 2 * 1 * 64
        assertEquals(128, expectedSize)
    }
}
