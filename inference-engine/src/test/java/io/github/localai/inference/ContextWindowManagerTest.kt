package io.github.localai.inference

import io.github.localai.inference.api.ChatRole
import io.github.localai.inference.api.InferenceChatMessage
import io.github.localai.inference.context.ContextWindowManager
import org.junit.Assert.*
import org.junit.Test

class ContextWindowManagerTest {

    @Test
    fun testTokenEstimation() {
        assertEquals(0, ContextWindowManager.estimateTokens(""))
        // 38 characters / 3.8 = 10 tokens
        val text38 = "12345678901234567890123456789012345678"
        assertEquals(10, ContextWindowManager.estimateTokens(text38))
    }

    @Test
    fun testThinkingTagStripping() {
        val input = "<think>\nStep 1: check facts\nStep 2: formulate answer\n</think>\nQuantum computing uses qubits."
        val stripped = ContextWindowManager.stripThinkingTags(input)
        assertEquals("Quantum computing uses qubits.", stripped)
        assertFalse(stripped.contains("<think>"))
        assertFalse(stripped.contains("</think>"))
    }

    @Test
    fun testThinkingTagStrippingUnclosed() {
        val input = "<think>\nThinking in progress..."
        val stripped = ContextWindowManager.stripThinkingTags(input)
        assertEquals("", stripped)
    }

    @Test
    fun testChronologicalPreservationOnOverflow() {
        val messages = mutableListOf<InferenceChatMessage>()
        for (i in 1..20) {
            messages.add(InferenceChatMessage(role = ChatRole.USER, text = "Turn $i question with some substantial context to consume budget."))
            messages.add(InferenceChatMessage(role = ChatRole.ASSISTANT, text = "Turn $i answer with substantial detail to consume tokens."))
        }

        val budgeted = ContextWindowManager.selectMessagesForBudget(messages, budgetTokens = 100)
        assertTrue("Budget should constrain messages", budgeted.size < messages.size)
        assertTrue("Budget should retain at least the latest message", budgeted.isNotEmpty())

        // Verify chronological preservation: latest turn must be present at the end
        val lastMessage = budgeted.last()
        assertEquals(messages.last().text, lastMessage.text)

        // Verify order: each message index in original list must be strictly increasing
        var lastOriginalIndex = -1
        for (msg in budgeted) {
            val idx = messages.indexOf(msg)
            assertTrue("Ordering must be strictly chronological", idx > lastOriginalIndex)
            lastOriginalIndex = idx
        }
    }

    @Test
    fun testPinnedDocumentRetentionAcrossTurns() {
        val messages = mutableListOf<InferenceChatMessage>()
        // Turn 1 has an attached document context
        messages.add(
            InferenceChatMessage(
                role = ChatRole.USER,
                text = "Analyze this contract.",
                extractedContext = "[Contract Body: Terms and Conditions Article 1 through 10]"
            )
        )
        messages.add(InferenceChatMessage(role = ChatRole.ASSISTANT, text = "I have reviewed Article 1 through 10."))

        // Add 15 filler turns to overflow conversation budget
        for (i in 2..16) {
            messages.add(InferenceChatMessage(role = ChatRole.USER, text = "Follow-up question $i with extra long content."))
            messages.add(InferenceChatMessage(role = ChatRole.ASSISTANT, text = "Follow-up answer $i with extra long explanation."))
        }

        val prompt = ContextWindowManager.buildPrompt(
            systemPrompt = "You are a legal assistant.",
            messages = messages,
            modelId = "meta-llama/Llama-3.2-1B-Instruct",
            totalContextLength = 512
        )

        // The Turn 1 message was evicted, but its extractedContext MUST be pinned into system prompt!
        assertTrue(prompt.contains("[Pinned Reference Context from Earlier Attachments]"))
        assertTrue(prompt.contains("[Contract Body: Terms and Conditions Article 1 through 10]"))
    }

    @Test
    fun testThinkingSuppressionDirective() {
        val prompt = ContextWindowManager.buildPrompt(
            systemPrompt = "You are an assistant.",
            messages = listOf(InferenceChatMessage(role = ChatRole.USER, text = "Answer directly.")),
            modelId = "deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B",
            useThinking = false,
            supportsThinking = true
        )

        assertTrue(prompt.contains("Do not include chain-of-thought or <think> tags."))
    }
}
