package com.example.pokemonalertsv2.catchroutes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.widget.FrameLayout
import com.example.pokemonalertsv2.ui.alerts.MAP_PIP_REFIT_METERS
import com.example.pokemonalertsv2.ui.alerts.MapLibreInitializer
import com.example.pokemonalertsv2.ui.alerts.MapPipFocus
import com.example.pokemonalertsv2.ui.alerts.openStreetMapStyleJson
import com.example.pokemonalertsv2.ui.alerts.resolveMapPipFocus
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.*
import kotlin.math.*

/**
 * Shared route renderer for the planner, Android PiP, the floating map and the area picker.
 *
 * Deliberately quiet: one line coloured from start (blue) to finish (violet) with the walked part
 * greyed out, a few direction chevrons spaced on screen, numbered stops only where Pokémon are caught
 * (the next one highlighted), small spawn dots, and start/finish pins. Range circles appear only for
 * the spawnpoints whose details are open.
 */
class CatchRouteMapView(context: Context) : FrameLayout(context) {
    private var map: MapLibreMap? = null
    private var view: MapView? = null
    private var settings = CatchRouteSettings()
    private var itinerary: CatchItinerary? = null
    private var user: CatchPoint? = null
    private var progressMeters: Double? = null
    private var fitted: CatchItinerary? = null
    /** The stop the trainer is walking to; what Follow frames alongside them. */
    private var focus: CatchPoint? = null
    /**
     * Follow keeps the trainer and the next stop in view as they walk; otherwise the camera shows
     * the whole route and stays where it is put. Panning or pinching by hand pauses Follow until
     * [follow] is called again, so the map never fights a trainer who is looking elsewhere.
     */
    var following = false
        private set
    private var framedUser: CatchPoint? = null
    private var framedFocus: CatchPoint? = null
    /** Told when Follow starts or stops, including when a gesture pauses it. */
    var onFollowChanged: ((Boolean) -> Unit)? = null
    var onPick: ((CatchPoint) -> Unit)? = null
    /** Tapped spawnpoints, by point id; nearest first. Takes precedence over [onPick]. */
    var onSpawnpointTap: ((List<String>) -> Unit)? = null
    private var selectedPointIds: Set<String> = emptySet()
    var onReady: (() -> Unit)? = null
    var onFailure: (() -> Unit)? = null
    private var started = false
    private var drawing = CatchRouteDrawing(emptyList(), emptyList(), emptyList(), emptyList())
    /** An area being drawn in the picker; replaces the route's own area while set. */
    private var editingArea: List<CatchPoint>? = null
    private val density = resources.displayMetrics.density

    // Screen-space Canvas markers stay crisp, declutter by screen distance, and avoid sprite-atlas updates.
    private val overlay = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val m = map ?: return
            fun screen(p: CatchPoint) = m.projection.toScreenLocation(LatLng(p.latitude, p.longitude))
            fun visible(x: Float, y: Float, pad: Float = 0f) = x >= -pad && y >= -pad && x <= width + pad && y <= height + pad
            val walked = progressMeters ?: -1.0
            val occupied = mutableListOf<RectF>()
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER

