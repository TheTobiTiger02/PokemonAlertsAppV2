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
        device.findObject(By.text("Map"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Alerts"))?.click()
        device.waitForIdle()
        device.findObject(By.text("History"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Settings"))?.click()
        device.waitForIdle()
    }
}
