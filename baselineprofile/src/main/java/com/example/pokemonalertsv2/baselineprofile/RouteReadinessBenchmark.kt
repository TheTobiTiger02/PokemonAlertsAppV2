package com.example.pokemonalertsv2.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Measures Catch Route setup after the user chooses it, on the same fixture in both builds. */
class RouteReadinessBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun catchRouteSetup() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 5,
        setupBlock = {
            killProcess()
            device.prepareBenchmarkPermissions()
            startActivityAndWait()
            device.completeOnboardingIfNeeded()
            device.requireClick(By.text("Map"))
            device.requireVisible(By.desc("Map settings and filters"))
            if (device.findObject(By.text("Routes")) != null) {
                device.requireClick(By.text("Routes"))
                device.requireVisible(By.text("Plan Catch Route"))
            } else {
                device.requireVisible(By.text("Route"))
            }
        }
    ) {
        val idleTimeout = Configurator.getInstance().waitForIdleTimeout
        Configurator.getInstance().waitForIdleTimeout = 0
        try {
            val button = if (device.findObject(By.text("Plan Catch Route")) != null) {
                "Plan Catch Route"
            } else "Route"
            val start = SystemClock.elapsedRealtimeNanos()
            device.requireClick(By.text(button))
            device.requireVisible(By.text("Minutes"))
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
            val label = InstrumentationRegistry.getArguments().getString("performanceLabel", "local")
            File(InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir(null),
                "route-readiness-$label.csv").appendText("setup,$elapsedMs\n")
        } finally {
            Configurator.getInstance().waitForIdleTimeout = idleTimeout
        }
    }
}
