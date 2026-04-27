package com.voicelog.util

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

enum class InferencePipeline {
    STT,
    LLM
}

enum class EngineKind {
    WHISPER_TFLITE,
    LLAMA_CPP_GGUF,
    LITERT_LM,
}

data class ModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val minSizeBytes: Long,
    val pipeline: InferencePipeline,
    val engineKind: EngineKind,
    val supportedBackends: Set<InferenceBackend>,
    val minApi: Int = 26,
    val description: String = "",
    val selectable: Boolean = true,
    val auxiliaryModelIds: List<String> = emptyList(),
    val experimental: Boolean = false,
    val requiresAuth: Boolean = false,
) {
    override fun toString(): String = displayName
}

object ModelUtils {

    private const val MODELS_DIR = "models"
    const val WHISPER_TINY_MODEL_NAME = "whisper-tiny-transcribe-translate.tflite"
    const val WHISPER_BASE_MODEL_NAME = "whisper-base-transcribe-translate.tflite"
    const val WHISPER_MODEL_NAME = "whisper-small-transcribe-translate.tflite"
    const val WHISPER_VOCAB_NAME = "filters_vocab_multilingual.bin"
    const val LLAMA_MODEL_NAME = "gemma-4-e2b.gguf"
    const val LITERT_GEMMA4_E2B_MODEL_NAME = "gemma-4-E2B-it.litertlm"
    const val LITERT_GEMMA3_1B_MODEL_NAME = "gemma3-1b-it-int4.litertlm"
    private val LEGACY_WHISPER_MODEL_NAMES = listOf(
        "whisper-medium.bin",
        "whisper-small.bin",
    )

    const val WHISPER_TINY_MIN_SIZE_BYTES = 40_000_000L
    const val WHISPER_BASE_MIN_SIZE_BYTES = 75_000_000L
    const val WHISPER_MIN_SIZE_BYTES = 240_000_000L
    const val WHISPER_VOCAB_MIN_SIZE_BYTES = 500_000L
    const val LLAMA_MIN_SIZE_BYTES = 1_000_000_000L
    const val LITERT_GEMMA4_E2B_MIN_SIZE_BYTES = 2_583_085_056L
    const val LITERT_GEMMA3_1B_MIN_SIZE_BYTES = 584_417_280L

    const val DEFAULT_STT_MODEL_ID = "whisper-small-tflite"
    const val FAST_STT_MODEL_ID = "whisper-tiny-tflite"
    const val BALANCED_STT_MODEL_ID = "whisper-base-tflite"
    const val COMPAT_LLM_MODEL_ID = "gemma-4-e2b-q4km"
    const val DEFAULT_LLM_MODEL_ID = "gemma-4-e2b-litertlm"
    const val FAST_LLM_MODEL_ID = "gemma3-1b-litertlm"

    val whisperTinySpec = ModelSpec(
        id = FAST_STT_MODEL_ID,
        displayName = "Whisper Tiny (TFLite, fastest)",
        fileName = WHISPER_TINY_MODEL_NAME,
        downloadUrl = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-tiny-transcribe-translate.tflite",
        minSizeBytes = WHISPER_TINY_MIN_SIZE_BYTES,
        pipeline = InferencePipeline.STT,
        engineKind = EngineKind.WHISPER_TFLITE,
        supportedBackends = setOf(InferenceBackend.CPU, InferenceBackend.GPU, InferenceBackend.NPU),
        description = "Smallest Whisper TFLite model. Useful for speed tests, but recognition quality is lower.",
        auxiliaryModelIds = listOf("whisper-multilingual-vocab"),
    )

    val whisperBaseSpec = ModelSpec(
        id = BALANCED_STT_MODEL_ID,
        displayName = "Whisper Base (TFLite, balanced)",
        fileName = WHISPER_BASE_MODEL_NAME,
        downloadUrl = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-base-transcribe-translate.tflite",
        minSizeBytes = WHISPER_BASE_MIN_SIZE_BYTES,
        pipeline = InferencePipeline.STT,
        engineKind = EngineKind.WHISPER_TFLITE,
        supportedBackends = setOf(InferenceBackend.CPU, InferenceBackend.GPU, InferenceBackend.NPU),
        description = "Balanced Whisper TFLite model. Faster than Small, usually more accurate than Tiny.",
        auxiliaryModelIds = listOf("whisper-multilingual-vocab"),
    )

