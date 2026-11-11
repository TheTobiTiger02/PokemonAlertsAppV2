package com.example.pokemonalertsv2.tracking

import com.example.pokemonalertsv2.ui.alerts.RouteLineStyle
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.FLOATING_MAP_MIN_OPACITY
import com.example.pokemonalertsv2.data.FloatingMapLook
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.clampFloatingMapOpacity
import com.example.pokemonalertsv2.hunt.HuntPath
import com.example.pokemonalertsv2.ui.alerts.MapLibreInitializer
import com.example.pokemonalertsv2.ui.alerts.HUNT_ORDINAL_MAX
import com.example.pokemonalertsv2.ui.alerts.AlertMapCoordinates
import com.example.pokemonalertsv2.ui.alerts.MapContentInsets
import com.example.pokemonalertsv2.ui.alerts.MapMarkerItem
import com.example.pokemonalertsv2.ui.alerts.MapMarkerPalette
import com.example.pokemonalertsv2.ui.alerts.MapUserPose
import com.example.pokemonalertsv2.ui.alerts.OpenStreetMapController
import com.example.pokemonalertsv2.ui.alerts.MAP_EMPHASIZED_MARKER_Z_INDEX
import com.example.pokemonalertsv2.ui.alerts.OpenStreetMapMarker
import com.example.pokemonalertsv2.ui.alerts.createImmediateOpenStreetMapMarker
import com.example.pokemonalertsv2.ui.alerts.createMapMarkerIcon
import com.example.pokemonalertsv2.ui.alerts.fitBoundsPadding
import com.example.pokemonalertsv2.ui.alerts.mapMarkerArtworkRasterPx
import com.example.pokemonalertsv2.ui.alerts.mapMarkerBaseIconCacheKey
import com.example.pokemonalertsv2.ui.alerts.openStreetMapIconRequest
import com.example.pokemonalertsv2.ui.alerts.openStreetMapCountdown
import com.example.pokemonalertsv2.ui.alerts.mapCountdownLabelHeightPx
import com.example.pokemonalertsv2.ui.alerts.resolveAlertVisualStyle
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
import org.maplibre.android.camera.CameraPosition
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
 * [FloatingWindow] owns the window itself — why it is an overlay and not PiP, how it moves and
 * resizes, and the two MapLibre details that make a map render in one. This class owns what is
 * inside it: the map, the handle bar's controls, the ⋯ panel, the markers, and the bubble the
 * window folds into when minimized.
 */
internal class FloatingMapOverlay(context: Context) {

    private val window = FloatingWindow(context, FloatingWindow.Size(
        widthDp = WIDTH_DP, heightDp = HEIGHT_DP,
        minWidthDp = MIN_WIDTH_DP, minHeightDp = MIN_HEIGHT_DP,
        maxWidthDp = MAX_WIDTH_DP, maxHeightDp = MAX_HEIGHT_DP,
    ))

    private val appContext = context.applicationContext

    private val themedContext = window.themedContext

    /** The window's content: the full map ([expanded]) or the [bubble], never both. */
    private var root: FrameLayout? = null
    private var expanded: FrameLayout? = null
    private var bubble: FrameLayout? = null
    private var bubbleIcon: ImageView? = null
    private var bubbleLabel: TextView? = null
    private var bubbleRing: View? = null
    private var noticeStrip: LinearLayout? = null
    private var noticeText: TextView? = null
    private var undoAction: TextView? = null
    private var routeLineSwitch: android.widget.Switch? = null
    private var spacialRendSwitch: android.widget.Switch? = null
    private var menuPanel: View? = null
    private var menuScrim: View? = null
    private var pauseItem: TextView? = null
    private var opacitySlider: SeekBar? = null
    private var opacityLabel: TextView? = null
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null

    /** The same controller the in-app map drives; it is plain Kotlin, not Compose. */
    private val controller = OpenStreetMapController()
    private var lifecycle: OpenStreetMapLifecycleGuard? = null
    /** Set once the trainer pans or pinches; cleared by recentre and fit. */
    private var cameraAdjustedByHand = false

    private var opacity = 1f
    private var startMinimized = false
    private var following = false
    private var paused = false
    private var undoName: String? = null
    private var noticeMessage: String? = null
    private var routeLineShown = true
    private var spacialRendOn = false
    /** Map padding that keeps pins and the trainer out from under the chrome; see [applyChromeInsets]. */
    private var chromeInsets = MapContentInsets(0, 0, 0, 0)
    private var huntMode = true
    /** Controls that only make sense with a hunt's route: hidden for a single journey. */
    private val huntOnlyViews = mutableListOf<View>()

    /** The chrome's colours, and everything that paints with them, for a theme change. */
    private var colors = OverlayColors.Light
    private val painters = mutableListOf<(OverlayColors) -> Unit>()

    /** Repaints the Follow button when following starts or stops. */
    private var followView: ((Boolean) -> Unit)? = null
    private var guidanceViews: OverlayChrome.GuidanceViews? = null
    private var guidanceTitle = "Hunting"
    private var guidanceDetail = ""
    private var readout = "Hunting"

    /** The bubble's ring colour (the target's category) and whether it shows artwork. */
    private var bubbleAccent: Int? = null
    private var bubbleHasArtwork = false

    /** Restores the geometry the trainer last left the window at. */
    fun restoreGeometry(x: Int, y: Int, width: Int, height: Int) =
        window.restoreGeometry(x, y, width, height)

