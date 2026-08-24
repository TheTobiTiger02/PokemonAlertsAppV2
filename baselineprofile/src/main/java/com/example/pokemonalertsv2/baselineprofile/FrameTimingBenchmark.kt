package com.example.pokemonalertsv2.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun primaryNavigationAndScrollingNoCompilation() = measureNavigation(
        CompilationMode.None()
    )

    @Test
    fun primaryNavigationAndScrollingWithBaselineProfile() = measureNavigation(
        CompilationMode.Partial(BaselineProfileMode.Require)
    )

    private fun measureNavigation(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = "com.example.pokemonalertsv2",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        device.prepareBenchmarkPermissions()
        startActivityAndWait()
        device.completeOnboardingIfNeeded()

        listOf("History", "Settings", "Map", "Alerts").forEach { tab ->
            device.clickIfPresent(By.text(tab))
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 3,
                24
            )
        }
    }
}
