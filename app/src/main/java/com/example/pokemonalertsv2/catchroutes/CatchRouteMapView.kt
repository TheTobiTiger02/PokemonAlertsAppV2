package com.example.pokemonalertsv2.catchroutes

import android.content.Context
import android.widget.FrameLayout
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import org.maplibre.android.style.expressions.Expression.get
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
    init {
        if (MapLibreInitializer.ensureInitialized(context)) {
            view = MapView(context).also { mv ->
                addView(mv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                mv.onCreate(null)
                mv.addOnDidFailLoadingMapListener { onFailure?.invoke() }
                mv.getMapAsync { m ->
                    map = m
                    m.cameraPosition = CameraPosition.Builder().target(LatLng(settings.start.latitude, settings.start.longitude)).zoom(16.0).build()
                    m.addOnMapClickListener { p -> onPick?.invoke(CatchPoint(p.latitude, p.longitude)); true }
                    m.setStyle(Style.Builder().fromJson(openStreetMapStyleJson())) { style ->
                        style.addSource(GeoJsonSource("catch-path"))
                        style.addLayer(LineLayer("catch-path-line", "catch-path").withProperties(lineColor("#246BFD"), lineWidth(5f), lineOpacity(0.85f)))
                        style.addSource(GeoJsonSource("catch-circles"))
                        style.addLayer(FillLayer("catch-range", "catch-circles").withProperties(fillColor("#2EAD81"), fillOpacity(0.12f)))
                        style.addSource(GeoJsonSource("catch-points"))
                        style.addLayer(SymbolLayer("catch-pin", "catch-points").withProperties(iconImage(get("icon")), iconAllowOverlap(false)))
                        style.addSource(GeoJsonSource("catch-endpoints"))
                        style.addLayer(CircleLayer("catch-endpoint", "catch-endpoints").withProperties(circleColor("#C45818"), circleRadius(8f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                        style.addSource(GeoJsonSource("catch-user"))
                        style.addLayer(CircleLayer("catch-user-dot", "catch-user").withProperties(circleColor("#246BFD"), circleRadius(7f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
                        render(); fit(); onReady?.invoke()
                    }
                }
            }
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
        style.getSourceAs<GeoJsonSource>("catch-path")?.setGeoJson(FeatureCollection.fromFeatures(
            if (path.size > 1) listOf(Feature.fromGeometry(LineString.fromLngLats(path.map { point(it.point) }))) else emptyList()))
        // Spatially group the display; itinerary keeps every opportunity, including later cycles.
        val encounters = itinerary?.encounters.orEmpty().distinctBy { it.opportunity.pointId }
        var cell = 0.0003
        var groups = encounters.groupBy { floor(it.opportunity.point.latitude / cell) to floor(it.opportunity.point.longitude / (cell * 1.5)) }
        while (groups.size > 300) { cell *= 1.5; groups = encounters.groupBy { floor(it.opportunity.point.latitude / cell) to floor(it.opportunity.point.longitude / (cell * 1.5)) } }
        val points = groups.values.map { it.first().opportunity.point }
        val features = groups.values.map { group ->
            val count = group.size
            val imageId = "catch-count-$count"
            if (style.getImage(imageId) == null) {
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawCircle(32f, 32f, 29f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffffffff.toInt() })
                canvas.drawCircle(32f, 32f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff218466.toInt() })
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffffffff.toInt(); textSize = if (count < 100) 27f else 20f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
                canvas.drawText(count.toString(), 32f, 32f - (paint.ascent() + paint.descent()) / 2, paint)
                style.addImage(imageId, bitmap)
            }
            Feature.fromGeometry(point(group.first().opportunity.point)).apply { addStringProperty("icon", imageId) }
        }
        style.getSourceAs<GeoJsonSource>("catch-points")?.setGeoJson(FeatureCollection.fromFeatures(features))
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
