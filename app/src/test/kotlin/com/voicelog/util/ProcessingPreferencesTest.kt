package com.voicelog.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingPreferencesTest {

    @Test
    fun immediate_runsRegardlessOfBatteryState() {
        assertTrue(
            ProcessingPreferences.shouldRun(
                policy = ProcessingPreferences.ExecutionPolicy.IMMEDIATE,
                isCharging = false,
                isBatteryLow = true,
                isPowerSaveMode = true,
            )
        )
    }

    @Test
    fun batteryAware_runsWhenBatteryIsHealthy() {
        assertTrue(
            ProcessingPreferences.shouldRun(
                policy = ProcessingPreferences.ExecutionPolicy.BATTERY_AWARE,
                isCharging = false,
                isBatteryLow = false,
                isPowerSaveMode = false,
            )
        )
    }

    @Test
    fun batteryAware_skipsWhenLowAndNotCharging() {
        assertFalse(
            ProcessingPreferences.shouldRun(
                policy = ProcessingPreferences.ExecutionPolicy.BATTERY_AWARE,
                isCharging = false,
                isBatteryLow = true,
                isPowerSaveMode = false,
            )
        )
    }

    @Test
    fun chargingOnly_requiresCharging() {
        assertFalse(
            ProcessingPreferences.shouldRun(
                policy = ProcessingPreferences.ExecutionPolicy.CHARGING_ONLY,
                isCharging = false,
                isBatteryLow = false,
                isPowerSaveMode = false,
            )
        )
        assertTrue(
            ProcessingPreferences.shouldRun(
                policy = ProcessingPreferences.ExecutionPolicy.CHARGING_ONLY,
                isCharging = true,
                isBatteryLow = true,
                isPowerSaveMode = true,
            )
        )
    }

    @Test
    fun manual_neverRunsAutomatically() {
        assertFalse(
            ProcessingPreferences.shouldRun(
                policy = ProcessingPreferences.ExecutionPolicy.MANUAL,
                isCharging = true,
                isBatteryLow = false,
                isPowerSaveMode = false,
            )
        )
    }
}
