package com.example.pokemonalertsv2.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun systemFollowsDeviceTheme() {
        assertTrue(AppThemeMode.SYSTEM.resolveDark(systemDark = true))
        assertFalse(AppThemeMode.SYSTEM.resolveDark(systemDark = false))
    }

    @Test
    fun explicitModesOverrideDeviceTheme() {
        assertTrue(AppThemeMode.DARK.resolveDark(systemDark = false))
        assertFalse(AppThemeMode.LIGHT.resolveDark(systemDark = true))
    }

    @Test
    fun unknownStoredValueFallsBackToSystem() {
        assertTrue(AppThemeMode.fromStored(99).resolveDark(systemDark = true))
        assertFalse(AppThemeMode.fromStored(99).resolveDark(systemDark = false))
    }
}
