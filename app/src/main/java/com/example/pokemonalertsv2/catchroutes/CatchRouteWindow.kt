package com.example.pokemonalertsv2.catchroutes

import com.example.pokemonalertsv2.ui.alerts.mapchrome.guidanceRest
import com.example.pokemonalertsv2.ui.alerts.mapchrome.guidanceDistance
import com.example.pokemonalertsv2.ui.alerts.mapchrome.RouteBadge
import com.example.pokemonalertsv2.tracking.OverlayGuidance
import com.example.pokemonalertsv2.tracking.OverlayColors
import com.example.pokemonalertsv2.tracking.OverlayChrome
import com.example.pokemonalertsv2.tracking.OverlayAction
import com.example.pokemonalertsv2.R
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.example.pokemonalertsv2.tracking.FloatingWindow
import com.example.pokemonalertsv2.ui.alerts.MapLocationTracker
import com.example.pokemonalertsv2.ui.alerts.MapPoseTracker
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The floating window a trainer walks a catch route by: the route map, and above it the one
 * line that says what to do next.
 *
 * It exists instead of picture-in-picture because a PiP window is a visible task, which hides
 * the promoted status-bar chip and cannot be moved or resized. [FloatingWindow] owns the window
 * itself — moving, resizing, geometry — so this class only decides what is in it.
 */
internal class CatchRouteWindow(context: Context) {

    private val window = FloatingWindow(context, FloatingWindow.Size(
        // Roomier than the hunt pill: it carries a map, a guidance line and a control row.
        widthDp = 260, heightDp = 300,
        minWidthDp = 200, minHeightDp = 180,
        maxWidthDp = 420, maxHeightDp = 560,
    ))

    private var root: FrameLayout? = null
    private var map: CatchRouteMapView? = null
    private val colors = OverlayColors.Light
    /** The banner's badge, distance, stop and detail. */
    private var guidanceViews: OverlayChrome.GuidanceViews? = null
    private var pauseLabel: TextView? = null
    private var followLabel: TextView? = null
    /**
     * A second-by-second fix and the compass, only while the window is up: the puck and Follow need
     * them, visit counting does not, so the service's own slower location stream stays as it is.
     */
    private var pose: MapPoseTracker? = null
    private var posePoint: CatchPoint? = null

    val isShowing: Boolean get() = root != null

    var onPause: () -> Unit = {}
    var onReplan: () -> Unit = {}
    var onClose: () -> Unit = {}
    var onGeometryChanged: (x: Int, y: Int, width: Int, height: Int) -> Unit
        get() = window.onGeometryChanged
        set(value) { window.onGeometryChanged = value }

    fun restoreGeometry(x: Int, y: Int, width: Int, height: Int) =
        window.restoreGeometry(x, y, width, height)

