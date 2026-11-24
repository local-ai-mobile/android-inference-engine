package io.github.localai.inference.monitor

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import io.github.localai.inference.hardware.InferenceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

object DeviceThermalMonitor {

    private const val TAG = "DeviceThermalMonitor"
    private var powerManager: PowerManager? = null
    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    private val _thermalLevel = MutableStateFlow(ThermalLevel.NORMAL)
    val thermalLevel: StateFlow<ThermalLevel> = _thermalLevel.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "thermal-monitor").apply { isDaemon = true }
    }

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager = pm
                if (pm != null) {
                    val initialStatus = pm.currentThermalStatus
                    _thermalLevel.value = mapThermalStatus(initialStatus)

                    val newListener = PowerManager.OnThermalStatusChangedListener { status ->
                        val mapped = mapThermalStatus(status)
                        Log.i(TAG, "Thermal status changed: status=$status, level=$mapped")
                        _thermalLevel.value = mapped
                    }
                    listener = newListener
                    pm.addThermalStatusListener(executor, newListener)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register thermal status listener: ${e.message}")
            }
        }
    }

    fun mapThermalStatus(status: Int): ThermalLevel {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (status) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NORMAL
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.EMERGENCY
                else -> ThermalLevel.NORMAL
            }
        } else {
            ThermalLevel.NORMAL
        }
    }

    fun applyThermalThrottling(
        baseProfile: InferenceProfile,
        level: ThermalLevel = _thermalLevel.value
    ): InferenceProfile {
        return when (level) {
            ThermalLevel.NORMAL, ThermalLevel.LIGHT -> baseProfile
            ThermalLevel.MODERATE -> {
                val throttledThreads = (baseProfile.perfCores - 1).coerceAtLeast(2).coerceAtMost(baseProfile.nThreads)
                val throttledGpuLayers = (baseProfile.recommendedGpuLayers * 0.6).toInt()
                baseProfile.copy(
                    nThreads = throttledThreads,
                    recommendedGpuLayers = throttledGpuLayers
                )
            }
            ThermalLevel.SEVERE, ThermalLevel.CRITICAL, ThermalLevel.EMERGENCY -> {
                val throttledThreads = 2.coerceAtMost(baseProfile.nThreads).coerceAtLeast(1)
                val throttledGpuLayers = baseProfile.recommendedGpuLayers.coerceAtMost(16)
                baseProfile.copy(
                    nThreads = throttledThreads,
                    nThreadsBatch = (throttledThreads + 1).coerceAtMost(baseProfile.totalCores),
                    recommendedGpuLayers = throttledGpuLayers,
                    useFlashAttn = false
                )
            }
        }
    }

    fun setThermalLevelForTesting(level: ThermalLevel) {
        _thermalLevel.value = level
    }
}
