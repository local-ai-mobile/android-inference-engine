package io.github.localai.inference.api

import kotlinx.coroutines.flow.Flow

interface InferenceEngine {
    val engineType: EngineType

    suspend fun loadModel(
        modelPath: String,
        modelId: String,
        options: EngineOptions = EngineOptions()
    ): Boolean

    fun generateResponses(
        prompt: String,
        attachmentUris: List<String> = emptyList(),
        options: EngineOptions = EngineOptions()
    ): Flow<String>

    fun generateStream(
        prompt: String,
        attachmentUris: List<String> = emptyList(),
        options: EngineOptions = EngineOptions()
    ): Flow<InferenceChunk>

    fun unloadModel()

    fun isModelLoaded(): Boolean

    fun getActiveModelId(): String?
}
