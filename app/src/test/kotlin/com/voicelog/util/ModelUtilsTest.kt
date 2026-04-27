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
    fun additionalWhisperModelNames_areCorrect() {
        assertEquals("whisper-tiny-transcribe-translate.tflite", ModelUtils.WHISPER_TINY_MODEL_NAME)
        assertEquals("whisper-base-transcribe-translate.tflite", ModelUtils.WHISPER_BASE_MODEL_NAME)
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
    fun liteRtGemmaSpec_hasExpectedRuntimeMetadata() {
        val spec = ModelUtils.litertGemma3Spec
        assertEquals(ModelUtils.FAST_LLM_MODEL_ID, spec.id)
        assertEquals(EngineKind.LITERT_LM, spec.engineKind)
        assertTrue(spec.supportedBackends.contains(InferenceBackend.GPU))
        assertFalse(spec.supportedBackends.contains(InferenceBackend.NPU))
        assertTrue(spec.downloadUrl.contains("litert-community/Gemma3-1B-IT"))
    }

    @Test
    fun sttModelCatalog_exposesFastBalancedAndAccurateChoices() {
        val sttModels = ModelUtils.getSelectableModels(InferencePipeline.STT)
        assertEquals(
            listOf(
                ModelUtils.FAST_STT_MODEL_ID,
                ModelUtils.BALANCED_STT_MODEL_ID,
                ModelUtils.DEFAULT_STT_MODEL_ID,
            ),
            sttModels.map { it.id },
        )
        assertEquals(ModelUtils.whisperSpec, ModelUtils.getDefaultModel(InferencePipeline.STT))
    }

    @Test
    fun sttModelSpecs_shareMultilingualVocabAndBackends() {
        val sttModels = ModelUtils.getSelectableModels(InferencePipeline.STT)

        sttModels.forEach { spec ->
            assertEquals(EngineKind.WHISPER_TFLITE, spec.engineKind)
            assertEquals(listOf("whisper-multilingual-vocab"), spec.auxiliaryModelIds)
            assertTrue(spec.supportedBackends.contains(InferenceBackend.CPU))
            assertTrue(spec.supportedBackends.contains(InferenceBackend.GPU))
            assertTrue(spec.supportedBackends.contains(InferenceBackend.NPU))
            assertTrue(spec.downloadUrl.startsWith("https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/"))
        }
    }

    @Test
    fun expandMixedSttSelection_includesSharedVocabOnce() {
        val expanded = ModelUtils.expandModelSelection(
            listOf(
                ModelUtils.FAST_STT_MODEL_ID,
                ModelUtils.BALANCED_STT_MODEL_ID,
                ModelUtils.DEFAULT_STT_MODEL_ID,
            ),
        )

        assertEquals(
            listOf(
                ModelUtils.FAST_STT_MODEL_ID,
                "whisper-multilingual-vocab",
                ModelUtils.BALANCED_STT_MODEL_ID,
                ModelUtils.DEFAULT_STT_MODEL_ID,
            ),
            expanded.map { it.id },
        )
    }

    @Test
    fun gemma4LiteRtSpec_isDefaultGpuCapableLlmModel() {
        val spec = ModelUtils.litertGemma4E2bSpec
        assertEquals(ModelUtils.DEFAULT_LLM_MODEL_ID, spec.id)
        assertEquals(spec, ModelUtils.getDefaultModel(InferencePipeline.LLM))
        assertEquals(EngineKind.LITERT_LM, spec.engineKind)
        assertTrue(spec.supportedBackends.contains(InferenceBackend.GPU))
        assertFalse(spec.supportedBackends.contains(InferenceBackend.NPU))
        assertTrue(spec.downloadUrl.contains("litert-community/gemma-4-E2B-it-litert-lm"))
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
