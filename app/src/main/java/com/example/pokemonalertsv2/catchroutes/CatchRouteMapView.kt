package com.example.pokemonalertsv2.catchroutes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.example.pokemonalertsv2.ui.alerts.MapLibreInitializer
import com.example.pokemonalertsv2.ui.alerts.RouteLineSources
import com.example.pokemonalertsv2.ui.alerts.RouteLineStyle
import com.example.pokemonalertsv2.ui.alerts.addRouteLineLayers
import com.example.pokemonalertsv2.ui.alerts.openStreetMapStyleJson
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
 * Deliberately quiet, in the same style as a hunt's route: the leg to the next stop bold with
 * chevrons, the rest faded, the walked part grey; numbered stops only where Pokémon are caught (the
 * next one highlighted), small spawn dots, and start/finish pins. Range circles appear only for the
 * spawnpoints whose details are open.
 */
class CatchRouteMapView(context: Context) : FrameLayout(context) {
    private var map: MapLibreMap? = null
    private var view: MapView? = null
    private var settings = CatchRouteSettings()
    private var itinerary: CatchItinerary? = null
    private var user: CatchPoint? = null
    /** Which way the trainer faces, degrees from north; null draws a plain dot. */
    private var heading: Float? = null
    private var accuracyMeters: Float? = null
    /**
     * The spawnpoints a guided walk still has ahead, with whether each is open now; null shows every
     * planned one, as the planner does before a walk starts.
     */
    private var spawns: List<Pair<SpawnOpportunity, Boolean>>? = null
    private var track: List<CatchPoint> = emptyList()
    /** Index into the numbered stops of the one guidance is walking to; null falls back to progress along the line. */
    private var nextStopIndex: Int? = null
    private var progressMeters: Double? = null
    private var fitted: CatchItinerary? = null
    /** The catch the trainer is walking to; what Follow frames alongside them. */
    private var focus: CatchPoint? = null
    /** Where along the route that catch is; the bold "next" leg ends there. */
    private var focusMeters: Double? = null
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
    private var drawing = CatchRouteDrawing(emptyList(), emptyList())
    /** Screen space the host covers with its own controls, kept clear when framing; see [setInsets]. */
    private var insets = IntArray(4)
    /** The camera shows the whole route and nobody has moved it since, so it is refitted when [insets] change. */
    private var overviewShown = false
    /** An area being drawn in the picker; replaces the route's own area while set. */
    private var editingArea: List<CatchPoint>? = null
    /**
     * While editing an area: true turns one-finger drags into a drawn outline. Otherwise corners can be
     * dragged, a tap on a corner removes it, and a tap on an edge's handle adds one there.
     */
    var drawingArea = false
        set(value) { field = value; stroke.clear(); overlay.invalidate() }
    /** Told when an edit to the area is finished: a corner dropped, added, removed, or an outline drawn. */
    var onAreaChanged: ((List<CatchPoint>) -> Unit)? = null
    private val stroke = mutableListOf<android.graphics.PointF>()
    /** The corner a finger holds; when [dragInserted], it was pulled out of an edge on this touch. */
    private var dragIndex: Int? = null
    private var dragMoved = false
    private var downAt: android.graphics.PointF? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
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

            // Numbered stops: the next one is larger and filled in the route's blue; others are white with a
            // blue number; walked ones shrink to grey; overlaps are skipped.
            // The stop the guidance line names, when a walk says which; otherwise the first not yet passed.
            val named = nextStopIndex?.let { drawing.stops.getOrNull(it) }
            val next = named ?: drawing.stops.firstOrNull { it.meters > walked + 1 }
            for (stop in listOfNotNull(next) + drawing.stops.filter { it !== next }) {
                val p = screen(stop.point)
                if (!visible(p.x, p.y, 20 * density)) continue
                val isNext = stop === next && progressMeters != null
                val done = if (named != null) drawing.stops.indexOf(stop) < nextStopIndex!! else stop.meters <= walked
                val r = (if (isNext) 14f else if (done) 8f else 10f) * density
                val box = RectF(p.x - r, p.y - r, p.x + r, p.y + r)
                if (!isNext && occupied.any { RectF.intersects(it, box) }) continue
                occupied += box
                paint.style = Paint.Style.FILL
                if (isNext) { paint.color = 0x33000000; canvas.drawCircle(p.x, p.y + 1 * density, r, paint) }
                paint.color = 0xFFFFFFFF.toInt(); canvas.drawCircle(p.x, p.y, r, paint)
                paint.color = if (done) RouteLineStyle.WALKED_COLOR else RouteLineStyle.COLOR
                if (isNext || done) canvas.drawCircle(p.x, p.y, r - 2.5f * density, paint)
                else {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * density
                    canvas.drawCircle(p.x, p.y, r - 1 * density, paint); paint.style = Paint.Style.FILL
                }
                paint.color = if (isNext || done) 0xFFFFFFFF.toInt() else RouteLineStyle.COLOR
                paint.textSize = (if (isNext) 13f else if (done) 8f else 10f) * density
                canvas.drawText(stop.label, p.x, p.y - (paint.ascent() + paint.descent()) / 2, paint)
            }

