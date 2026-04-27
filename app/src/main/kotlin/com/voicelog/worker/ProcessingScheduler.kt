package com.voicelog.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.voicelog.db.AppDatabase
import com.voicelog.util.ProcessingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ProcessingScheduler {

    private const val TAG = "ProcessingScheduler"
    private val schedulerScope = CoroutineScope(Dispatchers.IO)

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setConstraints(buildConstraints(context))
            .build()
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

    fun maybeEnqueue(context: Context) {
        schedulerScope.launch {
            val canRunStt = ProcessingPreferences.shouldRunSttNow(context)
            val canRunSummary = ProcessingPreferences.shouldRunSummaryNow(context)
            if (!canRunStt && !canRunSummary) {
                Log.i(TAG, "Skipping enqueue because neither STT nor summary can run now")
                return@launch
            }

            val recordingDao = AppDatabase.getInstance(context).recordingDao()
            val hasPendingStt = canRunStt &&
                recordingDao.hasAnyWithStatuses(listOf("pending", "queued"))
            val hasPendingSummary = canRunSummary &&
                recordingDao.hasAnyWithStatuses(listOf("transcript_ready"))

            if (hasPendingStt || hasPendingSummary) {
                enqueue(context)
            }
        }
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

    fun isBatteryLow(context: Context): Boolean {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return false
        return (level * 100 / scale) <= 15
    }

    fun isPowerSaveMode(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isPowerSaveMode == true
    }

    internal fun buildConstraints(context: Context): Constraints {
        val policies = listOf(
            ProcessingPreferences.getSttPolicy(context),
            ProcessingPreferences.getSummaryPolicy(context),
        ).filter { it != ProcessingPreferences.ExecutionPolicy.MANUAL }
        val hasChargingOnlyPolicy = policies.any {
            it == ProcessingPreferences.ExecutionPolicy.CHARGING_ONLY
        }
        val requiresCharging = policies.isNotEmpty() &&
            policies.all { it == ProcessingPreferences.ExecutionPolicy.CHARGING_ONLY }
        val requiresBatteryNotLow = !requiresCharging &&
            !hasChargingOnlyPolicy &&
            policies.any { it == ProcessingPreferences.ExecutionPolicy.BATTERY_AWARE }

        return Constraints.Builder()
            .setRequiresCharging(requiresCharging)
            .setRequiresBatteryNotLow(requiresBatteryNotLow)
            .build()
    }
}
