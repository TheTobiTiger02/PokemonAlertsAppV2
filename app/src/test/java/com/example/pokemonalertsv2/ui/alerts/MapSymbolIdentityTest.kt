package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

/**
 * What decides that two markers can share one drawing.
 *
 * On the OpenStreetMap provider these keys are style image ids, so two markers with equal keys
 * share a single GPU texture and a marker whose key does not change between updates is never
 * re-uploaded. That makes the equality rules here a rendering cost, not just a cache detail:
 * a key that varies too much re-uploads the map every tick, and one that varies too little
 * draws the wrong pin.
 */
class MapSymbolIdentityTest {

    private val palette = MapMarkerPalette(
        primary = 0xFF00FF00.toInt(),
        onPrimary = 0xFFFFFFFF.toInt(),
        surface = 0xFF101010.toInt(),
        onSurface = 0xFFFFFFFF.toInt(),
        outline = 0xFF808080.toInt(),
        error = 0xFFFF0000.toInt(),
        onError = 0xFFFFFFFF.toInt()
    )

    private fun request(
        species: String = "Pikachu",
        endTime: String? = null,
        timeLabel: String? = null,
        showTimeLabel: Boolean = false,
        sizePx: Int = 120,
        ordinal: Int? = null
    ) = MapMarkerIconRequest(
        sizePx = sizePx,
        categoryCode = "CP 500",
        speciesName = species,
        speciesImageUrl = "https://example.invalid/$species.png",
        endTime = endTime,
        showTimeLabel = showTimeLabel,
        timeLabel = timeLabel,
        palette = palette,
        goDexStatus = GoDexMatchStatus.NOT_CONFIGURED,
        category = AlertCategory.SPAWN,
        ordinal = ordinal
    )

    private fun inMinutes(minutes: Long): String =
        Instant.ofEpochMilli(NOW + minutes * 60_000L).toString()

    @Test
    fun sameSpeciesAndStateShareOneImage() {
        // The whole point of the shared-image layer: a screenful of the same spawn is one texture.
        assertEquals(
            mapMarkerBaseIconCacheKey(request(), NOW),
            mapMarkerBaseIconCacheKey(request(), NOW)
        )
    }

    @Test
    fun differentSpeciesDoNotShareAnImage() {
        assertNotEquals(
            mapMarkerBaseIconCacheKey(request(species = "Pikachu"), NOW),
            mapMarkerBaseIconCacheKey(request(species = "Bulbasaur"), NOW)
        )
    }

    @Test
    fun countdownTextDoesNotChangeThePinsIdentity() {
        // A tick must not invalidate the pin: the countdown is its own sprite now, and this is
        // what keeps a ticking marker from re-uploading its artwork every second.
        assertEquals(
            mapMarkerBaseIconCacheKey(request(timeLabel = "12m", showTimeLabel = true), NOW),
            mapMarkerBaseIconCacheKey(request(timeLabel = "11m", showTimeLabel = true), NOW)
        )
    }

    @Test
    fun expiryTimeAloneDoesNotChangeThePinsIdentity() {
        assertEquals(
            mapMarkerBaseIconCacheKey(request(endTime = inMinutes(40)), NOW),
            mapMarkerBaseIconCacheKey(request(endTime = inMinutes(55)), NOW)
        )
    }

    @Test
    fun becomingUrgentChangesThePinsIdentity() {
        // An urgent pin is drawn differently, so it must not be served the calm one's image.
        // The base key used to drop endTime entirely, which collapsed both onto one entry and
        // let whichever rendered first decide how both of them looked.
        assertNotEquals(
            mapMarkerBaseIconCacheKey(request(endTime = inMinutes(40)), NOW),
            mapMarkerBaseIconCacheKey(request(endTime = inMinutes(2)), NOW)
        )
    }

    @Test
    fun markerSizeChangesThePinsIdentity() {
        assertNotEquals(
            mapMarkerBaseIconCacheKey(request(sizePx = 120), NOW),
            mapMarkerBaseIconCacheKey(request(sizePx = 160), NOW)
        )
    }

    @Test
    fun countdownSpritesAreSharedByWhatTheySay() {
        assertEquals(
            mapCountdownLabelImageKey("12m", urgent = false, heightPx = 26),
            mapCountdownLabelImageKey("12m", urgent = false, heightPx = 26)
        )
        assertNotEquals(
            mapCountdownLabelImageKey("12m", urgent = false, heightPx = 26),
            mapCountdownLabelImageKey("11m", urgent = false, heightPx = 26)
        )
    }

    @Test
    fun urgentCountdownSpritesAreDistinct() {
        assertNotEquals(
            mapCountdownLabelImageKey("2m", urgent = false, heightPx = 26),
            mapCountdownLabelImageKey("2m", urgent = true, heightPx = 26)
        )
    }

    @Test
    fun countdownSpriteIdsAreRecognisableForEviction() {
        // Eviction drops unused countdown sprites every update but keeps pin artwork, so the
        // two have to be tellable apart by id alone.
        assertEquals(
            true,
            mapCountdownLabelImageKey("2m", urgent = false, heightPx = 26).startsWith("cd|")
        )
        assertEquals(
            false,
            mapMarkerBaseIconCacheKey(request(), NOW).startsWith("cd|")
        )
    }

    @Test
    fun countdownStripHeightFollowsMarkerSize() {
        assertEquals(26, mapCountdownLabelHeightPx(120))
        // Never smaller than legible, however small the marker gets.
        assertEquals(16, mapCountdownLabelHeightPx(20))
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }

    @Test
    fun `a hunt number changes the pins identity`() {
        // The number is drawn on the pin, so it has to be in the key. Sharing a key
        // would mean the second target renders the first one's texture -- on the
        // MapLibre path this key IS the style-image id.
        val plain = mapMarkerBaseIconCacheKey(request(), NOW)
        val first = mapMarkerBaseIconCacheKey(request(ordinal = 1), NOW)
        val second = mapMarkerBaseIconCacheKey(request(ordinal = 2), NOW)

        assertNotEquals(plain, first)
        assertNotEquals(first, second)
    }

    @Test
    fun `the same number on the same species shares one image`() {
        assertEquals(
            mapMarkerBaseIconCacheKey(request(ordinal = 3), NOW),
            mapMarkerBaseIconCacheKey(request(ordinal = 3), NOW)
        )
    }

    @Test
    fun `the number is in the countdown key too`() {
        assertNotEquals(
            mapMarkerIconCacheKey(request(ordinal = 1), NOW),
            mapMarkerIconCacheKey(request(ordinal = 2), NOW)
        )
    }
}
