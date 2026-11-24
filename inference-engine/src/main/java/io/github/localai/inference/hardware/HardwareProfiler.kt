package io.github.localai.inference.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.io.File
import java.util.Locale

object HardwareProfiler {

    @Volatile
    var mockAvailableBytesForTesting: Long? = null

    fun profile(context: Context, mode: PerformanceMode = PerformanceMode.BALANCED): InferenceProfile {
        val cores = Runtime.getRuntime().availableProcessors()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalRamGb = mem.totalMem / 1_073_741_824.0

        val gpuBrand = detectGpuBrand()
        val perfCores = detectPerformanceCores(cores)

        val tier = when {
            totalRamGb >= 12 -> DeviceTier.FLAGSHIP
            totalRamGb >= 8  -> DeviceTier.HIGH
            totalRamGb >= 6  -> DeviceTier.MID
            else             -> DeviceTier.LOW
        }

        val config = calculateModeConfig(mode, tier, gpuBrand, cores, perfCores, totalRamGb)

        val is32Bit = ArchitectureManager.is32Bit
        val finalGpuLayers = if (is32Bit) 0 else config.gpuLayers
        val finalThreads = if (is32Bit) 2.coerceAtMost(cores) else config.nThreads
        val finalThreadsBatch = if (is32Bit) 2.coerceAtMost(cores) else config.nThreadsBatch
        val finalContextLength = if (is32Bit) {
            ArchitectureManager.maxSafeContextLength.coerceAtMost(config.contextLength)
        } else {
            config.contextLength
        }
        val finalBatch = if (is32Bit) 128 else config.nBatch
        val finalUbatch = if (is32Bit) 32 else config.nUbatch
        val finalFlashAttn = if (is32Bit) false else config.useFlashAttn

        return InferenceProfile(
            gpuBrand = gpuBrand,
            recommendedGpuLayers = finalGpuLayers,
            nThreads = finalThreads,
            nThreadsBatch = finalThreadsBatch,
            nBatch = finalBatch,
            nUbatch = finalUbatch,
            contextLength = finalContextLength,
            useFlashAttn = finalFlashAttn,
            useMlock = config.useMlock,
            tier = tier,
            performanceMode = mode,
            totalRamGb = totalRamGb,
            totalCores = cores,
            perfCores = perfCores
        )
    }

    private data class ModeConfig(
        val gpuLayers: Int,
        val nThreads: Int,
        val nThreadsBatch: Int,
        val nBatch: Int,
        val nUbatch: Int,
        val contextLength: Int,
        val useFlashAttn: Boolean,
        val useMlock: Boolean
    )

    private fun calculateModeConfig(
        mode: PerformanceMode,
        tier: DeviceTier,
        gpuBrand: GpuBrand,
        totalCores: Int,
        perfCores: Int,
        totalRamGb: Double
    ): ModeConfig {
        return when (mode) {
            PerformanceMode.BATTERY_SAVER -> {
                val layers = if (gpuBrand == GpuBrand.ADRENO && tier >= DeviceTier.HIGH) 16 else 0
                val threads = 2.coerceAtMost(perfCores).coerceAtLeast(1)
                val batchThreads = (threads + 1).coerceAtMost(totalCores)
                val ctx = 2048
                ModeConfig(
                    gpuLayers = layers,
                    nThreads = threads,
                    nThreadsBatch = batchThreads,
                    nBatch = 256,
                    nUbatch = 64,
                    contextLength = ctx,
                    useFlashAttn = false,
                    useMlock = false
                )
            }
            PerformanceMode.BALANCED -> {
                val layers = when {
                    gpuBrand == GpuBrand.ADRENO && tier >= DeviceTier.MID -> 48
                    gpuBrand == GpuBrand.ADRENO && tier == DeviceTier.LOW -> 16
                    gpuBrand == GpuBrand.MALI && tier == DeviceTier.FLAGSHIP -> 32
                    gpuBrand == GpuBrand.MALI && tier == DeviceTier.HIGH -> 16
                    else -> 0
                }
                val threads = perfCores.coerceIn(2, 6)
                val batchThreads = (perfCores + 1).coerceAtMost(totalCores)
                val ctx = when (tier) {
                    DeviceTier.FLAGSHIP -> 3072
                    DeviceTier.HIGH -> 2560
                    else -> 2048
                }
                val ubatch = when (tier) {
                    DeviceTier.FLAGSHIP -> 256
                    DeviceTier.HIGH -> 128
                    DeviceTier.MID -> 128
                    DeviceTier.LOW -> 64
                }
                val flash = (gpuBrand == GpuBrand.ADRENO || gpuBrand == GpuBrand.MALI) && tier >= DeviceTier.MID
                ModeConfig(
                    gpuLayers = layers,
                    nThreads = threads,
                    nThreadsBatch = batchThreads,
                    nBatch = 512,
                    nUbatch = ubatch,
                    contextLength = ctx,
                    useFlashAttn = flash,
                    useMlock = false
                )
            }
            PerformanceMode.HIGH_PERFORMANCE -> {
                val layers = when {
                    gpuBrand == GpuBrand.ADRENO && tier >= DeviceTier.MID -> 99
                    gpuBrand == GpuBrand.ADRENO && tier == DeviceTier.LOW -> 32
                    gpuBrand == GpuBrand.MALI && tier >= DeviceTier.HIGH -> 48
                    gpuBrand == GpuBrand.MALI && tier == DeviceTier.MID -> 24
                    else -> 0
                }
                val threads = (perfCores + 1).coerceAtMost(totalCores - 1).coerceAtLeast(perfCores).coerceIn(2, 8)
                val batchThreads = totalCores.coerceIn(4, 8)
                val ctx = when (tier) {
                    DeviceTier.FLAGSHIP, DeviceTier.HIGH -> 4096
                    else -> 2048
                }
                val ubatch = when (tier) {
                    DeviceTier.FLAGSHIP -> 512
                    DeviceTier.HIGH, DeviceTier.MID -> 256
                    DeviceTier.LOW -> 128
                }
                val flash = tier >= DeviceTier.MID
                ModeConfig(
                    gpuLayers = layers,
                    nThreads = threads,
                    nThreadsBatch = batchThreads,
                    nBatch = 512,
                    nUbatch = ubatch,
                    contextLength = ctx,
                    useFlashAttn = flash,
                    useMlock = false
                )
            }
        }
    }

