package com.example.pokemonalertsv2.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadarLocationLayerInstrumentedTest {
    @Test
    fun foregroundLocationDotRemainsVisibleAboveAnOverlappingAlert() {
        val width = 320
        val height = 240
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val viewport = RadarViewport(RadarGeoPoint(49.8728, 8.6512), 16)
        val input = RadarRenderInput(
            alerts = emptyList(),
            location = null,
            widthPx = width,
            heightPx = height,
            density = 1f
        )
        val location = Location("test").apply {
            latitude = viewport.center.latitude
            longitude = viewport.center.longitude
            accuracy = 40f
        }

        WidgetRadarImageRenderer.drawRadarUserAccuracyRing(canvas, input, viewport, location)
        canvas.drawCircle(
            width / 2f,
            height / 2f,
            18f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }
        )
        assertEquals(Color.RED, bitmap.getPixel(width / 2, height / 2))

        WidgetRadarImageRenderer.drawRadarUserLocationDot(canvas, input, viewport, location)

        assertEquals(0xFF1A73E8.toInt(), bitmap.getPixel(width / 2, height / 2))
        assertNotEquals(Color.RED, bitmap.getPixel(width / 2, height / 2))
    }
}
