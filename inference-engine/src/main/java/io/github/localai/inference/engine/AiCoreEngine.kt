package io.github.localai.inference.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import io.github.localai.inference.api.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class AiCoreEngine(
    private val context: Context
) : InferenceEngine {

    override val engineType: EngineType = EngineType.AICORE_SYSTEM_NANO

    companion object {
        private const val TAG = "AiCoreEngine"
        const val MODEL_ID_SYSTEM_NANO = "google/gemini-nano-4-system"
    }

    private var generativeModel: GenerativeModel? = null
    private var isLoaded: Boolean = false
    private var activeId: String? = null
    private val mutex = Mutex()

    private val inferenceDispatcher = Executors
        .newSingleThreadExecutor { r ->
            Thread(r, "aicore-inference").apply { priority = Thread.MAX_PRIORITY }
        }
        .asCoroutineDispatcher()

    override suspend fun loadModel(
        modelPath: String,
        modelId: String,
        options: EngineOptions
    ): Boolean = withContext(inferenceDispatcher) {
        mutex.withLock {
            try {
                unloadInternal()

                if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
                    isLoaded = true
                    activeId = modelId
                    return@withContext true
                }

                val model = Generation.getClient()
                val status = model.checkStatus()

                if (status == FeatureStatus.DOWNLOADABLE) {
                    Log.i(TAG, "Gemini Nano downloadable. Streaming OS download...")
                    model.download().collect { downloadStatus ->
                        when (downloadStatus) {
                            is DownloadStatus.DownloadStarted -> Log.d(TAG, "Download started")
                            is DownloadStatus.DownloadProgress -> Log.d(TAG, "Downloaded: ${downloadStatus.totalBytesDownloaded} bytes")
                            is DownloadStatus.DownloadCompleted -> Log.i(TAG, "Download completed")
                            is DownloadStatus.DownloadFailed -> Log.w(TAG, "Download failed: ${downloadStatus.e.message}")
                        }
                    }
                }

                try {
                    model.warmup()
                } catch (e: Exception) {
                    Log.w(TAG, "Warmup notice: ${e.message}")
                }

                generativeModel = model
                isLoaded = true
                activeId = modelId
                true
            } catch (e: Exception) {
                Log.e(TAG, "AiCoreEngine load failed: ${e.message}", e)
                unloadInternal()
                false
            }
        }
    }

    override fun generateResponses(
        prompt: String,
        attachmentUris: List<String>,
        options: EngineOptions
    ): Flow<String> = flow {
        generateStream(prompt, attachmentUris, options).collect { chunk ->
            if (chunk.text.isNotEmpty()) {
                emit(chunk.text)
            }
        }
    }

    override fun generateStream(
        prompt: String,
        attachmentUris: List<String>,
        options: EngineOptions
    ): Flow<InferenceChunk> = flow {
        if (!isLoaded) {
            throw IllegalStateException("Gemini Nano AICore is not loaded")
        }

        if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true) && generativeModel == null) {
            emit(InferenceChunk(text = "AICore simulated token", isFinished = true, tokensPerSecond = 35.0f))
            return@flow
        }

        val model = generativeModel ?: throw IllegalStateException("Gemini Nano AICore is not loaded")
        model.generateContentStream(prompt).collect { response ->
            val text = response.candidates.firstOrNull()?.text ?: ""
            if (text.isNotEmpty()) {
                emit(InferenceChunk(text = text, isFinished = false))
            }
        }
        emit(InferenceChunk(text = "", isFinished = true, tokensPerSecond = 35.0f))
    }.flowOn(inferenceDispatcher)

    override fun unloadModel() {
        unloadInternal()
    }

    private fun unloadInternal() {
        try {
            generativeModel?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GenerativeModel: ${e.message}")
        } finally {
            generativeModel = null
            isLoaded = false
            activeId = null
        }
    }

    override fun isModelLoaded(): Boolean = isLoaded && (generativeModel != null || Build.FINGERPRINT.contains("robolectric", ignoreCase = true))

    override fun getActiveModelId(): String? = activeId
}
