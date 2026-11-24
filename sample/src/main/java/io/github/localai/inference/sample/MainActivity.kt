package io.github.localai.inference.sample

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.localai.inference.api.ChatRole
import io.github.localai.inference.api.InferenceChatMessage
import io.github.localai.inference.api.LocalInference
import io.github.localai.inference.hardware.PerformanceMode
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SampleMainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            textSize = 14f
            setPadding(32, 48, 32, 48)
        }
        setContentView(textView)

        lifecycleScope.launch {
            val silicon = LocalInference.detectSilicon(this@MainActivity)
            val profile = LocalInference.profileHardware(this@MainActivity, PerformanceMode.BALANCED)

            val sampleMessages = listOf(
                InferenceChatMessage(role = ChatRole.SYSTEM, text = "You are a concise on-device assistant."),
                InferenceChatMessage(role = ChatRole.USER, text = "Hello! What hardware acceleration is active?")
            )

            val formattedPrompt = LocalInference.formatChatPrompt(
                modelId = "meta-llama/Llama-3.2-1B-Instruct",
                systemPrompt = "You are a concise on-device assistant.",
                messages = sampleMessages
            )

            val displayInfo = buildString {
                appendLine("=== Android Inference Engine Sample ===")
                appendLine()
                appendLine("Silicon Acceleration:")
                appendLine("  Engine: ${silicon.engineLabel}")
                appendLine("  Architecture: ${silicon.architectureLabel}")
                appendLine("  KleidiAI: ${silicon.hasKleidiAi}")
                appendLine("  DotProd: ${silicon.hasDotProd}, I8MM: ${silicon.hasI8mm}")
                appendLine("  GPU: ${silicon.gpuLabel}")
                appendLine()
                appendLine("Hardware Profile:")
                appendLine("  Tier: ${profile.tier}")
                appendLine("  GPU Layers: ${profile.recommendedGpuLayers}")
                appendLine("  Threads: ${profile.nThreads} (Perf Cores: ${profile.perfCores})")
                appendLine("  Flash Attention: ${profile.useFlashAttn}")
                appendLine("  Context Length: ${profile.contextLength}")
                appendLine()
                appendLine("Formatted Prompt Preview:")
                appendLine(formattedPrompt)
            }

            Log.i(TAG, displayInfo)
            textView.text = displayInfo
        }
    }
}