    val whisperSpec = ModelSpec(
        id = DEFAULT_STT_MODEL_ID,
        displayName = "Whisper Small (TFLite, accurate)",
        fileName = WHISPER_MODEL_NAME,
        downloadUrl = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-small-transcribe-translate.tflite",
        minSizeBytes = WHISPER_MIN_SIZE_BYTES,
        pipeline = InferencePipeline.STT,
        engineKind = EngineKind.WHISPER_TFLITE,
        supportedBackends = setOf(InferenceBackend.CPU, InferenceBackend.GPU, InferenceBackend.NPU),
        description = "Default accuracy-focused STT model. Works with the TFLite runtime path.",
        auxiliaryModelIds = listOf("whisper-multilingual-vocab"),
    )

    val whisperVocabSpec = ModelSpec(
        id = "whisper-multilingual-vocab",
        displayName = "Whisper multilingual vocab",
        fileName = WHISPER_VOCAB_NAME,
        downloadUrl = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/filters_vocab_multilingual.bin",
        minSizeBytes = WHISPER_VOCAB_MIN_SIZE_BYTES,
        pipeline = InferencePipeline.STT,
        engineKind = EngineKind.WHISPER_TFLITE,
        supportedBackends = setOf(InferenceBackend.CPU),
        description = "Auxiliary vocab file required by the current Whisper TFLite pipeline.",
        selectable = false,
    )

    val llamaSpec = ModelSpec(
        id = COMPAT_LLM_MODEL_ID,
        displayName = "Gemma 4 E2B Q4_K_M (GGUF CPU)",
        fileName = LLAMA_MODEL_NAME,
        downloadUrl = "https://huggingface.co/dahus/gemma-4-e2b-it-Q4_K_M-GGUF/resolve/main/gemma-4-e2b-Q4_K_M.gguf?download=true",
        minSizeBytes = LLAMA_MIN_SIZE_BYTES,
        pipeline = InferencePipeline.LLM,
        engineKind = EngineKind.LLAMA_CPP_GGUF,
        supportedBackends = setOf(InferenceBackend.CPU),
        description = "Compatibility llama.cpp GGUF summary model. CPU-only fallback path.",
    )

    val litertGemma4E2bSpec = ModelSpec(
        id = DEFAULT_LLM_MODEL_ID,
        displayName = "Gemma 4 E2B IT (LiteRT-LM GPU)",
        fileName = LITERT_GEMMA4_E2B_MODEL_NAME,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/7fa1d78473894f7e736a21d920c3aa80f950c0db/gemma-4-E2B-it.litertlm?download=true",
        minSizeBytes = LITERT_GEMMA4_E2B_MIN_SIZE_BYTES,
        pipeline = InferencePipeline.LLM,
        engineKind = EngineKind.LITERT_LM,
        supportedBackends = setOf(InferenceBackend.CPU, InferenceBackend.GPU),
        description = "Gallery Gemma 4 E2B LiteRT-LM model. Supports GPU/CPU acceleration and uses the Gallery Hugging Face download URL pattern.",
        experimental = true,
    )

    val litertGemma3Spec = ModelSpec(
        id = FAST_LLM_MODEL_ID,
        displayName = "Gemma 3 1B IT (LiteRT-LM)",
        fileName = LITERT_GEMMA3_1B_MODEL_NAME,
        downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm?download=true",
        minSizeBytes = LITERT_GEMMA3_1B_MIN_SIZE_BYTES,
        pipeline = InferencePipeline.LLM,
        engineKind = EngineKind.LITERT_LM,
        supportedBackends = setOf(InferenceBackend.CPU, InferenceBackend.GPU),
        description = "Fast experimental LiteRT-LM model. Supports CPU/GPU today; NPU stays disabled until an NPU-compatible model is selected.",
        experimental = true,
    )

