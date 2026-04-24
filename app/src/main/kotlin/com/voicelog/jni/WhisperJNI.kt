package com.voicelog.jni

class WhisperJNI {
    external fun init(modelPath: String): Long
    external fun transcribe(ctxPtr: Long, audioData: FloatArray): String
    external fun free(ctxPtr: Long)

    companion object {
        init { System.loadLibrary("voicelog") }
    }
}
