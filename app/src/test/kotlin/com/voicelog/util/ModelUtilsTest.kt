package com.voicelog.util

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ModelUtilsTest {

    @Test
    fun nonExistentFile_isNotValid() {
        assertFalse(ModelUtils.isModelFileValid("/non/existent/path.bin", minSizeBytes = 1))
    }

    @Test
    fun emptyFile_isNotValid() {
        val f = File.createTempFile("model", ".bin")
        assertFalse(ModelUtils.isModelFileValid(f.absolutePath, minSizeBytes = 100))
        f.delete()
    }

    @Test
    fun fileAboveMinSize_isValid() {
        val f = File.createTempFile("model", ".bin")
        f.writeBytes(ByteArray(200) { 1 })
        assertTrue(ModelUtils.isModelFileValid(f.absolutePath, minSizeBytes = 100))
        f.delete()
    }

    @Test
    fun fileExactlyMinSize_isValid() {
        val f = File.createTempFile("model", ".bin")
        f.writeBytes(ByteArray(100) { 1 })
        assertTrue(ModelUtils.isModelFileValid(f.absolutePath, minSizeBytes = 100))
        f.delete()
    }

    @Test
    fun fileBelowMinSize_isNotValid() {
        val f = File.createTempFile("model", ".bin")
        f.writeBytes(ByteArray(50) { 1 })
        assertFalse(ModelUtils.isModelFileValid(f.absolutePath, minSizeBytes = 100))
        f.delete()
    }

    @Test
    fun whisperModelName_isCorrect() {
        assertEquals("whisper-small-transcribe-translate.tflite", ModelUtils.WHISPER_MODEL_NAME)
    }

    @Test
    fun whisperVocabName_isCorrect() {
        assertEquals("filters_vocab_multilingual.bin", ModelUtils.WHISPER_VOCAB_NAME)
    }

    @Test
    fun llamaModelName_isCorrect() {
        assertEquals("gemma-4-e2b.gguf", ModelUtils.LLAMA_MODEL_NAME)
    }

    @Test
    fun defaultMinSize_isOne() {
        val f = File.createTempFile("model", ".bin")
        f.writeBytes(ByteArray(1) { 1 })
        assertTrue(ModelUtils.isModelFileValid(f.absolutePath))
        f.delete()
    }

    @Test
    fun ggufWithoutHeader_isNotValid() {
        val f = File.createTempFile("model", ".gguf")
        f.writeBytes(ByteArray(200) { 0 })
        assertFalse(ModelUtils.isModelFileValid(f.absolutePath, minSizeBytes = 100))
        f.delete()
    }

    @Test
    fun ggufWithHeader_isValid() {
        val f = File.createTempFile("model", ".gguf")
        f.writeBytes(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()) + ByteArray(196) { 1 })
        assertTrue(ModelUtils.isModelFileValid(f.absolutePath, minSizeBytes = 100))
        f.delete()
    }
}
