package io.github.localai.inference.monitor

enum class ThermalLevel(val label: String, val isThrottled: Boolean) {
    NORMAL("Normal (Cool)", false),
    LIGHT("Light Warmth", false),
    MODERATE("Moderate Heat (Adaptive CPU Throttling Active)", true),
    SEVERE("Severe Heat (Cooling Active)", true),
    CRITICAL("Critical Thermal Alert", true),
    EMERGENCY("Emergency Cooldown", true)
}
