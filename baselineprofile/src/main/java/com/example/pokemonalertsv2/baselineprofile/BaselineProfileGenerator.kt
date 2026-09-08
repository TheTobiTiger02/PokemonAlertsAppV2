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

        // Exercise returning-user paths, asserting every required navigation step.
        device.requireVisible(By.text("Pokémon Alerts"))
        device.requireClick(By.text("Alerts"))
        // Restore the feed's scroll position between collection iterations before opening a card.
        device.swipe(device.displayWidth / 2, device.displayHeight / 3,
            device.displayWidth / 2, device.displayHeight * 3 / 4, 18)
        if (device.clickIfPresent(By.text("Pikachu"), timeoutMillis = 1_000)) {
            device.requireVisible(By.desc("Back"))
            device.pressBack()
        }
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
            device.displayWidth / 2, device.displayHeight / 3, 18)
        device.requireClick(By.text("Map"))
        device.requireVisible(By.desc("Map settings and filters"))
        device.requireClick(By.desc("Show all visible alerts"))
        device.swipe(device.displayWidth / 2, device.displayHeight / 2,
            device.displayWidth * 2 / 3, device.displayHeight / 2, 18)
        device.requireClick(By.desc("Map settings and filters"))
        device.requireVisible(By.textContains("alerts visible"))
        device.pressBack()
        device.requireClick(By.text("History"))
        device.requireVisible(By.text("Alert History"))
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
            device.displayWidth / 2, device.displayHeight / 3, 18)
        device.requireClick(By.text("Settings"))
        device.requireClick(By.text("Appearance & behavior"))
        device.requireVisible(By.text("Display and sorting"))
        device.pressBack()
        device.requireClick(By.text("GoDex checklist"))
        // A disconnected collection is a valid state; the fixture driver supplies a populated one.
        if (device.clickIfPresent(By.textContains("still needed"), timeoutMillis = 1_000)) {
            device.requireVisible(By.text("Search Pokémon, form, number, or key"))
            device.findObject(By.clazz("android.widget.EditText"))?.text = "99"
            device.waitForIdle()
            device.pressBack()
        }
        device.pressBack()
        device.requireClick(By.text("Alerts"))
    }
}
