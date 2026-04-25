package com.voicelog.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryTextFormatterTest {

    @Test
    fun normalize_prefersConciseSummarySection() {
        val raw = """
            원문 일부

            **요약:**
            긴 설명입니다.

            **간결한 요약:**
            인사와 안부를 주고받는 대화.
        """.trimIndent()

        assertEquals(
            "인사와 안부를 주고받는 대화.",
            SummaryTextFormatter.normalize(raw)
        )
    }

    @Test
    fun normalize_returnsOriginalWhenNoMarkerExists() {
        val raw = "짧은 요약 문장"
        assertEquals(raw, SummaryTextFormatter.normalize(raw))
    }
}
