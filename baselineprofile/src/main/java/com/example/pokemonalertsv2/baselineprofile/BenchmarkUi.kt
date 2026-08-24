package com.example.pokemonalertsv2.baselineprofile

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.example.pokemonalertsv2"

internal fun UiDevice.prepareBenchmarkPermissions() {
    listOf(
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION"
    ).forEach { permission ->
        executeShellCommand("pm grant $TARGET_PACKAGE $permission")
    }
}

internal fun UiDevice.completeOnboardingIfNeeded() {
    if (hasObject(By.text("Alerts"))) return
    repeat(3) {
        clickIfPresent(By.text("Continue"), timeoutMillis = 1_000)
    }
    clickIfPresent(By.text("Enable & finish"), timeoutMillis = 1_000)
    clickIfPresent(By.text("Not Now"), timeoutMillis = 500)
    wait(Until.hasObject(By.text("Alerts")), 5_000)
    waitForIdle()
}

internal fun UiDevice.clickIfPresent(
    selector: BySelector,
    timeoutMillis: Long = 2_000
): Boolean {
    repeat(3) {
        val target = wait(Until.findObject(selector), timeoutMillis) ?: return false
        try {
            target.click()
            waitForIdle()
            return true
        } catch (_: StaleObjectException) {
            // Compose may replace semantics nodes during animation; retry with a fresh node.
        }
    }
    return false
}
