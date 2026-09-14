package com.example.pokemonalertsv2.catchroutes

import android.content.Context
import android.widget.FrameLayout
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.example.pokemonalertsv2.ui.alerts.MapLibreInitializer
import com.example.pokemonalertsv2.ui.alerts.openStreetMapStyleJson
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.*
import kotlin.math.*

/** Shared route renderer for the planner, Android PiP and the floating map. */
class CatchRouteMapView(context: Context) : FrameLayout(context) {
    private var map: MapLibreMap? = null
    private var view: MapView? = null
    private var settings = CatchRouteSettings()
    private var itinerary: CatchItinerary? = null
    private var user: CatchPoint? = null
    private var fitted: CatchItinerary? = null
    var onPick: ((CatchPoint) -> Unit)? = null
    var onReady: (() -> Unit)? = null
    var onFailure: (() -> Unit)? = null
    private var started = false
    private var drawing = CatchRouteDrawing(emptyList(), emptyList(), emptyList())
    private var spawnLabels = emptyList<Pair<CatchPoint,Int>>()
    // Screen-space Canvas labels stay crisp and avoid asynchronous sprite-atlas updates.
    private val labels = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val m=map ?: return
            val density=resources.displayMetrics.density
            val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            fun screen(p: CatchPoint) = m.projection.toScreenLocation(LatLng(p.latitude,p.longitude))
            val arrowBoxes=mutableListOf<RectF>()
            for (arrow in drawing.arrows) {
                val p=screen(arrow.point)
                if(p.x !in 0f..width.toFloat() || p.y !in 0f..height.toFloat()) continue
                arrowBoxes += RectF(p.x-8*density,p.y-8*density,p.x+8*density,p.y+8*density)
                canvas.save();canvas.translate(p.x,p.y);canvas.rotate((arrow.bearing-m.cameraPosition.bearing).toFloat())
                val shape=android.graphics.Path().apply { moveTo(0f,-7*density);lineTo(5*density,4*density);lineTo(0f,1*density);lineTo(-5*density,4*density);close() }
                paint.style=Paint.Style.STROKE;paint.strokeWidth=3*density;paint.color=0xffffffff.toInt();canvas.drawPath(shape,paint)
                paint.style=Paint.Style.FILL;paint.color=0xff092766.toInt();canvas.drawPath(shape,paint);canvas.restore()
            }
            val boxes=mutableListOf<RectF>()
            paint.textSize=12*density;paint.typeface=android.graphics.Typeface.DEFAULT_BOLD;paint.textAlign=Paint.Align.CENTER
            for(marker in drawing.stages) {
                val p=screen(marker.point)
                if(p.x < -30*density || p.x > width+30*density || p.y < -30*density || p.y > height+30*density) continue
                val half=paint.measureText(marker.label)/2+8*density
                // Keep stage order readable where endpoints and return legs share a location.
                val offsets=listOf(0f to 0f,0f to -32f,0f to 32f,-48f to 0f,48f to 0f,0f to -64f,0f to 64f,-80f to -32f,80f to 32f)
                val box=offsets.asSequence().map { (dx,dy) ->
                    val x=(p.x+dx*density).coerceIn(half+3*density,maxOf(half+3*density,width-half-3*density))
                    val y=(p.y+dy*density).coerceIn(18*density,maxOf(18*density,height-18*density))
                    RectF(x-half,y-13*density,x+half,y+13*density)
                }.firstOrNull { candidate -> boxes.none { RectF.intersects(it,candidate) } } ?: continue
                val x=box.centerX();val y=box.centerY()
                if(hypot(x-p.x,y-p.y)>10*density) {
                    paint.color=0xffffffff.toInt();paint.strokeWidth=4*density;canvas.drawLine(p.x,p.y,x,y,paint)
                    paint.color=0xff092766.toInt();paint.strokeWidth=2*density;canvas.drawLine(p.x,p.y,x,y,paint)
                }
                boxes += RectF(box).apply { inset(-2*density,-2*density) }
                paint.color=0xffffffff.toInt();canvas.drawRoundRect(box,5*density,5*density,paint)
                box.inset(2*density,2*density);paint.color=0xff092766.toInt();canvas.drawRoundRect(box,4*density,4*density,paint)
                paint.color=0xffffffff.toInt();canvas.drawText(marker.label,x,y-(paint.ascent()+paint.descent())/2,paint)
            }
            val occupied=(boxes+arrowBoxes).toMutableList()
            for((point,count) in spawnLabels) {
                val p=screen(point);val r=12*density;val box=RectF(p.x-r,p.y-r,p.x+r,p.y+r)
                if(p.x !in 0f..width.toFloat() || p.y !in 0f..height.toFloat() || occupied.any { RectF.intersects(it,box) }) continue
                occupied += box
                paint.color=0xffffffff.toInt();canvas.drawCircle(p.x,p.y,r,paint)
                paint.color=0xff218466.toInt();canvas.drawCircle(p.x,p.y,r-2*density,paint)
                paint.color=0xffffffff.toInt();canvas.drawText(count.toString(),p.x,p.y-(paint.ascent()+paint.descent())/2,paint)
            }
        }
    }.apply { isClickable=false; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }

    init {
        if (MapLibreInitializer.ensureInitialized(context)) {
            view = MapView(context).also { mv ->
                addView(mv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                mv.onCreate(null)
                mv.addOnDidFailLoadingMapListener { onFailure?.invoke() }
                mv.getMapAsync { m ->
                    map = m
                    m.addOnCameraMoveListener { labels.invalidate() }
                    m.cameraPosition = CameraPosition.Builder().target(LatLng(settings.start.latitude, settings.start.longitude)).zoom(16.0).build()
                    m.addOnMapClickListener { p -> onPick?.invoke(CatchPoint(p.latitude, p.longitude)); true }
                    m.setStyle(Style.Builder().fromJson(openStreetMapStyleJson())) { style ->
                        style.addSource(GeoJsonSource("catch-path"))
                        style.addLayer(LineLayer("catch-path-outline", "catch-path").withProperties(lineColor("#FFFFFF"), lineWidth(9f), lineOpacity(1f)))
                        style.addLayer(LineLayer("catch-path-line", "catch-path").withProperties(lineColor("#144DCF"), lineWidth(5f), lineOpacity(1f)))
                        style.addSource(GeoJsonSource("catch-circles"))
                        style.addLayer(FillLayer("catch-range", "catch-circles").withProperties(fillColor("#2EAD81"), fillOpacity(0.045f)))
                        style.addSource(GeoJsonSource("catch-endpoints"))
                        style.addLayer(CircleLayer("catch-endpoint", "catch-endpoints").withProperties(circleColor("#C45818"), circleRadius(8f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                        style.addSource(GeoJsonSource("catch-user"))
                        style.addLayer(CircleLayer("catch-user-dot", "catch-user").withProperties(circleColor("#246BFD"), circleRadius(7f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
                        render(); fit(); onReady?.invoke()
                    }
                }
            }
            addView(labels, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!started) { view?.onStart(); view?.onResume(); started = true } }
    override fun onDetachedFromWindow() { if (started) { view?.onPause(); view?.onStop(); started = false }; super.onDetachedFromWindow() }
    fun destroy() { if (started) { view?.onPause(); view?.onStop(); started = false }; view?.onDestroy(); view = null; map = null }
    fun update(settings: CatchRouteSettings, itinerary: CatchItinerary?, location: CatchPoint?) {
        if (this.settings == settings && this.itinerary === itinerary && user == location) return
        val movedStart = this.settings.start != settings.start
        this.settings = settings; this.itinerary = itinerary; user = location
        render()
        if (itinerary != null && fitted !== itinerary) { fitted = itinerary; fit() }
        else if (itinerary == null && movedStart) map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(settings.start.latitude, settings.start.longitude)))
    }
    fun fit() {
        val coords = itinerary?.path?.map { LatLng(it.point.latitude, it.point.longitude) }
            ?: listOfNotNull(settings.start, settings.destination).map { LatLng(it.latitude, it.longitude) }
        if (coords.distinct().size > 1) map?.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(coords).build(), 60))
        else coords.firstOrNull()?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 16.0)) }
    }
    private fun point(p: CatchPoint) = Point.fromLngLat(p.longitude, p.latitude)
    private fun render() {
        val style = map?.style ?: return
        val path = itinerary?.path.orEmpty()
        drawing = catchRouteDrawing(path)
        style.getSourceAs<GeoJsonSource>("catch-path")?.setGeoJson(FeatureCollection.fromFeatures(drawing.segments.map {
            Feature.fromGeometry(LineString.fromLngLats(listOf(point(it.from),point(it.to))))
        }))
        // Spatially group the display; itinerary keeps every opportunity, including later cycles.
        val encounters = itinerary?.encounters.orEmpty().distinctBy { it.opportunity.pointId }
        var cell = 0.0003
        var groups = encounters.groupBy { floor(it.opportunity.point.latitude / cell) to floor(it.opportunity.point.longitude / (cell * 1.5)) }
        while (groups.size > 300) { cell *= 1.5; groups = encounters.groupBy { floor(it.opportunity.point.latitude / cell) to floor(it.opportunity.point.longitude / (cell * 1.5)) } }
        val points = groups.values.map { it.first().opportunity.point }
        spawnLabels=groups.values.map { it.first().opportunity.point to it.size }
        labels.invalidate()
        val polygons = points.map { p ->
            val ring = (0..32).map { i ->
                val angle = i * 2 * PI / 32
                point(CatchPoint(p.latitude + sin(angle) * settings.radius / 111_195,
                    p.longitude + cos(angle) * settings.radius / (111_195 * cos(Math.toRadians(p.latitude)))))
            }
            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
        }
        style.getSourceAs<GeoJsonSource>("catch-circles")?.setGeoJson(FeatureCollection.fromFeatures(polygons))
        style.getSourceAs<GeoJsonSource>("catch-endpoints")?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(settings.start, settings.destination).map { Feature.fromGeometry(point(it)) }))
        style.getSourceAs<GeoJsonSource>("catch-user")?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(user?.let { Feature.fromGeometry(point(it)) })))
    }
}