    private fun detectPerformanceCores(cores: Int): Int {
        try {
            val freqs = mutableListOf<Long>()
            for (i in 0 until cores) {
                val file = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (file.exists() && file.canRead()) {
                    val f = file.readText().trim().toLongOrNull()
                    if (f != null && f > 0) freqs.add(f)
                }
            }
            if (freqs.size == cores) {
                val minFreq = freqs.minOrNull() ?: 0L
                val perfCount = freqs.count { it > minFreq }
                if (perfCount in 1 until cores) return perfCount
            }
        } catch (_: Exception) {
            // Sysfs access restricted under SELinux, fallback to heuristics
        }

        return when {
            cores >= 8 -> 4
            cores >= 6 -> 3
            cores >= 4 -> 2
            else -> cores
        }
    }

    fun detectGpuBrand(): GpuBrand {
        val hardware = try { Build.HARDWARE?.lowercase() } catch (_: Throwable) { null } ?: ""
        val board = try { Build.BOARD?.lowercase() } catch (_: Throwable) { null } ?: ""
        val fingerprint = try { Build.FINGERPRINT?.lowercase() } catch (_: Throwable) { null } ?: ""
        return when {
            hardware.contains("adreno") || board.contains("msm")
                || board.contains("kalama") || board.contains("pineapple")
                || fingerprint.contains("qcom") -> GpuBrand.ADRENO
            hardware.contains("mali") || board.contains("exynos")
                || board.contains("mt") || board.contains("tiger")
                || hardware.contains("tensor") -> GpuBrand.MALI
            hardware.contains("powervr") -> GpuBrand.POWERVR
            else -> GpuBrand.UNKNOWN
        }
    }

