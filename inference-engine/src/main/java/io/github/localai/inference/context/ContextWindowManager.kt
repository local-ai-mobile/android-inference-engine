package io.github.localai.inference.context

import io.github.localai.inference.api.InferenceChatMessage

object ContextWindowManager {

    private const val CHARS_PER_TOKEN = 3.8
    private const val PER_MESSAGE_TOKEN_OVERHEAD = 12
    private const val SYSTEM_PROMPT_TOKEN_OVERHEAD = 24

    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length / CHARS_PER_TOKEN).toInt().coerceAtLeast(1)
    }

    fun estimateMessageTokens(message: InferenceChatMessage): Int {
        val textTokens = estimateTokens(message.text)
        val contextTokens = if (message.extractedContext.isNotBlank()) estimateTokens(message.extractedContext) else 0
        return textTokens + contextTokens + PER_MESSAGE_TOKEN_OVERHEAD
    }

    fun stripThinkingTags(text: String): String {
        if (!text.contains("<think>")) return text.trim()
        val withoutBlocks = text.replace(Regex("""<think>.*?</think>""", setOf(RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("""<think>.*""", setOf(RegexOption.DOT_MATCHES_ALL)), "")
        return withoutBlocks.trim()
    }

    fun selectMessagesForBudget(
        messages: List<InferenceChatMessage>,
        budgetTokens: Int
    ): List<InferenceChatMessage> {
        val valid = messages.filter { it.text.isNotBlank() || it.isUser || it.extractedContext.isNotBlank() }
        if (valid.isEmpty()) return emptyList()

        val selected = mutableListOf<InferenceChatMessage>()
        var accumulatedTokens = 0

        // Traverse backwards from newest to oldest
        for (i in valid.indices.reversed()) {
            val msg = valid[i]
            val cost = estimateMessageTokens(msg)

            if (selected.isEmpty()) {
                selected.add(msg)
                accumulatedTokens += cost
                continue
            }

            if (accumulatedTokens + cost <= budgetTokens) {
                selected.add(msg)
                accumulatedTokens += cost
            } else {
                break
            }
        }

        return selected.reversed()
    }

    fun buildPrompt(
        systemPrompt: String?,
        messages: List<InferenceChatMessage>,
        modelId: String,
        totalContextLength: Int = 2048,
        reservedOutputTokens: Int? = null,
        safetyMarginTokens: Int = 64,
        useThinking: Boolean = true,
        supportsThinking: Boolean = false
    ): String {
        val family = ChatTemplateEngine.detectFamily(modelId)
        val effectiveReservedOutput = reservedOutputTokens ?: (totalContextLength * 0.25).toInt().coerceIn(128, 512)

        val baseSystemPrompt = when {
            supportsThinking && !useThinking -> {
                val suppressionDirective = "Do not include chain-of-thought or <think> tags. Provide your final response directly."
                if (systemPrompt.isNullOrBlank()) suppressionDirective else "${systemPrompt.trim()}\n\n$suppressionDirective"
            }
            else -> systemPrompt?.trim()
        }

        val maxInputBudget = (totalContextLength - effectiveReservedOutput - safetyMarginTokens).coerceAtLeast(256)
        val systemTokens = if (!baseSystemPrompt.isNullOrBlank()) {
            estimateTokens(baseSystemPrompt) + SYSTEM_PROMPT_TOKEN_OVERHEAD
        } else 0

        val conversationBudget = (maxInputBudget - systemTokens).coerceAtLeast(150)

        val allPinnedDocs = messages.filter { it.extractedContext.isNotBlank() }
            .map { it.extractedContext.trim() }
            .distinct()

        val cleanedMessages = messages.map { msg ->
            if (!msg.isUser) {
                val stripped = stripThinkingTags(msg.text)
                msg.copy(text = if (stripped.isNotBlank()) stripped else msg.text)
            } else {
                msg
            }
        }

        val budgetedMessages = selectMessagesForBudget(cleanedMessages, conversationBudget)

        val retainedDocIds = budgetedMessages.filter { it.extractedContext.isNotBlank() }
            .map { it.extractedContext.trim() }
            .toSet()

        val evictedDocs = allPinnedDocs.filter { !retainedDocIds.contains(it) }

        val effectiveSystemPrompt = if (evictedDocs.isNotEmpty()) {
            val pinnedBlock = buildString {
                append("[Pinned Reference Context from Earlier Attachments]\n")
                evictedDocs.forEach { doc ->
                    append(doc).append("\n\n")
                }
            }.trim()

            if (baseSystemPrompt.isNullOrBlank()) pinnedBlock else "$baseSystemPrompt\n\n$pinnedBlock"
        } else {
            baseSystemPrompt
        }

        val formattedMessages = budgetedMessages.map { msg ->
            if (msg.isUser && msg.extractedContext.isNotBlank()) {
                val combinedText = if (msg.text.isNotBlank()) {
                    "${msg.extractedContext}\n\n${msg.text}".trim()
                } else {
                    msg.extractedContext.trim()
                }
                msg.copy(text = combinedText)
            } else {
                msg
            }
        }

        return ChatTemplateEngine.format(
            family = family,
            systemPrompt = effectiveSystemPrompt,
            messages = formattedMessages
        )
    }
}