    /** Adds the window. Returns false when the overlay grant is gone or the window cannot be added. */
    fun show(session: CatchSession, location: CatchPoint?): Boolean {
        if (root != null) return true
        if (!FloatingWindow.canDraw(window.themedContext)) return false
        val context = window.themedContext

        val routeMap = CatchRouteMapView(context)
        map = routeMap
        routeMap.onFollowChanged = { following -> followLabel?.text = if (following) "Following" else "Follow" }

        val layer = FrameLayout(context)
        val container = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.WHITE)
            }
            clipToOutline = true
            addView(routeMap, FrameLayout.LayoutParams(-1, -1))
            addView(layer, FrameLayout.LayoutParams(-1, -1))
        }
        buildChrome(layer, routeMap)

        if (!window.show(container)) {
            routeMap.destroy()
            map = null
            return false
        }
        root = container
        update(session, location)
        // A window opened to walk by follows the walk, not the whole route.
        routeMap.follow()
        pose = MapLocationTracker(context, onPose = { p ->
            val point = CatchPoint(p.location.latitude, p.location.longitude)
            posePoint = point
            map?.setPose(point, p.headingDegrees, p.location.accuracy.takeIf { p.location.hasAccuracy() })
        }, onStatus = {}).also { it.start() }
        return true
    }

    fun update(session: CatchSession, location: CatchPoint?, outOfDate: Boolean = false, replanning: Boolean = false) {
        if (root == null) return
        val now = System.currentTimeMillis()
        map?.update(session.itinerary.settings, session.itinerary, posePoint ?: location, session.progressMeters, session.nextLegEnd(now))
        // Only what is still ahead: visited windows, ones walked past and despawned ones drop off the map.
        map?.setSpawns(session.ahead(now).map { it.opportunity to (now >= it.opportunity.availableFrom) })
        map?.setNextStop(catchNextStopIndex(session, now))
        guidanceViews?.bind(catchOverlayGuidance(session, now, outOfDate, replanning), colors)
        pauseLabel?.text = if (session.paused) "Resume" else "Pause"
    }

    /**
     * The controls over the map: a blue banner on top with the next stop, how far it is and Close,
     * and a labelled bar along the bottom with Pause, Replan, Follow and the whole route.
     */
    private fun buildChrome(layer: FrameLayout, routeMap: CatchRouteMapView) {
        val kit = OverlayChrome(window.themedContext, window) { painter -> painter(colors) }
        routeMap.setWalking(true)
        fun add(view: View, gravity: Int, r: Int = 0, b: Int = 0, w: Int = -2, h: Int = -2) =
            layer.addView(view, FrameLayout.LayoutParams(w, h, gravity).apply { setMargins(0, 0, dp(r), dp(b)) })

        val pause = OverlayAction("Pause", glyph = "❚❚") { onPause() }
        val replan = OverlayAction("Replan", icon = R.drawable.ic_refresh) { onReplan() }
        val follow = OverlayAction("Follow", icon = R.drawable.ic_my_location) { routeMap.follow() }
        val route = OverlayAction("Whole route", icon = R.drawable.ic_route, label = "Route") { routeMap.overview() }
        val close = OverlayAction("Close", icon = R.drawable.ic_close_small) { onClose() }

        val (banner, views) = kit.banner(BANNER_DP, kit.circle(close, 30, OverlayChrome.Look.BANNER))
        guidanceViews = views
        add(banner, Gravity.TOP, w = -1, h = dp(BANNER_DP))
        val (bar, labels) = kit.labelBar(NAV_BAR_DP, pause, replan, follow, route)
        pauseLabel = labels[0]
        followLabel = labels[2]
        add(bar, Gravity.BOTTOM, w = -1, h = dp(NAV_BAR_DP))
        setMapMargins(dp(BANNER_DP), dp(NAV_BAR_DP))
        // Over the map's bottom-right corner, clear of the bar.
        add(window.buildResizeGrip(colors), Gravity.BOTTOM or Gravity.END, r = 6, b = NAV_BAR_DP + 6, w = dp(26), h = dp(26))
    }

    private fun setMapMargins(top: Int, bottom: Int) {
        val view = map ?: return
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.topMargin == top && params.bottomMargin == bottom) return
        params.topMargin = top
        params.bottomMargin = bottom
        view.post { view.layoutParams = params }
    }

    fun hide() {
        if (root == null) return
        pose?.stop()
        pose = null
        posePoint = null
        window.hide()
        map?.destroy()
        root = null
        map = null
        guidanceViews = null
        pauseLabel = null
        followLabel = null
    }

    private companion object {
        const val BANNER_DP = 66
        const val NAV_BAR_DP = 44
    }

    private fun dp(value: Int) = window.dp(value)
}

/**
 * The one line the floating window leads with: which stop is next, how far along the route it
 * is, when you reach it, what it holds and how long that holds. The wording follows the stop
 * timeline in the planner so the two never describe the same stop differently.
 */
