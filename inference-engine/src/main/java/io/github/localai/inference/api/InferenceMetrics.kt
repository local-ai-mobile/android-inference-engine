package io.github.localai.inference.api

data class InferenceMetrics(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val promptEvalTps: Float? = null,
    val generationTps: Float? = null,
    val ramUsageMb: Long? = null,
    val accelerationEngine: String? = null
)
