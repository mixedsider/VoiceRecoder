package com.voicelog.jni

class LlamaJNI {
    external fun init(modelPath: String): Long
    external fun generate(ctxPtr: Long, prompt: String): String
    external fun free(ctxPtr: Long)

    companion object {
        init { System.loadLibrary("voicelog") }
    }
}
