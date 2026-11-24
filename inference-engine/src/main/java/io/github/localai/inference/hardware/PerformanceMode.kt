package io.github.localai.inference.hardware

enum class PerformanceMode(val displayName: String, val description: String) {
    BATTERY_SAVER("Battery Saver", "Low heat, cool device, minimal battery drain"),
    BALANCED("Balanced", "Optimal speed, thermal safety & memory efficiency"),
    HIGH_PERFORMANCE("High Performance", "Maximum GPU offload and peak tokens/second")
}
