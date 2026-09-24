package com.example.pokemonalertsv2.baselineprofile

import android.os.SystemClock
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
    check(wait(Until.hasObject(By.text("Alerts")), 10_000)) { "Onboarding did not reach Alerts" }
    waitForIdle()
}

internal fun UiDevice.requireClick(selector: BySelector) {
    val clicked = clickIfPresent(selector, 10_000)
    if (!clicked) {
        val directory = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir(null)
        dumpWindowHierarchy(java.io.File(directory, "benchmark-click-failure.xml"))
        takeScreenshot(java.io.File(directory, "benchmark-click-failure.png"))
    }
    check(clicked) { "Required benchmark control missing: $selector" }
}

internal fun UiDevice.requireVisible(selector: BySelector) {
    val deadline = SystemClock.uptimeMillis() + 10_000L
    var ready = false
    while (!ready && SystemClock.uptimeMillis() < deadline) {
        // Poll the current hierarchy. Until.hasObject can miss Compose semantics updates
        // while UiAutomator's idle timeout is disabled for readiness measurements.
        ready = findObject(selector) != null
        if (!ready) SystemClock.sleep(100)
    }
    if (!ready) {
        val directory = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir(null)
        dumpWindowHierarchy(java.io.File(directory, "benchmark-failure.xml"))
        takeScreenshot(java.io.File(directory, "benchmark-failure.png"))
    }
    check(ready) { "Screen did not become ready: $selector" }
}

internal fun UiDevice.requireVisibleAny(vararg selectors: BySelector) {
    val deadline = SystemClock.uptimeMillis() + 10_000L
    while (SystemClock.uptimeMillis() < deadline) {
        if (selectors.any(::hasObject)) return
        SystemClock.sleep(100)
    }
    error("Screen did not become ready: ${selectors.joinToString()}")
}

internal fun UiDevice.clickIfPresent(
    selector: BySelector,
    timeoutMillis: Long = 2_000
): Boolean {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    while (SystemClock.uptimeMillis() < deadline) {
        val target = findObject(selector)
        if (target == null) {
            SystemClock.sleep(100)
            continue
        }
        try {
            target.click()
            return true
        } catch (_: StaleObjectException) {
            // Compose may replace semantics nodes during animation; reacquire until deadline.
        }
    }
    return false
}
