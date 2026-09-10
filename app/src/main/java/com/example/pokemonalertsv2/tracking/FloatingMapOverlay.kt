package com.example.pokemonalertsv2.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.alerts.MapLibreInitializer
import com.example.pokemonalertsv2.ui.alerts.MapMarkerItem
import com.example.pokemonalertsv2.ui.alerts.MapMarkerPalette
import com.example.pokemonalertsv2.ui.alerts.MapUserPose
import com.example.pokemonalertsv2.ui.alerts.OpenStreetMapController
import com.example.pokemonalertsv2.ui.alerts.MAP_EMPHASIZED_MARKER_Z_INDEX
import com.example.pokemonalertsv2.ui.alerts.OpenStreetMapMarker
import com.example.pokemonalertsv2.ui.alerts.createImmediateOpenStreetMapMarker
import com.example.pokemonalertsv2.ui.alerts.createMapMarkerIcon
import com.example.pokemonalertsv2.ui.alerts.mapMarkerArtworkRasterPx
import com.example.pokemonalertsv2.ui.alerts.mapMarkerBaseIconCacheKey
import com.example.pokemonalertsv2.ui.alerts.openStreetMapIconRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.example.pokemonalertsv2.ui.alerts.mapCoordinatesOrNull
import com.example.pokemonalertsv2.ui.alerts.OpenStreetMapLifecycleGuard
import com.example.pokemonalertsv2.ui.alerts.MAP_PIP_CLOSE_ZOOM
import com.example.pokemonalertsv2.ui.alerts.MapPipFocus
import com.example.pokemonalertsv2.ui.alerts.openStreetMapStyleJson
import com.example.pokemonalertsv2.ui.alerts.resolveMapPipFocus
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.roundToInt

/**
 * The floating map, hosted in a `WindowManager` overlay rather than in
 * picture-in-picture.
 *
 * The reason it is not PiP: Android hides a promoted-ongoing status bar chip
 * whenever the posting app's *task* is visible, and a PiP window is a visible
 * task — so the floating map and the live distance chip could never be on
 * screen at the same time. An overlay window is not a task, so both can.
 *
 * Two details are load-bearing and were the whole risk of this approach:
 *  - MapLibre defaults to a **SurfaceView**, which renders black in a
 *    translucent overlay window. [MapLibreMapOptions.textureMode] moves it to a
 *    TextureView, which composites normally, and the window is opaque.
 *  - `MapView` reads styled attributes, so it needs a themed context. The
 *    application context alone throws while inflating.
 */
