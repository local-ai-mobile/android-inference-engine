package io.github.localai.inference

import io.github.localai.inference.api.ChatRole
import io.github.localai.inference.api.InferenceChatMessage
import io.github.localai.inference.context.ChatTemplateEngine
import io.github.localai.inference.context.ModelFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTemplateEngineTest {

    @Test
    fun testFamilyDetection() {
        assertEquals(ModelFamily.LLAMA_3, ChatTemplateEngine.detectFamily("meta-llama/Llama-3.2-1B-Instruct"))
        assertEquals(ModelFamily.CHATML, ChatTemplateEngine.detectFamily("Qwen/Qwen2.5-1.5B-Instruct"))
        assertEquals(ModelFamily.CHATML, ChatTemplateEngine.detectFamily("deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B"))
        assertEquals(ModelFamily.GEMMA, ChatTemplateEngine.detectFamily("google/gemma-2-2b-it"))
        assertEquals(ModelFamily.PHI, ChatTemplateEngine.detectFamily("microsoft/Phi-3.5-mini-instruct"))
        assertEquals(ModelFamily.MISTRAL_LLAMA2, ChatTemplateEngine.detectFamily("mistralai/Mistral-7B-Instruct-v0.3"))
        assertEquals(ModelFamily.GENERIC, ChatTemplateEngine.detectFamily("unknown-custom-model"))
    }

    @Test
    fun testLlama3Formatting() {
        val messages = listOf(
            InferenceChatMessage(role = ChatRole.USER, text = "Hello Llama!")
        )
        val prompt = ChatTemplateEngine.format(
            family = ModelFamily.LLAMA_3,
            systemPrompt = "You are a helpful assistant.",
            messages = messages
        )

        assertTrue(prompt.startsWith("<|begin_of_text|>"))
        assertTrue(prompt.contains("<|start_header_id|>system<|end_header_id|>\n\nYou are a helpful assistant.<|eot_id|>"))
        assertTrue(prompt.contains("<|start_header_id|>user<|end_header_id|>\n\nHello Llama!<|eot_id|>"))
        assertTrue(prompt.endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n"))
    }

    @Test
    fun testChatMLFormatting() {
        val messages = listOf(
            InferenceChatMessage(role = ChatRole.USER, text = "Hello ChatML!")
        )
        val prompt = ChatTemplateEngine.format(
            family = ModelFamily.CHATML,
            systemPrompt = "System instructions.",
            messages = messages
        )

        assertTrue(prompt.contains("<|im_start|>system\nSystem instructions.<|im_end|>"))
        assertTrue(prompt.contains("<|im_start|>user\nHello ChatML!<|im_end|>"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun testGemmaFormatting() {
        val messages = listOf(
            InferenceChatMessage(role = ChatRole.USER, text = "Hello Gemma!")
        )
        val prompt = ChatTemplateEngine.format(
            family = ModelFamily.GEMMA,
            systemPrompt = "System note.",
            messages = messages
        )

        assertTrue(prompt.contains("<start_of_turn>user\nSystem note.\n\nHello Gemma!<end_of_turn>"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun testPhiFormatting() {
        val messages = listOf(
            InferenceChatMessage(role = ChatRole.USER, text = "Hello Phi!")
        )
        val prompt = ChatTemplateEngine.format(
            family = ModelFamily.PHI,
            systemPrompt = "System note.",
            messages = messages
        )

        assertTrue(prompt.contains("<|system|>\nSystem note.<|end|>"))
        assertTrue(prompt.contains("<|user|>\nHello Phi!<|end|>"))
        assertTrue(prompt.endsWith("<|assistant|>\n"))
    }

    @Test
    fun testMistralFormatting() {
        val messages = listOf(
            InferenceChatMessage(role = ChatRole.USER, text = "Hello Mistral!")
        )
        val prompt = ChatTemplateEngine.format(
            family = ModelFamily.MISTRAL_LLAMA2,
            systemPrompt = "System note.",
            messages = messages
        )

        assertTrue(prompt.startsWith("<s>[INST] <<SYS>>\nSystem note.\n<</SYS>>\n\nHello Mistral! [/INST]"))
    }

    @Test
    fun testGenericFormatting() {
        val messages = listOf(
            InferenceChatMessage(role = ChatRole.USER, text = "Hello!")
        )
        val prompt = ChatTemplateEngine.format(
            family = ModelFamily.GENERIC,
            systemPrompt = "System note.",
            messages = messages
        )

        assertTrue(prompt.contains("System: System note.\n\nUser: Hello!\nAssistant: "))
    }
}
