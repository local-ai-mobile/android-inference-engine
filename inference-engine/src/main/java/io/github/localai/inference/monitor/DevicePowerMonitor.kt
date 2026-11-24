package io.github.localai.inference.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DevicePowerMonitor {

    private const val TAG = "DevicePowerMonitor"

    private val _isPowerGated = MutableStateFlow(false)
    val isPowerGated: StateFlow<Boolean> = _isPowerGated.asStateFlow()

    private val _batteryPercentage = MutableStateFlow(100)
    val batteryPercentage: StateFlow<Int> = _batteryPercentage.asStateFlow()

    private val _isPowerSaveMode = MutableStateFlow(false)
    val isPowerSaveMode: StateFlow<Boolean> = _isPowerSaveMode.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private var receiverRegistered = false

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null) return
            refreshState(context)
        }
    }

    fun init(context: Context) {
        try {
            refreshState(context)
            if (!receiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                }
                ContextCompat.registerReceiver(
                    context,
                    powerReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                receiverRegistered = true
                Log.i(TAG, "DevicePowerMonitor initialized: isPowerGated=${_isPowerGated.value}, battery=${_batteryPercentage.value}%")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register power broadcast receiver: ${t.message}")
        }
    }

    fun refreshState(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

            val isSaver = pm?.isPowerSaveMode == true
            val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 100
            val status = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val gated = isSaver || (batteryPct < 15 && !charging)

            _isPowerSaveMode.value = isSaver
            _batteryPercentage.value = batteryPct
            _isCharging.value = charging
            _isPowerGated.value = gated
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to refresh power state: ${t.message}")
        }
    }

    fun isLowBatteryOrSaver(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

            val isSaver = pm?.isPowerSaveMode == true
            val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 100
            val status = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            isSaver || (batteryPct < 15 && !charging) || _isPowerGated.value
        } catch (_: Throwable) {
            _isPowerGated.value
        }
    }

    fun setPowerGatedForTesting(gated: Boolean) {
        _isPowerGated.value = gated
    }
}
