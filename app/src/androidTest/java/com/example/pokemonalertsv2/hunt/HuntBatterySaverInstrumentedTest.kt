package com.example.pokemonalertsv2.hunt

import android.graphics.Color
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Host drives real emulator acceleration at each checkpoint; no production test hooks. */
class HuntBatterySaverInstrumentedTest {
    @Test fun missingOverlayPermissionFailsWithAnEscape() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("enableBatteryPermissionFixture") == "true")
        val context = instrumentation.targetContext
        assertFalse(android.provider.Settings.canDrawOverlays(context))
        assertNotNull(HuntBatterySaver.unavailableReason(context))
        var escaped = false
        instrumentation.runOnMainSync {
            val saver = HuntBatterySaver(context) { escaped = true }
            try { saver.setEnabled(true); assertTrue(escaped) } finally { saver.close() }
        }
    }

    @Test fun coversOtherAppsRestoresAndEscapes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("enableBatteryFixture") == "true")
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        val previousInfo = automation.serviceInfo
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.contentDescription?.startsWith("Battery Saver. Turn") == true) return node
            for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
            return null
        }
        fun blackout(): AccessibilityNodeInfo? = automation.windows.firstNotNullOfOrNull { find(it.root) }
        fun awaitBlackout(present: Boolean, timeout: Long = 15_000): Boolean {
            val deadline = SystemClock.elapsedRealtime() + timeout
            while (SystemClock.elapsedRealtime() < deadline) {
                if ((blackout() != null) == present) return true
                SystemClock.sleep(100)
            }
            return false
        }
        assertNull(HuntBatterySaver.unavailableReason(context))
        var escaped = false
        lateinit var saver: HuntBatterySaver
        instrumentation.runOnMainSync { saver = HuntBatterySaver(context) { escaped = true }; saver.setEnabled(true) }
        try {
            automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            Log.i("HuntBatteryQA", "TILT_FIRST")
            assertTrue("Blackout must cover the launcher", awaitBlackout(true))
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull(screenshot)
            assertEquals(Color.BLACK, screenshot!!.getPixel(screenshot.width / 2, screenshot.height / 2))
            File(context.cacheDir, "battery-blackout.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            val now = SystemClock.uptimeMillis()
            val bounds = android.graphics.Rect().also { blackout()!!.getBoundsInScreen(it) }
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                val event = android.view.MotionEvent.obtain(now, now + 50, action, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
                automation.injectInputEvent(event, true)
                event.recycle()
            }
            assertNotNull("Pocket taps must be consumed", blackout())
            Log.i("HuntBatteryQA", "UPRIGHT")
            assertTrue("Upright must restore the previous app", awaitBlackout(false))
            assertFalse(escaped)
            Log.i("HuntBatteryQA", "TILT_ESCAPE")
            assertTrue(awaitBlackout(true))
            assertTrue(blackout()!!.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
            assertTrue(awaitBlackout(false, 5_000))
            instrumentation.runOnMainSync { assertTrue(escaped) }
            // Restart while still inverted, then stop: neither old listeners nor windows survive.
            instrumentation.runOnMainSync { saver.setEnabled(true) }
            assertTrue(awaitBlackout(true, 10_000))
            instrumentation.runOnMainSync { saver.close() }
            assertTrue(awaitBlackout(false, 5_000))
        } finally {
            instrumentation.runOnMainSync { saver.close() }
            automation.serviceInfo = previousInfo
        }
    }
}
