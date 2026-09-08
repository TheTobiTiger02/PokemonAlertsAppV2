package com.example.pokemonalertsv2.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Native gestures and a real system PiP transition, using the opt-in deterministic fixture. */
@OptIn(ExperimentalMetricApi::class)
class MapJourneyBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun gesturesAndPictureInPicture() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = 5,
        setupBlock = {
            killProcess()
            device.prepareBenchmarkPermissions()
            startActivityAndWait()
            device.completeOnboardingIfNeeded()
            device.requireClick(By.text("Map"))
            device.requireVisible(By.desc("Show all visible alerts"))
        }
    ) {
        device.requireClick(By.desc("Show all visible alerts"))
        val map = device.findObject(By.desc("Google Map"))
        val x = device.displayWidth / 2
        val y = device.displayHeight / 2
        device.swipe(x, y, x + device.displayWidth / 6, y, 18)
        device.swipe(x + device.displayWidth / 6, y, x, y, 18)
        if (map != null) map.pinchOpen(0.35f, 600) else {
            // Double-tap is also a native zoom gesture when the SDK omits its accessibility node.
            device.click(x, y)
            SystemClock.sleep(80)
            device.click(x, y)
        }
        device.requireClick(By.desc("Map settings and filters"))
        device.requireVisible(By.desc("Open map in picture-in-picture"))
        val start = SystemClock.elapsedRealtimeNanos()
        device.requireClick(By.desc("Open map in picture-in-picture"))
        val deadline = SystemClock.uptimeMillis() + 10_000
        var pinned = false
        while (!pinned && SystemClock.uptimeMillis() < deadline) {
            pinned = device.executeShellCommand("dumpsys activity activities").contains("mode=pinned")
            if (!pinned) SystemClock.sleep(100)
        }
        check(pinned) { "Map did not enter system picture-in-picture" }
        val label = InstrumentationRegistry.getArguments().getString("performanceLabel", "local")
        File(InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir(null),
            "map-pip-$label.csv").appendText("pip-entry,${(SystemClock.elapsedRealtimeNanos() - start) / 1e6}\n")
        // The measured journey ends in PiP. setupBlock closes it before the next iteration.
        // Relaunching a pinned activity does not necessarily restore it to full screen.
    }
}
