package io.github.localai.inference.hardware

data class InferenceProfile(
    val gpuBrand: GpuBrand,
    val recommendedGpuLayers: Int,
    val nThreads: Int,
    val nThreadsBatch: Int,
    val nBatch: Int,
    val nUbatch: Int,
    val contextLength: Int,
    val useFlashAttn: Boolean,
    val useMlock: Boolean = false,
    val tier: DeviceTier,
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED,
    val totalRamGb: Double = 0.0,
    val totalCores: Int = Runtime.getRuntime().availableProcessors(),
    val perfCores: Int = 4
)
