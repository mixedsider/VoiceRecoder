package com.voicelog.util

import android.content.Context

enum class InferenceBackend(val displayName: String) {
    AUTO("Auto"),
    CPU("CPU"),
    GPU("GPU"),
    NPU("NPU"),
}

object InferencePreferences {

    private const val PREFS_NAME = "voicelog_prefs"
    private const val KEY_STT_BACKEND = "stt_backend"
    private const val KEY_LLM_BACKEND = "llm_backend"
    private const val KEY_STT_MODEL_ID = "stt_model_id"
    private const val KEY_LLM_MODEL_ID = "llm_model_id"
    private const val KEY_LLM_GEMMA4_GPU_MIGRATION_DONE = "llm_gemma4_gpu_migration_done"

    fun getSttBackend(context: Context): InferenceBackend =
        getBackend(context, KEY_STT_BACKEND)

    fun setSttBackend(context: Context, backend: InferenceBackend) {
        setBackend(context, KEY_STT_BACKEND, backend)
    }

    fun getLlmBackend(context: Context): InferenceBackend =
        getBackend(context, KEY_LLM_BACKEND).let { backend ->
            if (backend == InferenceBackend.NPU) InferenceBackend.CPU else backend
        }

    fun setLlmBackend(context: Context, backend: InferenceBackend) {
        setBackend(context, KEY_LLM_BACKEND, backend)
    }

    fun getSttModelId(context: Context): String =
        getPrefs(context).getString(KEY_STT_MODEL_ID, ModelUtils.DEFAULT_STT_MODEL_ID)
            ?: ModelUtils.DEFAULT_STT_MODEL_ID

    fun setSttModelId(context: Context, modelId: String) {
        getPrefs(context).edit().putString(KEY_STT_MODEL_ID, modelId).apply()
    }

    fun getLlmModelId(context: Context): String =
        getMigratedLlmModelId(context)

    fun setLlmModelId(context: Context, modelId: String) {
        getPrefs(context).edit()
            .putString(KEY_LLM_MODEL_ID, modelId)
            .putBoolean(KEY_LLM_GEMMA4_GPU_MIGRATION_DONE, true)
            .apply()
    }

    fun getSelectedSttModel(context: Context): ModelSpec =
        ModelUtils.getModelSpecOrDefault(getSttModelId(context), InferencePipeline.STT)

    fun getSelectedLlmModel(context: Context): ModelSpec =
        ModelUtils.getModelSpecOrDefault(getLlmModelId(context), InferencePipeline.LLM)

    fun getSelectedModelIds(context: Context): List<String> =
        listOf(getSelectedSttModel(context).id, getSelectedLlmModel(context).id)

    fun getRequiredModelSpecs(context: Context): List<ModelSpec> =
        ModelUtils.expandModelSelection(getSelectedModelIds(context))

    fun getMissingModelSpecs(context: Context): List<ModelSpec> =
        getRequiredModelSpecs(context).filterNot { ModelUtils.isModelReady(context, it) }

    fun areSelectedModelsReady(context: Context): Boolean =
        getMissingModelSpecs(context).isEmpty()

    fun isSelectedSttModelReady(context: Context): Boolean =
        ModelUtils.expandModelSelection(listOf(getSelectedSttModel(context).id))
            .all { ModelUtils.isModelReady(context, it) }

    fun isSelectedLlmModelReady(context: Context): Boolean =
        ModelUtils.expandModelSelection(listOf(getSelectedLlmModel(context).id))
            .all { ModelUtils.isModelReady(context, it) }

    fun getReadyLlmModelOrFallback(context: Context): ModelSpec? {
        val selected = getSelectedLlmModel(context)
        if (ModelUtils.isModelAndAuxiliaryReady(context, selected)) {
            return selected
        }

        val fallbackOrder = listOf(
            ModelUtils.llamaSpec,
            ModelUtils.litertGemma3Spec,
            ModelUtils.litertGemma4E2bSpec,
        ).distinctBy { it.id }

        return fallbackOrder.firstOrNull { ModelUtils.isModelAndAuxiliaryReady(context, it) }
    }

    fun getSelectedSttModelPath(context: Context): String =
        ModelUtils.getModelFile(context, getSelectedSttModel(context)).absolutePath

    fun getSelectedLlmModelPath(context: Context): String =
        ModelUtils.getModelFile(context, getSelectedLlmModel(context)).absolutePath

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getMigratedLlmModelId(context: Context): String {
        val prefs = getPrefs(context)
        val stored = prefs.getString(KEY_LLM_MODEL_ID, null)
        if (stored == null) {
            return ModelUtils.DEFAULT_LLM_MODEL_ID
        }
        val shouldMigrateLegacyGemma4 = stored == ModelUtils.COMPAT_LLM_MODEL_ID &&
            !prefs.getBoolean(KEY_LLM_GEMMA4_GPU_MIGRATION_DONE, false)
        if (shouldMigrateLegacyGemma4) {
            prefs.edit()
                .putString(KEY_LLM_MODEL_ID, ModelUtils.DEFAULT_LLM_MODEL_ID)
                .putBoolean(KEY_LLM_GEMMA4_GPU_MIGRATION_DONE, true)
                .apply()
            return ModelUtils.DEFAULT_LLM_MODEL_ID
        }
        return stored
    }

    private fun getBackend(context: Context, key: String): InferenceBackend {
        val defaultBackend = if (key == KEY_LLM_BACKEND) {
            InferenceBackend.CPU
        } else {
            InferenceBackend.AUTO
        }
        val stored = getPrefs(context).getString(key, defaultBackend.name)
        return InferenceBackend.entries.firstOrNull { it.name == stored } ?: defaultBackend
    }

    private fun setBackend(context: Context, key: String, backend: InferenceBackend) {
        getPrefs(context).edit().putString(key, backend.name).apply()
    }
}
