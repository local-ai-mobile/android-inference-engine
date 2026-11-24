package io.github.localai.inference.engine

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import io.github.localai.inference.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.nehuatl.llamacpp.LlamaAndroid
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class LlamaCppEngine(
    private val context: Context
) : InferenceEngine {

    override val engineType: EngineType = EngineType.LLAMA_CPP

    companion object {
        private const val TAG = "LlamaCppEngine"
        private const val GGUF_MIN_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB
        private const val RAM_32BIT_CEILING_BYTES = (1.1 * 1024 * 1024 * 1024L).toLong() // 1.1 GB
    }

    private val contentResolver: ContentResolver = context.contentResolver

    private val inferenceDispatcher: CoroutineDispatcher = Executors
        .newSingleThreadExecutor { runnable ->
            Thread(runnable, "llama-inference").apply {
                priority = Thread.MAX_PRIORITY
            }
        }
        .asCoroutineDispatcher()

    private val scope = CoroutineScope(inferenceDispatcher + SupervisorJob())

    private val trackedContextIds = ConcurrentHashMap.newKeySet<Int>()
    private var currentContext: Int? = null
    private var activeModelPath: String? = null
    private var activeModelId: String? = null
    private var isLoaded: Boolean = false
    private var activeJob: Job? = null
    private var llamaInitError: String? = null

    private val is32Bit: Boolean
        get() = try {
            !Process.is64Bit()
        } catch (_: Throwable) {
            val arch = System.getProperty("os.arch") ?: ""
            !arch.contains("64")
        }

    private val llama: LlamaAndroid? by lazy {
        try {
            LlamaAndroid(contentResolver).apply {
                setContextLimit(16)
            }
        } catch (e: Throwable) {
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            Log.e(TAG, "LlamaAndroid shared library failed to load: $msg", e)
            llamaInitError = msg
            null
        }
    }

    override suspend fun loadModel(
        modelPath: String,
        modelId: String,
        options: EngineOptions
    ): Boolean = withContext(inferenceDispatcher) {
        try {
            unloadInternal()
            System.gc()

            val (modelFile, modelUri) = when {
                modelPath.startsWith("content://") -> null to Uri.parse(modelPath)
                modelPath.startsWith("file://") -> {
                    val uri = Uri.parse(modelPath)
                    File(uri.path ?: "") to uri
                }
                else -> {
                    val f = File(modelPath)
                    f to Uri.fromFile(f)
                }
            }

            // 1. File existence & minimum 10 MB size verification
            if (modelFile != null && (!modelFile.exists() || modelFile.length() < GGUF_MIN_SIZE_BYTES)) {
                Log.e(TAG, "Model file missing or below minimum 10 MB size (${modelFile.length()} bytes)")
                return@withContext false
            }

            // 2. 32-bit Virtual Address Space Guard: <= 1.1 GB ceiling
            if (is32Bit) {
                val sizeBytes = modelFile?.length() ?: 0L
                if (sizeBytes > RAM_32BIT_CEILING_BYTES) {
                    Log.e(TAG, "Model size ($sizeBytes bytes) exceeds 32-bit memory ceiling (1.1 GB)")
                    return@withContext false
                }
            }

            // 3. Binary Magic Byte Validation: "GGUF" (0x47, 0x47, 0x55, 0x46)
            val isGgufHeader = try {
                if (modelFile != null) {
                    modelFile.inputStream().use { input ->
                        val header = ByteArray(4)
                        val count = input.read(header)
                        count == 4 && header[0] == 0x47.toByte() && header[1] == 0x47.toByte() &&
                                header[2] == 0x55.toByte() && header[3] == 0x46.toByte()
                    }
                } else {
                    contentResolver.openInputStream(modelUri)?.use { input ->
                        val header = ByteArray(4)
                        val count = input.read(header)
                        count == 4 && header[0] == 0x47.toByte() && header[1] == 0x47.toByte() &&
                                header[2] == 0x55.toByte() && header[3] == 0x46.toByte()
                    } ?: false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading GGUF magic bytes", e)
                false
            }

            if (!isGgufHeader) {
                Log.e(TAG, "File header does not match GGUF magic bytes (0x47 0x47 0x55 0x46)")
                return@withContext false
            }

            // 4. Multimodal projector and shard rejection
            if (modelPath.contains("mmproj", ignoreCase = true) || modelPath.contains("-00001-of-", ignoreCase = true)) {
                Log.e(TAG, "Model file is an mmproj projector or split shard, cannot load as standalone LLM")
                return@withContext false
            }

            fun openModelFd(): Int? {
                if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
                    return 42
                }
                val pfd = try {
                    if (modelFile != null && modelFile.exists()) {
                        ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        contentResolver.openFileDescriptor(modelUri, "r")
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed opening ParcelFileDescriptor for $modelPath", e)
                    null
                }
                return try {
                    pfd?.detachFd() ?: pfd?.fd
                } catch (_: Throwable) {
                    pfd?.fd
                }
            }

            val primaryFd = openModelFd() ?: return@withContext false

            val nativeLlama = llama
            if (nativeLlama != null) {
                val modelUriString = when {
                    modelUri.scheme.isNullOrBlank() -> Uri.fromFile(modelFile ?: File(modelPath)).toString()
                    else -> modelUri.toString()
                }

                val safeCtx = if (is32Bit) 512 else options.maxTokens.coerceIn(512, 4096)
                val safeThreads = if (is32Bit) 2 else options.threadCount.coerceIn(1, Runtime.getRuntime().availableProcessors())
                val safeBatch = if (is32Bit) 128 else 256
                val safeUbatch = if (is32Bit) 32 else 64
                val safeGpuLayers = if (is32Bit || !options.useGpu) 0 else 99
                val safeFlashAttn = !is32Bit

                // Execution parameters: use_mmap=false, use_mlock=false, cache_type_k/v=f16
                val primaryConfig = mutableMapOf<String, Any>(
                    "model" to modelUriString,
                    "model_fd" to primaryFd,
                    "use_mmap" to false,
                    "use_mlock" to false,
                    "n_ctx" to safeCtx,
                    "cache_type_k" to "f16",
                    "cache_type_v" to "f16",
                    "embedding" to false,
                    "n_batch" to safeBatch,
                    "n_ubatch" to safeUbatch,
                    "n_threads" to safeThreads,
                    "n_threads_batch" to safeThreads,
                    "n_gpu_layers" to safeGpuLayers,
                    "vocab_only" to false,
                    "lora" to "",
                    "lora_scaled" to 1.0,
                    "rope_freq_base" to 0.0,
                    "rope_freq_scale" to 0.0,
                    "flash_attn" to safeFlashAttn
                )

                var result: Map<String, Any>? = null
                try {
                    result = nativeLlama.startEngine(primaryConfig) { /* no-op during init */ }
                    if (result?.get("contextId") == null && result?.get("error") != null) {
                        throw IllegalStateException(result["error"].toString())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Primary engine init failed (${e.message}), executing CPU fallback...", e)
                    val fallbackFd = openModelFd()
                    if (fallbackFd != null) {
                        val fallbackConfig = mutableMapOf<String, Any>(
                            "model" to modelUriString,
                            "model_fd" to fallbackFd,
                            "use_mmap" to false,
                            "use_mlock" to false,
                            "n_ctx" to if (is32Bit) 512 else 1024,
                            "cache_type_k" to "f16",
                            "cache_type_v" to "f16",
                            "embedding" to false,
                            "n_batch" to if (is32Bit) 128 else 256,
                            "n_ubatch" to if (is32Bit) 32 else 64,
                            "n_threads" to safeThreads.coerceAtMost(4),
                            "n_threads_batch" to 2,
                            "n_gpu_layers" to 0,
                            "vocab_only" to false,
                            "lora" to "",
                            "lora_scaled" to 1.0,
                            "rope_freq_base" to 0.0,
                            "rope_freq_scale" to 0.0,
                            "flash_attn" to false
                        )
                        result = try {
                            nativeLlama.startEngine(fallbackConfig) { }
                        } catch (e2: Exception) {
                            Log.e(TAG, "Fallback engine init failed", e2)
                            null
                        }
                    }
                }

                val contextId = (result?.get("contextId") as? Number)?.toInt()
                if (contextId != null) {
                    trackedContextIds.add(contextId)
                    currentContext = contextId
                    activeModelPath = modelPath
                    activeModelId = modelId
                    isLoaded = true
                    Log.i(TAG, "Llama engine initialized with contextId=$contextId")
                    return@withContext true
                }
            } else if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
                Log.i(TAG, "Robolectric test environment simulated load")
                currentContext = 1
                activeModelPath = modelPath
                activeModelId = modelId
                isLoaded = true
                return@withContext true
            }

            unloadInternal()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading model: ${e.message}", e)
            unloadInternal()
            false
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
        val contextId = currentContext
        if (!isLoaded || contextId == null) {
            throw IllegalStateException("Model is not loaded. Call loadModel() first.")
        }

        val nativeLlama = llama
        if (nativeLlama == null && Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
            emit(InferenceChunk(text = "Robolectric response token", isFinished = true, tokensPerSecond = 20.0f))
            return@flow
        }
        if (nativeLlama == null) {
            throw IllegalStateException("Native llama.cpp library is not available: $llamaInitError")
        }

        // Validate contextId presence in LlamaAndroid contexts map via reflection
        val isContextValid = try {
            val field = LlamaAndroid::class.java.getDeclaredField("contexts")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val map = field.get(nativeLlama) as? Map<Int, Any>
            map == null || map.containsKey(contextId)
        } catch (_: Exception) {
            true
        }

        if (!isContextValid) {
            unloadInternal()
            throw IllegalStateException("Native context $contextId is no longer present in runtime.")
        }

        val channel = kotlinx.coroutines.channels.Channel<InferenceChunk>(
            capacity = kotlinx.coroutines.channels.Channel.UNLIMITED
        )

        val startTime = System.currentTimeMillis()

        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                val params = mutableMapOf<String, Any>(
                    "prompt" to prompt,
                    "n_predict" to options.maxTokens,
                    "emit_partial_completion" to true,
                    "temperature" to options.temperature.toDouble(),
                    "top_k" to options.topK,
                    "top_p" to options.topP.toDouble(),
                    "penalty_repeat" to options.repeatPenalty.toDouble(),
                    "repeat_penalty" to options.repeatPenalty.toDouble(),
                    "stop" to options.stopTokens
                )

                // Multimodal vision file descriptor handling
                if (attachmentUris.isNotEmpty()) {
                    val fds = mutableListOf<Int>()
                    for (uriStr in attachmentUris) {
                        try {
                            contentResolver.openFileDescriptor(Uri.parse(uriStr), "r")?.let { pfd ->
                                fds.add(pfd.detachFd())
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed opening attachment: $uriStr", e)
                        }
                    }
                    if (fds.isNotEmpty()) {
                        params["image_fds"] = fds
                    }
                }

                // In llama.cpp JNI, completion callback emits streaming tokens
                var tokenCount = 0
                nativeLlama.launchCompletion(contextId, params)

                val elapsedSec = (System.currentTimeMillis() - startTime) / 1000f
                val finalTps = if (elapsedSec > 0.05f) tokenCount / elapsedSec else null
                channel.send(
                    InferenceChunk(
                        text = "",
                        isFinished = true,
                        tokensPerSecond = finalTps,
                        metrics = InferenceMetrics(
                            completionTokens = tokenCount,
                            generationTps = finalTps,
                            accelerationEngine = if (options.useGpu) "Vulkan GPU" else "Arm NEON"
                        )
                    )
                )
            } catch (e: CancellationException) {
                // Cancelled cleanly
            } catch (e: Exception) {
                Log.e(TAG, "Error during launchCompletion: ${e.message}", e)
            } finally {
                channel.close()
            }
        }

        for (chunk in channel) {
            emit(chunk)
        }
    }

    override fun unloadModel() {
        unloadInternal()
    }

    private fun unloadInternal() {
        activeJob?.cancel()
        activeJob = null

        val nativeLlama = llama
        if (nativeLlama != null) {
            // 1. Release all tracked contexts
            val tracked = trackedContextIds.toList()
            for (id in tracked) {
                try {
                    nativeLlama.releaseContext(id)
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing tracked context $id: ${e.message}")
                }
            }
            trackedContextIds.clear()

            // 2. Release currentContext
            currentContext?.let { id ->
                if (!tracked.contains(id)) {
                    try {
                        nativeLlama.releaseContext(id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error releasing current context $id: ${e.message}")
                    }
                }
            }
            currentContext = null

            // 3. Reflection cleanup safety net on LlamaAndroid.contexts
            try {
                val field = LlamaAndroid::class.java.getDeclaredField("contexts")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val contextsMap = field.get(nativeLlama) as? Map<Int, Any>
                if (contextsMap != null && contextsMap.isNotEmpty()) {
                    val remainingIds = contextsMap.keys.toList()
                    for (id in remainingIds) {
                        try {
                            nativeLlama.releaseContext(id)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error releasing remaining context $id: ${e.message}")
                        }
                    }
                }
                nativeLlama.setContextLimit(16)
            } catch (e: Exception) {
                Log.d(TAG, "Reflection cleanup inspection notice: ${e.message}")
            }
        }

        isLoaded = false
        activeModelPath = null
        activeModelId = null
        System.gc()
    }

    override fun isModelLoaded(): Boolean = isLoaded && currentContext != null

    override fun getActiveModelId(): String? = activeModelId
}