internal class FloatingMapOverlay(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** MapView inflates from theme attributes; the app context alone is not enough. */
    private val themedContext =
        ContextThemeWrapper(appContext, R.style.Theme_PokemonAlertsV2)

    private var root: FrameLayout? = null
    private var undoButton: TextView? = null
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null

    /** The same controller the in-app map drives; it is plain Kotlin, not Compose. */
    private val controller = OpenStreetMapController()
    private var lifecycle: OpenStreetMapLifecycleGuard? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    /** Where the trainer last dragged it, kept across shows within a session. */
    private var savedX: Int? = null
    private var savedY: Int? = null
    private var savedWidth: Int? = null

    /** Set once the trainer pans or pinches; cleared by recentre and fit. */
    private var cameraAdjustedByHand = false
    private var savedHeight: Int? = null

    /** Restores the geometry the trainer last left the window at. */
    fun restoreGeometry(x: Int, y: Int, width: Int, height: Int) {
        if (x >= 0) savedX = x
        if (y >= 0) savedY = y
        if (width > 0) savedWidth = width
        if (height > 0) savedHeight = height
    }

    val isShowing: Boolean get() = root != null

    /**
     * What the buttons do. An overlay window receives real touch events, so these
     * are plain listeners — the picture-in-picture window had to round-trip every
     * press through a PendingIntent, a broadcast and the Activity.
     */
    var onPrevious: () -> Unit = {}
    var onNext: () -> Unit = {}
    var onGotIt: () -> Unit = {}

    /**
     * The way back from the tick. It sits between the two step arrows, so it gets
     * hit by accident on a map being read while walking.
     */
    var onUndo: () -> Unit = {}
    /**
     * The close button. It ends the hunt rather than only hiding the window: the
     * window is the hunt's face, and a hunt still running behind a closed window
     * -- still holding GPS, still posting a chip -- is not what closing looks like.
     */
    var onClose: () -> Unit = {}

    /** Frame the trainer alone, at walking zoom. */
    var onRecenter: () -> Unit = {}

    /** Frame the trainer and the target they are walking to together. */
    var onFit: () -> Unit = {}

    /** Bring the app forward, on the map. */
    var onOpenApp: () -> Unit = {}



    /** Reported after a move or resize so the caller can persist the geometry. */
    var onGeometryChanged: (x: Int, y: Int, width: Int, height: Int) -> Unit = { _, _, _, _ -> }

    fun show(onMapReady: (MapLibreMap) -> Unit = {}) {
        if (root != null || !canDraw(appContext)) return
        if (!MapLibreInitializer.ensureInitialized(appContext)) {
            Log.w(TAG, "MapLibre could not initialise; no floating map")
            return
        }

        val options = MapLibreMapOptions.createFromAttributes(themedContext)
            // A SurfaceView in an overlay window draws behind everything and reads
            // as a black box. TextureView costs a little performance and renders.
            .textureMode(true)
        val view = MapView(themedContext, options).apply { onCreate(null) }

        val guard = OpenStreetMapLifecycleGuard(
            onStart = { if (!view.isDestroyed) view.onStart() },
            onResume = { if (!view.isDestroyed) view.onResume() },
            onPause = { if (!view.isDestroyed) view.onPause() },
            onStop = { if (!view.isDestroyed) view.onStop() },
            onDestroy = { if (!view.isDestroyed) view.onDestroy() }
        )

        val container = FrameLayout(themedContext).apply {
            // Rounded and clipped so the window reads as a component rather than a
            // rectangle of map pasted over the launcher.
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(CORNER_DP).toFloat())
                }
            }
            clipToOutline = true
            elevation = dp(8).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(CORNER_DP).toFloat()
                setColor(0xFFFFFFFF.toInt())
                setStroke(dp(1), 0x33000000)
            }
            addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { topMargin = dp(HANDLE_DP) }
            )
            addView(buildHandleBar(), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(HANDLE_DP)
            ))
            // Floating over the map rather than in a second bar. A bar costs the
            // whole width of the window in height; three round buttons in the
            // corner cost only what they cover, and can be big enough to read.
            addView(buildMapControls(), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(dp(6), 0, 0, dp(6))
            })
            addView(buildResizeGrip(), FrameLayout.LayoutParams(
                dp(GRIP_DP),
                dp(GRIP_DP)
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, dp(6), dp(6))
            })
        }

        val params = buildLayoutParams()
        runCatching { windowManager.addView(container, params) }
            .onFailure {
                // A revoked grant must never take the journey down with it.
                Log.w(TAG, "Could not add the floating map", it)
                runCatching { view.onDestroy() }
                return
            }

        root = container
        mapView = view
        map = null
        lifecycle = guard
        layoutParams = params

        guard.start()
        guard.resume()

        view.getMapAsync { ready ->
            if (view.isDestroyed) return@getMapAsync
            ready.setPrefetchesTiles(false)
            ready.setMinZoomPreference(3.0)
            ready.setMaxZoomPreference(20.0)
            ready.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = false
                isRotateGesturesEnabled = false
            }
            ready.setStyle(Style.Builder().fromJson(openStreetMapStyleJson())) { style ->
                if (view.isDestroyed) return@setStyle
                map = ready
                controller.attach(ready, themedContext)
                controller.attachStyle(style, themedContext)
                controller.setGesturesEnabled(true)
                ready.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        cameraAdjustedByHand = true
                    }
                }
                onMapReady(ready)
            }
        }
    }

    /**
     * Draws the hunt's targets, plus the trainer's own position.
     *
     * Uses the immediate marker builder rather than the full one: the full pass
     * loads species artwork over the network, which is the right trade for a
     * full-screen map and the wrong one for a 220dp window on a walk.
     */
    fun setAlerts(alerts: List<PokemonAlert>, emphasizedId: String?) {
        if (map == null) return
        val markers = alerts.mapNotNull { alert ->
            val coordinates = alert.mapCoordinatesOrNull() ?: return@mapNotNull null
            val emphasized = alert.uniqueId == emphasizedId
            createImmediateOpenStreetMapMarker(
                item = MapMarkerItem.Alert(alert, coordinates.latitude, coordinates.longitude),
                markerSizePx = dp(if (emphasized) EMPHASIZED_MARKER_DP else MARKER_DP),
                clusterMarkerSizePx = dp(MARKER_DP),
                showTimeLabels = false,
                nowMillis = System.currentTimeMillis(),
                minutePrecision = true,
                basePalette = OVERLAY_PALETTE,
                goDexMatches = emptyMap(),
                emphasized = emphasized
            )
        }
        runCatching { controller.setMarkers(themedContext, markers, alerts) }
            .onFailure { Log.w(TAG, "Could not draw the floating map markers", it) }
    }

    /**
     * Replaces the placeholder pins with real artwork.
     *
     * The immediate builder can only *find* artwork someone else cached, and those
     * caches are written solely by the in-app map — so in a service-driven process
     * every pin fell back to initials, forever. This is the pass that actually
     * loads them, and it populates the shared caches for everyone.
     */
    suspend fun loadArtwork(alerts: List<PokemonAlert>, emphasizedId: String?) {
        if (map == null) return
        // Teaches the Context-free raster helper the canonical size. Until this has
        // run once the fallback path looks artwork up under a key nothing writes.
        mapMarkerArtworkRasterPx(themedContext, dp(MARKER_DP))

        val loaded = withContext(Dispatchers.IO) {
            val gate = Semaphore(ARTWORK_CONCURRENCY)
            alerts.map { alert ->
                async {
                    val coordinates = alert.mapCoordinatesOrNull() ?: return@async null
                    val emphasized = alert.uniqueId == emphasizedId
                    val sizePx = dp(if (emphasized) EMPHASIZED_MARKER_DP else MARKER_DP)
                    val request = openStreetMapIconRequest(alert, sizePx, OVERLAY_PALETTE, emptyMap())
                    val icon = gate.withPermit {
                        createMapMarkerIcon(
                            context = themedContext,
                            sizePx = request.sizePx,
                            categoryCode = request.categoryCode,
                            speciesName = request.speciesName,
                            speciesImageUrl = request.speciesImageUrl,
                            endTime = request.endTime,
                            showTimeLabel = false,
                            timeLabel = null,
                            palette = request.palette,
                            goDexStatus = request.goDexStatus,
                            category = request.category,
                            isHundo = request.isHundo,
                            isNundo = request.isNundo,
                            isPvp = request.isPvp,
                            isRare = request.isRare,
                            questQuantity = request.questQuantity,
                            raidTier = request.raidTier,
                            isRocket = request.isRocket,
                            isKecleon = request.isKecleon
                        )
                    } ?: return@async null
                    OpenStreetMapMarker(
                        item = MapMarkerItem.Alert(alert, coordinates.latitude, coordinates.longitude),
                        iconId = mapMarkerBaseIconCacheKey(request),
                        icon = icon,
                        zIndex = if (emphasized) MAP_EMPHASIZED_MARKER_Z_INDEX else 0f
                    )
                }
            }.awaitAll().filterNotNull()
        }
        Log.d(TAG, "Artwork pass: ${loaded.size}/${alerts.size} pins loaded")
        if (map == null || loaded.isEmpty()) return
        runCatching { controller.setMarkers(themedContext, loaded, alerts) }
            .onFailure { Log.w(TAG, "Could not publish the loaded artwork", it) }
    }

    fun setUserPose(pose: MapUserPose?) {
        if (map == null) return
        runCatching { controller.setUserPose(pose) }
    }

    fun hide() {
        val container = root ?: return
        runCatching { controller.detach() }
        lifecycle?.let { guard ->
            guard.pause()
            guard.stop()
            guard.destroy()
        }
        runCatching { windowManager.removeView(container) }
            .onFailure { Log.w(TAG, "Could not remove the floating map", it) }
        root = null
        undoButton = null
        mapView = null
        map = null
        lifecycle = null
        layoutParams = null
    }

    /**
     * The one thing the map itself cannot provide: somewhere to grab.
     *
     * The previous build put a touch listener on the whole container, which never
     * fired -- MapView consumes touches for pan and pinch, so the parent never saw
     * them. A dedicated bar keeps both gestures working.
     */
    /** Shows or hides the undo control, following the live offer. */
    fun setUndoOffer(visible: Boolean) {
        undoButton?.isVisible = visible
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildHandleBar(): LinearLayout = LinearLayout(themedContext).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), 0, dp(6), 0)
        setBackgroundColor(0xFFF2F4F8.toInt())
        setOnTouchListener(MoveListener())
        addView(controlButton("‹") { onPrevious() })
        addView(controlButton("✓") { onGotIt() })
        addView(controlButton("›") { onNext() })
        // Built always, hidden until there is something to undo. Never replaces the
        // tick: the next target can be caught inside the undo window.
        addView(controlButton("↺") { onUndo() }.also { undoButton = it; it.isVisible = false })
        // Spacer: the buttons sit left, the grab area is everything right of them.
        addView(View(themedContext), LinearLayout.LayoutParams(0, 1, 1f))
        addView(controlButton("×") { onClose() })
    }

    /**
     * Recentre, fit and open-the-app, as round icon buttons over the map's bottom
     * corner. Icons rather than glyphs because at this size a "⤡" is a
     * squiggle -- the buttons have to say what they do at a glance.
     */
    private fun buildMapControls(): LinearLayout = LinearLayout(themedContext).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton(R.drawable.ic_my_location) { onRecenter() })
        addView(iconButton(R.drawable.ic_fit_map) { onFit() })
        addView(iconButton(R.drawable.ic_map) { onOpenApp() })
    }

    private fun iconButton(iconRes: Int, onClick: () -> Unit): ImageView =
        ImageView(themedContext).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(0xFF16181D.toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
            val inset = dp(7)
            setPadding(inset, inset, inset, inset)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                // Not fully opaque: these sit on top of the map, and a hint of what
                // is underneath keeps them reading as controls rather than holes.
                setColor(0xF2FFFFFF.toInt())
                setStroke(dp(1), 0x22000000)
            }
            elevation = dp(2).toFloat()
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(CONTROL_DP), dp(CONTROL_DP)).apply {
                marginEnd = dp(6)
            }
        }

    private fun controlButton(label: String, onClick: () -> Unit): TextView =
        TextView(themedContext).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(0xFF16181D.toInt())
            setPadding(dp(9), dp(2), dp(9), dp(2))
            // Claims its own touches, so a tap on a button is not read as a drag
            // of the bar underneath it.
            isClickable = true
            setOnClickListener { onClick() }
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildResizeGrip(): View = TextView(themedContext).apply {
        text = "◢"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(0xFF5B6472.toInt())
        gravity = Gravity.CENTER
        // Given the same chip as the map controls: a bare glyph over map tiles is
        // invisible against half the places you might be standing.
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xF2FFFFFF.toInt())
            setStroke(dp(1), 0x22000000)
        }
        elevation = dp(2).toFloat()
        setOnTouchListener(ResizeListener())
    }

    /**
     * Frames the trainer and their target together, reusing the same rule the
     * picture-in-picture window used: fit both points, unless they are close
     * enough that bounds would slam the camera to maximum zoom.
     */
    fun focus(
        userLatitude: Double?,
        userLongitude: Double?,
        targetLatitude: Double,
        targetLongitude: Double,
        force: Boolean = false
    ) {
        val ready = map ?: return
        // Once you have panned somewhere yourself, the map stays where you put it
        // until you ask for it back. Automatic framing on top of a hand-moved
        // camera is the behaviour that made the window feel like it was fighting you.
        if (cameraAdjustedByHand && !force) return
        if (force) cameraAdjustedByHand = false
        val update = if (userLatitude == null || userLongitude == null) {
            // No fix yet: show the target rather than leaving the camera at
            // null island, which is where MapLibre starts.
            CameraUpdateFactory.newLatLngZoom(
                LatLng(targetLatitude, targetLongitude),
                MAP_PIP_CLOSE_ZOOM
            )
        } else {
            when (
                val focus = resolveMapPipFocus(
                    userLatitude = userLatitude,
                    userLongitude = userLongitude,
                    alertLatitude = targetLatitude,
                    alertLongitude = targetLongitude
                )
            ) {
                is MapPipFocus.Centre ->
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(focus.latitude, focus.longitude),
                        focus.zoom
                    )
                is MapPipFocus.Fit ->
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder()
                            .include(LatLng(focus.south, focus.west))
                            .include(LatLng(focus.north, focus.east))
                            .build(),
                        dp(FIT_PADDING_DP)
                    )
            }
        }
        runCatching { ready.animateCamera(update) }
            .onFailure { Log.w(TAG, "Could not move the floating map camera", it) }
    }

    /**
     * Puts the camera back on the trainer at walking zoom, and hands automatic
     * framing back to the map.
     */
    fun recenter(latitude: Double, longitude: Double) {
        val ready = map ?: return
        cameraAdjustedByHand = false
        runCatching {
            ready.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), MAP_PIP_CLOSE_ZOOM)
            )
        }.onFailure { Log.w(TAG, "Could not recentre the floating map", it) }
    }

    /** A long-lived service holding a map is exactly where this starts to matter. */
    fun onLowMemory() {
        runCatching { mapView?.onLowMemory() }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            savedWidth ?: dp(WIDTH_DP),
            savedHeight ?: dp(HEIGHT_DP),
            overlayType(),
            // Not focusable so it never steals input from Pokemon GO; touch still
            // reaches the window, which is what pan and pinch need.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            // Opaque, not translucent: see the class note about SurfaceView.
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX ?: dp(12)
            y = savedY ?: dp(120)
        }

    /** Moves the window. Lives on the handle bar, never on the map. */
    private inner class MoveListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            return when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    // Claim the gesture: declining here is what broke the old drag.
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downX).roundToInt()
                    params.y = startY + (event.rawY - downY).roundToInt()
                    applyLayout(params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onGeometryChanged(params.x, params.y, params.width, params.height)
                    true
                }
                else -> false
            }
        }
    }

    /** Resizes from the bottom-right corner, clamped so it cannot vanish or fill the screen. */
    private inner class ResizeListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startWidth = 0
        private var startHeight = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            return when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startWidth = params.width
                    startHeight = params.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.width = (startWidth + (event.rawX - downX).roundToInt())
                        .coerceIn(dp(MIN_WIDTH_DP), dp(MAX_WIDTH_DP))
                    params.height = (startHeight + (event.rawY - downY).roundToInt())
                        .coerceIn(dp(MIN_HEIGHT_DP), dp(MAX_HEIGHT_DP))
                    applyLayout(params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onGeometryChanged(params.x, params.y, params.width, params.height)
                    true
                }
                else -> false
            }
        }
    }

    private fun applyLayout(params: WindowManager.LayoutParams) {
        val container = root ?: return
        runCatching { windowManager.updateViewLayout(container, params) }
    }


    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        appContext.resources.displayMetrics
    ).roundToInt()

    companion object {
        private const val TAG = "FloatingMapOverlay"
        private const val WIDTH_DP = 220
        private const val HEIGHT_DP = 170
        private const val FIT_PADDING_DP = 24
        private const val CORNER_DP = 16
        private const val HANDLE_DP = 30
        private const val GRIP_DP = 26
        private const val CONTROL_DP = 34
        private const val MIN_WIDTH_DP = 160
        private const val MIN_HEIGHT_DP = 140
        private const val MAX_WIDTH_DP = 360
        private const val MAX_HEIGHT_DP = 420
        private const val MARKER_DP = 32
        private const val EMPHASIZED_MARKER_DP = 40
        private const val ARTWORK_CONCURRENCY = 8

        /**
         * Static rather than themed: the window has no Compose tree to read
         * MaterialTheme from, and the map's own palette is fixed anyway.
         */
        private val OVERLAY_PALETTE = MapMarkerPalette(
            primary = 0xFF0057D9.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            surface = 0xFFFFFFFF.toInt(),
            onSurface = 0xFF16181D.toInt(),
            outline = 0xFFD8DEE8.toInt(),
            error = 0xFFEF4444.toInt(),
            onError = 0xFFFFFFFF.toInt()
        )

        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
