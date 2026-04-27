package com.voicelog.util

import android.content.Context
import com.voicelog.worker.ProcessingScheduler

object ProcessingPreferences {

    private const val PREFS_NAME = "voicelog_prefs"
    private const val KEY_STT_POLICY = "stt_execution_policy"
    private const val KEY_SUMMARY_POLICY = "summary_execution_policy"
    private const val POLICY_ALWAYS = "always"
    private const val POLICY_IMMEDIATE = "immediate"
    private const val POLICY_BATTERY_AWARE = "battery_aware"
    private const val POLICY_CHARGING_ONLY = "charging_only"
    private const val POLICY_MANUAL = "manual"

    enum class ExecutionPolicy {
        IMMEDIATE,
        BATTERY_AWARE,
        CHARGING_ONLY,
        MANUAL,
    }

    fun getSttPolicy(context: Context): ExecutionPolicy {
        return getPolicy(context, KEY_STT_POLICY)
    }

    fun setSttPolicy(context: Context, policy: ExecutionPolicy) {
        setPolicy(context, KEY_STT_POLICY, policy)
    }

    fun getSummaryPolicy(context: Context): ExecutionPolicy {
        return getPolicy(context, KEY_SUMMARY_POLICY)
    }

    fun setSummaryPolicy(context: Context, policy: ExecutionPolicy) {
        setPolicy(context, KEY_SUMMARY_POLICY, policy)
    }

    fun shouldRunSttNow(context: Context): Boolean {
        return shouldRunNow(context, getSttPolicy(context))
    }

    fun shouldRunSummaryNow(context: Context): Boolean {
        return shouldRunNow(context, getSummaryPolicy(context))
    }

    private fun shouldRunNow(context: Context, policy: ExecutionPolicy): Boolean {
        return shouldRun(
            policy = policy,
            isCharging = ProcessingScheduler.isDeviceCharging(context),
            isBatteryLow = ProcessingScheduler.isBatteryLow(context),
            isPowerSaveMode = ProcessingScheduler.isPowerSaveMode(context),
        )
    }

    internal fun shouldRun(
        policy: ExecutionPolicy,
        isCharging: Boolean,
        isBatteryLow: Boolean,
        isPowerSaveMode: Boolean,
    ): Boolean {
        return when (policy) {
            ExecutionPolicy.IMMEDIATE -> true
            ExecutionPolicy.BATTERY_AWARE -> isCharging || (!isBatteryLow && !isPowerSaveMode)
            ExecutionPolicy.CHARGING_ONLY -> isCharging
            ExecutionPolicy.MANUAL -> false
        }
    }

    private fun getPolicy(context: Context, key: String): ExecutionPolicy {
        val defaultValue = if (key == KEY_STT_POLICY) {
            POLICY_BATTERY_AWARE
        } else {
            POLICY_CHARGING_ONLY
        }
        return when (
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(key, defaultValue)
        ) {
            POLICY_ALWAYS,
            POLICY_IMMEDIATE -> ExecutionPolicy.IMMEDIATE
            POLICY_BATTERY_AWARE -> ExecutionPolicy.BATTERY_AWARE
            POLICY_MANUAL -> ExecutionPolicy.MANUAL
            else -> ExecutionPolicy.CHARGING_ONLY
        }
    }

    private fun setPolicy(context: Context, key: String, policy: ExecutionPolicy) {
        val storedValue = when (policy) {
            ExecutionPolicy.IMMEDIATE -> POLICY_IMMEDIATE
            ExecutionPolicy.BATTERY_AWARE -> POLICY_BATTERY_AWARE
            ExecutionPolicy.CHARGING_ONLY -> POLICY_CHARGING_ONLY
            ExecutionPolicy.MANUAL -> POLICY_MANUAL
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, storedValue)
            .apply()
    }
}
