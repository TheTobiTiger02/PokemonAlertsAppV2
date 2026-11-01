package com.example.pokemonalertsv2.hunt

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pokemonalertsv2.catchroutes.CatchPoint
import com.example.pokemonalertsv2.ui.alerts.MapLibreInitializer
import com.example.pokemonalertsv2.ui.alerts.openStreetMapStyleJson
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * A finished hunt on the map: the walked line, where it started and ended, and a gold dot per
 * catch. Only shown in a hunt's summary, so its tiles are fetched once when it is opened (and
 * then come from MapLibre's cache), never for the history list.
 */
@Composable
internal fun HuntPathMap(track: List<CatchPoint>, catches: List<CatchPoint>, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context -> HuntPathMapView(context).also { it.show(track, catches) } },
        onRelease = { it.destroy() },
        modifier = modifier
    )
}

internal class HuntPathMapView(context: Context) : FrameLayout(context) {
    private var view: MapView? = null
    private var started = false
    private val density = resources.displayMetrics.density

    fun show(track: List<CatchPoint>, catches: List<CatchPoint>) {
        if (!MapLibreInitializer.ensureInitialized(context)) return
        val all = track + catches
        val first = all.firstOrNull() ?: return
        view = MapView(context).also { mv ->
            addView(mv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            mv.onCreate(null)
            mv.getMapAsync { map ->
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
                map.cameraPosition = CameraPosition.Builder().target(LatLng(first.latitude, first.longitude)).zoom(15.0).build()
                map.setStyle(Style.Builder().fromJson(openStreetMapStyleJson())) { style ->
                    if (track.size >= 2) {
                        style.addSource(GeoJsonSource("hunt-track", FeatureCollection.fromFeatures(listOf(
                            Feature.fromGeometry(LineString.fromLngLats(track.map(::point)))))))
                        style.addLayer(LineLayer("hunt-track-casing", "hunt-track").withProperties(
                            lineColor("#FFFFFF"), lineWidth(7f), lineOpacity(0.9f),
                            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
                        style.addLayer(LineLayer("hunt-track-line", "hunt-track").withProperties(
                            lineColor("#00897B"), lineWidth(4f),
                            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
                        style.addSource(GeoJsonSource("hunt-ends", FeatureCollection.fromFeatures(listOf(
                            Feature.fromGeometry(point(track.first())), Feature.fromGeometry(point(track.last()))))))
                        style.addLayer(CircleLayer("hunt-ends-dot", "hunt-ends").withProperties(
                            circleColor("#20242C"), circleRadius(5f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                    }
                    if (catches.isNotEmpty()) {
                        style.addSource(GeoJsonSource("hunt-catches", FeatureCollection.fromFeatures(
                            catches.map { Feature.fromGeometry(point(it)) })))
                        style.addLayer(CircleLayer("hunt-catches-dot", "hunt-catches").withProperties(
                            circleColor("#E0A800"), circleRadius(6f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                    }
                }
                val coords = all.map { LatLng(it.latitude, it.longitude) }.distinct()
                if (coords.size > 1) {
                    post {
                        runCatching {
                            map.moveCamera(CameraUpdateFactory.newLatLngBounds(
                                LatLngBounds.Builder().includes(coords).build(), (32 * density).toInt()))
                        }
                    }
                }
            }
        }
        if (isAttachedToWindow) start()
    }

    private fun start() { if (!started) { view?.onStart(); view?.onResume(); started = true } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); start() }
    override fun onDetachedFromWindow() { if (started) { view?.onPause(); view?.onStop(); started = false }; super.onDetachedFromWindow() }
    fun destroy() { if (started) { view?.onPause(); view?.onStop(); started = false }; view?.onDestroy(); view = null }

    private fun point(p: CatchPoint) = Point.fromLngLat(p.longitude, p.latitude)
}
