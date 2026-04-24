package com.voicelog.util

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioUtilsTest {

    @Test
    fun writeWavFile_hasRiffHeader() {
        val f = File.createTempFile("test", ".wav")
        val pcm = ShortArray(16000) { (it % 100).toShort() }
        AudioUtils.writeWavFile(f.absolutePath, pcm, 16000)

        val bytes = f.readBytes()
        val header = bytes.slice(0..3).map { it.toInt().toChar() }.joinToString("")
        assertEquals("RIFF", header)
        f.delete()
    }

    @Test
    fun writeWavFile_hasWaveFormat() {
        val f = File.createTempFile("test", ".wav")
        AudioUtils.writeWavFile(f.absolutePath, ShortArray(100), 16000)

        val bytes = f.readBytes()
        val wave = bytes.slice(8..11).map { it.toInt().toChar() }.joinToString("")
        assertEquals("WAVE", wave)
        f.delete()
    }

    @Test
    fun writeWavFile_correctSampleRate() {
        val f = File.createTempFile("test", ".wav")
        AudioUtils.writeWavFile(f.absolutePath, ShortArray(100), 16000)

        val bytes = f.readBytes()
        val sampleRate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(16000, sampleRate)
        f.delete()
    }

    @Test
    fun writeWavFile_correctDataSize() {
        val f = File.createTempFile("test", ".wav")
        val numSamples = 1600
        AudioUtils.writeWavFile(f.absolutePath, ShortArray(numSamples), 16000)

        val bytes = f.readBytes()
        val dataSize = ByteBuffer.wrap(bytes, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(numSamples * 2, dataSize)
        f.delete()
    }

    @Test
    fun writeWavFile_totalFileSizeCorrect() {
        val f = File.createTempFile("test", ".wav")
        val numSamples = 800
        AudioUtils.writeWavFile(f.absolutePath, ShortArray(numSamples), 16000)

        // 파일 크기 = 44(헤더) + numSamples*2(데이터)
        assertEquals((44 + numSamples * 2).toLong(), f.length())
        f.delete()
    }

    @Test
    fun writeWavFile_emptySamples_writesHeaderOnly() {
        val f = File.createTempFile("test", ".wav")
        AudioUtils.writeWavFile(f.absolutePath, ShortArray(0), 16000)
        assertEquals(44L, f.length())
        f.delete()
    }

    @Test
    fun writeWavFile_pcmDataWrittenCorrectly() {
        val f = File.createTempFile("test", ".wav")
        val pcm = shortArrayOf(100, -100, 32767, -32768)
        AudioUtils.writeWavFile(f.absolutePath, pcm, 16000)

        val bytes = f.readBytes()
        val buf = ByteBuffer.wrap(bytes, 44, 8).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(100.toShort(), buf.short)
        assertEquals((-100).toShort(), buf.short)
        assertEquals(32767.toShort(), buf.short)
        assertEquals((-32768).toShort(), buf.short)
        f.delete()
    }

    @Test
    fun formatTimestamp_lengthIs15() {
        val result = AudioUtils.formatTimestamp(1745500800000L)
        assertEquals(15, result.length) // yyyyMMdd_HHmmss
    }

    @Test
    fun formatTimestamp_containsUnderscore() {
        val result = AudioUtils.formatTimestamp(1745500800000L)
        assertTrue(result.contains("_"))
    }

    @Test
    fun formatTimestamp_differentTimestamps_differentStrings() {
        val a = AudioUtils.formatTimestamp(1745500800000L)
        val b = AudioUtils.formatTimestamp(1745500800000L + 60000L)
        assertNotEquals(a, b)
    }
}
