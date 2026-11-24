package io.github.localai.inference

import android.content.ComponentCallbacks2
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.localai.inference.monitor.MemoryLimiterKillGuard
import io.github.localai.inference.monitor.MemoryTrimListener
import io.github.localai.inference.monitor.ModelHibernationManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoryGuardTest {

    private lateinit var context: Context

    private var uiTrimCount = 0
    private var kvTrimCount = 0
    private var criticalEvictCount = 0

    private val testListener = object : MemoryTrimListener {
        override fun onTrimUiCaches() {
            uiTrimCount++
        }

        override fun onTrimKvCaches() {
            kvTrimCount++
        }

        override fun onCriticalMemoryPressure(shouldEvictModel: Boolean) {
            if (shouldEvictModel) criticalEvictCount++
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        uiTrimCount = 0
        kvTrimCount = 0
        criticalEvictCount = 0
        MemoryLimiterKillGuard.registerListener(testListener)
        ModelHibernationManager.resetForTesting()
    }

    @After
    fun tearDown() {
        MemoryLimiterKillGuard.unregisterListener(testListener)
        ModelHibernationManager.resetForTesting()
    }

    @Test
    fun testUiHiddenTrim() {
        MemoryLimiterKillGuard.handleTrimMemory(context, ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(1, uiTrimCount)
        assertEquals(0, kvTrimCount)
        assertEquals(0, criticalEvictCount)
    }

    @Test
    fun testRunningLowTrim() {
        MemoryLimiterKillGuard.handleTrimMemory(context, ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(1, uiTrimCount)
        assertEquals(1, kvTrimCount)
        assertEquals(0, criticalEvictCount)
    }

    @Test
    fun testCriticalMemoryTrim() {
        MemoryLimiterKillGuard.handleTrimMemory(context, ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(1, uiTrimCount)
        assertEquals(1, kvTrimCount)
        assertEquals(1, criticalEvictCount)
    }

    @Test
    fun testEmergencyLowMemory() {
        MemoryLimiterKillGuard.handleLowMemory(context)
        assertEquals(1, uiTrimCount)
        assertEquals(1, kvTrimCount)
        assertEquals(1, criticalEvictCount)
    }

    @Test
    fun testModelHibernationInactivity() {
        var hibernated = false
        var isGenerating = false

        ModelHibernationManager.init(
            context = context,
            isGeneratingCheck = { isGenerating },
            onHibernate = { hibernated = true }
        )

        // Activity occurred, not yet timed out
        ModelHibernationManager.recordActivity()
        ModelHibernationManager.checkInactivity()
        assertFalse(hibernated)

        // When generating, even if timeout occurs, should never hibernate
        isGenerating = true
        ModelHibernationManager.checkInactivity()
        assertFalse(hibernated)
    }
}
