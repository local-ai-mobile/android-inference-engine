package io.github.localai.inference.router

import io.github.localai.inference.api.EngineType

data class ModelCapabilities(
    val supportsMtp: Boolean,
    val supportsVision: Boolean,
    val supportsThinking: Boolean = false,
    val recommendedEngine: EngineType
)
