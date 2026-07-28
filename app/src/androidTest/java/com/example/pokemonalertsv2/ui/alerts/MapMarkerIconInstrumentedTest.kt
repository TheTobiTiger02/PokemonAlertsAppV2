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
}
