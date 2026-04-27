package com.voicelog.inference

import android.content.Context
import com.voicelog.R

object SummaryPromptBuilder {
    const val RECORDING_TRANSCRIPT_LIMIT = 4_000
    const val DAILY_TRANSCRIPT_LIMIT = 6_000

    fun recordingPrompt(
        transcript: String,
        outputLanguage: String = "Korean",
        context: Context? = null,
    ): String {
        val input = transcript.trim().take(RECORDING_TRANSCRIPT_LIMIT)
        return context?.getString(R.string.summary_prompt_recording, outputLanguage, input)
            ?: "Summarize this voice note concisely in $outputLanguage. Keep action items and facts.\n\n$input"
    }

    fun dailyPrompt(
        recordingSummaries: List<String>,
        fallbackTranscripts: List<String>,
        outputLanguage: String = "Korean",
        context: Context? = null,
    ): String {
        val summaries = recordingSummaries
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val input = if (summaries.isNotEmpty()) {
            summaries.joinToString("\n\n") { "- $it" }
        } else {
            fallbackTranscripts
                .joinToString("\n\n") { it.trim() }
                .take(DAILY_TRANSCRIPT_LIMIT)
        }

        return context?.getString(R.string.summary_prompt_daily, outputLanguage, input)
            ?: "Create a concise $outputLanguage daily summary from these voice note summaries. " +
                "Group related topics and keep important action items.\n\n$input"
    }
}
