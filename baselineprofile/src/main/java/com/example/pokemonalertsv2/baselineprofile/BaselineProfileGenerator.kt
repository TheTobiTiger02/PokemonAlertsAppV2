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
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        device.findObject(By.text("Skip"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Allow"))?.click()
        device.waitForIdle()

        // Alerts: exercise card composition, feed scrolling, overflow, and details.
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
            device.displayWidth / 2, device.displayHeight / 3, 18)
        device.waitForIdle()
        device.findObject(By.desc("More alert actions"))?.let { overflow ->
            overflow.click()
            device.waitForIdle()
            device.pressBack()
        }
        device.findObject(By.textContains("Rocket"))?.click()
        device.waitForIdle()
        device.pressBack()

        // Map: include initialization, filter chips, and map-detail rendering when data exists.
        device.findObject(By.text("Map"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Raids"))?.click()
        device.waitForIdle()
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        device.waitForIdle()
        device.findObject(By.desc("More map alert actions"))?.let { overflow ->
            overflow.click()
            device.waitForIdle()
            device.pressBack()
        }

        device.findObject(By.text("Alerts"))?.click()
        device.waitForIdle()
        device.findObject(By.text("History"))?.click()
        device.waitForIdle()
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
            device.displayWidth / 2, device.displayHeight / 3, 18)
        device.waitForIdle()
        device.findObject(By.desc("More alert actions"))?.let { overflow ->
            overflow.click()
            device.waitForIdle()
            device.pressBack()
        }
        device.findObject(By.text("Settings"))?.click()
        device.waitForIdle()
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
            device.displayWidth / 2, device.displayHeight / 3, 18)
        device.waitForIdle()
    }
}