    /**
     * Restores how the window looked when it was last open. Only before [show]: the
     * window opens straight into that state rather than flashing the other one first.
     */
    fun restoreLook(look: FloatingMapLook) {
        opacity = clampFloatingMapOpacity(look.opacity)
        window.setOpacity(opacity)
        window.restoreCompactPosition(look.bubbleX ?: -1, look.bubbleY ?: -1)
        if (root == null) startMinimized = look.minimized
    }

    val isShowing: Boolean get() = root != null

    /** Folded into the bubble. The map behind it is paused, not torn down. */
    val isMinimized: Boolean get() = window.isCompact

    /**
     * What the buttons do. An overlay window receives real touch events, so these
     * are plain listeners — the picture-in-picture window had to round-trip every
     * press through a PendingIntent, a broadcast and the Activity.
     */
    var onPrevious: () -> Unit = {}
    var onNext: () -> Unit = {}
    var onGotIt: () -> Unit = {}

    /**
     * A pin was tapped. The window is where you are looking while walking, so this is
     * how you change your mind about where you are going.
     */
    var onAlertTap: (PokemonAlert) -> Unit = {}

    /** A stack was tapped. Opening it up is the only useful answer at this size. */
    var onClusterTap: (MapMarkerItem.Cluster) -> Unit = {}

    /** The way back from the tick, offered in the strip under the bar after a catch. */
    var onUndo: () -> Unit = {}
    /**
     * The close button. It ends the hunt rather than only hiding the window: the
     * window is the hunt's face, and a hunt still running behind a closed window
     * -- still holding GPS, still posting a chip -- is not what closing looks like.
     */
    var onClose: () -> Unit = {}

    /** Start following the trainer, at walking zoom. */
    var onRecenter: () -> Unit = {}

    /** The trainer moved the map by hand, which is how following ends. */
    var onFollowCancelled: () -> Unit = {}

    /** Frame the trainer and the target they are walking to together. */
    var onFit: () -> Unit = {}

    /** Bring the app forward, on the map. */
    var onOpenApp: () -> Unit = {}

    /** Open the app on the hunt's target picker, to change what is being hunted. */
    var onEditTargets: () -> Unit = {}

    /** The ⋯ panel's route line switch was flipped. */
    var onRouteLineToggle: (Boolean) -> Unit = {}

    /** The ⋯ panel's Spacial Rend switch was flipped. */
    var onSpacialRendToggle: (Boolean) -> Unit = {}

    /**
     * Re-plan the route from where the trainer is standing now.
     *
     * The route is chained onward from the target you are walking to, which is right
     * until you go somewhere else -- an errand, a bus, a friend calling you over. Then
     * the plan describes a walk you are no longer on, and this is how you say so
     * without ending the hunt and starting it again.
     */
    var onRecalculate: () -> Unit = {}

    /** Park the route, or pick it back up. See HuntSession.paused. */
    var onPauseToggle: () -> Unit = {}

    /** The window was folded into the bubble, or opened back out of it. */
    var onMinimizedChanged: (Boolean) -> Unit = {}

    /** The opacity slider moved; reported live, so the caller should debounce saving it. */
    var onOpacityChanged: (Float) -> Unit = {}

    /** Reported after a move or resize so the caller can persist the geometry. */
    var onGeometryChanged: (x: Int, y: Int, width: Int, height: Int) -> Unit
        get() = window.onGeometryChanged
        set(value) { window.onGeometryChanged = value }

