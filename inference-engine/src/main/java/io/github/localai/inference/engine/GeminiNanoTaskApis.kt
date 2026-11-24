package io.github.localai.inference.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiNanoTaskApis {
    private const val TAG = "GeminiNanoTaskApis"

    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    suspend fun summarize(context: Context, text: String): String? =
        withContext(Dispatchers.IO) {
            if (!isAvailable) return@withContext null
            try {
                val model = Generation.getClient()
                if (model.checkStatus() != FeatureStatus.AVAILABLE) {
                    model.close()
                    return@withContext null
                }
                val prompt = "Summarize the following text clearly and concisely using bullet points:\n\n$text"
                val response = model.generateContent(prompt)
                model.close()
                response.candidates.firstOrNull()?.text
            } catch (e: Exception) {
                Log.w(TAG, "summarize error: ${e.message}")
                null
            }
        }

    suspend fun proofread(context: Context, text: String): String? =
        withContext(Dispatchers.IO) {
            if (!isAvailable) return@withContext null
            try {
                val model = Generation.getClient()
                if (model.checkStatus() != FeatureStatus.AVAILABLE) {
                    model.close()
                    return@withContext null
                }
                val prompt = "Proofread and correct the following text for spelling, grammar, and punctuation. Output only the corrected text without explanations or quotes:\n\n$text"
                val response = model.generateContent(prompt)
                model.close()
                response.candidates.firstOrNull()?.text
            } catch (e: Exception) {
                Log.w(TAG, "proofread error: ${e.message}")
                null
            }
        }

    suspend fun rewrite(context: Context, text: String, style: String = "formal"): String? =
        withContext(Dispatchers.IO) {
            if (!isAvailable) return@withContext null
            try {
                val model = Generation.getClient()
                if (model.checkStatus() != FeatureStatus.AVAILABLE) {
                    model.close()
                    return@withContext null
                }
                val prompt = "Rewrite the following text in a $style tone. Return only the rewritten text:\n\n$text"
                val response = model.generateContent(prompt)
                model.close()
                response.candidates.firstOrNull()?.text
            } catch (e: Exception) {
                Log.w(TAG, "rewrite error: ${e.message}")
                null
            }
        }

    suspend fun describeImage(
        context: Context,
        bitmap: Bitmap,
        prompt: String = "Describe this image in detail."
    ): String? = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext null
        try {
            val model = Generation.getClient()
            if (model.checkStatus() != FeatureStatus.AVAILABLE) {
                model.close()
                return@withContext null
            }
            val request = generateContentRequest(
                ImagePart(bitmap),
                TextPart(prompt)
            ) {}
            val response = model.generateContent(request)
            model.close()
            response.candidates.firstOrNull()?.text
        } catch (e: Exception) {
            Log.w(TAG, "describeImage error: ${e.message}")
            null
        }
    }
}
