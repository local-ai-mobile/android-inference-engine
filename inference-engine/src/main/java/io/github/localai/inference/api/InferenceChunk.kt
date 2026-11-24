package io.github.localai.inference.api

data class InferenceChunk(
    val text: String,
    val isFinished: Boolean = false,
    val tokensPerSecond: Float? = null,
    val promptEvalTps: Float? = null,
    val ramUsageMb: Long? = null,
    val accelerationEngine: String? = null,
    val metrics: InferenceMetrics? = null
)
