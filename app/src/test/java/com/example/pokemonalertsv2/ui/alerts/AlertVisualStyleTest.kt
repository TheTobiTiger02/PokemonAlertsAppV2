package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertVisualStyleTest {
    @Test
    fun resolvesKnownTypeToLabelAndShortCode() {
        val style = resolveAlertVisualStyle("raid boss")

        assertEquals("Raid", style.label)
        assertEquals("RD", style.shortCode)
    }

    @Test
    fun fallsBackToGenericStyleForUnknownType() {
        val style = resolveAlertVisualStyle("something-new")

        assertEquals("Alert", style.label)
        assertEquals("ALT", style.shortCode)
    }

    @Test
    fun structuredAlertFlagsOverrideLooseTypeText() {
        val hundo = PokemonAlert(
            name = "Perfect Eevee",
            type = listOf("Spawn"),
            ivAttack = 15,
            ivDefense = 15,
            ivStamina = 15
        )
        val nundo = PokemonAlert(
            name = "Zero Chansey",
            type = listOf("Spawn"),
            ivAttack = 0,
            ivDefense = 0,
            ivStamina = 0
        )
        val weather = PokemonAlert(name = "Weather", type = listOf("WeatherChange"))
        val rocket = PokemonAlert(name = "Rocket", type = listOf("Spawn"), gruntType = "Grass")

        assertEquals("100", resolveAlertVisualStyle(hundo).shortCode)
        assertEquals("000", resolveAlertVisualStyle(nundo).shortCode)
        assertEquals("WX", resolveAlertVisualStyle(weather).shortCode)
        assertEquals("RKT", resolveAlertVisualStyle(rocket).shortCode)
    }

    @Test
    fun categoriesUseDistinctSemanticAccents() {
        assertEquals(AlertCategory.HUNDO, resolveAlertVisualStyle("hundo").category)
        assertEquals(AlertCategory.NUNDO, resolveAlertVisualStyle("nundo").category)
        assertEquals(AlertCategory.PVP, resolveAlertVisualStyle("pvp").category)
        assertEquals(AlertCategory.RARE, resolveAlertVisualStyle("rare").category)
        assertEquals(AlertCategory.SPAWN, resolveAlertVisualStyle("spawn").category)
    }
}
