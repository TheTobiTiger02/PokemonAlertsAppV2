package com.example.pokemonalertsv2.ui.alerts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

internal const val MAP_USER_LOCATION_BLUE: Int = 0xFF1A73E8.toInt()

internal fun createMapUserMarkerBitmap(context: Context, directional: Boolean): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (56f * density).toInt().coerceAtLeast(56)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val blueColor = MAP_USER_LOCATION_BLUE

        if (directional) {
            val beamRadius = size * 0.46f
            val beamAngle = 44f
            val startAngle = -90f - (beamAngle / 2f)

            // Draw soft Google Maps style directional beam fan radiating forward
            val beamPath = Path().apply {
                moveTo(center, center)
                arcTo(
                    RectF(center - beamRadius, center - beamRadius, center + beamRadius, center + beamRadius),
                    startAngle,
                    beamAngle
                )
                close()
            }

            val beamGradient = RadialGradient(
                center,
                center,
                beamRadius,
                intArrayOf(
                    Color.argb(160, 26, 115, 232),
                    Color.argb(80, 26, 115, 232),
                    Color.argb(0, 26, 115, 232)
                ),
                floatArrayOf(0.0f, 0.65f, 1.0f),
                Shader.TileMode.CLAMP
            )

            val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = beamGradient
                style = Paint.Style.FILL
            }
            canvas.drawPath(beamPath, beamPaint)

            val beamEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(100, 26, 115, 232)
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
            }
            canvas.drawPath(beamPath, beamEdgePaint)
        }

        // Draw crisp white halo ring
        val whiteHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(4f * density, 0f, 1.5f * density, Color.argb(60, 0, 0, 0))
        }
        canvas.drawCircle(center, center, size * 0.18f, whiteHaloPaint)

        // Draw center solid blue dot
        val blueDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blueColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, size * 0.13f, blueDotPaint)
    }
}