/** Index into [catchStops] of the stop holding the first window still ahead, or null when none is. */
internal fun catchNextStopIndex(session: CatchSession, now: Long): Int? {
    val next = session.ahead(now).minByOrNull { it.meters } ?: return null
    return catchStops(session.itinerary.encounters).indexOfFirst { stop -> stop.any { it.opportunity.id == next.opportunity.id } }.takeIf { it >= 0 }
}

/**
 * The same walk for the designed windows: the stop as a badge, how far as the figure, and the
 * pace and route advice after the details.
 */
internal fun catchOverlayGuidance(session: CatchSession, now: Long, outOfDate: Boolean = false, replanning: Boolean = false): OverlayGuidance {
    val info = catchGuidance(session, catchStops(session.itinerary.encounters), now)
    val pace = catchPaceHint(session, now)
    val advice = when {
        replanning -> "Replanning…"
        outOfDate -> "Route out of date, tap Replan"
        else -> null
    }
    return OverlayGuidance(
        badge = (info.badge as? RouteBadge.Number)?.label,
        title = (if (session.paused && !session.finished) "Paused · " else "") + info.title,
        headline = guidanceDistance(info.detail),
        detail = listOfNotNull(guidanceRest(info.detail).takeIf { it.isNotBlank() }, pace, advice).joinToString(" · "),
        progress = info.progress,
        warning = pace != null || outOfDate,
    )
}

internal fun catchGuidance(session: CatchSession, now: Long, outOfDate: Boolean = false, replanning: Boolean = false): String {
    if (session.finished) return "Route finished"
    val remaining = session.ahead(now).sortedBy { it.meters }
    val tally = "${remaining.size} left · ${session.caught} caught"
    if (remaining.isEmpty()) return (if (session.paused) "Paused · no stops ahead" else "No stops ahead") + "\n" + tally +
        (if (replanning) " · Replanning…" else if (session.remaining.isNotEmpty()) " · tap Replan for more" else "")
    val next = remaining.first()
    val group = remaining.takeWhile { it.meters - next.meters < CATCH_STOP_SPAN_METERS }
    val stops = catchStops(session.itinerary.encounters)
    val number = stops.indexOfFirst { stop -> stop.any { it.opportunity.id == next.opportunity.id } } + 1
    val settings = session.itinerary.settings
    val walk = (next.meters - session.progressMeters).coerceAtLeast(0.0)
    val arrival = now + (walk / settings.speedMps * 1000).roundToInt()
    val margin = ((group.minOf { it.opportunity.despawnAt } - now) / 60_000).coerceAtLeast(0)
    val wait = session.itinerary.waits.firstOrNull { kotlin.math.abs(it.meters - next.meters) <= settings.radius * 2 }
    return buildString {
        if (session.paused) append("Paused · ")
        if (number > 0) append("Stop $number · ")
        append("${walk.roundToInt()} m · ")
        append(String.format(Locale.getDefault(), "%tR", arrival))
        append(" · ${group.size} Pokémon · gone in ${if (margin == 0L) "<1" else margin} min")
        if (wait != null) append(" · wait ${wait.millis / 60_000}:${String.format(Locale.US, "%02d", wait.millis / 1000 % 60)}")
        append("\n").append(tally)
        catchPaceHint(session, now)?.let { append(" · ").append(it) }
        // Advice, never an automatic change: the route stays put until the trainer asks.
        if (replanning) append(" · Replanning…") else if (outOfDate) append(" · Route out of date, tap Replan")
    }
}

/**
 * "3 min behind, speed up" or "2 min early, slow down" once the walk is [CATCH_PACE_HINT_MILLIS] off the
 * plan's clock. Spawns are timed, so being early can mean arriving before one appears, not just waiting.
 */
internal fun catchPaceHint(session: CatchSession, now: Long): String? {
    val delta = catchScheduleDelta(session, now) ?: return null
    if (abs(delta) < CATCH_PACE_HINT_MILLIS) return null
    val minutes = (abs(delta) / 60_000).coerceAtLeast(1)
    return if (delta > 0) "$minutes min behind, speed up" else "$minutes min early, slow down"
}
