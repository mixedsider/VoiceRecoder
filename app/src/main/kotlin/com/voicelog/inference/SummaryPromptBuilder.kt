package com.voicelog.inference

object SummaryPromptBuilder {
    const val RECORDING_TRANSCRIPT_LIMIT = 4_000
    const val DAILY_TRANSCRIPT_LIMIT = 6_000

    fun recordingPrompt(transcript: String): String {
        val input = transcript.trim().take(RECORDING_TRANSCRIPT_LIMIT)
        return "Summarize this voice note in concise Korean. Keep action items and facts.\n\n$input"
    }

    fun dailyPrompt(
        recordingSummaries: List<String>,
        fallbackTranscripts: List<String>,
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

        return "Create a concise Korean daily summary from these voice note summaries. " +
            "Group related topics and keep important action items.\n\n$input"
    }
}
