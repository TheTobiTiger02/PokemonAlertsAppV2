package com.example.pokemonalertsv2.ui.alerts

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.MAP_TAB_INDEX
import com.example.pokemonalertsv2.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertsMapActivityRedirectTest {

    @Test
    fun legacyMapActivityRedirectsToMainMapTab() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)

        try {
            context.startActivity(
                Intent(context, AlertsMapActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )

            val mainActivity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000L)
            assertNotNull(mainActivity)
            assertEquals(MAP_TAB_INDEX, MainActivity.requestedTab(mainActivity.intent))
            instrumentation.runOnMainSync { mainActivity.finish() }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
