package com.voicelog.util

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

data class ModelSpec(
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val minSizeBytes: Long,
)

object ModelUtils {

    private const val MODELS_DIR = "models"
    const val WHISPER_MODEL_NAME = "whisper-small-transcribe-translate.tflite"
    const val WHISPER_VOCAB_NAME = "filters_vocab_multilingual.bin"
    const val LLAMA_MODEL_NAME = "gemma-4-e2b.gguf"
    private val LEGACY_WHISPER_MODEL_NAMES = listOf(
        "whisper-medium.bin",
        "whisper-small.bin",
    )

    const val WHISPER_MIN_SIZE_BYTES = 240_000_000L
    const val WHISPER_VOCAB_MIN_SIZE_BYTES = 500_000L
    const val LLAMA_MIN_SIZE_BYTES = 1_000_000_000L

    val whisperSpec = ModelSpec(
        displayName = "Whisper Small (TFLite)",
        fileName = WHISPER_MODEL_NAME,
        downloadUrl = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-small-transcribe-translate.tflite?download=true",
        minSizeBytes = WHISPER_MIN_SIZE_BYTES,
    )

    val whisperVocabSpec = ModelSpec(
        displayName = "Whisper multilingual vocab",
        fileName = WHISPER_VOCAB_NAME,
        downloadUrl = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/filters_vocab_multilingual.bin?download=true",
        minSizeBytes = WHISPER_VOCAB_MIN_SIZE_BYTES,
    )

    val llamaSpec = ModelSpec(
        displayName = "Gemma 4 E2B Q4_K_M",
        fileName = LLAMA_MODEL_NAME,
        downloadUrl = "https://huggingface.co/dahus/gemma-4-e2b-it-Q4_K_M-GGUF/resolve/main/gemma-4-e2b-Q4_K_M.gguf?download=true",
        minSizeBytes = LLAMA_MIN_SIZE_BYTES,
    )

    val requiredModels = listOf(whisperSpec, whisperVocabSpec, llamaSpec)

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

    fun isModelFileValid(path: String, minSizeBytes: Long = 1L): Boolean {
        val file = File(path)
        if (!file.exists() || !file.isFile || file.length() < minSizeBytes) {
            return false
        }

        if (path.endsWith(".gguf", ignoreCase = true)) {
            return hasMagicHeader(file, byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
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
