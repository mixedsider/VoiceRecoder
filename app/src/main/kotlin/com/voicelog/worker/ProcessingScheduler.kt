package com.voicelog.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.voicelog.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ProcessingScheduler {

    private const val TAG = "ProcessingScheduler"
    private val schedulerScope = CoroutineScope(Dispatchers.IO)

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<ProcessingWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ProcessingWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        schedulerScope.launch {
            AppDatabase.getInstance(context).recordingDao().markPendingAsQueued()
        }
        Log.i(TAG, "Enqueued processing work")
    }

    fun isDeviceCharging(context: Context): Boolean {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }
}
