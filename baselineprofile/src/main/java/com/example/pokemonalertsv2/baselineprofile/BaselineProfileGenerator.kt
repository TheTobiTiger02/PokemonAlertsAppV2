package com.example.pokemonalertsv2.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun criticalUserJourneys() = rule.collect(
        packageName = "com.example.pokemonalertsv2",
        includeInStartupProfile = false,
        maxIterations = 5
    ) {
        pressHome()
        device.prepareBenchmarkPermissions()
        startActivityAndWait()
        device.waitForIdle()
        device.completeOnboardingIfNeeded()

        // Alerts: exercise card composition, feed scrolling, overflow, and details.
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
            device.displayWidth / 2, device.displayHeight / 3, 18)
        device.waitForIdle()
        if (device.clickIfPresent(By.desc("More alert actions"), timeoutMillis = 500)) {
            device.pressBack()
        }
        device.clickIfPresent(By.textContains("Rocket"), timeoutMillis = 500)
        device.waitForIdle()
        device.pressBack()

        // Map: include initialization, filter chips, and map-detail rendering when data exists.
        device.clickIfPresent(By.text("Map"))
        device.clickIfPresent(By.text("Raids"), timeoutMillis = 500)
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        device.waitForIdle()
        if (device.clickIfPresent(By.desc("More map alert actions"), timeoutMillis = 500)) {
            device.pressBack()
        }

        device.clickIfPresent(By.text("Alerts"))
        device.clickIfPresent(By.text("History"))
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
            device.displayWidth / 2, device.displayHeight / 3, 18)
        device.waitForIdle()
        if (device.clickIfPresent(By.desc("More alert actions"), timeoutMillis = 500)) {
            device.pressBack()
        }
        device.clickIfPresent(By.text("Settings"))
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
            device.displayWidth / 2, device.displayHeight / 3, 18)
        device.waitForIdle()
    }
}
