package com.voicelog.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.voicelog.worker.ProcessingScheduler

class ChargingReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ChargingReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED) return
        Log.i(TAG, "Power connected broadcast received")
        ProcessingScheduler.enqueue(context)
    }
}
