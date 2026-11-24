package io.github.localai.inference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.localai.inference.hardware.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HardwareProfilerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        HardwareProfiler.mockAvailableBytesForTesting = null
        ArchitectureManager.set64BitForTesting(true)
    }

    @After
    fun tearDown() {
        HardwareProfiler.mockAvailableBytesForTesting = null
        ArchitectureManager.set64BitForTesting(null)
    }

    @Test
    fun testDefaultBalancedProfile() {
        val profile = HardwareProfiler.profile(context, PerformanceMode.BALANCED)
        assertNotNull(profile)
        assertEquals(PerformanceMode.BALANCED, profile.performanceMode)
        assertTrue(profile.nThreads in 2..8)
        assertTrue(profile.contextLength >= 2048)
        assertFalse(profile.useMlock)
    }

    @Test
    fun testBatterySaverProfile() {
        val profile = HardwareProfiler.profile(context, PerformanceMode.BATTERY_SAVER)
        assertEquals(PerformanceMode.BATTERY_SAVER, profile.performanceMode)
        assertTrue(profile.nThreads <= 2)
        assertFalse(profile.useFlashAttn)
        assertEquals(2048, profile.contextLength)
    }

    @Test
    fun testHighPerformanceProfile() {
        val profile = HardwareProfiler.profile(context, PerformanceMode.HIGH_PERFORMANCE)
        assertEquals(PerformanceMode.HIGH_PERFORMANCE, profile.performanceMode)
        assertTrue(profile.nThreads >= 2)
        assertTrue(profile.contextLength >= 2048)
    }

    @Test
    fun test32BitHardwareClamping() {
        ArchitectureManager.set64BitForTesting(false)
        val profile = HardwareProfiler.profile(context, PerformanceMode.HIGH_PERFORMANCE)

        assertEquals(0, profile.recommendedGpuLayers)
        assertTrue(profile.nThreads <= 2)
        assertEquals(512, profile.contextLength)
        assertEquals(128, profile.nBatch)
        assertEquals(32, profile.nUbatch)
        assertFalse(profile.useFlashAttn)
    }

    @Test
    fun testStorageSafetyAssessmentSufficient() {
        HardwareProfiler.mockAvailableBytesForTesting = 10L * 1024 * 1024 * 1024 // 10 GB
        val modelSize = 2L * 1024 * 1024 * 1024 // 2 GB

        val assessment = HardwareProfiler.checkStorageSafety(context, modelSize)
        assertTrue("Storage should be safe", assessment.isSafe)
        assertNull(assessment.warningMessage)
        assertEquals(0.0, assessment.missingGb, 0.01)
    }

    @Test
    fun testStorageSafetyAssessmentInsufficient() {
        HardwareProfiler.mockAvailableBytesForTesting = 1L * 1024 * 1024 * 1024 // 1 GB
        val modelSize = 2L * 1024 * 1024 * 1024 // 2 GB

        val assessment = HardwareProfiler.checkStorageSafety(context, modelSize)
        assertFalse("Storage should not be safe", assessment.isSafe)
        assertNotNull(assessment.warningMessage)
        assertTrue(assessment.missingGb > 0.0)
    }

    @Test
    fun testMemorySafetyAssessment32BitRejection() {
        ArchitectureManager.set64BitForTesting(false)
        val assessment = HardwareProfiler.checkMemorySafety(context, requiredRamGb = 2.0, sizeGb = 1.5)
        assertFalse("32-bit should reject 1.5 GB model", assessment.isSafe)
        assertNotNull(assessment.warningMessage)
        assertTrue(assessment.warningMessage!!.contains("32-bit"))
    }
}
