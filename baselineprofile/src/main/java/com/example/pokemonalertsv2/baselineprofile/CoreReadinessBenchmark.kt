package com.example.pokemonalertsv2.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Same offline fixture and APK signing on before and after runs. */
@OptIn(ExperimentalMetricApi::class)
class CoreReadinessBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    private var mapTapX = 0
    private var mapTapY = 0
    private var feedTapX = 0
    private var feedTapY = 0

    @Test fun feedAndMap() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
        compilationMode = CompilationMode.None(),
        iterations = 5,
        setupBlock = {
            killProcess()
            device.prepareBenchmarkPermissions()
            startActivityAndWait()
            device.completeOnboardingIfNeeded()
            device.requireVisible(By.text("Pokémon Alerts"))
            device.findObject(By.text("Map")).visibleBounds.let {
                mapTapX = it.centerX()
                mapTapY = it.centerY()
            }
            device.findObject(By.text("Alerts")).visibleBounds.let {
                feedTapX = it.centerX()
                feedTapY = it.centerY()
            }
        }
    ) {
        val label = InstrumentationRegistry.getArguments().getString("performanceLabel", "local")
        val output = File(
            InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir(null),
            "core-readiness-$label.csv"
        )
        val mapStart = SystemClock.elapsedRealtimeNanos()
        device.executeShellCommand("input tap $mapTapX $mapTapY")
        val mapClicked = SystemClock.elapsedRealtimeNanos()
        device.requireVisible(By.desc("Map settings and filters"))
        val mapVisible = SystemClock.elapsedRealtimeNanos()
        output.appendText("map-click,${(mapClicked - mapStart) / 1e6}\n")
        output.appendText("map-visible,${(mapVisible - mapClicked) / 1e6}\n")
        output.appendText("map,${(mapVisible - mapStart) / 1e6}\n")

        val feedStart = SystemClock.elapsedRealtimeNanos()
        device.executeShellCommand("input tap $feedTapX $feedTapY")
        val feedClicked = SystemClock.elapsedRealtimeNanos()
        device.requireVisible(By.text("Pokémon Alerts"))
        val feedVisible = SystemClock.elapsedRealtimeNanos()
        output.appendText("feed-click,${(feedClicked - feedStart) / 1e6}\n")
        output.appendText("feed-visible,${(feedVisible - feedClicked) / 1e6}\n")
        output.appendText("feed,${(feedVisible - feedStart) / 1e6}\n")
    }
}
