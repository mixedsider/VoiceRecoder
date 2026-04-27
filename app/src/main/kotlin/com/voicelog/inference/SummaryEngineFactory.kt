package com.voicelog.inference

import android.content.Context
import android.util.Log
import com.voicelog.util.BackendResolution
import com.voicelog.util.EngineKind
import com.voicelog.util.InferenceBackend
import com.voicelog.util.ModelSpec
import com.voicelog.util.ModelUtils

data class SummaryEngineHandle(
    val engine: SummaryEngine,
    val model: ModelSpec,
    val effectiveBackend: InferenceBackend,
    val fallbackReason: String?,
)

object SummaryEngineFactory {

    private const val TAG = "SummaryEngineFactory"
    const val DEFAULT_CONTEXT_SIZE = 2048
    const val RECORDING_MAX_TOKENS = 160
    const val DAILY_MAX_TOKENS = 240

    fun create(
        context: Context,
        model: ModelSpec,
        resolution: BackendResolution,
    ): SummaryEngineHandle? {
        return when (model.engineKind) {
            EngineKind.LLAMA_CPP_GGUF -> createLlama(context, model)
            EngineKind.LITERT_LM -> createLiteRtWithFallback(context, model, resolution)
            EngineKind.WHISPER_TFLITE -> null
        }
    }

    private fun createLlama(context: Context, model: ModelSpec): SummaryEngineHandle? {
        val engine = LlamaSummaryEngine.create(
            modelPath = ModelUtils.getModelFile(context, model).absolutePath,
            contextSize = DEFAULT_CONTEXT_SIZE,
        ) ?: return null
        return SummaryEngineHandle(
            engine = engine,
            model = model,
            effectiveBackend = InferenceBackend.CPU,
            fallbackReason = null,
        )
    }

    private fun createLiteRtWithFallback(
        context: Context,
        model: ModelSpec,
        resolution: BackendResolution,
    ): SummaryEngineHandle? {
        val modelPath = ModelUtils.getModelFile(context, model).absolutePath
        val preferred = resolution.effectiveBackend
        val fallbackOrder = listOf(preferred, InferenceBackend.GPU, InferenceBackend.CPU)
            .distinct()
            .filter { model.supportedBackends.contains(it) }

        for (backend in fallbackOrder) {
            try {
                val engine = LiteRtLmSummaryEngine.create(
                    context = context,
                    modelPath = modelPath,
                    backend = backend,
                    maxNumTokens = DAILY_MAX_TOKENS,
                )
                return SummaryEngineHandle(
                    engine = engine,
                    model = model,
                    effectiveBackend = backend,
                    fallbackReason = if (backend == preferred) {
                        resolution.fallbackReason
                    } else {
                        "LiteRT-LM ${preferred.displayName} initialization failed; using ${backend.displayName}."
                    },
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize LiteRT-LM with ${backend.displayName}", e)
            }
        }

        val fallbackModel = ModelUtils.llamaSpec
        if (!ModelUtils.isModelReady(context, fallbackModel)) {
            return null
        }
        val llama = LlamaSummaryEngine.create(
            modelPath = ModelUtils.getModelFile(context, fallbackModel).absolutePath,
            contextSize = DEFAULT_CONTEXT_SIZE,
        ) ?: return null
        return SummaryEngineHandle(
            engine = llama,
            model = fallbackModel,
            effectiveBackend = InferenceBackend.CPU,
            fallbackReason = "LiteRT-LM initialization failed; using GGUF CPU fallback.",
        )
    }
}
