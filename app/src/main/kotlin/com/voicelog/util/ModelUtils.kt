package com.voicelog.util

import android.content.Context
import java.io.File

object ModelUtils {

    private const val MODELS_DIR = "models"
    const val WHISPER_MODEL_NAME = "whisper-medium.bin"
    const val LLAMA_MODEL_NAME = "gemma-4-e2b.gguf"

    const val WHISPER_MIN_SIZE_BYTES = 500_000_000L
    const val LLAMA_MIN_SIZE_BYTES = 1_000_000_000L

    fun getModelsDir(context: Context): File =
        File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

    fun getWhisperModelPath(context: Context): String =
        File(getModelsDir(context), WHISPER_MODEL_NAME).absolutePath

    fun getLlamaModelPath(context: Context): String =
        File(getModelsDir(context), LLAMA_MODEL_NAME).absolutePath

    fun isModelFileValid(path: String, minSizeBytes: Long = 1L): Boolean {
        val file = File(path)
        return file.exists() && file.isFile && file.length() >= minSizeBytes
    }

    fun isWhisperModelReady(context: Context): Boolean =
        isModelFileValid(getWhisperModelPath(context), WHISPER_MIN_SIZE_BYTES)

    fun isLlamaModelReady(context: Context): Boolean =
        isModelFileValid(getLlamaModelPath(context), LLAMA_MIN_SIZE_BYTES)
}
