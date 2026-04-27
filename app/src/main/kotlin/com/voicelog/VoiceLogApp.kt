package com.voicelog

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.voicelog.util.AppLanguagePreferences

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
        AppLanguagePreferences.applyStoredLanguage(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val localizedContext = AppLanguagePreferences.localizedContext(this)

        val recordingChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            localizedContext.getString(R.string.notification_channel_recording_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = localizedContext.getString(R.string.notification_channel_recording_description)
        }

        val modelDownloadChannel = NotificationChannel(
            MODEL_DOWNLOAD_CHANNEL_ID,
            localizedContext.getString(R.string.notification_channel_model_download_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = localizedContext.getString(R.string.notification_channel_model_download_description)
        }

        val processingChannel = NotificationChannel(
            PROCESSING_CHANNEL_ID,
            localizedContext.getString(R.string.notification_channel_processing_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = localizedContext.getString(R.string.notification_channel_processing_description)
        }

        notificationManager.createNotificationChannel(recordingChannel)
        notificationManager.createNotificationChannel(modelDownloadChannel)
        notificationManager.createNotificationChannel(processingChannel)
    }
}
