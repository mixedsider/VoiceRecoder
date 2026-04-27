package com.voicelog.inference

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.voicelog.util.InferenceBackend

class LiteRtLmSummaryEngine private constructor(
    private val engine: AutoCloseable,
    private val conversation: AutoCloseable,
) : SummaryEngine {

    override fun generate(prompt: String, maxNewTokens: Int): SummaryResult {
        val startedAt = SystemClock.elapsedRealtime()
        val message = conversation.javaClass
            .getMethod("sendMessage", String::class.java, Map::class.java)
            .invoke(conversation, prompt, emptyMap<String, Any>())
        return SummaryResult(
            text = message?.toString().orEmpty(),
            metrics = SummaryMetrics(
                totalMs = SystemClock.elapsedRealtime() - startedAt,
                promptEvalMs = 0.0,
                decodeMs = 0.0,
                samplingMs = 0.0,
                timeToFirstTokenMs = 0.0,
                promptTokenCount = 0,
                generatedTokenCount = 0,
            ),
        )
    }

    override fun close() {
        try {
            conversation.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close LiteRT-LM conversation", e)
        }
        try {
            engine.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close LiteRT-LM engine", e)
        }
    }

    companion object {
        private const val TAG = "LiteRtLmSummaryEngine"
        private const val PACKAGE_NAME = "com.google.ai.edge.litertlm"

        fun create(
            context: Context,
            modelPath: String,
            backend: InferenceBackend,
            maxNumTokens: Int,
        ): LiteRtLmSummaryEngine {
            val backendClass = Class.forName("$PACKAGE_NAME.Backend")
            val engineConfigClass = Class.forName("$PACKAGE_NAME.EngineConfig")
            val engineClass = Class.forName("$PACKAGE_NAME.Engine")
            val conversationConfigClass = Class.forName("$PACKAGE_NAME.ConversationConfig")

            val liteRtBackend = createBackend(context, backend)
            val engineConfig = engineConfigClass
                .getConstructor(
                    String::class.java,
                    backendClass,
                    backendClass,
                    backendClass,
                    Integer::class.java,
                    String::class.java,
                )
                .newInstance(
                    modelPath,
                    liteRtBackend,
                    null,
                    null,
                    Integer.valueOf(maxNumTokens),
                    context.externalCacheDir?.absolutePath,
                )
            val engine = engineClass.getConstructor(engineConfigClass).newInstance(engineConfig)
            engineClass.getMethod("initialize").invoke(engine)
            val conversationConfig = conversationConfigClass.getConstructor().newInstance()
            val conversation = engineClass
                .getMethod("createConversation", conversationConfigClass)
                .invoke(engine, conversationConfig)

            return LiteRtLmSummaryEngine(
                engine = engine as AutoCloseable,
                conversation = conversation as AutoCloseable,
            )
        }

        private fun createBackend(context: Context, backend: InferenceBackend): Any {
            return when (backend) {
                InferenceBackend.NPU -> Class.forName("$PACKAGE_NAME.Backend\$NPU")
                    .getConstructor(String::class.java)
                    .newInstance(context.applicationInfo.nativeLibraryDir)
                InferenceBackend.GPU -> Class.forName("$PACKAGE_NAME.Backend\$GPU")
                    .getConstructor()
                    .newInstance()
                else -> Class.forName("$PACKAGE_NAME.Backend\$CPU")
                    .getConstructor()
                    .newInstance()
            }
        }
    }
}
