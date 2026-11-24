package io.github.localai.inference.hardware

import io.github.localai.inference.api.EngineType

data class PredictedSpeed(
    val minTps: Int,
    val maxTps: Int,
    val label: String,
    val icon: String,
    val chipText: String,
    val detailedText: String
)

object TpsPredictionEngine {

    fun predictSpeed(
        paramSizeB: Double,
        quantization: String,
        engineType: EngineType = EngineType.LLAMA_CPP,
        historicalBenchmarks: List<BenchmarkRecord> = emptyList(),
        cpuMaxFreqGhz: Double = 2.4,
        perfCoreCount: Int = 4,
        is32Bit: Boolean = false
    ): PredictedSpeed {
        if (engineType == EngineType.AICORE_SYSTEM_NANO) {
            return formatSpeed(30, 45, "Ultra Fast (NPU)", "⚡")
        }

        val validBenchmarks = historicalBenchmarks.filter { it.tokensPerSecond > 0.5f && it.paramSizeB > 0.1 }
        val targetQuantFactor = getQuantizationFactor(quantization)
        val targetParam = paramSizeB.coerceAtLeast(0.3)

        val estimatedTps: Double = if (validBenchmarks.isNotEmpty()) {
            val avgK = validBenchmarks.map { record ->
                val qFactor = getQuantizationFactor(record.quantization)
                record.tokensPerSecond * record.paramSizeB * qFactor
            }.average()
            avgK / (targetParam * targetQuantFactor)
        } else {
            val baseThroughput = when {
                is32Bit -> 10.0
                cpuMaxFreqGhz >= 2.8 && perfCoreCount >= 4 -> 45.0
                cpuMaxFreqGhz >= 2.2 && perfCoreCount >= 2 -> 28.0
                else -> 16.0
            }
            baseThroughput / (targetParam * targetQuantFactor)
        }

        val roundedTps = estimatedTps.coerceAtLeast(1.0)
        val minTps = (roundedTps * 0.85).toInt().coerceAtLeast(1)
        val maxTps = (roundedTps * 1.15).toInt().coerceAtLeast(minTps + 1)

        val (label, icon) = when {
            roundedTps >= 15.0 -> Pair("Very Fast", "⚡")
            roundedTps >= 9.0  -> Pair("Fast", "🚀")
            roundedTps >= 5.0  -> Pair("Moderate", "⏱️")
            else               -> Pair("Slow", "⏳")
        }

        return formatSpeed(minTps, maxTps, label, icon)
    }

    private fun getQuantizationFactor(quant: String): Double {
        val q = quant.uppercase()
        return when {
            q.contains("Q8") -> 1.45
            q.contains("Q6") -> 1.25
            q.contains("Q5") -> 1.10
            q.contains("Q3") || q.contains("IQ3") -> 0.85
            q.contains("Q2") || q.contains("IQ2") -> 0.70
            else -> 1.0
        }
    }

    private fun formatSpeed(minTps: Int, maxTps: Int, label: String, icon: String): PredictedSpeed {
        val chipText = "$icon ~$minTps–$maxTps tps"
        val detailedText = "$icon Expected Speed: ~$minTps–$maxTps tokens/sec ($label)"
        return PredictedSpeed(minTps, maxTps, label, icon, chipText, detailedText)
    }
}
