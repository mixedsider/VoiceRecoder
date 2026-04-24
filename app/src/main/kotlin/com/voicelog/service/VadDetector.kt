package com.voicelog.service

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import java.nio.LongBuffer

class VadDetector(context: Context) : AutoCloseable {

    companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME_SIZE = 512
        const val VOICE_THRESHOLD = 0.5f
    }

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val h = FloatArray(2 * 1 * 64)
    private val c = FloatArray(2 * 1 * 64)

    init {
        val modelBytes = context.assets.open("silero_vad.onnx").readBytes()
        val opts = OrtSession.SessionOptions()
        session = env.createSession(modelBytes, opts)
    }

    fun isVoice(pcmFrame: ShortArray): Boolean {
        require(pcmFrame.size == FRAME_SIZE) { "Frame must be $FRAME_SIZE samples" }

        val floatFrame = FloatArray(FRAME_SIZE) { pcmFrame[it] / 32768f }

        val inputTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(floatFrame),
            longArrayOf(1, FRAME_SIZE.toLong())
        )
        val srTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(longArrayOf(SAMPLE_RATE.toLong())),
            longArrayOf(1)
        )
        val hTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(h), longArrayOf(2, 1, 64))
        val cTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(c), longArrayOf(2, 1, 64))

        val inputs = mapOf(
            "input" to inputTensor,
            "sr" to srTensor,
            "h" to hTensor,
            "c" to cTensor
        )

        return try {
            val output = session.run(inputs)
            val prob = (output[0].value as Array<*>)[0] as FloatArray
            val hn = output[1].value
            val cn = output[2].value

            updateState(hn, h)
            updateState(cn, c)

            output.close()
            prob[0] > VOICE_THRESHOLD
        } finally {
            inputTensor.close()
            srTensor.close()
            hTensor.close()
            cTensor.close()
        }
    }

    private fun updateState(src: Any?, dst: FloatArray) {
        if (src is Array<*>) {
            var idx = 0
            for (layer in src) {
                if (layer is Array<*>) {
                    for (batch in layer) {
                        if (batch is FloatArray) {
                            batch.copyInto(dst, idx)
                            idx += batch.size
                        }
                    }
                }
            }
        }
    }

    fun resetState() {
        h.fill(0f)
        c.fill(0f)
    }

    override fun close() {
        session.close()
        // OrtEnvironment is a global singleton — do not close it
    }
}
