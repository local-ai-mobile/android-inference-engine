package io.github.localai.inference.api

import android.content.Context
import android.graphics.Bitmap
import io.github.localai.inference.context.ChatTemplateEngine
import io.github.localai.inference.context.ContextWindowManager
import io.github.localai.inference.engine.AiCoreEngine
import io.github.localai.inference.engine.GeminiNanoTaskApis
import io.github.localai.inference.engine.LiteRTEngine
import io.github.localai.inference.engine.LlamaCppEngine
import io.github.localai.inference.hardware.HardwareProfiler
import io.github.localai.inference.hardware.InferenceProfile
import io.github.localai.inference.hardware.PerformanceMode
import io.github.localai.inference.hardware.SiliconCapabilities
import io.github.localai.inference.router.InferenceEngineRouter

object LocalInference {

    fun profileHardware(
        context: Context,
        mode: PerformanceMode = PerformanceMode.BALANCED
    ): InferenceProfile = HardwareProfiler.profile(context, mode)

    fun detectSilicon(
        context: Context,
        cpuInfoContent: String? = null
    ): SiliconCapabilities = HardwareProfiler.detectSiliconAcceleration(cpuInfoContent)

    fun resolveEngineType(modelId: String, modelPath: String? = null): EngineType =
        InferenceEngineRouter.determineEngineType(modelId, modelPath)

    fun createEngine(context: Context, engineType: EngineType): InferenceEngine = when (engineType) {
        EngineType.LLAMA_CPP -> LlamaCppEngine(context)
        EngineType.LITERT_PLAY_SERVICES -> LiteRTEngine(context)
        EngineType.AICORE_SYSTEM_NANO -> AiCoreEngine(context)
    }

    fun formatChatPrompt(
        modelId: String,
        systemPrompt: String?,
        messages: List<InferenceChatMessage>
    ): String {
        val family = ChatTemplateEngine.detectFamily(modelId)
        return ChatTemplateEngine.format(family, systemPrompt, messages)
    }

    fun buildBudgetedPrompt(
        modelId: String,
        systemPrompt: String?,
        messages: List<InferenceChatMessage>,
        totalContextLength: Int = 2048,
        reservedOutputTokens: Int? = null,
        useThinking: Boolean = true
    ): String {
        val supportsThinking = InferenceEngineRouter.supportsThinking(modelId)
        return ContextWindowManager.buildPrompt(
            systemPrompt = systemPrompt,
            messages = messages,
            modelId = modelId,
            totalContextLength = totalContextLength,
            reservedOutputTokens = reservedOutputTokens,
            useThinking = useThinking,
            supportsThinking = supportsThinking
        )
    }

    suspend fun summarize(context: Context, text: String): String? =
        GeminiNanoTaskApis.summarize(context, text)

    suspend fun proofread(context: Context, text: String): String? =
        GeminiNanoTaskApis.proofread(context, text)

    suspend fun rewrite(context: Context, text: String, style: String = "formal"): String? =
        GeminiNanoTaskApis.rewrite(context, text, style)

    suspend fun describeImage(
        context: Context,
        bitmap: Bitmap,
        prompt: String = "Describe this image in detail."
    ): String? = GeminiNanoTaskApis.describeImage(context, bitmap, prompt)
}
