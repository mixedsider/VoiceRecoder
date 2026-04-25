package com.voicelog.worker

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.workDataOf
import com.voicelog.VoiceLogApp
import com.voicelog.ui.MainActivity
import com.voicelog.util.ModelSpec
import com.voicelog.util.ModelUtils
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "model-download"
        const val PROGRESS_PERCENT = "progress_percent"
        const val CURRENT_MODEL_NAME = "current_model_name"
        const val ERROR_MESSAGE = "error_message"
        private const val NOTIFICATION_ID = 2
        private const val BUFFER_SIZE = 1024 * 1024
    }

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo("모델 다운로드 준비 중", -1))

        return try {
            for (spec in ModelUtils.requiredModels) {
                if (ModelUtils.isModelReady(applicationContext, spec)) {
                    continue
                }
                downloadModel(spec)
            }
            setProgress(workDataOf(PROGRESS_PERCENT to 100))
            Result.success()
        } catch (e: Exception) {
            Result.failure(Data.Builder().putString(ERROR_MESSAGE, e.message ?: "알 수 없는 오류").build())
        }
    }

    private suspend fun downloadModel(spec: ModelSpec) {
        val targetFile = ModelUtils.getModelFile(applicationContext, spec)
        val tempFile = File(targetFile.parentFile, "${spec.fileName}.part")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val connection = (URL(spec.downloadUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "VoiceLog/1.0")
        }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            var lastPercent = -1

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val percent = if (totalBytes > 0L) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }

                        if (percent != lastPercent) {
                            lastPercent = percent
                            reportProgress(spec.displayName, percent)
                        }
                    }
                    output.fd.sync()
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                throw IllegalStateException("Failed to move ${spec.fileName}")
            }
            if (!ModelUtils.isModelReady(applicationContext, spec)) {
                throw IllegalStateException("Downloaded file is incomplete: ${spec.fileName}")
            }
            if (spec == ModelUtils.whisperSpec || spec == ModelUtils.whisperVocabSpec) {
                ModelUtils.deleteLegacyWhisperModelsIfPresent(applicationContext)
            }
            reportProgress(spec.displayName, 100)
        } finally {
            connection.disconnect()
            if (tempFile.exists() && !ModelUtils.isModelReady(applicationContext, spec)) {
                tempFile.delete()
            }
        }
    }

    private suspend fun reportProgress(modelName: String, percent: Int) {
        setProgress(
            workDataOf(
                CURRENT_MODEL_NAME to modelName,
                PROGRESS_PERCENT to percent,
            )
        )
        setForeground(createForegroundInfo(modelName, percent))
    }

    private fun createForegroundInfo(modelName: String, percent: Int): ForegroundInfo {
        val contentText = when {
            percent in 0..99 -> "$modelName 다운로드 중... $percent%"
            percent >= 100 -> "$modelName 다운로드 완료"
            else -> "$modelName 다운로드 준비 중"
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(
            applicationContext,
            VoiceLogApp.MODEL_DOWNLOAD_CHANNEL_ID,
        )
            .setContentTitle("VoiceLog 모델 다운로드")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceAtLeast(0), percent !in 0..100)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
