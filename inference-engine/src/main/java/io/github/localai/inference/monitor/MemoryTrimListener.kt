package io.github.localai.inference.monitor

enum class MemoryRiskLevel {
    NORMAL,
    MODERATE,
    CRITICAL
}

data class MemorySnapshot(
    val rssMb: Long,
    val nativeHeapMb: Long,
    val javaHeapMb: Long,
    val availMemMb: Long,
    val riskLevel: MemoryRiskLevel
)

interface MemoryTrimListener {
    fun onTrimUiCaches()
    fun onTrimKvCaches()
    fun onCriticalMemoryPressure(shouldEvictModel: Boolean)
}