            // Endpoints first: they must never be hidden by a stop badge. Not while drawing an area.
            for (marker in if (editingArea == null) drawing.endpoints else emptyList()) {
                val p = screen(marker.point)
                if (!visible(p.x, p.y, 40 * density)) continue
                val finish = marker.label == "Finish"
                paint.style = Paint.Style.FILL
                paint.color = 0xFFFFFFFF.toInt(); canvas.drawCircle(p.x, p.y, 11 * density, paint)
                paint.color = if (finish) 0xFF20242C.toInt() else 0xFF1B9E5A.toInt(); canvas.drawCircle(p.x, p.y, 8.5f * density, paint)
                if (marker.label == "Start / Finish") {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f * density; paint.color = 0xFF20242C.toInt()
                    canvas.drawCircle(p.x, p.y, 8.5f * density, paint); paint.style = Paint.Style.FILL
                }
                paint.color = 0xFFFFFFFF.toInt(); paint.textSize = 10 * density
                canvas.drawText(if (finish) "F" else "S", p.x, p.y - (paint.ascent() + paint.descent()) / 2, paint)
                // A small caption under the pin says which is which.
                paint.textSize = 11 * density
                val caption = marker.label
                val half = paint.measureText(caption) / 2 + 5 * density
                val box = RectF(p.x - half, p.y + 13 * density, p.x + half, p.y + 29 * density)
                paint.color = 0xE6FFFFFF.toInt(); canvas.drawRoundRect(box, 8 * density, 8 * density, paint)
                paint.color = 0xFF20242C.toInt(); canvas.drawText(caption, box.centerX(), box.centerY() - (paint.ascent() + paint.descent()) / 2, paint)
                occupied += RectF(p.x - 12 * density, p.y - 12 * density, p.x + 12 * density, p.y + 12 * density)
                occupied += box
            }

            // Numbered stops: the next one is larger and orange; walked ones fade; overlaps are skipped.
            val next = drawing.stops.firstOrNull { it.meters > walked + 1 }
            for (stop in listOfNotNull(next) + drawing.stops.filter { it !== next }) {
                val p = screen(stop.point)
                if (!visible(p.x, p.y, 20 * density)) continue
                val isNext = stop === next && progressMeters != null
                val done = stop.meters <= walked
                val r = (if (isNext) 13f else 10f) * density
                val box = RectF(p.x - r, p.y - r, p.x + r, p.y + r)
                if (!isNext && occupied.any { RectF.intersects(it, box) }) continue
                occupied += box
                paint.style = Paint.Style.FILL
                paint.color = 0xFFFFFFFF.toInt(); canvas.drawCircle(p.x, p.y, r, paint)
                paint.color = when { isNext -> 0xFFE8710A.toInt(); done -> 0xFF9AA3B2.toInt(); else -> 0xFF1E4FD8.toInt() }
                canvas.drawCircle(p.x, p.y, r - 2 * density, paint)
                paint.color = 0xFFFFFFFF.toInt(); paint.textSize = (if (isNext) 12f else 10f) * density
                canvas.drawText(stop.label, p.x, p.y - (paint.ascent() + paint.descent()) / 2, paint)
            }

