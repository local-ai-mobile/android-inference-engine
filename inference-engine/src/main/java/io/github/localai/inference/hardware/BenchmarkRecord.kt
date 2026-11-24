package io.github.localai.inference.hardware

data class BenchmarkRecord(
    val id: Long = 0,
    val modelId: String,
    val paramSizeB: Double,
    val quantization: String,
    val tokensPerSecond: Float,
    val promptEvalTps: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)
