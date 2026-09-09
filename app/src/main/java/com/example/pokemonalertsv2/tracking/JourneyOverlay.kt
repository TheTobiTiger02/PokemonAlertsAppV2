package com.example.pokemonalertsv2.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.hunt.huntInRangeChipText
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import kotlin.math.roundToInt

/**
 * The floating journey pill.
 *
 * Android 15 has no status-bar chip a third-party app can reach — the surface
 * the Clock's timer uses is gated behind a per-app allowlist, and the AOSP API
 * for it (promoted ongoing notifications) is API 36. An overlay window is the
 * only way to keep the distance on screen while the trainer is in Pokemon GO,
 * so that is what this is: one line, always on top, never in the way.
 *
 * Built from plain views rather than Compose. Hosting a ComposeView in a
 * WindowManager overlay needs ViewTreeLifecycleOwner and SavedStateRegistryOwner
 * plumbing that nothing else in this app requires, and this is one row of text.
 */
internal class JourneyOverlay(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: LinearLayout? = null
    private var titleView: TextView? = null
    private var detailView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    /** Hidden by the user for the current journey; cleared when the journey ends. */
    private var dismissedForJourney = false

    fun show(
        alert: PokemonAlert,
        distanceMeters: Float?,
        inRange: Boolean,
        huntActive: Boolean
    ) {
        if (dismissedForJourney || !canDraw(appContext)) return
        val view = root ?: createView().also { created ->
            val params = buildLayoutParams()
            layoutParams = params
            runCatching { windowManager.addView(created, params) }
                .onFailure {
                    // A denied or revoked overlay grant must never take the journey
                    // down with it: the notification is still doing its job.
                    Log.w(TAG, "Could not add the journey overlay", it)
                    root = null
                    return
                }
            root = created
        }
        bind(view, alert, distanceMeters, inRange, huntActive)
    }

    fun hide() {
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
            .onFailure { Log.w(TAG, "Could not remove the journey overlay", it) }
        root = null
        titleView = null
        detailView = null
        layoutParams = null
    }

    /** Ends the journey's claim on the pill, including a user dismissal. */
    fun reset() {
        dismissedForJourney = false
        hide()
    }

    private fun bind(
        view: View,
        alert: PokemonAlert,
        distanceMeters: Float?,
        inRange: Boolean,
        huntActive: Boolean
    ) {
        titleView?.text = alert.pokemon?.takeIf { it.isNotBlank() }
            ?: alert.name.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.app_name)
        detailView?.text = detailText(alert, distanceMeters, inRange, huntActive)
        view.setOnClickListener {
            runCatching {
                appContext.startActivity(
                    AlertDetailActivity.createIntent(appContext, alert, returnToAlerts = true)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { Log.w(TAG, "Could not open the alert from the overlay", it) }
        }
    }

    /**
     * The same rule the notification chip follows, so the two never disagree:
     * distance while walking, then whatever decides your next tap once you are
     * close enough to see the thing.
     */
    private fun detailText(
        alert: PokemonAlert,
        distanceMeters: Float?,
        inRange: Boolean,
        huntActive: Boolean
    ): String = when {
        inRange -> (if (huntActive) huntInRangeChipText(alert) else null)
            ?: appContext.getString(R.string.journey_overlay_in_range)
        distanceMeters != null -> formatDistance(distanceMeters)
        else -> appContext.getString(R.string.journey_overlay_locating)
    }

    private fun formatDistance(distanceMeters: Float): String =
        if (distanceMeters < 1_000f) {
            "${distanceMeters.roundToInt()} m"
        } else {
            String.format(java.util.Locale.getDefault(), "%.1f km", distanceMeters / 1_000f)
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun createView(): LinearLayout {
        val horizontal = dp(14)
        val vertical = dp(8)

        val title = TextView(appContext).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(appContext, R.color.widget_on_surface))
            maxLines = 1
            // Bounded and elided, because the pill is WRAP_CONTENT: an unbounded name
            // -- and server names run to "Dragon Grunt @ <long stop name>" -- grows the
            // row until the distance is pushed off the edge of the screen. The distance
            // is the reason the pill exists, so the name is the part that gives way.
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(150)
        }
        val detail = TextView(appContext).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(appContext, R.color.widget_primary))
            maxLines = 1
            setPadding(dp(8), 0, 0, 0)
        }
        val close = TextView(appContext).apply {
            text = "×"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(appContext, R.color.widget_on_surface_variant))
            setPadding(dp(10), 0, 0, 0)
            contentDescription = appContext.getString(R.string.journey_overlay_hide)
            setOnClickListener {
                // For this journey only: the next one starts visible again, so a
                // dismissal never silently disables the feature.
                dismissedForJourney = true
                hide()
            }
        }

        titleView = title
        detailView = detail

        return LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(horizontal, vertical, horizontal, vertical)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
                setColor(ContextCompat.getColor(appContext, R.color.widget_surface))
                setStroke(dp(1), ContextCompat.getColor(appContext, R.color.widget_outline))
            }
            elevation = dp(6).toFloat()
            addView(title)
            addView(detail)
            addView(close)
            setOnTouchListener(DragListener())
        }
    }

    /**
     * Drag to move. The pill sits where the trainer put it, because the one spot
     * that is always wrong is the one covering whatever they are reading.
     */
    private inner class DragListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            return when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && kotlin.math.hypot(dx, dy) > touchSlop) dragging = true
                    if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    dragging
                }
                MotionEvent.ACTION_UP -> {
                    // A tap that never became a drag still has to open the alert.
                    if (!dragging) view.performClick()
                    dragging
                }
                else -> false
            }
        }
    }

    private val touchSlop = dp(8).toFloat()

    private fun buildLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            // Not focusable: the pill must never take input away from Pokemon GO.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(96)
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
        private const val TAG = "JourneyOverlay"

        /** The grant is revocable at any time, so this is checked on every post. */
        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
