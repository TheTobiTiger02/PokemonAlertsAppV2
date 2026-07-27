package com.example.pokemonalertsv2.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapFallbackImageGeneratorTest {

    @Test
    fun paddedSprite_isCroppedCenteredAndAspectRatioPreserved() {
        val background = Color.rgb(82, 98, 116)
        val output = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888).apply {
            eraseColor(background)
        }
        val sprite = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        Canvas(sprite).drawRect(
            20f,
            35f,
            80f,
            65f,
            Paint().apply { color = Color.RED }
        )

        MapFallbackImageGenerator.drawPokemonAtCoordinate(
            canvas = Canvas(output),
            coordinateX = 256f,
            coordinateY = 128f,
            minDimension = 256,
            sprite = sprite
        )

        val redBounds = colorBounds(output) { color ->
            Color.red(color) > 180 && Color.green(color) < 80 && Color.blue(color) < 80
        }
        assertNotNull(redBounds)
        redBounds!!
        assertEquals(256f, redBounds.centerX(), 1.5f)
        assertEquals(128f, redBounds.centerY(), 1.5f)
        assertEquals(2f, redBounds.width().toFloat() / redBounds.height(), 0.2f)
        assertTrue(redBounds.width() in 58..63)

        // Transparent space around the cropped sprite remains map-only.
        assertEquals(background, output.getPixel(256, 110))
        assertEquals(background, output.getPixel(224, 104))
        for (y in 0 until output.height) {
            for (x in 0 until output.width) {
                if (x in 215..297 && y in 99..157) continue
                assertEquals("Unexpected map change at ($x, $y)", background, output.getPixel(x, y))
            }
        }
    }

    @Test
    fun fullyTransparentSprite_usesCoordinateDotWithoutBadge() {
        val background = Color.rgb(82, 98, 116)
        val output = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            eraseColor(background)
        }
        val transparentSprite = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)

        MapFallbackImageGenerator.drawPokemonAtCoordinate(
            canvas = Canvas(output),
            coordinateX = 128f,
            coordinateY = 128f,
            minDimension = 256,
            sprite = transparentSprite
        )

        assertEquals(Color.rgb(40, 105, 216), output.getPixel(128, 128))
        assertEquals(background, output.getPixel(128, 100))
    }

    @Test
    fun visibleContentBounds_ignoresTransparentPaddingAndRejectsEmptySprites() {
        val padded = Bitmap.createBitmap(20, 16, Bitmap.Config.ARGB_8888)
        padded.setPixel(3, 4, Color.RED)
        padded.setPixel(14, 11, Color.BLUE)

        val bounds = MapFallbackImageGenerator.visibleContentBounds(padded)

        assertNotNull(bounds)
        assertEquals(3, bounds!!.left)
        assertEquals(4, bounds.top)
        assertEquals(15, bounds.right)
        assertEquals(12, bounds.bottom)
        assertNull(
            MapFallbackImageGenerator.visibleContentBounds(
                Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            )
        )
    }

    @Test
    fun spriteTargetSize_scalesForWidgetAndNotificationAndCapsForShareCard() {
        assertEquals(40, MapFallbackImageGenerator.spriteTargetSizeFor(168))
        assertEquals(61, MapFallbackImageGenerator.spriteTargetSizeFor(256))
        assertEquals(128, MapFallbackImageGenerator.spriteTargetSizeFor(650))
    }

    private data class PixelBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        fun width(): Int = right - left + 1
        fun height(): Int = bottom - top + 1
        fun centerX(): Float = (left + right) / 2f
        fun centerY(): Float = (top + bottom) / 2f
    }

    private fun colorBounds(bitmap: Bitmap, matches: (Int) -> Boolean): PixelBounds? {
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (!matches(bitmap.getPixel(x, y))) continue
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        return if (right < left || bottom < top) null else PixelBounds(left, top, right, bottom)
    }
}
