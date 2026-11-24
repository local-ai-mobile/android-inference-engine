package io.github.localai.inference.hardware

data class SiliconCapabilities(
    val hasKleidiAi: Boolean,
    val hasDotProd: Boolean,
    val hasI8mm: Boolean,
    val hasNeon: Boolean,
    val hasFp16: Boolean,
    val hasSve: Boolean,
    val isArm64: Boolean,
    val architectureLabel: String,
    val engineLabel: String,
    val gpuDelegateReady: Boolean,
    val gpuLabel: String
)
