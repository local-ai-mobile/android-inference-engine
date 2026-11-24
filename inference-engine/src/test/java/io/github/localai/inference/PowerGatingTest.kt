package io.github.localai.inference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.localai.inference.monitor.DevicePowerMonitor
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PowerGatingTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DevicePowerMonitor.init(context)
    }

    @After
    fun tearDown() {
        DevicePowerMonitor.setPowerGatedForTesting(false)
    }

    @Test
    fun testDefaultStateNotGated() {
        assertFalse(DevicePowerMonitor.isPowerGated.value)
    }

    @Test
    fun testPowerGatedTestingOverride() {
        DevicePowerMonitor.setPowerGatedForTesting(true)
        assertTrue(DevicePowerMonitor.isPowerGated.value)
        assertTrue(DevicePowerMonitor.isLowBatteryOrSaver(context))

        DevicePowerMonitor.setPowerGatedForTesting(false)
        assertFalse(DevicePowerMonitor.isPowerGated.value)
    }
}
