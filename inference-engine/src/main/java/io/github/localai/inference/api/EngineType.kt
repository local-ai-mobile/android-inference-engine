package io.github.localai.inference.api

enum class EngineType(
    val displayName: String,
    val badge: String,
    val accelerationLabel: String
) {
    LLAMA_CPP(
        displayName = "llama.cpp",
        badge = "GGUF Universal",
        accelerationLabel = "llama.cpp Native Engine • CPU & GPU"
    ),
    LITERT_PLAY_SERVICES(
        displayName = "LiteRT (Play Services)",
        badge = "LiteRT NPU/GPU",
        accelerationLabel = "CompiledModel Play Services • NPU & GPU"
    ),
    AICORE_SYSTEM_NANO(
        displayName = "Google Gemini Nano 4 (AICore)",
        badge = "System NPU • 0 MB",
        accelerationLabel = "Android AICore • System NPU & DSP"
    )
}
