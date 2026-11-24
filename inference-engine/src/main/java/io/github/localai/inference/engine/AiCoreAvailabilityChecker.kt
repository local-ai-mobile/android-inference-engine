package io.github.localai.inference.engine

import android.content.Context
import android.os.Build
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiCoreAvailabilityChecker {
    private const val TAG = "AiCoreChecker"

    enum class Status {
        AVAILABLE_AND_READY,
        DOWNLOADING,
        UNSUPPORTED
    }

    data class CheckResult(
        val status: Status,
        val details: String,
        val progressBytes: Long = 0L
    )

    @Volatile
    private var cachedResult: CheckResult? = null

    val cachedStatus: Status?
        get() = cachedResult?.status

    suspend fun checkAvailability(context: Context, forceRefresh: Boolean = false): CheckResult =
        withContext(Dispatchers.IO) {
            if (!forceRefresh && cachedResult != null) {
                return@withContext cachedResult!!
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val result = CheckResult(
                    status = Status.UNSUPPORTED,
                    details = "Android AICore requires Android 14+ (API 34+). Current: Android ${Build.VERSION.RELEASE}"
                )
                cachedResult = result
                return@withContext result
            }

            try {
                val model = Generation.getClient()
                val status = model.checkStatus()
                val result = when (status) {
                    FeatureStatus.AVAILABLE -> CheckResult(
                        status = Status.AVAILABLE_AND_READY,
                        details = "Google Gemini Nano is available and hardware accelerated."
                    )
                    FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> CheckResult(
                        status = Status.DOWNLOADING,
                        details = "Gemini Nano is downloading or being updated by Android OS."
                    )
                    else -> CheckResult(
                        status = Status.UNSUPPORTED,
                        details = "Gemini Nano not supported on this device."
                    )
                }
                model.close()
                cachedResult = result
                result
            } catch (e: GenAiException) {
                val result = if (e.errorCode == GenAiException.ErrorCode.BUSY) {
                    CheckResult(status = Status.DOWNLOADING, details = "Gemini Nano is busy or updating.")
                } else {
                    CheckResult(status = Status.UNSUPPORTED, details = "AICore unavailable: ${e.message}")
                }
                cachedResult = result
                result
            } catch (e: Throwable) {
                val result = CheckResult(status = Status.UNSUPPORTED, details = "AICore unavailable: ${e.message}")
                cachedResult = result
                result
            }
        }

    fun invalidateCache() {
        cachedResult = null
    }
}
