package com.voicelog

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class VoiceLogApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "voicelog_recording"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "VoiceLog 녹음",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "음성 자동 감지 및 녹음 중"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
