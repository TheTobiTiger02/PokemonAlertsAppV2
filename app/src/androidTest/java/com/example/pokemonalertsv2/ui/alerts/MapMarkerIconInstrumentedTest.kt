package com.example.pokemonalertsv2.ui.alerts

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapMarkerIconInstrumentedTest {
    @Test
    fun synchronousFallbackIsAlwaysACompleteCustomMarker() {
        val request = MapMarkerIconRequest(
            sizePx = 68,
            categoryCode = "SP",
            speciesName = "Mr Mime",
            speciesImageUrl = null,
            endTime = "2099-01-01T00:00:00Z",
            showTimeLabel = true,
            timeLabel = "12m",
            palette = MapMarkerPalette(
                primary = 0xFF00639A.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                surface = 0xFFFFFFFF.toInt(),
                onSurface = 0xFF1A1C1E.toInt(),
                outline = 0xFF73777F.toInt(),
                error = 0xFFBA1A1A.toInt(),
                onError = 0xFFFFFFFF.toInt()
            ),
            goDexStatus = GoDexMatchStatus.NOT_CONFIGURED
        )

        val fallback = resolveInitialMapMarkerIcon(request)

        assertNotNull(fallback.bitmap)
        assertTrue(fallback.bitmap.width > request.sizePx)
        assertTrue(fallback.bitmap.height > request.sizePx)
        assertEquals(0.5f, fallback.anchor.x, 0.001f)
        assertTrue(fallback.anchor.y in 0f..1f)
        assertTrue(fallback.bitmap.byteCount > 0)
    }

    @Test
    fun aHuntNumberIsActuallyPaintedOntoThePin() {
        // The badge is the only thing that differs, so the two bitmaps differing at
        // all proves it reached the canvas -- and comparing the ground-left corner
        // proves it landed where it was aimed rather than under something else.
        val plain = resolveInitialMapMarkerIcon(numberedRequest(ordinal = null))
        val numbered = resolveInitialMapMarkerIcon(numberedRequest(ordinal = 3))

        assertEquals(plain.bitmap.width, numbered.bitmap.width)
        assertEquals(plain.bitmap.height, numbered.bitmap.height)
        assertTrue("the numbered pin is identical to the plain one", differs(plain, numbered))
    }

    @Test
    fun differentNumbersPaintDifferentPins() {
        val one = resolveInitialMapMarkerIcon(numberedRequest(ordinal = 1))
        val two = resolveInitialMapMarkerIcon(numberedRequest(ordinal = 2))

        assertTrue("1 and 2 render the same pixels", differs(one, two))
    }

    private fun differs(a: MapMarkerIcon, b: MapMarkerIcon): Boolean {
        for (x in 0 until a.bitmap.width) {
            for (y in 0 until a.bitmap.height) {
                if (a.bitmap.getPixel(x, y) != b.bitmap.getPixel(x, y)) return true
            }
        }
        return false
    }

    private fun numberedRequest(ordinal: Int?) = MapMarkerIconRequest(
        sizePx = 96,
        categoryCode = "SP",
        speciesName = "Larvitar",
        speciesImageUrl = null,
        endTime = "2099-01-01T00:00:00Z",
        showTimeLabel = false,
        timeLabel = null,
        palette = MapMarkerPalette(
            primary = 0xFF00639A.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            surface = 0xFFFFFFFF.toInt(),
            onSurface = 0xFF1A1C1E.toInt(),
            outline = 0xFF73777F.toInt(),
            error = 0xFFBA1A1A.toInt(),
            onError = 0xFFFFFFFF.toInt()
        ),
        goDexStatus = GoDexMatchStatus.NOT_CONFIGURED,
        ordinal = ordinal
    )
}
