package io.github.localai.inference.monitor

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Debug
import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.FileReader
import java.util.concurrent.CopyOnWriteArrayList

object MemoryLimiterKillGuard {

    private const val TAG = "MemoryGuard"
    private val listeners = CopyOnWriteArrayList<MemoryTrimListener>()

    fun registerListener(listener: MemoryTrimListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun unregisterListener(listener: MemoryTrimListener) {
        listeners.remove(listener)
    }

    fun init(context: Context) {
        val snapshot = captureSnapshot(context)
        Log.i(TAG, "Initialized MemoryLimiterKillGuard: RSS=${snapshot.rssMb}MB, Avail=${snapshot.availMemMb}MB, Risk=${snapshot.riskLevel}")
    }

    fun handleTrimMemory(context: Context, level: Int) {
        val snapshot = captureSnapshot(context)
        Log.w(TAG, "onTrimMemory intercepted: level=$level, RSS=${snapshot.rssMb}MB, Avail=${snapshot.availMemMb}MB, Risk=${snapshot.riskLevel}")

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                listeners.forEach { it.onTrimUiCaches() }
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                listeners.forEach {
                    it.onTrimUiCaches()
                    it.onTrimKvCaches()
                }
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                listeners.forEach {
                    it.onTrimUiCaches()
                    it.onTrimKvCaches()
                    it.onCriticalMemoryPressure(shouldEvictModel = true)
                }
            }
            else -> {
                listeners.forEach { it.onTrimUiCaches() }
            }
        }
    }

    fun handleLowMemory(context: Context) {
        val snapshot = captureSnapshot(context)
        Log.e(TAG, "onLowMemory intercepted! Immediate emergency trim.")
        listeners.forEach {
            it.onTrimUiCaches()
            it.onTrimKvCaches()
            it.onCriticalMemoryPressure(shouldEvictModel = true)
        }
    }

    fun captureSnapshot(context: Context): MemorySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val availMemMb = memInfo.availMem / (1024 * 1024)
        val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        val javaHeapMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
        val rssMb = readRssMb()

        val riskLevel = when {
            memInfo.lowMemory || availMemMb < 350 -> MemoryRiskLevel.CRITICAL
            availMemMb < 750 -> MemoryRiskLevel.MODERATE
            else -> MemoryRiskLevel.NORMAL
        }

        return MemorySnapshot(
            rssMb = rssMb,
            nativeHeapMb = nativeHeapMb,
            javaHeapMb = javaHeapMb,
            availMemMb = availMemMb,
            riskLevel = riskLevel
        )
    }

    private fun readRssMb(): Long {
        return try {
            BufferedReader(FileReader("/proc/${Process.myPid()}/status")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line?.startsWith("VmRSS:") == true) {
                        val parts = line!!.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            val kb = parts[1].toLongOrNull() ?: 0L
                            return kb / 1024
                        }
                    }
                }
            }
            0L
        } catch (_: Exception) {
            Debug.getPss() / 1024
        }
    }
}
