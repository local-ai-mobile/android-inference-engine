package io.github.localai.inference.context

import io.github.localai.inference.api.InferenceChatMessage

object ChatTemplateEngine {

    fun detectFamily(modelId: String): ModelFamily {
        val lower = modelId.lowercase()
        return when {
            lower.contains("llama-3") || lower.contains("llama3") -> ModelFamily.LLAMA_3
            lower.contains("phi-3") || lower.contains("phi-4") || lower.contains("phi3") || lower.contains("phi4") -> ModelFamily.PHI
            lower.contains("gemma") -> ModelFamily.GEMMA
            lower.contains("qwen") || lower.contains("deepseek") || lower.contains("smollm") || lower.contains("yi") || lower.contains("chatml") -> ModelFamily.CHATML
            lower.contains("mistral") || lower.contains("llama-2") || lower.contains("llama2") || lower.contains("zephyr") || lower.contains("llama") -> ModelFamily.MISTRAL_LLAMA2
            else -> ModelFamily.GENERIC
        }
    }

    fun format(
        family: ModelFamily,
        systemPrompt: String?,
        messages: List<InferenceChatMessage>
    ): String {
        val trimmedSystem = systemPrompt?.trim()?.takeIf { it.isNotEmpty() }
        val validMessages = messages.filter { it.text.isNotBlank() || it.isUser }

        return when (family) {
            ModelFamily.LLAMA_3 -> formatLlama3(trimmedSystem, validMessages)
            ModelFamily.CHATML -> formatChatML(trimmedSystem, validMessages)
            ModelFamily.GEMMA -> formatGemma(trimmedSystem, validMessages)
            ModelFamily.PHI -> formatPhi(trimmedSystem, validMessages)
            ModelFamily.MISTRAL_LLAMA2 -> formatMistral(trimmedSystem, validMessages)
            ModelFamily.GENERIC -> formatGeneric(trimmedSystem, validMessages)
        }
    }

    private fun formatLlama3(systemPrompt: String?, messages: List<InferenceChatMessage>): String {
        val sb = StringBuilder()
        sb.append("<|begin_of_text|>")
        if (systemPrompt != null) {
            sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
                .append(systemPrompt)
                .append("<|eot_id|>")
        }
        for (msg in messages) {
            val role = if (msg.isUser) "user" else "assistant"
            sb.append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                .append(msg.text.trim())
                .append("<|eot_id|>")
        }
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    private fun formatChatML(systemPrompt: String?, messages: List<InferenceChatMessage>): String {
        val sb = StringBuilder()
        if (systemPrompt != null) {
            sb.append("<|im_start|>system\n")
                .append(systemPrompt)
                .append("<|im_end|>\n")
        }
        for (msg in messages) {
            val role = if (msg.isUser) "user" else "assistant"
            sb.append("<|im_start|>").append(role).append("\n")
                .append(msg.text.trim())
                .append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun formatGemma(systemPrompt: String?, messages: List<InferenceChatMessage>): String {
        val sb = StringBuilder()
        var isFirstUserTurn = true
        for (msg in messages) {
            if (msg.isUser) {
                sb.append("<start_of_turn>user\n")
                if (isFirstUserTurn && systemPrompt != null) {
                    sb.append(systemPrompt).append("\n\n")
                    isFirstUserTurn = false
                }
                sb.append(msg.text.trim())
                    .append("<end_of_turn>\n")
            } else {
                sb.append("<start_of_turn>model\n")
                    .append(msg.text.trim())
                    .append("<end_of_turn>\n")
            }
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun formatPhi(systemPrompt: String?, messages: List<InferenceChatMessage>): String {
        val sb = StringBuilder()
        if (systemPrompt != null) {
            sb.append("<|system|>\n")
                .append(systemPrompt)
                .append("<|end|>\n")
        }
        for (msg in messages) {
            val role = if (msg.isUser) "user" else "assistant"
            sb.append("<|").append(role).append("|>\n")
                .append(msg.text.trim())
                .append("<|end|>\n")
        }
        sb.append("<|assistant|>\n")
        return sb.toString()
    }

    private fun formatMistral(systemPrompt: String?, messages: List<InferenceChatMessage>): String {
        val sb = StringBuilder()
        var isFirstUser = true
        sb.append("<s>")
        for (msg in messages) {
            if (msg.isUser) {
                sb.append("[INST] ")
                if (isFirstUser && systemPrompt != null) {
                    sb.append("<<SYS>>\n").append(systemPrompt).append("\n<</SYS>>\n\n")
                    isFirstUser = false
                }
                sb.append(msg.text.trim()).append(" [/INST]")
            } else {
                sb.append(" ").append(msg.text.trim()).append("</s>")
            }
        }
        return sb.toString()
    }

    private fun formatGeneric(systemPrompt: String?, messages: List<InferenceChatMessage>): String {
        val sb = StringBuilder()
        if (systemPrompt != null) {
            sb.append("System: ").append(systemPrompt).append("\n\n")
        }
        for (msg in messages) {
            val role = if (msg.isUser) "User" else "Assistant"
            sb.append(role).append(": ").append(msg.text.trim()).append("\n")
        }
        sb.append("Assistant: ")
        return sb.toString()
    }
}
