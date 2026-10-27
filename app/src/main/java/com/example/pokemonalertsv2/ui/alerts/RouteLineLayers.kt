package com.example.pokemonalertsv2.ui.alerts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolPlacement
import org.maplibre.android.style.layers.PropertyFactory.symbolSpacing
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.maps.Style

/** How a line's length is broken up. Dots and dashes are measured in line widths, as MapLibre does. */
internal enum class RoutePattern { SOLID, DASHED, DOTTED }

/** One part of a route: the next leg, the later legs, or what is already walked. A [width] of 0 hides it. */
internal data class RouteStroke(
    val color: Int,
    val width: Float,
    val opacity: Float = 1f,
    val casingColor: Int = android.graphics.Color.WHITE,
    /** 0 draws no casing. */
    val casingWidth: Float = 0f,
    val casingOpacity: Float = 1f,
    val pattern: RoutePattern = RoutePattern.SOLID,
)

/**
 * How a walking route is drawn: the leg you walk now stands out, everything after it is quieter,
 * and what is already walked is quietest. Widths are in dp, as MapLibre takes them; Google
 * polylines multiply by the screen density.
 */
internal data class RouteLineStyle(
    val next: RouteStroke,
    val later: RouteStroke,
    val walked: RouteStroke,
    /** Direction chevrons along the next leg; MapLibre only. */
    val chevrons: Boolean,
) {
    companion object {
        const val COLOR = 0xFF1E63F0.toInt()
        const val WALKED_COLOR = 0xFF9AA3B2.toInt()
        const val CHEVRON_IMAGE = "route-chevron"
        /** Screen distance between chevrons, so zooming in never crowds them. */
        const val CHEVRON_SPACING_DP = 80f
        /** Gap between dots, in line widths; casings scale it so their dots sit under the line's. */
        const val DOT_GAP = 2f

        /** The in-app maps at rest: bold cased next leg with chevrons, faded later legs, grey walked part. */
        val CLASSIC = RouteLineStyle(
            next = RouteStroke(COLOR, 6f, casingWidth = 10f),
            later = RouteStroke(COLOR, 4f, opacity = 0.45f, casingWidth = 7f, casingOpacity = 0.6f),
            walked = RouteStroke(WALKED_COLOR, 3.5f, opacity = 0.7f),
            chevrons = true,
        )

        /** Walking a catch route: thick with a dark navy casing like a car's guidance line; later legs grey dashes. */
        val NAVIGATION = RouteLineStyle(
            next = RouteStroke(COLOR, 8f, casingColor = 0xFF0B2A6B.toInt(), casingWidth = 12f),
            later = RouteStroke(0xFF7D8696.toInt(), 4f, opacity = 0.85f, pattern = RoutePattern.DASHED),
            walked = RouteStroke(WALKED_COLOR, 3.5f, opacity = 0.7f),
            chevrons = true,
        )

        /** The floating hunt map: white-ringed dots for the next leg, smaller faded dots after it. */
        val FOOTSTEPS = RouteLineStyle(
            next = RouteStroke(COLOR, 7f, casingWidth = 10f, pattern = RoutePattern.DOTTED),
            later = RouteStroke(COLOR, 5f, opacity = 0.45f, pattern = RoutePattern.DOTTED),
            walked = RouteStroke(WALKED_COLOR, 4f, opacity = 0.6f, pattern = RoutePattern.DOTTED),
            chevrons = false,
        )
    }
}

/**
 * The dash array for a stroke [pattern] drawn [width] wide, in that width's units. [lineWidth] is the
 * width of the line a casing sits under, so the casing's dots land on the line's; null when solid.
 */
internal fun routeDashArray(pattern: RoutePattern, width: Float, lineWidth: Float = width): Array<Float>? = when (pattern) {
    RoutePattern.SOLID -> null
    RoutePattern.DASHED -> arrayOf(2f * lineWidth / width, 2f * lineWidth / width)
    RoutePattern.DOTTED -> arrayOf(0f, RouteLineStyle.DOT_GAP * lineWidth / width)
}

/** GeoJSON source ids for a route's parts; [walked] is optional because a hunt does not track it. */
internal data class RouteLineSources(val next: String, val later: String, val walked: String? = null)

private fun routeLayerIds(prefix: String) = listOf(
    "$prefix-walked-casing", "$prefix-walked", "$prefix-later-casing", "$prefix-later",
    "$prefix-next-casing", "$prefix-next", "$prefix-chevrons"
)

/**
 * Adds the route layers, bottom to top: walked, later legs, the next leg, its chevrons, under
 * [below] when given. The sources must already exist. Chevrons are placed by the map along the
 * line, so camera moves cost nothing extra on the UI thread.
 */
internal fun Style.addRouteLineLayers(
    prefix: String,
    sources: RouteLineSources,
    context: Context,
    look: RouteLineStyle = RouteLineStyle.CLASSIC,
    below: String? = null,
) {
    if (getImage(RouteLineStyle.CHEVRON_IMAGE) == null) {
        addImage(RouteLineStyle.CHEVRON_IMAGE, routeChevronBitmap(context.resources.displayMetrics.density))
    }
    fun add(layer: Layer) = if (below != null) addLayerBelow(layer, below) else addLayer(layer)
    fun line(id: String, source: String, color: Int, width: Float, opacity: Float, dashes: Array<Float>?) =
        LineLayer(id, source).withProperties(
            lineColor(color), lineWidth(width), lineOpacity(opacity),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
        ).also { layer -> dashes?.let { layer.setProperties(lineDasharray(it)) } }
    // Every layer exists in every look, so replacing a look always knows what to remove.
    fun stroke(id: String, source: String?, stroke: RouteStroke) {
        source ?: return
        add(line("$id-casing", source, stroke.casingColor, stroke.casingWidth,
            if (stroke.casingWidth > 0f && stroke.width > 0f) stroke.casingOpacity else 0f,
            routeDashArray(stroke.pattern, stroke.casingWidth.coerceAtLeast(1f), stroke.width.coerceAtLeast(1f))))
        add(line(id, source, stroke.color, stroke.width, if (stroke.width > 0f) stroke.opacity else 0f,
            routeDashArray(stroke.pattern, stroke.width.coerceAtLeast(1f))))
    }
    stroke("$prefix-walked", sources.walked, look.walked)
    stroke("$prefix-later", sources.later, look.later)
    stroke("$prefix-next", sources.next, look.next)
    add(
        // A line whose feature says arrows=false (a route preview) goes without.
        SymbolLayer("$prefix-chevrons", sources.next).withFilter(
            Expression.neq(Expression.get("arrows"), Expression.literal(false))
        ).withProperties(
            symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
            symbolSpacing(RouteLineStyle.CHEVRON_SPACING_DP),
            iconImage(RouteLineStyle.CHEVRON_IMAGE),
            iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
            visibility(if (look.chevrons) Property.VISIBLE else Property.NONE)
        )
    )
}

/**
 * Swaps the route's look in place: the layers go and come back at the same height, between the
 * same neighbours. The sources and their data stay.
 */
internal fun Style.replaceRouteLineLayers(prefix: String, sources: RouteLineSources, context: Context, look: RouteLineStyle) {
    val ids = routeLayerIds(prefix).toSet()
    val order = layers.map { it.id }
    val top = order.indexOfLast { it in ids }
    if (top < 0) return
    val above = order.drop(top + 1).firstOrNull()
    order.filter { it in ids }.forEach { removeLayer(it) }
    addRouteLineLayers(prefix, sources, context, look, below = above)
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
