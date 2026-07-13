package com.example.pokemonalertsv2.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetThemePaletteTest {
    @Test
    fun lightAndDarkPalettesUseIndependentSurfacesAndForegrounds() {
        assertFalse(WidgetThemePalette.Light.dark)
        assertTrue(WidgetThemePalette.Dark.dark)
        assertNotEquals(WidgetThemePalette.Light.backgroundDrawable, WidgetThemePalette.Dark.backgroundDrawable)
        assertNotEquals(WidgetThemePalette.Light.itemDrawable, WidgetThemePalette.Dark.itemDrawable)
        assertNotEquals(WidgetThemePalette.Light.onSurface, WidgetThemePalette.Dark.onSurface)
    }
}
