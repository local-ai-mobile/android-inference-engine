package io.github.localai.inference.api

data class EngineOptions(
    val useNpu: Boolean = true,
    val useGpu: Boolean = true,
    val threadCount: Int = 4,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val repeatPenalty: Float = 1.1f,
    val useMtp: Boolean = false,
    val maxTokens: Int = 1024,
    val stopTokens: List<String> = listOf(
        "<|im_end|>", "<|im_start|>", "<|eot_id|>", "<|end_of_text|>",
        "<end_of_turn>", "<|end|>", "</s>", "[/INST]"
    )
)
