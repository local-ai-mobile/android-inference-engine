package io.github.localai.inference.hardware

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import java.io.File

object ArchitectureManager {

    @Volatile
    private var is64BitOverride: Boolean? = null

    fun set64BitForTesting(is64Bit: Boolean?) {
        is64BitOverride = is64Bit
    }

    val is64Bit: Boolean
        get() {
            is64BitOverride?.let { return it }
            return try {
                Process.is64Bit()
            } catch (_: Throwable) {
                val arch = System.getProperty("os.arch") ?: ""
                arch.contains("64")
            }
        }

    val is32Bit: Boolean get() = !is64Bit

    val primaryAbi: String
        get() = try {
            Build.SUPPORTED_ABIS?.firstOrNull() ?: if (is64Bit) "arm64-v8a" else "armeabi-v7a"
        } catch (_: Throwable) {
            if (is64Bit) "arm64-v8a" else "armeabi-v7a"
        }

    val supportedAbis: List<String>
        get() = try {
            Build.SUPPORTED_ABIS?.toList() ?: listOf(primaryAbi)
        } catch (_: Throwable) {
            listOf(primaryAbi)
        }

    val isArm32: Boolean get() = supportedAbis.any { it.startsWith("armeabi", ignoreCase = true) }
    val isArm64: Boolean get() = supportedAbis.any { it.contains("arm64", ignoreCase = true) }

    val maxSafeModelSizeGb: Double get() = if (is32Bit) 1.0 else 8.0
    val maxSafeContextLength: Int get() = if (is32Bit) 512 else 4096

    fun isModelSupported(sizeGb: Double, requiredRamGb: Double): Boolean {
        if (is32Bit) {
            return sizeGb <= 1.2 && requiredRamGb <= 1.5
        }
        return true
    }

    fun getBatteryTemperatureC(context: Context): Float {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            temp / 10.0f
        } catch (_: Exception) {
            25.0f
        }
    }

    fun getThermalStatus(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
            } catch (_: Exception) {
                0
            }
        } else {
            0
        }
    }

    fun getMaxCpuFreqGhz(cores: Int = Runtime.getRuntime().availableProcessors()): Double {
        return try {
            var maxFreqKhz = 0L
            for (i in 0 until cores) {
                val file = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (file.exists() && file.canRead()) {
                    val freq = file.readText().trim().toLongOrNull() ?: 0L
                    if (freq > maxFreqKhz) maxFreqKhz = freq
                }
            }
            if (maxFreqKhz > 0) maxFreqKhz / 1_000_000.0 else 2.0
        } catch (_: Exception) {
            2.0
        }
    }
}
