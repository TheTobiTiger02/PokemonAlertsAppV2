package com.example.pokemonalertsv2.ui.history

import android.content.Context
import android.widget.FrameLayout
import com.example.pokemonalertsv2.data.insights.SpawnHourPoint
import com.example.pokemonalertsv2.ui.alerts.MapLibreInitializer
import com.example.pokemonalertsv2.ui.alerts.openStreetMapStyleJson
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Where one insights hour's spawnpoints are: a dot each, solid where our scanner saw it, faded where only the
 * catalogue did. A still overview inside a scrolling page, so gestures are off and it frames itself once.
 */
class SpawnHourMapView(context: Context) : FrameLayout(context) {
    private var view: MapView? = null
    private var style: Style? = null
    private var map: MapLibreMap? = null
    private var points: List<SpawnHourPoint> = emptyList()
    private var framed = false
    private var started = false

    init {
        if (MapLibreInitializer.ensureInitialized(context)) {
            view = MapView(context).also { mv ->
                addView(mv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                mv.onCreate(null)
                mv.getMapAsync { m ->
                    map = m
                    m.uiSettings.setAllGesturesEnabled(false)
                    m.uiSettings.isAttributionEnabled = true
                    m.setStyle(Style.Builder().fromJson(openStreetMapStyleJson())) { loaded ->
                        loaded.addSource(GeoJsonSource(SOURCE))
                        loaded.addLayer(CircleLayer(LAYER, SOURCE).withProperties(
                            circleColor(Expression.switchCase(Expression.get("live"), Expression.literal(LIVE_COLOR), Expression.literal(CATALOGUE_COLOR))),
                            circleRadius(2.5f),
                            circleOpacity(0.85f)))
                        style = loaded
                        render()
                    }
                }
            }
        }
    }

    /** True when MapLibre could not start; the caller shows a note instead. */
    val unavailable: Boolean get() = view == null

    fun setPoints(next: List<SpawnHourPoint>) {
        if (next === points) return
        points = next
        render()
    }

    private fun render() {
        val loaded = style ?: return
        loaded.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(points.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude)).also { it.addBooleanProperty("live", p.source == "live") }
        }))
        // The hours of one area cover the same ground: frame the first one and keep that view.
        if (!framed && points.size > 1) {
            val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.latitude, it.longitude) }).build()
            map?.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, (12 * resources.displayMetrics.density).toInt()))
            framed = true
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!started) { view?.onStart(); view?.onResume(); started = true } }
    override fun onDetachedFromWindow() { if (started) { view?.onPause(); view?.onStop(); started = false }; super.onDetachedFromWindow() }
    fun destroy() { if (started) { view?.onPause(); view?.onStop(); started = false }; view?.onDestroy(); view = null; map = null; style = null }

    private companion object {
        const val SOURCE = "insights-hour-points"
        const val LAYER = "insights-hour-dot"
        const val LIVE_COLOR = "#1E63F0"
        const val CATALOGUE_COLOR = "#8B909B"
    }
}