    /** Reported after the bubble is dragged. */
    var onBubbleMoved: (x: Int, y: Int) -> Unit
        get() = window.onCompactMoved
        set(value) { window.onCompactMoved = value }

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
        view.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) applyChromeInsets()
        }

        val guard = OpenStreetMapLifecycleGuard(
            onStart = { if (!view.isDestroyed) view.onStart() },
            onResume = { if (!view.isDestroyed) view.onResume() },
            onPause = { if (!view.isDestroyed) view.onPause() },
            onStop = { if (!view.isDestroyed) view.onStop() },
            onDestroy = { if (!view.isDestroyed) view.onDestroy() }
        )

        val mapFrame = FrameLayout(themedContext).apply {
            // Rounded and clipped so the window reads as a component rather than a
            // rectangle of map pasted over the launcher.
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(CORNER_DP).toFloat())
                }
            }
            clipToOutline = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(CORNER_DP).toFloat()
            }
            paint { colors ->
                (background as? GradientDrawable)?.apply {
                    setColor(colors.panel)
                    setStroke(dp(1), colors.outline)
                }
            }
            addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { bottomMargin = dp(DOCK_DP - DOCK_OVERLAP_DP) }
            )
            addView(buildChrome(), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(buildNoticeStrip(), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(UNDO_STRIP_DP)
            ).apply { topMargin = dp(TOP_BUTTONS_DP) })
            // Last, so the panel and the scrim that closes it sit above everything.
            addView(View(themedContext).apply {
                isVisible = false
                isClickable = true
                setOnClickListener { setMenuOpen(false) }
                menuScrim = this
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { topMargin = dp(TOP_BUTTONS_DP) })
            addView(buildMenuPanel(), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(6), dp(TOP_BUTTONS_DP + 4), dp(6), dp(6))
            })
        }

        mapView = view
        huntOnlyViews.forEach { it.isVisible = huntMode }
        val bubbleView = buildBubble()
        val container = FrameLayout(themedContext).apply {
            addView(mapFrame, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(bubbleView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        val minimized = startMinimized
        startMinimized = false
        if (minimized) window.enterCompact(dp(BUBBLE_WIDTH_DP), dp(BUBBLE_HEIGHT_DP))
        mapFrame.isVisible = !minimized
        bubbleView.isVisible = minimized

        if (!window.show(container)) {
            // A revoked grant must never take the journey down with it.
            runCatching { view.onDestroy() }
            return
        }

        root = container
        expanded = mapFrame
        mapView = view
        map = null
        lifecycle = guard
        syncOpacityControls()

        if (!minimized) {
            guard.start()
            guard.resume()
        }

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
                // The hit-testing was already running here and dispatching to nothing:
                // attach() installs a map click listener that resolves a tap through
                // queryRenderedFeatures back to an alert id. This is the whole of it.
                controller.onAlertClick = { alert -> onAlertTap(alert) }
                controller.onClusterClick = { cluster -> onClusterTap(cluster) }
                controller.routeLook = RouteLineStyle.FOOTSTEPS
                controller.attachStyle(style, themedContext)
                controller.setGesturesEnabled(true)
                applyChromeInsets()
                ready.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        cameraAdjustedByHand = true
                        if (following) onFollowCancelled()
                    }
                }
                onMapReady(ready)
            }
        }
    }

    /** The hunt's walk along the streets; [HuntPath.None] clears it. */
    fun setHuntPath(path: HuntPath) {
        if (map == null) return
        runCatching { controller.setHuntPath(path) }
            .onFailure { Log.w(TAG, "Could not draw the hunt path", it) }
    }

    /**
     * The hunt's targets, in walking order.
     *
     * The order is the point: [alerts] starts with the planned route
     * ([com.example.pokemonalertsv2.hunt.HuntPlan.route]), so for the first [numbered] alerts
     * the index *is* the position in the walk. Those wear it as a number, up to
     * [HUNT_ORDINAL_MAX], along with how long they have left; the rest are matches the plan
     * is not sending you to and wear neither. [dimmedIds] are shown for reference only -- matches
     * outside the hunt's area -- and drawn faded; tapping one still walks you there.
     *
     * Uses the immediate marker builder rather than the full one: the full pass
     * loads species artwork over the network, which is the right trade for a
     * full-screen map and the wrong one for a 220dp window on a walk.
     */
    fun setAlerts(alerts: List<PokemonAlert>, emphasizedId: String?, numbered: Int, dimmedIds: Set<String> = emptySet()) {
        if (map == null) return
        val now = System.currentTimeMillis()
        val markers = alerts.mapIndexedNotNull { index, alert ->
            val coordinates = alert.mapCoordinatesOrNull() ?: return@mapIndexedNotNull null
            val emphasized = alert.uniqueId == emphasizedId
            val sizePx = dp(if (emphasized) EMPHASIZED_MARKER_DP else MARKER_DP)
            val ordinal = huntOrdinalFor(index, numbered)
            createImmediateOpenStreetMapMarker(
                item = MapMarkerItem.Alert(alert, coordinates.latitude, coordinates.longitude),
                markerSizePx = sizePx,
                clusterMarkerSizePx = dp(MARKER_DP),
                showTimeLabels = false,
                nowMillis = now,
                minutePrecision = true,
                basePalette = OVERLAY_PALETTE,
                goDexMatches = emptyMap(),
                emphasized = emphasized,
                ordinal = ordinal
            ).withCountdown(alert, ordinal, sizePx, now).copy(dimmed = alert.uniqueId in dimmedIds && !emphasized)
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
    suspend fun loadArtwork(alerts: List<PokemonAlert>, emphasizedId: String?, numbered: Int, dimmedIds: Set<String> = emptySet()) {
        if (map == null) return
        // Teaches the Context-free raster helper the canonical size. Until this has
        // run once the fallback path looks artwork up under a key nothing writes.
        mapMarkerArtworkRasterPx(themedContext, dp(MARKER_DP))

        val loaded = withContext(Dispatchers.IO) {
            val gate = Semaphore(ARTWORK_CONCURRENCY)
            alerts.mapIndexed { index, alert ->
                async {
                    val coordinates = alert.mapCoordinatesOrNull() ?: return@async null
                    val emphasized = alert.uniqueId == emphasizedId
                    val sizePx = dp(if (emphasized) EMPHASIZED_MARKER_DP else MARKER_DP)
                    val request = openStreetMapIconRequest(
                        alert, sizePx, OVERLAY_PALETTE, emptyMap(), huntOrdinalFor(index, numbered)
                    )
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
                            isKecleon = request.isKecleon,
                            ordinal = request.ordinal
                        )
                    } ?: return@async null
                    OpenStreetMapMarker(
                        item = MapMarkerItem.Alert(alert, coordinates.latitude, coordinates.longitude),
                        iconId = mapMarkerBaseIconCacheKey(request),
                        icon = icon,
                        zIndex = if (emphasized) MAP_EMPHASIZED_MARKER_Z_INDEX else 0f,
                        dimmed = alert.uniqueId in dimmedIds && !emphasized
                    ).withCountdown(alert, request.ordinal, sizePx, System.currentTimeMillis())
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
        if (root == null) return
        runCatching { controller.detach() }
        lifecycle?.destroy()
        // Hidden while folded: the next hunt opens the same way this one was left.
        startMinimized = window.isCompact
        window.exitCompact()
        window.hide()
        root = null
        expanded = null
        bubble = null
        bubbleIcon = null
        bubbleLabel = null
        bubbleRing = null
        noticeStrip = null
        noticeText = null
        undoAction = null
        routeLineSwitch = null
        spacialRendSwitch = null
        painters.clear()
        huntOnlyViews.clear()
        followView = null
        guidanceViews = null
        menuPanel = null
        menuScrim = null
        pauseItem = null
        opacitySlider = null
        opacityLabel = null
        mapView = null
        map = null
        lifecycle = null
    }

    /**
     * Shows the undo offer in the strip under the bar, or hides it with null.
     *
     * A strip that says what was caught rather than a bare ↺: on a moving map, "Caught
     * Mewtwo · UNDO" is readable at a glance, and the tick next to it cannot be taken for it.
     */
    fun setUndoOffer(caughtName: String?) {
        undoName = caughtName
        renderNoticeStrip()
    }

    /** A short notice in the same strip, such as "Mewtwo raid ended"; null clears it. */
    fun setNotice(message: String?) {
        noticeMessage = message
        renderNoticeStrip()
    }

    private fun renderNoticeStrip() {
        val undo = undoName
        noticeText?.text = if (undo != null) "Caught $undo" else noticeMessage
        undoAction?.isVisible = undo != null
        val shown = undo != null || noticeMessage != null
        if (noticeStrip?.isVisible != shown) {
            noticeStrip?.isVisible = shown
            applyChromeInsets()
        }
    }

    /**
     * Pads the map by the chrome drawn over it: the ⋯ – × row (and the notice strip under it while
     * shown) and a pin's height at the top, the Follow and Fit row at the bottom. Following, recentring and every
     * framing then aim at the part of the map you can actually see.
     */
    private fun applyChromeInsets() {
        val height = mapView?.height?.takeIf { it > 0 } ?: return
        val stripShown = noticeStrip?.isVisible == true
        chromeInsets = floatingMapChromeInsets(
            mapHeightPx = height,
            // Pins stand up from their coordinate, so a pin framed right at the edge still needs its height.
            topPx = dp(TOP_BUTTONS_DP + (if (stripShown) UNDO_STRIP_DP else 0) + EMPHASIZED_MARKER_DP),
            bottomPx = dp(CONTROLS_ROW_DP + CONTROLS_GAP_DP),
            sidePx = dp(CHROME_SIDE_INSET_DP)
        )
        if (map == null) return
        runCatching { controller.setContentInsets(chromeInsets) }
            .onFailure { Log.w(TAG, "Could not pad the floating map", it) }
    }

    /** Keeps the panel's Spacial Rend switch on the stored setting. */
    fun setSpacialRend(enabled: Boolean) {
        spacialRendOn = enabled
        spacialRendSwitch?.isChecked = enabled
    }

    /** Keeps the panel's route line switch on the stored setting. */
    fun setRouteLine(shown: Boolean) {
        routeLineShown = shown
        routeLineSwitch?.isChecked = shown
    }

    /** Repaints the window's chrome for a theme change, without rebuilding anything. */
    fun applyColors(colors: OverlayColors) {
        if (colors == this.colors) return
        this.colors = colors
        painters.forEach { it(colors) }
        window.applyGripColors(colors)
        rebindGuidance()
    }

    /** Hides the window while the app's own map is on screen; see [InAppMapVisibility]. */
    fun setSuppressed(suppressed: Boolean) {
        if (suppressed) setMenuOpen(false)
        window.setSuppressed(suppressed)
        if (root == null || window.isCompact) return
        if (suppressed) lifecycle?.stop() else lifecycle?.resume()
    }

    /** Registers [block] to paint this view now and again on every [applyColors]. */
    private fun <T : View> T.paint(block: T.(OverlayColors) -> Unit) {
        val view = this
        val painter: (OverlayColors) -> Unit = { view.block(it) }
        painters += painter
        painter(colors)
    }

    private fun paintBubbleIcon() {
        val icon = bubbleIcon ?: return
        val accent = bubbleAccent ?: colors.primary
        val fill = colors.bubble
        (icon.background as? GradientDrawable)?.apply {
            setColor(fill)
            setStroke(dp(3), accent)
        }
        (bubbleRing?.background as? GradientDrawable)?.setStroke(dp(3), accent)
        icon.imageTintList = if (bubbleHasArtwork) null else ColorStateList.valueOf(colors.onSurface)
    }

    /** Names the pause row after whichever thing it would do next. */
    /**
     * A hunt's window, or a single "I'm going" target's: the latter has no route to step
     * through, edit or pause, and its × hides the map rather than ending the walk.
     */
    fun setHuntMode(hunt: Boolean) {
        huntMode = hunt
        huntOnlyViews.forEach { it.isVisible = hunt }
    }

    fun setPaused(paused: Boolean) {
        this.paused = paused
        pauseItem?.text = if (paused) "▶  Resume route" else "❚❚  Pause route"
    }

    /** Folds the window into the bubble. Closing the panel first, so it is not open on return. */
    fun minimize() {
        if (root == null || window.isCompact) return
        setMenuOpen(false)
        lifecycle?.stop()
        expanded?.isVisible = false
        bubble?.isVisible = true
        window.enterCompact(dp(BUBBLE_WIDTH_DP), dp(BUBBLE_HEIGHT_DP))
        onMinimizedChanged(true)
    }

    fun expand() {
        if (root == null || !window.isCompact) return
        window.exitCompact()
        bubble?.isVisible = false
        expanded?.isVisible = true
        lifecycle?.resume()
        onMinimizedChanged(false)
    }

    /**
     * The bubble's picture: the target's artwork in a ring of its category colour, or the
     * map glyph while the hunt has nothing to walk to.
     */
    suspend fun setBubbleTarget(alert: PokemonAlert?) {
        val icon = bubbleIcon ?: return
        bubbleAccent = alert?.let { resolveAlertVisualStyle(it).category.accentArgb.toInt() }
        val bitmap = alert?.let { loadBubbleArtwork(it) }
        if (bubbleIcon !== icon) return
        bubbleHasArtwork = bitmap != null
        if (bitmap != null) {
            icon.setImageBitmap(bitmap)
            val inset = dp(5)
            icon.setPadding(inset, inset, inset, inset)
        } else {
            icon.setImageResource(R.drawable.ic_map)
            val inset = dp(14)
            icon.setPadding(inset, inset, inset, inset)
        }
        paintBubbleIcon()
        icon.contentDescription = alert?.let { "Hunting ${it.pokemon ?: it.name}" } ?: "Hunt map"
    }

    /** The short line under the bubble: a distance, "In range", or what the hunt is doing. */
    fun setBubbleReadout(text: String) {
        bubbleLabel?.text = text
        readout = text
        rebindGuidance()
    }

    /** The target the dock names: [title], then the bubble's readout and [detail] under it. */
    fun setGuidance(title: String, detail: String) {
        guidanceTitle = title
        guidanceDetail = detail
        rebindGuidance()
    }

    private fun rebindGuidance() {
        guidanceViews?.bind(OverlayGuidance(badge = null, title = guidanceTitle, headline = readout, detail = guidanceDetail), colors)
    }

    /** Three rings rippling out of the bubble: you are in range of what it shows. */
    fun pulseBubble() {
        val ring = bubbleRing ?: return
        if (!window.isCompact) return
        ring.animate().cancel()
        val scaleX = ObjectAnimator.ofFloat(ring, View.SCALE_X, 1f, 1.3f)
        val scaleY = ObjectAnimator.ofFloat(ring, View.SCALE_Y, 1f, 1.3f)
        val fade = ObjectAnimator.ofFloat(ring, View.ALPHA, 0.9f, 0f)
        listOf(scaleX, scaleY, fade).forEach { it.repeatCount = PULSE_REPEATS - 1; it.repeatMode = ValueAnimator.RESTART }
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, fade)
            duration = PULSE_MILLIS
            start()
        }
    }

    /**
     * Shows whether the camera is following the trainer, the way every map app does: the
     * location button turns solid blue while it is.
     */
    fun setFollowing(following: Boolean) {
        this.following = following
        followView?.invoke(following)
    }

    /**
     * Keeps the trainer in the middle of the map at the zoom they chose. [engage] is the
     * press that starts following: from a zoomed-out view it also brings the camera down to
     * walking zoom, after which the trainer's own zoom is left alone.
     */
    fun follow(latitude: Double, longitude: Double, engage: Boolean = false) {
        val ready = map ?: return
        cameraAdjustedByHand = false
        val target = LatLng(latitude, longitude)
        val zoom = ready.cameraPosition.zoom
        val update = centredIn(target, if (engage && zoom < FOLLOW_MIN_ZOOM) MAP_PIP_CLOSE_ZOOM else zoom)
        runCatching { ready.animateCamera(update, if (engage) ENGAGE_ANIMATION_MS else FOLLOW_ANIMATION_MS) }
            .onFailure { Log.w(TAG, "Could not follow the trainer", it) }
    }

    private fun setMenuOpen(open: Boolean) {
        menuPanel?.isVisible = open
        menuScrim?.isVisible = open
    }

    private fun syncOpacityControls() {
        val percent = (opacity * 100).roundToInt()
        opacitySlider?.progress = percent - OPACITY_MIN_PERCENT
        opacityLabel?.text = "Opacity  $percent%"
    }

    private suspend fun loadBubbleArtwork(alert: PokemonAlert): Bitmap? {
        // The pins' order: the thumbnail is the species art, the image can be a map snapshot.
        val url = alert.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: alert.imageUrl?.takeIf { it.isNotBlank() }
            ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(appContext)
                    .data(url)
                    .size(dp(BUBBLE_DP))
                    .scale(Scale.FIT)
                    .allowHardware(false)
                    .build()
                val result = Coil.imageLoader(appContext).execute(request)
                ((result as? SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            }.getOrNull()
        }
    }

    /** A long-lived service holding a map is exactly where this starts to matter. */
    fun onLowMemory() {
        runCatching { mapView?.onLowMemory() }
    }

    /**
     * The controls over the map: a card along the bottom naming the target, with its ‹ ✓ ›, and
     * a tap on it opens the app; ⋯ – × in the top-right corner; Follow and Fit just above the card.
     */
    private fun buildChrome(): FrameLayout = FrameLayout(themedContext).apply {
        val kit = OverlayChrome(themedContext, window) { painter -> painters += painter; painter(colors) }
        fun add(view: View, gravity: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0, w: Int = -2, h: Int = -2) =
            addView(view, FrameLayout.LayoutParams(w, h, gravity).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) })
        val previous = OverlayAction("Previous target", icon = R.drawable.ic_pip_prev) { onPrevious() }
        val gotIt = OverlayAction("Got it", icon = R.drawable.ic_check) { onGotIt() }
        val nextTarget = OverlayAction("Next target", icon = R.drawable.ic_pip_next) { onNext() }
        val more = OverlayAction("More options", glyph = "⋯") { setMenuOpen(menuPanel?.isVisible != true) }
        val minimize = OverlayAction("Minimize map", glyph = "–") { minimize() }
        val close = OverlayAction(if (huntMode) "Stop hunt" else "Hide map", icon = R.drawable.ic_close_small) { onClose() }
        val follow = OverlayAction("Follow my location", icon = R.drawable.ic_my_location) { onRecenter() }
        val fit = OverlayAction("Focus Hunt target or route", icon = R.drawable.ic_fit_map) { onFit() }
        fun huntOnly(view: View) = view.also { huntOnlyViews += it }

        // Handles first, so every button drawn after them still takes its own touches: the whole top
        // row and a band down each side drag the window, not only the dock.
        add(dragHandle("Move map"), Gravity.TOP, w = -1, h = dp(TOP_BUTTONS_DP))
        val bandBottom = DOCK_DP - DOCK_OVERLAP_DP
        add(dragHandle(null), Gravity.START, t = TOP_BUTTONS_DP, b = bandBottom, w = dp(EDGE_HANDLE_DP), h = -1)
        add(dragHandle(null), Gravity.END, t = TOP_BUTTONS_DP, b = bandBottom, w = dp(EDGE_HANDLE_DP), h = -1)
        val (dock, views) = kit.dock(DOCK_DP, huntOnly(kit.circle(previous, 30)),
            kit.circle(gotIt, 30, OverlayChrome.Look.FILLED), huntOnly(kit.circle(nextTarget, 30)), onTap = { onOpenApp() })
        guidanceViews = views
        add(dock, Gravity.BOTTOM, w = -1, h = dp(DOCK_DP))
        add(kit.row(kit.circle(more, 28), kit.circle(minimize, 28), kit.circle(close, 28)), Gravity.TOP or Gravity.END, t = 6, r = 6)
        val above = DOCK_DP - DOCK_OVERLAP_DP + 6
        val followButton = kit.circle(follow, 30)
        followView = { on ->
            kit.setCircleOn(followButton, on, colors)
            followButton.contentDescription = if (on) "Following your location" else "Follow my location"
        }
        add(kit.row(followButton, kit.circle(fit, 30)), Gravity.BOTTOM or Gravity.START, l = 2, b = above)
        add(window.buildResizeGrip(colors), Gravity.BOTTOM or Gravity.END, r = 6, b = above, w = dp(GRIP_DP), h = dp(GRIP_DP))
        // After the buttons' own painters, so the follow state wins over the plain style.
        paint { setFollowing(following) }
        rebindGuidance()
    }

    /** A transparent strip the window is dragged by; the map under it gives up those touches. */
    private fun dragHandle(description: String?): View = View(themedContext).apply {
        contentDescription = description
        window.dragWith(this)
    }

    /**
     * The strip under the bar: the undo offer after a catch, or a short notice such as a
     * target that ended before you got there. Undo wins when both are live -- it is the
     * one with something to press.
     */
    private fun buildNoticeStrip(): LinearLayout = LinearLayout(themedContext).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(4), 0)
        paint { setBackgroundColor(it.control) }
        isVisible = false
        addView(TextView(themedContext).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            paint { setTextColor(it.onSurface) }
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            // The strip covers the top of the map, so it moves the window like the row above it.
            window.dragWith(this)
            noticeText = this
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(themedContext).apply {
            text = "UNDO"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            paint { setTextColor(it.primary) }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = selectableBackground()
            isClickable = true
            contentDescription = "Undo catch"
            setOnClickListener { onUndo() }
            undoAction = this
        })
        noticeStrip = this
    }

    /**
     * The ⋯ panel: opacity, and the controls that act on the whole route rather than on
     * one target. Scrolls, because the window can be resized smaller than the panel.
     */
    private fun buildMenuPanel(): View {
        val column = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(TextView(themedContext).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                paint { setTextColor(it.muted) }
                setPadding(dp(12), dp(2), dp(12), 0)
                opacityLabel = this
            })
            addView(SeekBar(themedContext).apply {
                max = 100 - OPACITY_MIN_PERCENT
                paint {
                    progressTintList = ColorStateList.valueOf(it.primary)
                    thumbTintList = ColorStateList.valueOf(it.primary)
                }
                contentDescription = "Map opacity"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        opacity = clampFloatingMapOpacity((progress + OPACITY_MIN_PERCENT) / 100f)
                        window.setOpacity(opacity)
                        opacityLabel?.text = "Opacity  ${(opacity * 100).roundToInt()}%"
                        onOpacityChanged(opacity)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
                opacitySlider = this
            })
            addView(divider())
            addView(buildRouteLineRow())
            addView(buildSpacialRendRow())
            addView(menuItem("✎  Edit targets") { setMenuOpen(false); onEditTargets() }.also { huntOnlyViews += it })
            addView(menuItem("⟳  Recalculate route") { setMenuOpen(false); onRecalculate() }.also { huntOnlyViews += it })
            addView(menuItem(if (paused) "▶  Resume route" else "❚❚  Pause route") {
                setMenuOpen(false)
                onPauseToggle()
            }.also { pauseItem = it; huntOnlyViews += it })
        }
        return ScrollView(themedContext).apply {
            isVisible = false
            isVerticalScrollBarEnabled = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
            }
            paint { colors ->
                (background as? GradientDrawable)?.apply {
                    setColor(colors.panel)
                    setStroke(dp(1), colors.outline)
                }
            }
            elevation = dp(6).toFloat()
            addView(column)
            menuPanel = this
        }
    }

    /** "Route line" with a switch: the same setting as the map filter sheet's Hunt path. */
    private fun buildRouteLineRow(): LinearLayout = buildSwitchRow("〰  Route line", "Show route line", routeLineShown) { checked ->
        if (checked != routeLineShown) {
            routeLineShown = checked
            onRouteLineToggle(checked)
        }
    }.also { routeLineSwitch = it.second }.first

    /** "Spacial Rend": the app's setting that widens a spawn's in-range circle from 40 m to 80 m. */
    private fun buildSpacialRendRow(): LinearLayout = buildSwitchRow("〇  Spacial Rend", "Spacial Rend active", spacialRendOn) { checked ->
        if (checked != spacialRendOn) {
            spacialRendOn = checked
            onSpacialRendToggle(checked)
        }
    }.also { spacialRendSwitch = it.second }.first

    @Suppress("UseSwitchCompatOrMaterialCode")
    private fun buildSwitchRow(
        label: String,
        description: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ): Pair<LinearLayout, android.widget.Switch> {
        lateinit var toggle: android.widget.Switch
        val row = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(2), dp(8), dp(2))
            background = selectableBackground()
            isClickable = true
            addView(TextView(themedContext).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                paint { setTextColor(it.onSurface) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            toggle = android.widget.Switch(themedContext).apply {
                isChecked = checked
                contentDescription = description
                paint { colors ->
                    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                    thumbTintList = ColorStateList(states, intArrayOf(colors.primary, colors.muted))
                    trackTintList = ColorStateList(states, intArrayOf(colors.primary and 0x66FFFFFF, colors.divider))
                }
                setOnCheckedChangeListener { _, on -> onToggle(on) }
            }
            addView(toggle)
            setOnClickListener { toggle.isChecked = !toggle.isChecked }
        }
        return row to toggle
    }

    private fun menuItem(label: String, onClick: () -> Unit): TextView =
        TextView(themedContext).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            paint { setTextColor(it.onSurface) }
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = selectableBackground()
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun divider(): View = View(themedContext).apply {
        paint { setBackgroundColor(it.divider) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            setMargins(0, dp(4), 0, dp(4))
        }
    }

    /**
     * The minimized window: one round button of artwork with a distance under it.
     * Dragged anywhere, opened with a tap. Room is left around the circle for the pulse.
     */
    private fun buildBubble(): FrameLayout = FrameLayout(themedContext).apply {
        isVisible = false
        val circle = dp(BUBBLE_DP)
        addView(View(themedContext).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(3), bubbleAccent ?: this@FloatingMapOverlay.colors.primary)
            }
            alpha = 0f
            bubbleRing = this
        }, FrameLayout.LayoutParams(circle, circle).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(BUBBLE_TOP_DP)
        })
        addView(ImageView(themedContext).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            elevation = dp(4).toFloat()
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.ic_map)
            val inset = dp(14)
            setPadding(inset, inset, inset, inset)
            contentDescription = "Hunt map"
            bubbleIcon = this
        }, FrameLayout.LayoutParams(circle, circle).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(BUBBLE_TOP_DP)
        })
        addView(TextView(themedContext).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(7), dp(1), dp(7), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
            }
            paint { colors ->
                setTextColor(colors.onLabel)
                (background as? GradientDrawable)?.setColor(colors.label)
            }
            elevation = dp(5).toFloat()
            text = "Hunting"
            bubbleLabel = this
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(2)
        })
        paint { paintBubbleIcon() }
        window.dragOrTapWith(this) { expand() }
        bubble = this
    }

    private fun selectableBackground() = TypedValue().let { value ->
        themedContext.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        themedContext.getDrawable(value.resourceId)
    }

    /** Opens a tapped stack up, so the tap does something rather than nothing. */
    fun focusCluster(coordinates: List<AlertMapCoordinates>) {
        if (coordinates.isEmpty()) return
        cameraAdjustedByHand = true
        runCatching { controller.fitAlerts(coordinates, dp(CLUSTER_FIT_PADDING_DP)) }
            .onFailure { Log.w(TAG, "Could not open the tapped stack", it) }
    }

    fun focusRoute(coordinates: List<AlertMapCoordinates>, force: Boolean = false) {
        if (cameraAdjustedByHand && !force) return
        if (force) cameraAdjustedByHand = false
        runCatching { controller.fitAlerts(coordinates, dp(FIT_PADDING_DP)) }
            .onFailure { Log.w(TAG, "Could not frame the Hunt route", it) }
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
            centredIn(LatLng(targetLatitude, targetLongitude), MAP_PIP_CLOSE_ZOOM)
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
                    centredIn(LatLng(focus.latitude, focus.longitude), focus.zoom)
                is MapPipFocus.Fit -> {
                    // Bounds framing replaces the map's own padding, so the chrome is added per side.
                    val (left, top, right, bottom) =
                        fitBoundsPadding(chromeInsets, dp(FIT_PADDING_DP), ready.width, ready.height)
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder()
                            .include(LatLng(focus.south, focus.west))
                            .include(LatLng(focus.north, focus.east))
                            .build(),
                        left, top, right, bottom
                    )
                }
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
            ready.animateCamera(centredIn(LatLng(latitude, longitude), MAP_PIP_CLOSE_ZOOM))
        }.onFailure { Log.w(TAG, "Could not recentre the floating map", it) }
    }

    /**
     * A camera at [target] centred in the part of the map the chrome leaves visible. The padding
     * travels with the camera, because a bounds fit before it will have replaced the map's own.
     */
    private fun centredIn(target: LatLng, zoom: Double) = chromeInsets.let { insets ->
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(target).zoom(zoom).padding(
                insets.left.toDouble(), insets.top.toDouble(), insets.right.toDouble(), insets.bottom.toDouble()
            ).build()
        )
    }

    private fun dp(value: Int): Int = window.dp(value)

    /**
     * The number this pin wears, or null past [HUNT_ORDINAL_MAX].
     *
     * A target that has scrolled out of the numbered head is still drawn -- it just
     * stops claiming a place in the plan.
     */
    private fun huntOrdinalFor(index: Int, numbered: Int): Int? =
        (index + 1).takeIf { index < numbered && it <= HUNT_ORDINAL_MAX }

    /**
     * Hangs the time left under a numbered pin. Minute precision on purpose: the label is a
     * shared image, so it only changes -- and the window only redraws -- once a minute.
     */
    private fun OpenStreetMapMarker.withCountdown(
        alert: PokemonAlert,
        ordinal: Int?,
        sizePx: Int,
        nowMillis: Long
    ): OpenStreetMapMarker {
        if (ordinal == null) return this
        val countdown = openStreetMapCountdown(
            alert, nowMillis, minutePrecision = true, mapCountdownLabelHeightPx(sizePx), OVERLAY_PALETTE
        ) ?: return this
        return copy(labelId = countdown.first, labelBitmap = countdown.second)
    }

    companion object {
        private const val TAG = "FloatingMapOverlay"

        private const val WIDTH_DP = 220
        private const val HEIGHT_DP = 170
        private const val FIT_PADDING_DP = 24
        private const val CORNER_DP = 16
        private const val DOCK_DP = 60
        /** The dock's rounded top runs over the map's bottom edge by this much. */
        private const val DOCK_OVERLAP_DP = 10
        /** Height of the ⋯ – × row: the notice strip and the ⋯ panel hang under it. */
        private const val TOP_BUTTONS_DP = 40
        private const val UNDO_STRIP_DP = 28
        private const val GRIP_DP = 26
        /** Width of the drag bands down the map's sides. */
        private const val EDGE_HANDLE_DP = 12
        /** Follow and Fit: 30dp circles sitting this far above the map's bottom edge. */
        private const val CONTROLS_ROW_DP = 30
        private const val CONTROLS_GAP_DP = 6
        private const val CHROME_SIDE_INSET_DP = 4
        private const val MIN_WIDTH_DP = 160
        private const val MIN_HEIGHT_DP = 140
        private const val MAX_WIDTH_DP = 360
        private const val MAX_HEIGHT_DP = 420
        /** Enough room that an opened stack is not glued to the window's edges. */
        private const val CLUSTER_FIT_PADDING_DP = 16

        private const val MARKER_DP = 32
        private const val EMPHASIZED_MARKER_DP = 40
        private const val ARTWORK_CONCURRENCY = 8

        /** The bubble: a 56dp circle, with room around it for the pulse and below it for the label. */
        private const val BUBBLE_DP = 56
        private const val BUBBLE_TOP_DP = 8
        private const val BUBBLE_WIDTH_DP = 80
        private const val BUBBLE_HEIGHT_DP = 90
        private const val PULSE_MILLIS = 700L
        private const val PULSE_REPEATS = 3

        private val OPACITY_MIN_PERCENT = (FLOATING_MAP_MIN_OPACITY * 100).roundToInt()

        /** Below this a follow press zooms in to walking zoom; above it the trainer's zoom stays. */
        private const val FOLLOW_MIN_ZOOM = 15.0
        private const val ENGAGE_ANIMATION_MS = 600
        private const val FOLLOW_ANIMATION_MS = 900


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

        fun canDraw(context: Context): Boolean = FloatingWindow.canDraw(context)
    }
}

/**
 * Map padding for the chrome over the floating map, shrunk proportionally when the window is so
 * small that the full padding would leave less than [MIN_VISIBLE_FRACTION] of the map to frame in.
 */
internal fun floatingMapChromeInsets(mapHeightPx: Int, topPx: Int, bottomPx: Int, sidePx: Int): MapContentInsets {
    val allowed = (mapHeightPx * (1 - MIN_VISIBLE_FRACTION)).toInt()
    val wanted = topPx + bottomPx
    val scale = if (wanted > allowed && wanted > 0) allowed.toFloat() / wanted else 1f
    return MapContentInsets(
        left = sidePx,
        top = (topPx * scale).toInt(),
        right = sidePx,
        bottom = (bottomPx * scale).toInt()
    )
}

private const val MIN_VISIBLE_FRACTION = 0.4f
