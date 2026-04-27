package com.voicelog.util

object SummaryTextFormatter {

    private val preferredMarkers = listOf(
        "간결한 요약:",
        "Korean Summary:",
        "요약:"
    )

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }

        preferredMarkers.forEach { marker ->
            extractSection(trimmed, marker)?.let { return it }
        }

        return trimmed
    }

    fun createFallbackRecordingSummary(transcript: String): String {
        val normalized = transcript
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()

        if (normalized.isEmpty()) {
            return ""
        }

        val sentence = Regex("""^(.+?[.!?。]|.+?$)""").find(normalized)?.value?.trim().orEmpty()
        if (sentence.isNotEmpty()) {
            return sentence
        }

        return normalized.take(120).trimEnd()
    }

    private fun extractSection(text: String, marker: String): String? {
        val startIndex = listOf(
            marker,
            "**$marker**",
            "**$marker"
        ).map { candidate ->
            text.indexOf(candidate, ignoreCase = true)
                .takeIf { it >= 0 }
                ?.let { it to candidate.length }
        }.filterNotNull().minByOrNull { it.first } ?: return null

        val section = text.substring(startIndex.first + startIndex.second)
            .replaceFirst(Regex("""^\*+\s*"""), "")
            .lineSequence()
            .map { it.trim() }
            .dropWhile { it.isBlank() }
            .takeWhile { line ->
                line.isNotBlank() &&
                    !line.startsWith("**") &&
                    !line.startsWith("---") &&
                    !line.startsWith("***")
            }
            .joinToString(" ")
            .trim()

        return section.ifBlank { null }
    }
}