    private val allModelSpecs = listOf(
        whisperTinySpec,
        whisperBaseSpec,
        whisperSpec,
        whisperVocabSpec,
        litertGemma4E2bSpec,
        llamaSpec,
        litertGemma3Spec,
    )

    val requiredModels = expandModelSelection(
        listOf(DEFAULT_STT_MODEL_ID, DEFAULT_LLM_MODEL_ID)
    )

    fun getModelsDir(context: Context): File =
        File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

    fun getModelFile(context: Context, spec: ModelSpec): File =
        File(getModelsDir(context), spec.fileName)

    fun getWhisperModelPath(context: Context): String =
        getModelFile(context, whisperSpec).absolutePath

    fun getWhisperVocabPath(context: Context): String =
        getModelFile(context, whisperVocabSpec).absolutePath

    fun getLlamaModelPath(context: Context): String =
        getModelFile(context, llamaSpec).absolutePath

    fun getSelectableModels(pipeline: InferencePipeline): List<ModelSpec> =
        allModelSpecs.filter { it.pipeline == pipeline && it.selectable }

    fun getModelSpec(id: String): ModelSpec? =
        allModelSpecs.firstOrNull { it.id == id }

    fun getDefaultModel(pipeline: InferencePipeline): ModelSpec {
        return when (pipeline) {
            InferencePipeline.STT -> whisperSpec
            InferencePipeline.LLM -> litertGemma4E2bSpec
        }
    }

    fun getModelSpecOrDefault(id: String?, pipeline: InferencePipeline): ModelSpec {
        return id?.let(::getModelSpec)?.takeIf { it.pipeline == pipeline }
            ?: getDefaultModel(pipeline)
    }

    fun expandModelSelection(modelIds: Collection<String>): List<ModelSpec> {
        val ordered = linkedMapOf<String, ModelSpec>()

        fun visit(spec: ModelSpec) {
            if (ordered.containsKey(spec.id)) return
            ordered[spec.id] = spec
            spec.auxiliaryModelIds.forEach { auxId ->
                getModelSpec(auxId)?.let(::visit)
            }
        }

        modelIds.mapNotNull(::getModelSpec).forEach(::visit)
        return ordered.values.toList()
    }

    fun isModelFileValid(path: String, minSizeBytes: Long = 1L): Boolean {
        val file = File(path)
        if (!file.exists() || !file.isFile || file.length() < minSizeBytes) {
            return false
        }

        if (path.endsWith(".gguf", ignoreCase = true)) {
            return hasMagicHeader(
                file,
                byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte())
            )
        }

        return true
    }

    private fun hasMagicHeader(file: File, expectedHeader: ByteArray): Boolean {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(expectedHeader.size)
                val read = raf.read(header)
                read == expectedHeader.size && header.contentEquals(expectedHeader)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isModelReady(context: Context, spec: ModelSpec): Boolean =
        isModelFileValid(getModelFile(context, spec).absolutePath, spec.minSizeBytes)

    fun isModelAndAuxiliaryReady(context: Context, spec: ModelSpec): Boolean =
        expandModelSelection(listOf(spec.id)).all { isModelReady(context, it) }

    fun isWhisperModelReady(context: Context): Boolean =
        isModelReady(context, whisperSpec) && isModelReady(context, whisperVocabSpec)

    fun isLlamaModelReady(context: Context): Boolean =
        isModelReady(context, llamaSpec)

    fun areAllModelsReady(context: Context): Boolean =
        requiredModels.all { isModelReady(context, it) }

    fun getMissingModels(context: Context): List<ModelSpec> =
        requiredModels.filterNot { isModelReady(context, it) }

    fun deleteLegacyWhisperModelsIfPresent(context: Context) {
        LEGACY_WHISPER_MODEL_NAMES.forEach { legacyName ->
            val legacyFile = File(getModelsDir(context), legacyName)
            if (legacyFile.exists() && legacyFile.isFile) {
                legacyFile.delete()
            }
        }
    }
}
