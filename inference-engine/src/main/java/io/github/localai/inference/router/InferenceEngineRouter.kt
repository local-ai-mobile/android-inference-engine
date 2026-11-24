package io.github.localai.inference.router

import io.github.localai.inference.api.EngineType
import io.github.localai.inference.hardware.ArchitectureManager
import java.io.File

object InferenceEngineRouter {

    fun determineEngineType(modelId: String, modelPath: String? = null): EngineType {
        val lowerId = modelId.lowercase()
        val lowerPath = (modelPath ?: "").lowercase()

        // 0. Gemini Nano 4 via Android AICore (Requires 64-bit hardware)
        if (lowerId.contains("gemini-nano-4-system") || lowerId == "google/gemini-nano-4-system" || lowerId.contains("aicore")) {
            return if (ArchitectureManager.is32Bit) {
                EngineType.LITERT_PLAY_SERVICES
            } else {
                EngineType.AICORE_SYSTEM_NANO
            }
        }

        // 1. Inspect physical file magic bytes
        if (modelPath != null && modelPath.isNotBlank()) {
            val file = File(modelPath)
            if (file.exists() && file.length() >= 4L) {
                val isGguf = try {
                    file.inputStream().use { input ->
                        val magic = ByteArray(4)
                        val read = input.read(magic)
                        read == 4 && magic[0] == 'G'.code.toByte() && magic[1] == 'G'.code.toByte() &&
                                magic[2] == 'U'.code.toByte() && magic[3] == 'F'.code.toByte()
                    }
                } catch (_: Exception) {
                    false
                }
                if (isGguf) return EngineType.LLAMA_CPP

                // Check for TFLite flatbuffer magic ("TFL3" at byte offset 4..7)
                val isTflite = try {
                    if (file.length() >= 8L) {
                        file.inputStream().use { input ->
                            val buffer = ByteArray(8)
                            val read = input.read(buffer)
                            read == 8 && buffer[4] == 'T'.code.toByte() && buffer[5] == 'F'.code.toByte() &&
                                    buffer[6] == 'L'.code.toByte() && buffer[7] == '3'.code.toByte()
                        }
                    } else false
                } catch (_: Exception) {
                    false
                }
                if (isTflite) return EngineType.LITERT_PLAY_SERVICES
            }

            // Extension check fallback
            if (lowerPath.endsWith(".gguf")) return EngineType.LLAMA_CPP
            if (lowerPath.endsWith(".litert") ||
                lowerPath.endsWith(".litertlm") ||
                lowerPath.endsWith(".task") ||
                lowerPath.endsWith(".bin") ||
                lowerPath.endsWith(".tflite")
            ) {
                return EngineType.LITERT_PLAY_SERVICES
            }
        }

        // 2. Explicit GGUF in ID
        if (lowerId.endsWith(".gguf") || lowerId.contains("-gguf")) {
            return EngineType.LLAMA_CPP
        }

        // 3. Explicit LiteRT format in ID
        if (lowerId.endsWith(".litertlm") ||
            lowerId.endsWith(".task") ||
            lowerId.endsWith(".bin") ||
            lowerId.endsWith(".tflite") ||
            lowerId.endsWith(".litert")
        ) {
            return EngineType.LITERT_PLAY_SERVICES
        }

        if (lowerId.contains("gemma-4-e2b-it-litert") || lowerId.contains("gemma-4-e4b-it-litert")) {
            return EngineType.LLAMA_CPP
        }

        if (lowerId.contains("litert")) {
            return EngineType.LITERT_PLAY_SERVICES
        }

        return EngineType.LLAMA_CPP
    }

    fun supportsMtp(modelId: String): Boolean {
        val lower = modelId.lowercase()
        return lower.contains("gemma-4") || lower.contains("mtp") || lower.contains("nano-4")
    }

    fun supportsVision(modelId: String): Boolean {
        val lower = modelId.lowercase()
        return lower.contains("gemma-4") ||
                lower.contains("vision") ||
                lower.contains("vl") ||
                lower.contains("nano-4") ||
                lower.contains("llava")
    }

    fun supportsThinking(modelId: String): Boolean {
        val lower = modelId.lowercase()
        return lower.contains("deepseek") || lower.contains("reason") || lower.contains("thinking")
    }
}
