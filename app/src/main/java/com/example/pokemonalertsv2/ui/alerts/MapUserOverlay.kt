package com.example.pokemonalertsv2.ui.alerts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

internal const val MAP_USER_LOCATION_BLUE: Int = 0xFF2196F3.toInt()

internal fun createMapUserMarkerBitmap(context: Context, directional: Boolean): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (44f * density).toInt().coerceAtLeast(44)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MAP_USER_LOCATION_BLUE }

        if (directional) {
            val outline = Path().apply {
                moveTo(center, size * 0.05f)
                lineTo(size * 0.82f, size * 0.88f)
                lineTo(center, size * 0.70f)
                lineTo(size * 0.18f, size * 0.88f)
                close()
            }
            canvas.drawPath(outline, white)
            val arrow = Path().apply {
                moveTo(center, size * 0.14f)
                lineTo(size * 0.73f, size * 0.78f)
                lineTo(center, size * 0.63f)
                lineTo(size * 0.27f, size * 0.78f)
                close()
            }
            canvas.drawPath(arrow, blue)
        } else {
            canvas.drawCircle(center, center, size * 0.28f, white)
            canvas.drawCircle(center, center, size * 0.20f, blue)
        }
    }
}
