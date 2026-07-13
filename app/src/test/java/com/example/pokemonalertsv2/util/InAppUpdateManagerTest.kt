package com.example.pokemonalertsv2.util

import com.example.pokemonalertsv2.data.GitHubRelease
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppUpdateManagerTest {

    @Test
    fun versionComparisonHandlesReleasePrefixesAndSegments() {
        assertTrue(InAppUpdateManager.isNewerVersion("1.0.4", "v1.0.5"))
        assertTrue(InAppUpdateManager.isNewerVersion("v1.0.4", "1.1.0"))
        assertFalse(InAppUpdateManager.isNewerVersion("1.0.5", "v1.0.5"))
        assertFalse(InAppUpdateManager.isNewerVersion("1.1.0", "v1.0.5"))
    }

    @Test
    fun automaticChecksStaySilentWhileManualChecksExposeFeedback() {
        assertSame(UpdateState.Idle, noUpdateState(UpdateCheckSource.AUTOMATIC))
        assertSame(UpdateState.UpToDate, noUpdateState(UpdateCheckSource.MANUAL))
        assertSame(UpdateState.Idle, updateErrorState(UpdateCheckSource.AUTOMATIC, "offline"))
        assertTrue(updateErrorState(UpdateCheckSource.MANUAL, "offline") is UpdateState.Error)
    }

    @Test
    fun activeUpdateFlowsBlockDuplicateChecks() {
        assertTrue(canStartUpdateCheck(UpdateState.Idle))
        assertTrue(canStartUpdateCheck(UpdateState.UpToDate))
        assertTrue(canStartUpdateCheck(UpdateState.Error("retry")))
        assertFalse(canStartUpdateCheck(UpdateState.Checking))
        assertFalse(canStartUpdateCheck(UpdateState.Installing))
        assertFalse(canStartUpdateCheck(UpdateState.Downloading(0.5f)))
        assertFalse(canStartUpdateCheck(UpdateState.AwaitingInstallPermission("v1.0.5")))
        assertFalse(
            canStartUpdateCheck(
                UpdateState.UpdateAvailable(
                    GitHubRelease(
                        tagName = "v1.0.5",
                        htmlUrl = "https://example.invalid/releases/v1.0.5",
                        body = null,
                        assets = emptyList()
                    )
                )
            )
        )
    }
}