            // Area editing: a handle on every edge to pull a new corner out of, then the corners on top.
            editingArea?.let { corners ->
                if (!drawingArea) for (mid in areaEdgeMidpoints(corners)) {
                    val p = screen(mid)
                    paint.style = Paint.Style.FILL; paint.color = 0xFFFFFFFF.toInt(); canvas.drawCircle(p.x, p.y, 7 * density, paint)
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * density; paint.color = 0xFF1E63F0.toInt()
                    canvas.drawCircle(p.x, p.y, 7 * density, paint)
                    canvas.drawLine(p.x - 3.5f * density, p.y, p.x + 3.5f * density, p.y, paint)
                    canvas.drawLine(p.x, p.y - 3.5f * density, p.x, p.y + 3.5f * density, paint)
                }
                paint.style = Paint.Style.FILL
                corners.forEachIndexed { i, corner ->
                    val p = screen(corner)
                    val held = i == dragIndex
                    paint.color = 0xFFFFFFFF.toInt(); canvas.drawCircle(p.x, p.y, (if (held) 13f else 10f) * density, paint)
                    paint.color = 0xFF1E63F0.toInt(); canvas.drawCircle(p.x, p.y, (if (held) 10f else 7f) * density, paint)
                }
                if (stroke.size > 1) {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * density; paint.color = 0xFF1E63F0.toInt()
                    paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
                    canvas.drawPath(Path().apply { moveTo(stroke[0].x, stroke[0].y); stroke.drop(1).forEach { lineTo(it.x, it.y) } }, paint)
                    paint.style = Paint.Style.FILL
                }
            }

