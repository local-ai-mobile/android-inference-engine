package io.github.localai.inference.engine

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.agent.Agent
import com.google.mlkit.genai.agent.AgentEvent
import com.google.mlkit.genai.agent.AgentRunner
import com.google.mlkit.genai.agent.AgentSession
import com.google.mlkit.genai.agent.textTokens
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Content
import kotlinx.coroutines.flow.catch
import java.util.concurrent.ConcurrentHashMap

class GeminiNanoAgentEngine(
    private val context: Context,
    systemInstructions: String = "You are a helpful on-device AI assistant."
) : AutoCloseable {

    companion object {
        private const val TAG = "GeminiNanoAgent"
    }

    private val agent = Agent.builder()
        .setName("LocalAI")
        .setInstructions(systemInstructions)
        .build()

    private val runner = AgentRunner(agent, temperature = 0.0f)
    private val sessions = ConcurrentHashMap<String, AgentSession>()

    fun getOrCreateSession(sessionId: String): AgentSession {
        return sessions.computeIfAbsent(sessionId) { id ->
            runner.getOrCreateSession(id)
        }
    }

    suspend fun runTurn(sessionId: String, userText: String): String {
        val session = getOrCreateSession(sessionId)
        val content = Content.builder().text(userText).build()
        val sb = StringBuilder()
        try {
            session.runAsync(content)
                .catch { e ->
                    Log.e(TAG, "AgentSession error: ${e.message}", e)
                    when {
                        e is GenAiException && e.errorCode == GenAiException.ErrorCode.MAX_TURNS_EXCEEDED ->
                            sb.append("[Agent reached maximum reasoning turns.]")
                        e is GenAiException && e.errorCode == GenAiException.ErrorCode.NOT_AVAILABLE ->
                            sb.append("[Gemini Nano unavailable on this device.]")
                        e is GenAiException && e.errorCode == GenAiException.ErrorCode.BUSY ->
                            sb.append("[On-device AI is busy. Please try again.]")
                        else ->
                            sb.append("[Error: ${e.localizedMessage ?: "Unknown error"}]")
                    }
                }
                .collect { event ->
                    if (event is AgentEvent.TextMessage) {
                        sb.append(event.text)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "runTurn exception: ${e.message}", e)
            sb.append("[Agent error: ${e.localizedMessage ?: "Unknown error"}]")
        }
        return sb.toString().trim()
    }

    suspend fun runSingleTurn(userText: String): String {
        val content = Content.builder().text(userText).build()
        val sb = StringBuilder()
        try {
            runner.runSingleTurnAsync(content)
                .textTokens()
                .catch { e ->
                    Log.e(TAG, "runSingleTurn error: ${e.message}", e)
                    sb.append("[Agent error: ${e.localizedMessage ?: "Error"}]")
                }
                .collect { token ->
                    sb.append(token)
                }
        } catch (e: Exception) {
            Log.e(TAG, "runSingleTurn exception: ${e.message}", e)
            sb.append("[Error: ${e.localizedMessage ?: "Unknown error"}]")
        }
        return sb.toString().trim()
    }

    fun closeSession(sessionId: String) {
        sessions.remove(sessionId)?.close()
    }

    fun closeAllSessions() {
        sessions.values.forEach {
            try { it.close() } catch (_: Exception) {}
        }
        sessions.clear()
    }

    override fun close() {
        closeAllSessions()
        try { runner.close() } catch (_: Exception) {}
    }
}