    fun detectSiliconAcceleration(cpuInfoContent: String? = null): SiliconCapabilities {
        var hasNeon = false
        var hasDotProd = false
        var hasI8mm = false
        var hasFp16 = false
        var hasSve = false

        val content = cpuInfoContent ?: run {
            try {
                val f = File("/proc/cpuinfo")
                if (f.exists() && f.canRead()) f.readText() else null
            } catch (_: Exception) {
                null
            }
        }

        val abis = try { Build.SUPPORTED_ABIS } catch (_: Throwable) { null } ?: emptyArray()
        val isArmFromContent = content != null && (
            content.contains("CPU architecture: 8", ignoreCase = true) ||
            content.contains("aarch64", ignoreCase = true) ||
            content.contains("asimd", ignoreCase = true) ||
            content.contains("dotprod", ignoreCase = true) ||
            content.contains("i8mm", ignoreCase = true)
        )
        val isArm64 = abis.any { it.contains("arm64", ignoreCase = true) } || isArmFromContent
        val isArm32 = abis.any { it.startsWith("armeabi", ignoreCase = true) }

        try {
            if (!content.isNullOrBlank()) {
                val featuresLine = content.lines().firstOrNull { it.trimStart().startsWith("Features", ignoreCase = true) } ?: ""
                val features = featuresLine.substringAfter(":").trim().lowercase().split("\\s+".toRegex()).toSet()

                hasNeon = features.contains("asimd") || features.contains("neon") || isArm64
                hasDotProd = features.contains("asimddp") || features.contains("dotprod")
                hasI8mm = features.contains("i8mm")
                hasFp16 = features.contains("fphp") || features.contains("fp16") || features.contains("asimdhp")
                hasSve = features.contains("sve") || features.contains("sve2")
            } else {
                hasNeon = isArm64 || isArm32
            }
        } catch (_: Exception) {
            hasNeon = isArm64 || isArm32
        }

        val hasKleidiAi = isArm64 && (hasDotProd || hasI8mm || hasNeon)

        val engineLabel = when {
            hasDotProd && hasI8mm -> "KleidiAI (NEON+I8MM)"
            hasDotProd -> "KleidiAI (Arm DotProd)"
            hasI8mm -> "KleidiAI (Arm I8MM)"
            hasNeon -> "Arm NEON"
            !isArm64 && !isArm32 -> "x86_64 SIMD"
            else -> "CPU Standard"
        }

        val archLabel = when {
            isArm64 && (hasDotProd || hasI8mm) -> "ARM64-v8.2A+ (KleidiAI)"
            isArm64 -> "ARM64-v8A (NEON)"
            isArm32 -> "ARMv7 (NEON)"
            else -> abis.firstOrNull() ?: "Universal"
        }

        val gpuBrand = detectGpuBrand()
        val gpuDelegateReady = gpuBrand == GpuBrand.ADRENO || gpuBrand == GpuBrand.MALI

        return SiliconCapabilities(
            hasKleidiAi = hasKleidiAi,
            hasDotProd = hasDotProd,
            hasI8mm = hasI8mm,
            hasNeon = hasNeon,
            hasFp16 = hasFp16,
            hasSve = hasSve,
            isArm64 = isArm64,
            architectureLabel = archLabel,
            engineLabel = engineLabel,
            gpuDelegateReady = gpuDelegateReady,
            gpuLabel = if (gpuDelegateReady) "Vulkan 1.3 / OpenCL" else "CPU Only"
        )
    }

    fun checkMemorySafety(
        context: Context,
        requiredRamGb: Double,
        sizeGb: Double = 0.0
    ): MemorySafetyAssessment {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalRamGb = if (mem.totalMem > 0L) mem.totalMem / 1_073_741_824.0 else 4.0
        val availRamGb = if (mem.availMem > 0L) mem.availMem / 1_073_741_824.0 else 2.5

        if (ArchitectureManager.is32Bit) {
            val isSupported = ArchitectureManager.isModelSupported(sizeGb, requiredRamGb)
            if (!isSupported) {
                return MemorySafetyAssessment(
                    isSafe = false,
                    warningMessage = "Not Supported on This Device: This model requires 64-bit architecture and more computation/memory power than is available on this 32-bit device.",
                    availableRamGb = availRamGb,
                    requiredRamGb = requiredRamGb,
                    totalRamGb = totalRamGb
                )
            }
        }

        val safeThreshold = (totalRamGb * 0.75).coerceAtMost(availRamGb + 0.5)
        val isSafe = requiredRamGb <= safeThreshold

        val warning = if (!isSafe) {
            if (requiredRamGb > totalRamGb * 0.85) {
                "Critical: This model requires ~%.1f GB RAM, which exceeds your device's physical memory boundaries (%.1f GB total). Loading this may trigger Android's Low Memory Killer (LMK).".format(Locale.US, requiredRamGb, totalRamGb)
            } else {
                "RAM Notice: Device currently has %.1f GB available. Model requires ~%.1f GB RAM, which may slow multitasking.".format(Locale.US, availRamGb, requiredRamGb)
            }
        } else null

        return MemorySafetyAssessment(
            isSafe = isSafe,
            warningMessage = warning,
            availableRamGb = availRamGb,
            requiredRamGb = requiredRamGb,
            totalRamGb = totalRamGb
        )
    }

    fun checkStorageSafety(
        context: Context,
        modelSizeBytes: Long,
        safetyBufferBytes: Long = 1024L * 1024L * 1024L
    ): StorageSafetyAssessment {
        val availableBytes = mockAvailableBytesForTesting ?: try {
            val stat = StatFs(context.filesDir.path)
            stat.availableBytes
        } catch (_: Throwable) {
            10L * 1024L * 1024L * 1024L
        }

        val requiredBytes = modelSizeBytes + safetyBufferBytes
        val isSafe = availableBytes >= requiredBytes
        val availableGb = availableBytes / 1_073_741_824.0
        val requiredGb = requiredBytes / 1_073_741_824.0
        val missingGb = if (isSafe) 0.0 else (requiredBytes - availableBytes) / 1_073_741_824.0

        val warningMessage = if (!isSafe) {
            String.format(
                Locale.US,
                "Requires %.1f GB free (%.1f GB available)",
                requiredGb,
                availableGb
            )
        } else null

        return StorageSafetyAssessment(
            isSafe = isSafe,
            availableBytes = availableBytes,
            requiredBytes = requiredBytes,
            availableGb = availableGb,
            requiredGb = requiredGb,
            missingGb = missingGb,
            warningMessage = warningMessage
        )
    }
}
