package com.example.pokemonalertsv2.ui.alerts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolPlacement
import org.maplibre.android.style.layers.PropertyFactory.symbolSpacing
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.maps.Style

/**
 * One look for every walking route on a map, Hunt and Catch Route alike. The leg you are
 * walking now is bold with a white casing and direction chevrons; everything after it is the
 * same blue, thinner and faded; what is already walked is a quiet grey. Widths are in dp, as
 * MapLibre takes them; Google polylines multiply by the screen density.
 */
internal object RouteLineStyle {
    const val COLOR = 0xFF1E63F0.toInt()
    const val WALKED_COLOR = 0xFF9AA3B2.toInt()
    const val NEXT_WIDTH = 6f
    const val NEXT_CASING_WIDTH = 10f
    const val LATER_WIDTH = 4f
    const val LATER_CASING_WIDTH = 7f
    const val LATER_OPACITY = 0.45f
    const val LATER_CASING_OPACITY = 0.6f
    const val WALKED_WIDTH = 3.5f
    const val WALKED_OPACITY = 0.7f
    const val CHEVRON_IMAGE = "route-chevron"
    /** Screen distance between chevrons, so zooming in never crowds them. */
    const val CHEVRON_SPACING_DP = 80f
}

/** GeoJSON source ids for a route's parts; [walked] is optional because a hunt does not track it. */
internal data class RouteLineSources(val next: String, val later: String, val walked: String? = null)

/**
 * Adds the route layers, bottom to top: walked, later legs, the next leg, its chevrons. The
 * sources must already exist. Chevrons are placed by the map along the line, so camera moves
 * cost nothing extra on the UI thread.
 */
internal fun Style.addRouteLineLayers(prefix: String, sources: RouteLineSources, context: Context) {
    if (getImage(RouteLineStyle.CHEVRON_IMAGE) == null) {
        addImage(RouteLineStyle.CHEVRON_IMAGE, routeChevronBitmap(context.resources.displayMetrics.density))
    }
    fun line(id: String, source: String, color: Int, width: Float, opacity: Float) =
        LineLayer(id, source).withProperties(
            lineColor(color), lineWidth(width), lineOpacity(opacity),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
        )
    sources.walked?.let {
        addLayer(line("$prefix-walked", it, RouteLineStyle.WALKED_COLOR, RouteLineStyle.WALKED_WIDTH, RouteLineStyle.WALKED_OPACITY))
    }
    addLayer(line("$prefix-later-casing", sources.later, android.graphics.Color.WHITE,
        RouteLineStyle.LATER_CASING_WIDTH, RouteLineStyle.LATER_CASING_OPACITY))
    addLayer(line("$prefix-later", sources.later, RouteLineStyle.COLOR, RouteLineStyle.LATER_WIDTH, RouteLineStyle.LATER_OPACITY))
    addLayer(line("$prefix-next-casing", sources.next, android.graphics.Color.WHITE, RouteLineStyle.NEXT_CASING_WIDTH, 1f))
    addLayer(line("$prefix-next", sources.next, RouteLineStyle.COLOR, RouteLineStyle.NEXT_WIDTH, 1f))
    addLayer(
        // A line whose feature says arrows=false (a route preview) goes without.
        SymbolLayer("$prefix-chevrons", sources.next).withFilter(
            Expression.neq(Expression.get("arrows"), Expression.literal(false))
        ).withProperties(
            symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
            symbolSpacing(RouteLineStyle.CHEVRON_SPACING_DP),
            iconImage(RouteLineStyle.CHEVRON_IMAGE),
            iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            iconAllowOverlap(true),
            iconIgnorePlacement(true)
        )
    )
}

/** A small white ">" that points along the line; MapLibre turns it to follow each segment. */
private fun routeChevronBitmap(density: Float): Bitmap {
    val size = (10 * density).toInt().coerceAtLeast(8)
    val bitmap = createBitmap(size, size)
    val unit = size / 10f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * unit
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    Canvas(bitmap).drawPath(Path().apply {
        moveTo(3.5f * unit, 2f * unit); lineTo(6.5f * unit, 5f * unit); lineTo(3.5f * unit, 8f * unit)
    }, paint)
    return bitmap
}
