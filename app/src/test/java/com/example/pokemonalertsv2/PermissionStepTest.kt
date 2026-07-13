package com.example.pokemonalertsv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStepTest {
    @Test
    fun notificationAlwaysAdvancesToForegroundLocation() {
        assertEquals(PermissionStep.FOREGROUND_LOCATION, PermissionStep.NOTIFICATION.afterResult(true))
        assertEquals(PermissionStep.FOREGROUND_LOCATION, PermissionStep.NOTIFICATION.afterResult(false))
    }

    @Test
    fun backgroundOnlyFollowsGrantedForegroundLocation() {
        assertEquals(PermissionStep.BACKGROUND_LOCATION, PermissionStep.FOREGROUND_LOCATION.afterResult(true))
        assertEquals(PermissionStep.COMPLETE, PermissionStep.FOREGROUND_LOCATION.afterResult(false))
    }

    @Test
    fun onlyPermissionRequestsAreActive() {
        assertTrue(PermissionStep.NOTIFICATION.isRequestActive())
        assertTrue(PermissionStep.FOREGROUND_LOCATION.isRequestActive())
        assertTrue(PermissionStep.BACKGROUND_LOCATION.isRequestActive())
        assertFalse(PermissionStep.IDLE.isRequestActive())
        assertFalse(PermissionStep.COMPLETE.isRequestActive())
    }
}
