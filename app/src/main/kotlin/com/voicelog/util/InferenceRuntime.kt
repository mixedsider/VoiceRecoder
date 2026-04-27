package com.voicelog.util

import android.content.Context
import android.os.Build

data class BackendAvailability(
    val backend: InferenceBackend,
    val available: Boolean,
    val reason: String? = null,
)

data class BackendResolution(
    val pipeline: InferencePipeline,
    val engineKind: EngineKind,
    val requestedBackend: InferenceBackend,
    val effectiveBackend: InferenceBackend,
    val fallbackReason: String? = null,
    val availabilities: List<BackendAvailability>,
) {
    val isFallback: Boolean
        get() = requestedBackend != effectiveBackend

    val availableBackends: List<InferenceBackend>
        get() = availabilities
            .filter { it.available && it.backend != InferenceBackend.AUTO }
            .map { it.backend }
}

object InferenceRuntime {

    fun resolveStt(context: Context): BackendResolution {
        val model = InferencePreferences.getSelectedSttModel(context)
        val requested = InferencePreferences.getSttBackend(context)
        return resolve(
            pipeline = InferencePipeline.STT,
            model = model,
            requestedBackend = requested,
        )
    }

    fun resolveLlm(context: Context): BackendResolution {
        val model = InferencePreferences.getSelectedLlmModel(context)
        return resolveLlm(context, model)
    }

    fun resolveLlm(context: Context, model: ModelSpec): BackendResolution {
        val requested = InferencePreferences.getLlmBackend(context)
        return resolve(
            pipeline = InferencePipeline.LLM,
            model = model,
            requestedBackend = requested,
        )
    }

    fun getAvailabilitySummary(context: Context, pipeline: InferencePipeline): List<BackendAvailability> {
        val model = when (pipeline) {
            InferencePipeline.STT -> InferencePreferences.getSelectedSttModel(context)
            InferencePipeline.LLM -> InferencePreferences.getSelectedLlmModel(context)
        }
        return buildAvailabilities(pipeline, model)
    }

    internal fun resolveForTest(
        pipeline: InferencePipeline,
        model: ModelSpec,
        requestedBackend: InferenceBackend,
        sdkInt: Int,
    ): BackendResolution = resolve(
        pipeline = pipeline,
        model = model,
        requestedBackend = requestedBackend,
        sdkInt = sdkInt,
    )

    private fun resolve(
        pipeline: InferencePipeline,
        model: ModelSpec,
        requestedBackend: InferenceBackend,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): BackendResolution {
        val availabilities = buildAvailabilities(pipeline, model, sdkInt)
        val availableMap = availabilities.associateBy { it.backend }

        if (requestedBackend == InferenceBackend.AUTO) {
            val preferredBackend = when {
                availableMap[InferenceBackend.NPU]?.available == true -> InferenceBackend.NPU
                availableMap[InferenceBackend.GPU]?.available == true -> InferenceBackend.GPU
                else -> InferenceBackend.CPU
            }
            val reason = if (preferredBackend == InferenceBackend.CPU) {
                firstUnavailableAccelerationReason(availabilities)
            } else {
                null
            }
            return BackendResolution(
                pipeline = pipeline,
                engineKind = model.engineKind,
                requestedBackend = requestedBackend,
                effectiveBackend = preferredBackend,
                fallbackReason = reason,
                availabilities = availabilities,
            )
        }

        val requestedAvailability = availableMap[requestedBackend]
        return if (requestedAvailability?.available == true) {
            BackendResolution(
                pipeline = pipeline,
                engineKind = model.engineKind,
                requestedBackend = requestedBackend,
                effectiveBackend = requestedBackend,
                availabilities = availabilities,
            )
        } else {
            val fallbackBackend = if (
                requestedBackend == InferenceBackend.NPU &&
                availableMap[InferenceBackend.GPU]?.available == true
            ) {
                InferenceBackend.GPU
            } else {
                InferenceBackend.CPU
            }
            BackendResolution(
                pipeline = pipeline,
                engineKind = model.engineKind,
                requestedBackend = requestedBackend,
                effectiveBackend = fallbackBackend,
                fallbackReason = requestedAvailability?.reason ?: "Requested backend is unavailable.",
                availabilities = availabilities,
            )
        }
    }

    private fun buildAvailabilities(
        pipeline: InferencePipeline,
        model: ModelSpec,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): List<BackendAvailability> {
        fun supported(backend: InferenceBackend): Boolean = model.supportedBackends.contains(backend)

        val cpu = BackendAvailability(
            backend = InferenceBackend.CPU,
            available = supported(InferenceBackend.CPU),
            reason = if (supported(InferenceBackend.CPU)) null else "The selected model does not support CPU.",
        )

        val gpuReason = when {
            supported(InferenceBackend.GPU) -> null
            model.engineKind == EngineKind.LLAMA_CPP_GGUF ->
                "The current GGUF llama.cpp summary path is CPU-only in this build."
            model.engineKind == EngineKind.LITERT_LM ->
                "The selected LiteRT-LM model does not declare GPU support."
            pipeline == InferencePipeline.STT ->
                "The selected STT model does not declare GPU delegate support."
            else ->
                "The selected model does not support GPU."
        }
        val gpu = BackendAvailability(
            backend = InferenceBackend.GPU,
            available = supported(InferenceBackend.GPU),
            reason = gpuReason,
        )

        val npuReason = when {
            !supported(InferenceBackend.NPU) && model.engineKind == EngineKind.LITERT_LM ->
                "The selected LiteRT-LM model is not marked as NPU-compatible."
            !supported(InferenceBackend.NPU) && model.engineKind == EngineKind.LLAMA_CPP_GGUF ->
                "The current GGUF summary model cannot use the planned LiteRT-LM NPU path."
            !supported(InferenceBackend.NPU) ->
                "The selected model does not support NPU."
            pipeline == InferencePipeline.LLM && sdkInt < Build.VERSION_CODES.S ->
                "LLM NPU paths require Android 12 or newer on target devices."
            pipeline == InferencePipeline.STT && sdkInt < Build.VERSION_CODES.O_MR1 ->
                "STT NNAPI execution requires Android 8.1 or newer."
            else ->
                null
        }
        val npu = BackendAvailability(
            backend = InferenceBackend.NPU,
            available = npuReason == null,
            reason = npuReason,
        )

        return listOf(
            BackendAvailability(
                backend = InferenceBackend.AUTO,
                available = true,
                reason = "Auto selects the best integrated backend available on this device.",
            ),
            cpu,
            gpu,
            npu,
        )
    }

    private fun firstUnavailableAccelerationReason(availabilities: List<BackendAvailability>): String? {
        return availabilities
            .firstOrNull { it.backend == InferenceBackend.GPU && !it.available }
            ?.reason
            ?: availabilities.firstOrNull { it.backend == InferenceBackend.NPU && !it.available }?.reason
    }
}
