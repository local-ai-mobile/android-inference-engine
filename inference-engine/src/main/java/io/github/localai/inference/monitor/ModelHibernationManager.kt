package io.github.localai.inference.monitor

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ModelHibernationManager {

    private const val TAG = "ModelHibernation"
    const val PREFS_KEY_HIBERNATION_TIMEOUT = "model_hibernation_timeout_minutes"
    const val DEFAULT_TIMEOUT_MINUTES = 5

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    @Volatile
    private var lastActivityTimestampMs: Long = System.currentTimeMillis()

    private val _isHibernated = MutableStateFlow(false)
    val isHibernated: StateFlow<Boolean> = _isHibernated.asStateFlow()

    private val _timeoutMinutes = MutableStateFlow(DEFAULT_TIMEOUT_MINUTES)
    val timeoutMinutes: StateFlow<Int> = _timeoutMinutes.asStateFlow()

    private var hibernateCallback: (() -> Unit)? = null
    private var isGeneratingPredicate: (() -> Boolean)? = null

    fun init(
        context: Context,
        isGeneratingCheck: () -> Boolean,
        onHibernate: () -> Unit
    ) {
        val prefs = context.getSharedPreferences("inference_settings", Context.MODE_PRIVATE)
        val savedTimeout = prefs.getInt(PREFS_KEY_HIBERNATION_TIMEOUT, DEFAULT_TIMEOUT_MINUTES)
        _timeoutMinutes.value = savedTimeout

        hibernateCallback = onHibernate
        isGeneratingPredicate = isGeneratingCheck
        recordActivity()

        startMonitorLoop()
        Log.i(TAG, "ModelHibernationManager initialized with timeout: $savedTimeout minutes")
    }

    private fun startMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(15_000L)
                checkInactivity()
            }
        }
    }

    fun recordActivity() {
        lastActivityTimestampMs = System.currentTimeMillis()
    }

    fun checkInactivity() {
        val timeout = _timeoutMinutes.value
        if (timeout <= 0) return
        if (_isHibernated.value) return

        val isGenerating = isGeneratingPredicate?.invoke() == true
        if (isGenerating) {
            recordActivity()
            return
        }

        val elapsed = System.currentTimeMillis() - lastActivityTimestampMs
        val timeoutMs = timeout * 60 * 1000L

        if (elapsed >= timeoutMs) {
            Log.i(TAG, "Inactivity timeout reached ($elapsed ms >= $timeoutMs ms). Triggering model hibernation...")
            _isHibernated.value = true
            hibernateCallback?.invoke()
        }
    }

    fun markHibernated() {
        _isHibernated.value = true
    }

    fun markWoken() {
        _isHibernated.value = false
        recordActivity()
    }

    fun setTimeoutMinutes(context: Context, minutes: Int) {
        _timeoutMinutes.value = minutes
        context.getSharedPreferences("inference_settings", Context.MODE_PRIVATE)
            .edit()
            .putInt(PREFS_KEY_HIBERNATION_TIMEOUT, minutes)
            .apply()
        recordActivity()
    }

    fun resetForTesting() {
        monitorJob?.cancel()
        _isHibernated.value = false
        lastActivityTimestampMs = System.currentTimeMillis()
    }
}