            // The trainer, last, so no line, badge or arrow can hide them: an accuracy halo, a heading
            // cone, and a white-ringed dot in a colour the route line never uses.
            user?.let { u ->
                val p = screen(u)
                if (!visible(p.x, p.y, 40 * density)) return@let
                accuracyMeters?.takeIf { it in 8f..120f }?.let { acc ->
                    val edge = screen(CatchPoint(u.latitude + acc / 111_195.0, u.longitude))
                    val r = hypot(edge.x - p.x, edge.y - p.y)
                    if (r > 14 * density) {
                        paint.color = 0x2200897B; canvas.drawCircle(p.x, p.y, r, paint)
                    }
                }
                heading?.let { h ->
                    canvas.save(); canvas.translate(p.x, p.y); canvas.rotate((h - m.cameraPosition.bearing).toFloat())
                    val cone = Path().apply {
                        moveTo(0f, 0f); lineTo(-11 * density, -26 * density)
                        quadTo(0f, -31 * density, 11 * density, -26 * density); close()
                    }
                    paint.color = 0x6600897B; canvas.drawPath(cone, paint)
                    canvas.restore()
                }
                paint.color = 0x33000000; canvas.drawCircle(p.x, p.y + 1 * density, 10 * density, paint)
                paint.color = 0xFFFFFFFF.toInt(); canvas.drawCircle(p.x, p.y, 9.5f * density, paint)
                paint.color = CATCH_USER_COLOR; canvas.drawCircle(p.x, p.y, 6.5f * density, paint)
            }
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
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) overviewShown = false
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
                        listOf("catch-walked", "catch-later", "catch-next").forEach { style.addSource(GeoJsonSource(it)) }
                        style.addRouteLineLayers("catch-route", RouteLineSources(next = "catch-next", later = "catch-later", walked = "catch-walked"), context)
                        // What was actually walked, for a finished route's summary: over the plan, under the markers.
                        style.addSource(GeoJsonSource("catch-track"))
                        style.addLayer(LineLayer("catch-track-line", "catch-track").withProperties(lineColor("#00897B"), lineWidth(4f),
                            lineOpacity(0.9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
                        // One small dot per spawnpoint: the thing you tap. Event spawnpoints are amber.
                        style.addSource(GeoJsonSource("catch-spawns"))
                        // Open now: filled. Opening later on this walk: hollow, in the same colour.
                        style.addLayer(CircleLayer("catch-spawn-dot", "catch-spawns").withProperties(
                            circleColor(Expression.switchCase(Expression.get("open"), Expression.get("color"), Expression.literal("#FFFFFF"))),
                            circleRadius(Expression.switchCase(Expression.get("open"), Expression.literal(3.5f), Expression.literal(3.5f))),
                            circleOpacity(0.9f),
                            circleStrokeColor(Expression.switchCase(Expression.get("open"), Expression.literal("#FFFFFF"), Expression.get("color"))),
                            circleStrokeWidth(Expression.switchCase(Expression.get("open"), Expression.literal(1f), Expression.literal(2f)))))
                        style.addSource(GeoJsonSource("catch-selected"))
                        style.addLayer(CircleLayer("catch-selected-ring", "catch-selected").withProperties(circleColor("#00000000"), circleRadius(12f),
                            circleStrokeColor("#1E63F0"), circleStrokeWidth(3f)))
                        style.addSource(GeoJsonSource("catch-area-corners"))
                        style.addLayer(CircleLayer("catch-area-corner", "catch-area-corners").withProperties(circleColor("#1E63F0"), circleRadius(6f),
                            circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
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
     * [progressMeters] is how far along the route a guided session has walked, and [next] the catch
     * it is walking to; both null for a preview.
     */
    fun update(settings: CatchRouteSettings, itinerary: CatchItinerary?, location: CatchPoint?, progressMeters: Double? = null,
        next: CatchEncounter? = null) {
        val nextStop = next?.opportunity?.point
        if (this.settings == settings && this.itinerary === itinerary && user == location && this.progressMeters == progressMeters &&
            focus == nextStop && focusMeters == next?.meters) return
        focusMeters = next?.meters
        val movedStart = this.settings.start != settings.start
        val routeChanged = this.itinerary !== itinerary || this.settings.area != settings.area
        this.settings = settings; this.itinerary = itinerary; user = location; this.progressMeters = progressMeters; focus = nextStop
        if (routeChanged || itinerary == null) render() else renderProgress()
        if (following && itinerary != null) { fitted = itinerary; frameWalk(force = false) }
        else if (itinerary != null && fitted !== itinerary) { fitted = itinerary; fit() }
        else if (itinerary == null && routeChanged && settings.area.isNotEmpty()) fit()
        else if (itinerary == null && movedStart) map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(settings.start.latitude, settings.start.longitude)))
    }

    /** Where the trainer is and faces, from the pose tracker; drawn above everything and followed at once. */
    fun setPose(location: CatchPoint?, headingDegrees: Float?, accuracy: Float?) {
        if (user == location && heading == headingDegrees && accuracyMeters == accuracy) return
        user = location; heading = headingDegrees; accuracyMeters = accuracy
        if (following && itinerary != null) frameWalk(force = false)
        overlay.invalidate()
    }

    /** A walked track, drawn in the trainer's colour over the planned line. */
    fun setTrack(points: List<CatchPoint>) {
        track = points
        renderTrack()
    }

    private fun renderTrack() {
        val style = map?.style ?: return
        style.getSourceAs<GeoJsonSource>("catch-track")?.setGeoJson(FeatureCollection.fromFeatures(
            if (track.size >= 2) listOf(Feature.fromGeometry(LineString.fromLngLats(track.map(::point)))) else emptyList()))
    }

    fun setNextStop(index: Int?) {
        if (nextStopIndex == index) return
        nextStopIndex = index
        overlay.invalidate()
    }

    /** See [spawns]; re-sent as the walk visits, passes and outlasts them. */
    fun setSpawns(visible: List<Pair<SpawnOpportunity, Boolean>>?) {
        if (spawns == visible) return
        spawns = visible
        renderSpawns()
    }

    /** Shows an area being drawn (corners as handles); null returns to the route's own area. */
    fun editArea(corners: List<CatchPoint>?) {
        // A drag in progress owns the corners until the finger lifts.
        if (dragIndex != null || editingArea == corners) return
        editingArea = corners
        renderArea()
        overlay.invalidate()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val corners = editingArea ?: return false
        val m = map ?: return false
        if (event.actionMasked != MotionEvent.ACTION_DOWN || event.pointerCount > 1) return false
        if (drawingArea) return true
        val reach = 24 * density
        fun near(p: CatchPoint): Boolean { val s = m.projection.toScreenLocation(LatLng(p.latitude, p.longitude)); return hypot(s.x - event.x, s.y - event.y) <= reach }
        val corner = corners.indices.lastOrNull { near(corners[it]) }
        val edge = if (corner == null) areaEdgeMidpoints(corners).indexOfFirst { near(it) }.takeIf { it >= 0 } else null
        if (corner == null && edge == null) return false
        downAt = android.graphics.PointF(event.x, event.y)
        dragMoved = false
        if (corner != null) dragIndex = corner
        else {
            // Pulling on an edge's handle makes a new corner there, held from the start.
            val at = edge!! + 1
            editingArea = corners.take(at) + areaEdgeMidpoints(corners)[edge] + corners.drop(at)
            dragIndex = at
            dragMoved = true
            renderArea()
        }
        overlay.invalidate()
        return true
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val m = map ?: return false
        val corners = editingArea ?: return false
        fun at(e: MotionEvent) = m.projection.fromScreenLocation(android.graphics.PointF(e.x, e.y)).let { CatchPoint(it.latitude, it.longitude) }
        if (drawingArea) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { stroke.clear(); stroke += android.graphics.PointF(event.x, event.y) }
                MotionEvent.ACTION_MOVE -> stroke += android.graphics.PointF(event.x, event.y)
                MotionEvent.ACTION_UP -> {
                    val drawn = simplifyDrawnArea(stroke.map { s -> m.projection.fromScreenLocation(s).let { CatchPoint(it.latitude, it.longitude) } })
                    stroke.clear()
                    if (drawn.isArea()) { editingArea = drawn; renderArea(); onAreaChanged?.invoke(drawn) }
                }
                MotionEvent.ACTION_CANCEL -> stroke.clear()
            }
            overlay.invalidate()
            return true
        }
        val index = dragIndex ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val start = downAt
                if (!dragMoved && start != null && hypot(event.x - start.x, event.y - start.y) < touchSlop) return true
                dragMoved = true
                editingArea = corners.toMutableList().also { it[index] = at(event) }
                renderArea()
            }
            MotionEvent.ACTION_UP -> {
                // A tap on a corner, without moving it, removes it.
                val edited = if (dragMoved) corners else corners.filterIndexed { i, _ -> i != index }
                dragIndex = null
                editingArea = edited
                renderArea()
                onAreaChanged?.invoke(edited)
            }
            MotionEvent.ACTION_CANCEL -> { dragIndex = null; onAreaChanged?.invoke(corners) }
        }
        overlay.invalidate()
        return true
    }

    /** Frames [corners], e.g. a saved area just loaded into the picker. */
    fun fitArea(corners: List<CatchPoint>) {
        if (corners.size < 2) return
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(corners.map { LatLng(it.latitude, it.longitude) }).build(), (44 * density).toInt()))
    }

    fun centerOn(point: CatchPoint, zoom: Double = 15.0) {
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), zoom))
    }

    /**
     * Keeps framing clear of controls the host draws over the map, such as a floating top bar and
     * a bottom sheet, in pixels. Fit and Follow then centre in the part of the map you can see.
     */
    fun setInsets(left: Int, top: Int, right: Int, bottom: Int) {
        val next = intArrayOf(left, top, right, bottom)
        if (next.contentEquals(insets)) return
        insets = next
        if (following) frameWalk(force = true) else if (overviewShown) fit()
    }

    /** [insets] plus [extra] pixels on every side. MapLibre's bounds framing replaces the map's own padding, so it is always passed explicitly. */
    private fun padded(extra: Int) = IntArray(4) { insets[it] + extra }

    /** A camera at [target] and [zoom], centred in the part of the map the host's controls leave visible. */
    private fun centredIn(target: LatLng, zoom: Double) = CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(target).zoom(zoom)
        .padding(insets[0].toDouble(), insets[1].toDouble(), insets[2].toDouble(), insets[3].toDouble()).build())

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
     * Keeps the trainer and the next stop in view together, between [CATCH_FOLLOW_MIN_ZOOM] and
     * [CATCH_FOLLOW_ZOOM]. A stop too far for that keeps the trainer in the middle instead. Moves of
     * under two metres are ignored, and so are zoom changes too small to see, so GPS jitter does not
     * keep the camera breathing.
     */
    private fun frameWalk(force: Boolean) {
        val m = map ?: return
        val here = user
        val target = focus
        if (!force && target == framedFocus && here != null && framedUser?.let { catchDistance(it, here) < CATCH_FOLLOW_STEP_METERS } == true) return
        framedUser = here; framedFocus = target
        val update = when {
            here == null && target == null -> { fit(); return }
            here == null -> centredIn(LatLng(target!!.latitude, target.longitude), CATCH_FOLLOW_ZOOM)
            target != null && catchDistance(here, target) >= 25.0 -> {
                // Both the trainer and the next stop stay in view, however far apart they are.
                val bounds = LatLngBounds.Builder().include(LatLng(here.latitude, here.longitude)).include(LatLng(target.latitude, target.longitude)).build()
                val fitted = m.getCameraForLatLngBounds(bounds, padded((48 * density).toInt()))
                val wanted = fitted?.zoom ?: CATCH_FOLLOW_ZOOM
                val current = m.cameraPosition.zoom
                val zoom = (if (!force && abs(wanted - current) < CATCH_FOLLOW_ZOOM_STEP) current else wanted).coerceIn(CATCH_FOLLOW_MIN_ZOOM, CATCH_FOLLOW_ZOOM)
                // Too far to frame both: stay on the trainer rather than lose them.
                val centre = if (wanted < CATCH_FOLLOW_MIN_ZOOM) LatLng(here.latitude, here.longitude)
                    else LatLng((here.latitude + target.latitude) / 2, (here.longitude + target.longitude) / 2)
                centredIn(centre, zoom)
            }
            else -> centredIn(LatLng(here.latitude, here.longitude), CATCH_FOLLOW_ZOOM)
        }
        m.easeCamera(update, if (force) 600 else 400)
    }

    fun fit() {
        val coords = (itinerary?.path?.map { it.point } ?: listOfNotNull(settings.start, settings.destination) + settings.area)
            .map { LatLng(it.latitude, it.longitude) }
        overviewShown = true
        val pad = padded((44 * density).toInt())
        if (coords.distinct().size > 1) map?.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(coords).build(), pad[0], pad[1], pad[2], pad[3]))
        else coords.firstOrNull()?.let { map?.animateCamera(centredIn(it, 16.0)) }
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
        val encounters = (spawns?.map { it.first } ?: itinerary?.encounters.orEmpty().map { it.opportunity }).distinctBy { it.pointId }
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
        renderSpawns()
        renderSelection()
        renderArea()
        renderTrack()
    }

    private fun renderSpawns() {
        val style = map?.style ?: return
        val shown = spawns ?: itinerary?.encounters.orEmpty().map { it.opportunity to true }
        style.getSourceAs<GeoJsonSource>("catch-spawns")?.setGeoJson(FeatureCollection.fromFeatures(
            // An open window wins over a later one at the same spawnpoint.
            shown.sortedByDescending { it.second }.distinctBy { it.first.pointId }.map { (o, open) ->
                Feature.fromGeometry(point(o.point)).also {
                    it.addStringProperty("pointId", o.pointId)
                    it.addStringProperty("color", if (o.eventOnly) "#E3A008" else "#21A67A")
                    it.addBooleanProperty("open", open)
                }
            }))
    }

    /** The line is cut at how far a session has walked and at the next stop, so it is redrawn as it moves. */
    private fun renderProgress() {
        val style = map?.style ?: return
        val path = itinerary?.path.orEmpty()
        val walked = progressMeters
        val slices = catchRouteSlices(path, walked, focusMeters)
        // A preview's chevrons would point both ways along out-and-back streets; the numbered stops give its order.
        fun set(source: String, points: List<CatchPoint>) = style.getSourceAs<GeoJsonSource>(source)?.setGeoJson(FeatureCollection.fromFeatures(
            if (points.size >= 2) listOf(Feature.fromGeometry(LineString.fromLngLats(points.map(::point))).also { it.addBooleanProperty("arrows", walked != null) })
            else emptyList()))
        set("catch-walked", slices.walked)
        set("catch-later", slices.later)
        set("catch-next", slices.next)
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
        // Corners being edited are drawn on the overlay, where a dragged one can follow the finger.
    }
}

/** The trainer's dot: teal, a colour the blue-to-violet route line never takes. */
internal const val CATCH_USER_COLOR = 0xFF00897B.toInt()
/** Street level: a block or two around the trainer in the floating window. */
internal const val CATCH_FOLLOW_ZOOM = 17.5
/** Follow never zooms out further than this to bring the next stop in. */
internal const val CATCH_FOLLOW_MIN_ZOOM = 14.5
/** Zoom changes smaller than this are ignored while following. */
internal const val CATCH_FOLLOW_ZOOM_STEP = 0.3
internal const val CATCH_FOLLOW_STEP_METERS = 2.0
