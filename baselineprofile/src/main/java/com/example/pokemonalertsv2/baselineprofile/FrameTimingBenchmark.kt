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
    fun primaryNavigationAndScrolling() = rule.measureRepeated(
        packageName = "com.example.pokemonalertsv2",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
        device.findObject(By.text("Skip"))?.click()
        device.findObject(By.text("Allow"))?.click()
        device.waitForIdle()

        repeat(2) {
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 3,
                18
            )
        }
        device.findObject(By.desc("More alert actions"))?.let { overflow ->
            overflow.click()
            device.waitForIdle()
            device.pressBack()
        }
        listOf("Map", "History", "Settings", "Alerts").forEach { tab ->
            device.findObject(By.text(tab))?.click()
            device.waitForIdle()
        }
    }
}
