package com.voicelog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceRuntimeTest {

    @Test
    fun modelSpecToString_returnsDisplayName() {
        val spec = ModelUtils.whisperSpec
        assertEquals(spec.displayName, spec.toString())
    }

    @Test
    fun expandModelSelection_includesAuxiliaryModelsOnce() {
        val expanded = ModelUtils.expandModelSelection(
            listOf(ModelUtils.DEFAULT_STT_MODEL_ID, ModelUtils.DEFAULT_STT_MODEL_ID)
        )

        assertEquals(listOf("whisper-small-tflite", "whisper-multilingual-vocab"), expanded.map { it.id })
    }

    @Test
    fun selectableModels_excludeAuxiliaryEntries() {
        val sttModels = ModelUtils.getSelectableModels(InferencePipeline.STT)
        assertTrue(sttModels.any { it.id == ModelUtils.FAST_STT_MODEL_ID })
        assertTrue(sttModels.any { it.id == ModelUtils.BALANCED_STT_MODEL_ID })
        assertTrue(sttModels.any { it.id == ModelUtils.DEFAULT_STT_MODEL_ID })
        assertFalse(sttModels.any { it.id == "whisper-multilingual-vocab" })
    }

    @Test
    fun ggufModel_npuFallsBackToCpu() {
        val resolution = InferenceRuntime.resolveForTest(
            pipeline = InferencePipeline.LLM,
            model = ModelUtils.llamaSpec,
            requestedBackend = InferenceBackend.NPU,
            sdkInt = 35,
        )

        assertEquals(EngineKind.LLAMA_CPP_GGUF, resolution.engineKind)
        assertEquals(InferenceBackend.CPU, resolution.effectiveBackend)
        assertTrue(resolution.fallbackReason!!.contains("GGUF"))
    }

    @Test
    fun liteRtModel_autoPrefersGpu() {
        val resolution = InferenceRuntime.resolveForTest(
            pipeline = InferencePipeline.LLM,
            model = ModelUtils.litertGemma4E2bSpec,
            requestedBackend = InferenceBackend.AUTO,
            sdkInt = 35,
        )

        assertEquals(EngineKind.LITERT_LM, resolution.engineKind)
        assertEquals(InferenceBackend.GPU, resolution.effectiveBackend)
    }

    @Test
    fun liteRtModel_npuFallsBackToGpuWhenModelDoesNotSupportNpu() {
        val resolution = InferenceRuntime.resolveForTest(
            pipeline = InferencePipeline.LLM,
            model = ModelUtils.litertGemma4E2bSpec,
            requestedBackend = InferenceBackend.NPU,
            sdkInt = 35,
        )

        assertEquals(InferenceBackend.GPU, resolution.effectiveBackend)
        assertTrue(resolution.fallbackReason!!.contains("NPU-compatible"))
    }

    @Test
    fun sttNnapiRequiresAndroidOreoMr1() {
        val resolution = InferenceRuntime.resolveForTest(
            pipeline = InferencePipeline.STT,
            model = ModelUtils.whisperSpec,
            requestedBackend = InferenceBackend.NPU,
            sdkInt = 26,
        )

        assertEquals(InferenceBackend.GPU, resolution.effectiveBackend)
        assertTrue(resolution.fallbackReason!!.contains("Android 8.1"))
    }
}
