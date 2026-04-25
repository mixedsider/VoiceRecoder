package com.voicelog

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class VoiceLogApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "voicelog_recording"
        const val NOTIFICATION_ID = 1
        const val MODEL_DOWNLOAD_CHANNEL_ID = "voicelog_model_download"
        const val PROCESSING_CHANNEL_ID = "voicelog_processing"
        const val PROCESSING_NOTIFICATION_ID = 3
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val recordingChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "VoiceLog recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows microphone recording status."
        }

        val modelDownloadChannel = NotificationChannel(
            MODEL_DOWNLOAD_CHANNEL_ID,
            "VoiceLog model download",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows first-run model download progress."
        }

        val processingChannel = NotificationChannel(
            PROCESSING_CHANNEL_ID,
            "VoiceLog processing",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows background transcription and summarization progress."
        }

        notificationManager.createNotificationChannel(recordingChannel)
        notificationManager.createNotificationChannel(modelDownloadChannel)
        notificationManager.createNotificationChannel(processingChannel)
    }
}
