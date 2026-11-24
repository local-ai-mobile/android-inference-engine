package io.github.localai.inference

import io.github.localai.inference.api.EngineType
import io.github.localai.inference.hardware.ArchitectureManager
import io.github.localai.inference.router.InferenceEngineRouter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class InferenceRouterTest {

    @Before
    fun setUp() {
        ArchitectureManager.set64BitForTesting(true)
    }

    @After
    fun tearDown() {
        ArchitectureManager.set64BitForTesting(null)
    }

    @Test
    fun testGgufMagicBytesOnDisk() {
        val tempFile = File.createTempFile("test_model", ".litert")
        try {
            tempFile.outputStream().use {
                it.write(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x01, 0x00, 0x00, 0x00))
            }
            val engine = InferenceEngineRouter.determineEngineType(
                modelId = "custom-model",
                modelPath = tempFile.absolutePath
            )
            assertEquals("Magic bytes GGUF must override .litert extension", EngineType.LLAMA_CPP, engine)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testTfliteMagicBytesOnDisk() {
        val tempFile = File.createTempFile("test_model", ".bin")
        try {
            // TFL3 at offset 4..7
            tempFile.outputStream().use {
                it.write(byteArrayOf(0x00, 0x00, 0x00, 0x00, 'T'.code.toByte(), 'F'.code.toByte(), 'L'.code.toByte(), '3'.code.toByte()))
            }
            val engine = InferenceEngineRouter.determineEngineType(
                modelId = "custom-model",
                modelPath = tempFile.absolutePath
            )
            assertEquals(EngineType.LITERT_PLAY_SERVICES, engine)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testAiCoreRouting64Bit() {
        val engine = InferenceEngineRouter.determineEngineType("google/gemini-nano-4-system")
        assertEquals(EngineType.AICORE_SYSTEM_NANO, engine)
    }

    @Test
    fun testAiCoreRouting32BitFallback() {
        ArchitectureManager.set64BitForTesting(false)
        val engine = InferenceEngineRouter.determineEngineType("google/gemini-nano-4-system")
        assertEquals(EngineType.LITERT_PLAY_SERVICES, engine)
    }

    @Test
    fun testFileExtensionFallbacks() {
        assertEquals(EngineType.LLAMA_CPP, InferenceEngineRouter.determineEngineType("model-q4.gguf"))
        assertEquals(EngineType.LITERT_PLAY_SERVICES, InferenceEngineRouter.determineEngineType("model.task"))
        assertEquals(EngineType.LITERT_PLAY_SERVICES, InferenceEngineRouter.determineEngineType("model.litertlm"))
    }

    @Test
    fun testMtpAndVisionCapabilities() {
        assertTrue(InferenceEngineRouter.supportsMtp("google/gemma-4-e2b-it-litert"))
        assertTrue(InferenceEngineRouter.supportsVision("google/gemma-4-e2b-it-litert"))
        assertTrue(InferenceEngineRouter.supportsVision("llava-v1.6-7b"))
        assertFalse(InferenceEngineRouter.supportsMtp("meta-llama/Llama-3.2-1B-Instruct"))
        assertFalse(InferenceEngineRouter.supportsVision("meta-llama/Llama-3.2-1B-Instruct"))
    }

    @Test
    fun testThinkingCapabilities() {
        assertTrue(InferenceEngineRouter.supportsThinking("deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B"))
        assertFalse(InferenceEngineRouter.supportsThinking("meta-llama/Llama-3.2-1B-Instruct"))
    }
}
