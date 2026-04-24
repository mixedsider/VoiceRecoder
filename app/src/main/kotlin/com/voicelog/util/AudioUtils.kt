package com.voicelog.util

import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AudioUtils {

    fun writeWavFile(filePath: String, pcmData: ShortArray, sampleRate: Int) {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = pcmData.size * 2
        val totalSize = 36 + dataSize

        FileOutputStream(filePath).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt(totalSize)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16)
                putShort(1)
                putShort(numChannels.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort(blockAlign.toShort())
                putShort(bitsPerSample.toShort())
                put("data".toByteArray())
                putInt(dataSize)
            }
            fos.write(header.array())

            val pcmBytes = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            pcmData.forEach { pcmBytes.putShort(it) }
            fos.write(pcmBytes.array())
        }
    }

    fun formatTimestamp(timestampMs: Long): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA)
        return sdf.format(Date(timestampMs))
    }
}
