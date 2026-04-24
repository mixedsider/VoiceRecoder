package com.voicelog.worker

import com.voicelog.util.AudioUtils
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProcessingWorkerTest {

    // loadWavAsFloat 로직을 테스트 클래스 내부에 복사해서 순수 JVM 테스트로 진행
    private fun loadWavAsFloat(filePath: String): FloatArray? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            if (bytes.size <= 44) return null
            val pcmOffset = 44
            val numSamples = (bytes.size - pcmOffset) / 2
            FloatArray(numSamples) { i ->
                val lo = bytes[pcmOffset + i * 2].toInt() and 0xFF
                val hi = bytes[pcmOffset + i * 2 + 1].toInt()
                ((hi shl 8) or lo).toShort() / 32768f
            }
        } catch (e: Exception) {
            null
        }
    }

    @Test
    fun loadWavAsFloat_nonExistentFile_returnsNull() {
        assertNull(loadWavAsFloat("/non/existent/file.wav"))
    }

    @Test
    fun loadWavAsFloat_emptyFile_returnsNull() {
        val f = File.createTempFile("test", ".wav")
        assertNull(loadWavAsFloat(f.absolutePath))
        f.delete()
    }

    @Test
    fun loadWavAsFloat_validWavFile_returnsFloatArray() {
        val f = File.createTempFile("test", ".wav")
        val pcm = shortArrayOf(0, 16384, -16384, 32767)
        AudioUtils.writeWavFile(f.absolutePath, pcm, 16000)

        val result = loadWavAsFloat(f.absolutePath)
        assertNotNull(result)
        assertEquals(4, result!!.size)
        f.delete()
    }

    @Test
    fun loadWavAsFloat_zeroPcm_returnsZeroFloats() {
        val f = File.createTempFile("test", ".wav")
        AudioUtils.writeWavFile(f.absolutePath, ShortArray(4) { 0 }, 16000)

        val result = loadWavAsFloat(f.absolutePath)
        assertNotNull(result)
        result!!.forEach { assertEquals(0.0f, it) }
        f.delete()
    }

    @Test
    fun loadWavAsFloat_maxPositiveSample_isNear1() {
        val f = File.createTempFile("test", ".wav")
        AudioUtils.writeWavFile(f.absolutePath, shortArrayOf(32767), 16000)

        val result = loadWavAsFloat(f.absolutePath)
        assertNotNull(result)
        assertTrue(result!![0] > 0.99f)
        f.delete()
    }

    @Test
    fun loadWavAsFloat_maxNegativeSample_isMinus1() {
        val f = File.createTempFile("test", ".wav")
        AudioUtils.writeWavFile(f.absolutePath, shortArrayOf(-32768), 16000)

        val result = loadWavAsFloat(f.absolutePath)
        assertNotNull(result)
        assertEquals(-1.0f, result!![0], 0.0001f)
        f.delete()
    }

    @Test
    fun loadWavAsFloat_headerOnly_returnsNull() {
        val f = File.createTempFile("test", ".wav")
        // 44바이트 이하 파일
        f.writeBytes(ByteArray(44) { 0 })
        assertNull(loadWavAsFloat(f.absolutePath))
        f.delete()
    }

    @Test
    fun dateFmt_formatsCorrectly() {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA)
        val result = fmt.format(java.util.Date(1745500800000L))
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }
}
