package com.example.pokemonalertsv2.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartupNoCompilation() = measureColdStartup(CompilationMode.None())

    @Test
    fun coldStartupWithBaselineProfile() = measureColdStartup(
        CompilationMode.Partial(BaselineProfileMode.Require)
    )

    @Test
    fun warmStartupWithBaselineProfile() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = {
            device.prepareBenchmarkPermissions()
            startActivityAndWait()
            device.completeOnboardingIfNeeded()
            pressHome()
        }
    ) { startActivityAndWait() }

    private fun measureColdStartup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = "com.example.pokemonalertsv2",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            device.prepareBenchmarkPermissions()
            startActivityAndWait()
            device.completeOnboardingIfNeeded()
            pressHome()
            device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        }
    ) {
        startActivityAndWait()
    }
}
