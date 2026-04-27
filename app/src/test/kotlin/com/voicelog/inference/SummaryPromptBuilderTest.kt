package com.voicelog.inference

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryPromptBuilderTest {

    @Test
    fun recordingPrompt_limitsTranscriptToFourThousandChars() {
        val prompt = SummaryPromptBuilder.recordingPrompt("a".repeat(5_000))

        assertTrue(prompt.length < 4_300)
        assertFalse(prompt.contains("a".repeat(4_500)))
    }

    @Test
    fun dailyPrompt_prefersRecordingSummaries() {
        val prompt = SummaryPromptBuilder.dailyPrompt(
            recordingSummaries = listOf("summary one", "summary two"),
            fallbackTranscripts = listOf("raw transcript should not be used"),
        )

        assertTrue(prompt.contains("summary one"))
        assertFalse(prompt.contains("raw transcript"))
    }

    @Test
    fun dailyPrompt_limitsFallbackTranscriptsToSixThousandChars() {
        val prompt = SummaryPromptBuilder.dailyPrompt(
            recordingSummaries = emptyList(),
            fallbackTranscripts = listOf("b".repeat(7_000)),
        )

        assertTrue(prompt.length < 6_300)
        assertFalse(prompt.contains("b".repeat(6_500)))
    }
}
