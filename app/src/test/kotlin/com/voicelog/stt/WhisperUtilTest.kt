package com.voicelog.stt

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class WhisperUtilTest {

    @Test
    fun decodePieces_restoresKoreanUtf8Text() {
        val util = WhisperUtil()
        val firstPiece = String(
            byteArrayOf(0xEA.toByte(), 0xB0.toByte(), 0x9C.toByte()),
            StandardCharsets.ISO_8859_1
        )
        val secondPiece = String(
            byteArrayOf(0xEB.toByte(), 0xB0.toByte(), 0x9C.toByte()),
            StandardCharsets.ISO_8859_1
        )

        val decoded = util.decodePieces(listOf(firstPiece, secondPiece))

        assertEquals("개발", decoded)
    }
}
