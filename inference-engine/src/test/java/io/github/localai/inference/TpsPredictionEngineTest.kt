package io.github.localai.inference

import io.github.localai.inference.api.EngineType
import io.github.localai.inference.hardware.BenchmarkRecord
import io.github.localai.inference.hardware.TpsPredictionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TpsPredictionEngineTest {

    @Test
    fun testAiCoreGeminiNanoSpeed() {
        val speed = TpsPredictionEngine.predictSpeed(
            paramSizeB = 4.0,
            quantization = "int4",
            engineType = EngineType.AICORE_SYSTEM_NANO
        )

        assertEquals(30, speed.minTps)
        assertEquals(45, speed.maxTps)
        assertEquals("Ultra Fast (NPU)", speed.label)
        assertEquals("⚡", speed.icon)
    }

    @Test
    fun testBaselineHeuristicFlagship() {
        val speed = TpsPredictionEngine.predictSpeed(
            paramSizeB = 1.0,
            quantization = "Q4_K_M",
            engineType = EngineType.LLAMA_CPP,
            cpuMaxFreqGhz = 3.2,
            perfCoreCount = 4,
            is32Bit = false
        )

        assertTrue(speed.minTps >= 25)
        assertTrue(speed.maxTps > speed.minTps)
        assertEquals("Very Fast", speed.label)
    }

    @Test
    fun testBaselineHeuristicBudget() {
        val speed = TpsPredictionEngine.predictSpeed(
            paramSizeB = 8.0,
            quantization = "Q4_K_M",
            engineType = EngineType.LLAMA_CPP,
            cpuMaxFreqGhz = 1.8,
            perfCoreCount = 2,
            is32Bit = false
        )

        assertTrue(speed.minTps <= 3)
        assertEquals("Slow", speed.label)
    }

    @Test
    fun testEmpiricalSelfLearning() {
        // Record: 3.0B model at 20.0 TPS with standard Q4 (qFactor=1.0) -> K = 60.0
        val history = listOf(
            BenchmarkRecord(
                modelId = "test-model-3b",
                paramSizeB = 3.0,
                quantization = "Q4_K_M",
                tokensPerSecond = 20.0f
            )
        )

        // For a 1.5B model, estimated TPS = 60.0 / 1.5 = 40.0 TPS
        val speed = TpsPredictionEngine.predictSpeed(
            paramSizeB = 1.5,
            quantization = "Q4_K_M",
            historicalBenchmarks = history
        )

        assertTrue("Expected speed around 40 TPS", speed.minTps in 30..40)
        assertTrue(speed.maxTps >= 40)
        assertEquals("Very Fast", speed.label)
    }

    @Test
    fun testQuantizationFactorScaling() {
        val speedQ8 = TpsPredictionEngine.predictSpeed(paramSizeB = 3.0, quantization = "Q8_0")
        val speedQ4 = TpsPredictionEngine.predictSpeed(paramSizeB = 3.0, quantization = "Q4_K_M")
        val speedQ2 = TpsPredictionEngine.predictSpeed(paramSizeB = 3.0, quantization = "IQ2_XXS")

        // Smaller quant -> higher TPS (lower compute/memory burden)
        assertTrue(speedQ2.minTps >= speedQ4.minTps)
        assertTrue(speedQ4.minTps >= speedQ8.minTps)
    }
}
