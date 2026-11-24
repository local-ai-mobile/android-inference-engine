package io.github.localai.inference

import io.github.localai.inference.hardware.DeviceTier
import io.github.localai.inference.hardware.GpuBrand
import io.github.localai.inference.hardware.InferenceProfile
import io.github.localai.inference.hardware.PerformanceMode
import io.github.localai.inference.monitor.DeviceThermalMonitor
import io.github.localai.inference.monitor.ThermalLevel
import org.junit.Assert.*
import org.junit.Test

class ThermalThrottlingTest {

    private val baseProfile = InferenceProfile(
        gpuBrand = GpuBrand.ADRENO,
        recommendedGpuLayers = 48,
        nThreads = 4,
        nThreadsBatch = 6,
        nBatch = 512,
        nUbatch = 128,
        contextLength = 2048,
        useFlashAttn = true,
        useMlock = false,
        tier = DeviceTier.HIGH,
        performanceMode = PerformanceMode.HIGH_PERFORMANCE,
        totalCores = 8,
        perfCores = 4
    )

    @Test
    fun testNormalStatusPreservesProfile() {
        val throttled = DeviceThermalMonitor.applyThermalThrottling(baseProfile, ThermalLevel.NORMAL)
        assertEquals(baseProfile.nThreads, throttled.nThreads)
        assertEquals(baseProfile.recommendedGpuLayers, throttled.recommendedGpuLayers)
        assertEquals(baseProfile.useFlashAttn, throttled.useFlashAttn)
        assertEquals(PerformanceMode.HIGH_PERFORMANCE, throttled.performanceMode)
    }

    @Test
    fun testModerateStatusThrottles() {
        val throttled = DeviceThermalMonitor.applyThermalThrottling(baseProfile, ThermalLevel.MODERATE)
        assertEquals(3, throttled.nThreads) // (perfCores 4 - 1) = 3
        assertEquals((48 * 0.6).toInt(), throttled.recommendedGpuLayers) // 28
        assertEquals(PerformanceMode.HIGH_PERFORMANCE, throttled.performanceMode) // Preserves user preference
    }

    @Test
    fun testSevereStatusEnforcesCooldown() {
        val throttled = DeviceThermalMonitor.applyThermalThrottling(baseProfile, ThermalLevel.SEVERE)
        assertEquals(2, throttled.nThreads)
        assertTrue(throttled.recommendedGpuLayers <= 16)
        assertFalse(throttled.useFlashAttn)
        assertEquals(PerformanceMode.HIGH_PERFORMANCE, throttled.performanceMode) // Still preserves user preference
    }

    @Test
    fun testEmergencyStatusClampsToMinimum() {
        val throttled = DeviceThermalMonitor.applyThermalThrottling(baseProfile, ThermalLevel.EMERGENCY)
        assertEquals(2, throttled.nThreads)
        assertTrue(throttled.recommendedGpuLayers <= 16)
        assertFalse(throttled.useFlashAttn)
    }
}
