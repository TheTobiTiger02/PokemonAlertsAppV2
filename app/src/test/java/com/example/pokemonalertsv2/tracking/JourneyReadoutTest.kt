package com.example.pokemonalertsv2.tracking

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class JourneyReadoutTest {

    @Test
    fun `Android 16 uses the status bar chip, whatever else is available`() {
        // The chip needs no permission and cannot be covered, so it always wins.
        listOf(true, false).forEach { overlays ->
            listOf(true, false).forEach { allowed ->
                assertEquals(
                    "sdk=36 overlays=$overlays allowed=$allowed",
                    JourneyReadoutSurface.STATUS_BAR_CHIP,
                    resolveJourneyReadoutSurface(36, overlays, allowed)
                )
            }
        }
    }

    @Test
    fun `below 16 the pill takes over, but only with the grant and the preference`() {
        assertEquals(
            JourneyReadoutSurface.OVERLAY_PILL,
            resolveJourneyReadoutSurface(35, canDrawOverlays = true, overlayAllowed = true)
        )
        assertEquals(
            "no grant means no pill",
            JourneyReadoutSurface.MAP_LABEL,
            resolveJourneyReadoutSurface(35, canDrawOverlays = false, overlayAllowed = true)
        )
        assertEquals(
            "declining the pill drops to the map, not to nothing",
            JourneyReadoutSurface.MAP_LABEL,
            resolveJourneyReadoutSurface(35, canDrawOverlays = true, overlayAllowed = false)
        )
    }

    @Test
    fun `an old device with nothing granted still gets a readout`() {
        assertEquals(
            JourneyReadoutSurface.MAP_LABEL,
            resolveJourneyReadoutSurface(26, canDrawOverlays = false, overlayAllowed = false)
        )
    }

    @Test
    fun `a hunt never opens picture-in-picture, on any device`() {
        // It draws its map in an overlay window instead. PiP is a visible task and
        // would suppress the chip; an overlay is not and does not.
        JourneyReadoutSurface.entries.forEach { surface ->
            assertEquals(surface.name, false, shouldOpenHuntPictureInPicture(surface))
        }
    }

    @Test
    fun `the floating map carries the readout unless the pill already does`() {
        // The chip is always hidden while that window is up, so the map has to.
        assertEquals(true, shouldLabelJourneyOnMap(JourneyReadoutSurface.STATUS_BAR_CHIP))
        assertEquals(true, shouldLabelJourneyOnMap(JourneyReadoutSurface.MAP_LABEL))
        assertEquals(
            "the pill is already on screen; two copies of one number is noise",
            false,
            shouldLabelJourneyOnMap(JourneyReadoutSurface.OVERLAY_PILL)
        )
    }

    @Test
    fun `walking shows the distance`() {
        assertEquals("320 m", detail(distanceMeters = 320f, inRange = false))
        // Built with the same locale rather than hard-coded: the decimal separator
        // is a comma in the locale this project is developed in, and the formatting
        // being locale-aware is the correct behaviour, not the bug.
        val kilometres = String.format(Locale.getDefault(), "%.1f km", 1.5f)
        assertEquals(kilometres, detail(distanceMeters = 1_500f, inRange = false))
    }

    @Test
    fun `no fix yet says so rather than showing a wrong number`() {
        assertEquals("locating", detail(distanceMeters = null, inRange = false))
    }

    @Test
    fun `in range on a hunt swaps the distance for the deciding fact`() {
        assertEquals(
            "CP 1234",
            detail(distanceMeters = 12f, inRange = true, huntActive = true)
        )
    }

    @Test
    fun `in range without a hunt keeps the plain wording`() {
        assertEquals(
            "in range",
            detail(distanceMeters = 12f, inRange = true, huntActive = false)
        )
    }

    @Test
    fun `a hunt target with nothing worth showing falls back too`() {
        val bare = PokemonAlert(name = "Ditto", type = listOf("Spawn"), pokemon = "Ditto")
        assertEquals(
            "in range",
            journeyDetailText(bare, 12f, inRange = true, huntActive = true, "in range", "locating")
        )
    }

    private fun detail(
        distanceMeters: Float?,
        inRange: Boolean,
        huntActive: Boolean = false
    ): String = journeyDetailText(
        alert = PokemonAlert(
            name = "Larvitar",
            type = listOf("Spawn"),
            pokemon = "Larvitar",
            cp = 1234
        ),
        distanceMeters = distanceMeters,
        inRange = inRange,
        huntActive = huntActive,
        inRangeFallback = "in range",
        locatingFallback = "locating"
    )
}
