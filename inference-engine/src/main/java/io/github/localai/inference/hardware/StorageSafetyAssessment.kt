package io.github.localai.inference.hardware

data class StorageSafetyAssessment(
    val isSafe: Boolean,
    val availableBytes: Long,
    val requiredBytes: Long,
    val availableGb: Double,
    val requiredGb: Double,
    val missingGb: Double,
    val warningMessage: String?
)
