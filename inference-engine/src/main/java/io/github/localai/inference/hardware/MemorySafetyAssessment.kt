package io.github.localai.inference.hardware

data class MemorySafetyAssessment(
    val isSafe: Boolean,
    val warningMessage: String?,
    val availableRamGb: Double,
    val requiredRamGb: Double,
    val totalRamGb: Double
)