            // Chevrons: at most one per ~90 dp of line on screen, never on top of a marker, not on the walked part.
            var last: android.graphics.PointF? = null
            paint.style = Paint.Style.FILL
            for (arrow in drawing.arrows) {
                if (arrow.meters <= walked) continue
                val p = screen(arrow.point)
                if (!visible(p.x, p.y)) { last = null; continue }
                if (last != null && hypot(p.x - last.x, p.y - last.y) < 90 * density) continue
                val box = RectF(p.x - 6 * density, p.y - 6 * density, p.x + 6 * density, p.y + 6 * density)
                if (occupied.any { RectF.intersects(it, box) }) continue
                last = p
                canvas.save(); canvas.translate(p.x, p.y); canvas.rotate((arrow.bearing - m.cameraPosition.bearing).toFloat())
                val shape = Path().apply { moveTo(-4 * density, 2.5f * density); lineTo(0f, -2.5f * density); lineTo(4 * density, 2.5f * density) }
                paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
                paint.strokeWidth = 2.2f * density; paint.color = 0xFFFFFFFF.toInt(); canvas.drawPath(shape, paint)
                canvas.restore()
            }
            paint.style = Paint.Style.FILL
        }
    }.apply { isClickable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }

    init {
        if (MapLibreInitializer.ensureInitialized(context)) {
            view = MapView(context).also { mv ->
                addView(mv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                mv.onCreate(null)
                mv.addOnDidFailLoadingMapListener { onFailure?.invoke() }
                mv.getMapAsync { m ->
                    map = m
                    m.addOnCameraMoveListener { overlay.invalidate() }
                    m.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && following) {
                            following = false
                            onFollowChanged?.invoke(false)
                        }
                    }
                    m.addOnCameraIdleListener { overlay.invalidate() }
                    m.cameraPosition = CameraPosition.Builder().target(LatLng(settings.start.latitude, settings.start.longitude)).zoom(16.0).build()
                    m.addOnMapClickListener { p ->
                        val tapped = if (editingArea == null) onSpawnpointTap?.let { spawnpointsAt(m, p) }.orEmpty() else emptyList()
                        if (tapped.isNotEmpty()) onSpawnpointTap?.invoke(tapped)
                        else onPick?.invoke(CatchPoint(p.latitude, p.longitude))
                        true
                    }
                    m.setStyle(Style.Builder().fromJson(openStreetMapStyleJson())) { style ->
                        style.addSource(GeoJsonSource("catch-area"))
                        style.addLayer(FillLayer("catch-area-fill", "catch-area").withProperties(fillColor("#1E63F0"), fillOpacity(0.06f)))
                        style.addLayer(LineLayer("catch-area-line", "catch-area").withProperties(lineColor("#1E63F0"), lineWidth(2f),
                            lineOpacity(0.7f), lineDasharray(arrayOf(2f, 1.5f))))
                        style.addSource(GeoJsonSource("catch-circles"))
                        style.addLayer(FillLayer("catch-range", "catch-circles").withProperties(fillColor("#1E63F0"), fillOpacity(0.10f)))
                        style.addLayer(LineLayer("catch-range-line", "catch-circles").withProperties(lineColor("#1E63F0"), lineWidth(1.5f), lineOpacity(0.6f)))
                        style.addSource(GeoJsonSource("catch-path"))
                        style.addLayer(LineLayer("catch-path-casing", "catch-path").withProperties(lineColor("#FFFFFF"), lineWidth(9f),
                            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
                        style.addLayer(LineLayer("catch-path-line", "catch-path").withProperties(lineColor(Expression.get("color")), lineWidth(5f),
                            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
                        // One small dot per spawnpoint: the thing you tap. Event spawnpoints are amber.
                        style.addSource(GeoJsonSource("catch-spawns"))
                        style.addLayer(CircleLayer("catch-spawn-dot", "catch-spawns").withProperties(circleColor(Expression.get("color")),
                            circleRadius(3.5f), circleOpacity(0.85f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(1f)))
                        style.addSource(GeoJsonSource("catch-selected"))
                        style.addLayer(CircleLayer("catch-selected-ring", "catch-selected").withProperties(circleColor("#00000000"), circleRadius(12f),
                            circleStrokeColor("#1E63F0"), circleStrokeWidth(3f)))
                        style.addSource(GeoJsonSource("catch-area-corners"))
                        style.addLayer(CircleLayer("catch-area-corner", "catch-area-corners").withProperties(circleColor("#1E63F0"), circleRadius(6f),
                            circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                        style.addSource(GeoJsonSource("catch-user"))
                        style.addLayer(CircleLayer("catch-user-dot", "catch-user").withProperties(circleColor("#246BFD"), circleRadius(7f),
                            circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
                        // Follow may have been asked for before the map existed.
                        render(); if (following) frameWalk(force = true) else fit(); onReady?.invoke()
                    }
                }
            }
            addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!started) { view?.onStart(); view?.onResume(); started = true } }
    override fun onDetachedFromWindow() { if (started) { view?.onPause(); view?.onStop(); started = false }; super.onDetachedFromWindow() }
    fun destroy() { if (started) { view?.onPause(); view?.onStop(); started = false }; view?.onDestroy(); view = null; map = null }

    /**
     * [progressMeters] is how far along the route a guided session has walked, and [nextStop] where
     * it is walking to; both null for a preview.
     */
    fun update(settings: CatchRouteSettings, itinerary: CatchItinerary?, location: CatchPoint?, progressMeters: Double? = null,
        nextStop: CatchPoint? = null) {
        if (this.settings == settings && this.itinerary === itinerary && user == location && this.progressMeters == progressMeters &&
            focus == nextStop) return
        val movedStart = this.settings.start != settings.start
        val routeChanged = this.itinerary !== itinerary || this.settings.area != settings.area
        this.settings = settings; this.itinerary = itinerary; user = location; this.progressMeters = progressMeters; focus = nextStop
        if (routeChanged || itinerary == null) render() else renderProgress()
        if (following && itinerary != null) { fitted = itinerary; frameWalk(force = false) }
        else if (itinerary != null && fitted !== itinerary) { fitted = itinerary; fit() }
        else if (itinerary == null && routeChanged && settings.area.isNotEmpty()) fit()
        else if (itinerary == null && movedStart) map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(settings.start.latitude, settings.start.longitude)))
    }

    /** Shows an area being drawn (corners as handles); null returns to the route's own area. */
    fun editArea(corners: List<CatchPoint>?) {
        editingArea = corners
        renderArea()
        overlay.invalidate()
    }

    fun centerOn(point: CatchPoint, zoom: Double = 15.0) {
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), zoom))
    }

    /** Starts following the trainer and the next stop, and frames them now. */
    fun follow() {
        val changed = !following
        following = true
        frameWalk(force = true)
        if (changed) onFollowChanged?.invoke(true)
    }

    /** Stops following and shows the whole route. */
    fun overview() {
        val changed = following
        following = false
        fit()
        if (changed) onFollowChanged?.invoke(false)
    }

    /**
     * Frames the trainer and the next stop: centred when they are close, both in view otherwise.
     * Only redrawn once the trainer has moved [MAP_PIP_REFIT_METERS] or the next stop changed, so
     * a stream of fixes does not keep the camera twitching.
     */
    private fun frameWalk(force: Boolean) {
        val m = map ?: return
        val here = user
        val target = focus
        if (!force && target == framedFocus && here != null && framedUser?.let { catchDistance(it, here) < MAP_PIP_REFIT_METERS } == true) return
        framedUser = here; framedFocus = target
        when {
            here != null && target != null -> when (val framing = resolveMapPipFocus(here.latitude, here.longitude, target.latitude, target.longitude)) {
                is MapPipFocus.Centre -> m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(framing.latitude, framing.longitude), framing.zoom))
                is MapPipFocus.Fit -> m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder()
                    .include(LatLng(framing.south, framing.west)).include(LatLng(framing.north, framing.east)).build(), (56 * density).toInt()))
            }
            // No stops left, or no fix yet: whichever of the two exists.
            here != null || target != null -> (here ?: target)!!.let { m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 17.0)) }
            else -> fit()
        }
    }

    fun fit() {
        val coords = (itinerary?.path?.map { it.point } ?: listOfNotNull(settings.start, settings.destination) + settings.area)
            .map { LatLng(it.latitude, it.longitude) }
        if (coords.distinct().size > 1) map?.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(coords).build(), (44 * density).toInt()))
        else coords.firstOrNull()?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 16.0)) }
    }
    private fun point(p: CatchPoint) = Point.fromLngLat(p.longitude, p.latitude)

    /** Rings the given spawnpoints and shows their range, so the one whose details are open is obvious. */
    fun select(pointIds: Set<String>) {
        if (selectedPointIds == pointIds) return
        selectedPointIds = pointIds
        renderSelection()
    }

    private fun renderSelection() {
        val style = map?.style ?: return
        val points = itinerary?.encounters.orEmpty().map { it.opportunity }.filter { it.pointId in selectedPointIds }.distinctBy { it.pointId }
        style.getSourceAs<GeoJsonSource>("catch-selected")?.setGeoJson(FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(point(it.point)) }))
        style.getSourceAs<GeoJsonSource>("catch-circles")?.setGeoJson(FeatureCollection.fromFeatures(points.map { o ->
            val ring = (0..32).map { i ->
                val angle = i * 2 * PI / 32
                point(CatchPoint(o.point.latitude + sin(angle) * settings.radius / 111_195,
                    o.point.longitude + cos(angle) * settings.radius / (111_195 * cos(Math.toRadians(o.point.latitude)))))
            }
            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
        }))
    }

    /**
     * Spawnpoints under a tap: dots within a finger's reach first, else every point whose range
     * circle contains the tap. Nearest first, so a single tap on a dense block still reads well.
     */
    private fun spawnpointsAt(m: MapLibreMap, at: LatLng): List<String> {
        val screen = m.projection.toScreenLocation(at)
        val reach = 22 * density
        val box = RectF(screen.x - reach, screen.y - reach, screen.x + reach, screen.y + reach)
        val tap = CatchPoint(at.latitude, at.longitude)
        val encounters = itinerary?.encounters.orEmpty().map { it.opportunity }.distinctBy { it.pointId }
        val byId = encounters.associateBy { it.pointId }
        val dots = m.queryRenderedFeatures(box, "catch-spawn-dot").mapNotNull { it.getStringProperty("pointId") }
        val ids = dots.ifEmpty { encounters.filter { catchDistance(it.point, tap) <= settings.radius }.map { it.pointId } }
        return ids.distinct().sortedBy { id -> byId[id]?.let { catchDistance(it.point, tap) } ?: Double.MAX_VALUE }
    }

    private fun render() {
        val style = map?.style ?: return
        val plan = itinerary
        drawing = catchRouteDrawing(plan?.path.orEmpty(), catchStopMeters(plan?.encounters.orEmpty()))
        // Before a route exists, still show where it will start and end.
        if (plan == null) drawing = drawing.copy(endpoints = listOfNotNull(CatchDrawMarker(settings.start, "Start"),
            settings.destination?.takeIf { settings.finish == CatchFinish.PIN }?.let { CatchDrawMarker(it, "Finish") }))
        renderProgress()
        val encounters = plan?.encounters.orEmpty().distinctBy { it.opportunity.pointId }
        style.getSourceAs<GeoJsonSource>("catch-spawns")?.setGeoJson(FeatureCollection.fromFeatures(encounters.map { e ->
            Feature.fromGeometry(point(e.opportunity.point)).also {
                it.addStringProperty("pointId", e.opportunity.pointId)
                it.addStringProperty("color", if (e.opportunity.eventOnly) "#E3A008" else "#21A67A")
            }
        }))
        renderSelection()
        renderArea()
        style.getSourceAs<GeoJsonSource>("catch-user")?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(user?.let { Feature.fromGeometry(point(it)) })))
    }

    /** Line colours depend on how far a session has walked, so they are redrawn as it moves. */
    private fun renderProgress() {
        val style = map?.style ?: return
        val total = itinerary?.distanceMeters?.takeIf { it > 0 } ?: 1.0
        val walked = progressMeters ?: -1.0
        style.getSourceAs<GeoJsonSource>("catch-path")?.setGeoJson(FeatureCollection.fromFeatures(drawing.segments.map { s ->
            Feature.fromGeometry(LineString.fromLngLats(listOf(point(s.from), point(s.to)))).also {
                val color = if (s.endMeters <= walked) 0xFFB4BBC6.toInt() else catchRouteColor(s.startMeters / total)
                it.addStringProperty("color", String.format("#%06X", color and 0xFFFFFF))
            }
        }))
        style.getSourceAs<GeoJsonSource>("catch-user")?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(user?.let { Feature.fromGeometry(point(it)) })))
        overlay.invalidate()
    }

    private fun renderArea() {
        val style = map?.style ?: return
        val corners = editingArea ?: settings.area
        val features = when {
            corners.size >= 3 -> listOf(Feature.fromGeometry(Polygon.fromLngLats(listOf((corners + corners.first()).map(::point)))))
            corners.size == 2 -> listOf(Feature.fromGeometry(LineString.fromLngLats(corners.map(::point))))
            else -> emptyList()
        }
        style.getSourceAs<GeoJsonSource>("catch-area")?.setGeoJson(FeatureCollection.fromFeatures(features))
        style.getSourceAs<GeoJsonSource>("catch-area-corners")?.setGeoJson(FeatureCollection.fromFeatures(
            editingArea.orEmpty().map { Feature.fromGeometry(point(it)) }))
    }
}
