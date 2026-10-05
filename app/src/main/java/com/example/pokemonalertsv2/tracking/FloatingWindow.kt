package com.example.pokemonalertsv2.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import com.example.pokemonalertsv2.R
import kotlin.math.roundToInt

/**
 * A movable, resizable `WindowManager` overlay: the window itself, with no opinion about what
 * goes in it.
 *
 * The reason these are overlays and not picture-in-picture: Android hides a promoted-ongoing
 * status bar chip whenever the posting app's *task* is visible, and a PiP window is a visible
 * task — so a floating map and the live chip could never be on screen at the same time. An
 * overlay window is not a task, so both can. An overlay also receives real touch events, so its
 * buttons are plain listeners instead of a round trip through a PendingIntent.
 *
 * Two details are load-bearing for anything that hosts a MapLibre `MapView` in here:
 *  - MapLibre defaults to a **SurfaceView**, which renders black in a translucent overlay
 *    window. `MapLibreMapOptions.textureMode` moves it to a TextureView, which composites
 *    normally. The window is translucent so rounded corners and the round bubble stay round,
 *    and so [setOpacity] can let the game show through.
 *  - `MapView` reads styled attributes, so it needs [themedContext]. The application context
 *    alone throws while inflating.
 */
internal class FloatingWindow(
    context: Context,
    private val size: Size,
) {
    /** Default and permitted extents, in dp. A window that can vanish or fill the screen is a bug. */
    data class Size(
        val widthDp: Int,
        val heightDp: Int,
        val minWidthDp: Int,
        val minHeightDp: Int,
        val maxWidthDp: Int,
        val maxHeightDp: Int,
        val xDp: Int = 12,
        val yDp: Int = 120,
    )

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** A `MapView` inflates from theme attributes; the app context alone is not enough. */
    val themedContext: Context = ContextThemeWrapper(appContext, R.style.Theme_PokemonAlertsV2)

    private var root: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    /** Where the trainer last dragged and sized it. */
    private var savedX: Int? = null
    private var savedY: Int? = null
    private var savedWidth: Int? = null
    private var savedHeight: Int? = null

    /**
     * While compact the window is a small bubble: its own size, its own position, and none of
     * it written over the geometry the full window returns to.
     */
    var isCompact: Boolean = false
        private set
    private var bubbleX: Int? = null
    private var bubbleY: Int? = null
    private var compactWidth = 0
    private var compactHeight = 0

    private var opacity = 1f

    /** Reported on every drag and resize so a service can persist the geometry. */
    var onGeometryChanged: (x: Int, y: Int, width: Int, height: Int) -> Unit = { _, _, _, _ -> }

    /** Reported when the compact bubble is dragged; its position is kept apart from the window's. */
    var onCompactMoved: (x: Int, y: Int) -> Unit = { _, _ -> }

    /**
     * Keeps the geometry a drag or resize just produced, so hiding and showing the window again
     * puts it back where it was left. Persisting it is the caller's job and may not have finished
     * — or may not happen at all — by the time the window is reopened.
     */
    private fun rememberGeometry(params: WindowManager.LayoutParams) {
        savedX = params.x
        savedY = params.y
        savedWidth = params.width
        savedHeight = params.height
        onGeometryChanged(params.x, params.y, params.width, params.height)
    }

    val isShowing: Boolean get() = root != null

    /** Restores the geometry the trainer last left the window at. Non-positive values are ignored. */
    fun restoreGeometry(x: Int, y: Int, width: Int, height: Int) {
        if (x >= 0) savedX = x
        if (y >= 0) savedY = y
        if (width > 0) savedWidth = width
        if (height > 0) savedHeight = height
    }

    /** Where the bubble was last left. Negative values are ignored. */
    fun restoreCompactPosition(x: Int, y: Int) {
        if (x >= 0) bubbleX = x
        if (y >= 0) bubbleY = y
    }

    fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        appContext.resources.displayMetrics
    ).roundToInt()

    /**
     * Adds [content] to the screen. Returns false when the grant has been revoked or the window
     * could not be added, which must never take the caller's service down with it.
     */
    fun show(content: View): Boolean {
        if (root != null) return true
        content.alpha = opacity
        val params = buildLayoutParams()
        val added = runCatching { windowManager.addView(content, params) }
            .onFailure { Log.w(TAG, "Could not add the floating window", it) }
            .isSuccess
        if (!added) return false
        root = content
        layoutParams = params
        return true
    }

    fun hide() {
        val container = root ?: return
        runCatching { windowManager.removeView(container) }
            .onFailure { Log.w(TAG, "Could not remove the floating window", it) }
        root = null
        layoutParams = null
    }

    /**
     * The content's opacity, 0..1: the map, the controls and the bubble fade alike.
     *
     * Deliberately the content view's alpha, not `LayoutParams.alpha`. Android stops sending
     * touches to an overlay window whose *window* alpha is below its occlusion threshold
     * (logged as "window opacity ... is below the threshold"), so a faded window would also
     * be a dead one. A view's alpha leaves the window fully opaque as far as input knows.
     */
    fun setOpacity(alpha: Float) {
        opacity = alpha
        root?.alpha = alpha
    }

    /**
     * Folds the window into a [widthPx] x [heightPx] bubble. The full window's geometry is kept
     * so [exitCompact] puts it back exactly where it was.
     */
    fun enterCompact(widthPx: Int, heightPx: Int) {
        compactWidth = widthPx
        compactHeight = heightPx
        if (isCompact) return
        isCompact = true
        val params = layoutParams ?: return
        savedX = params.x
        savedY = params.y
        savedWidth = params.width
        savedHeight = params.height
        params.width = widthPx
        params.height = heightPx
        params.x = bubbleX ?: params.x
        params.y = bubbleY ?: params.y
        applyLayout(params)
    }

    fun exitCompact() {
        if (!isCompact) return
        isCompact = false
        val params = layoutParams ?: return
        params.x = savedX ?: dp(size.xDp)
        params.y = savedY ?: dp(size.yDp)
        params.width = savedWidth ?: dp(size.widthDp)
        params.height = savedHeight ?: dp(size.heightDp)
        applyLayout(params)
    }

    /** Makes [view] the handle the window is dragged by. Never the map, which needs its own touches. */
    @SuppressLint("ClickableViewAccessibility")
    fun dragWith(view: View) = view.setOnTouchListener(MoveListener())

    /**
     * Makes [view] drag the window, and a press that never travels past the touch slop a tap.
     * For the bubble, which is all handle and still has to open on a tap.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun dragOrTapWith(view: View, onTap: () -> Unit) = view.setOnTouchListener(MoveListener(onTap))

    /** The corner grip that resizes the window, sized [gripDp] square by the caller's layout. */
    @SuppressLint("ClickableViewAccessibility")
    fun buildResizeGrip(): View = TextView(themedContext).apply {
        text = "◢"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(0xFF5B6472.toInt())
        gravity = Gravity.CENTER
        // A bare glyph over map tiles is invisible against half the places you might stand.
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xF2FFFFFF.toInt())
            setStroke(dp(1), 0x22000000)
        }
        elevation = dp(2).toFloat()
        contentDescription = "Resize map"
        setOnTouchListener(ResizeListener())
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            if (isCompact) compactWidth else savedWidth ?: dp(size.widthDp),
            if (isCompact) compactHeight else savedHeight ?: dp(size.heightDp),
            overlayType(),
            // Not focusable so it never steals input from Pokémon GO; touch still reaches the
            // window, which is what dragging, panning and pinching need.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            // Translucent: see the class note about SurfaceView and TextureView.
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (if (isCompact) bubbleX else null) ?: savedX ?: dp(size.xDp)
            y = (if (isCompact) bubbleY else null) ?: savedY ?: dp(size.yDp)
        }

    /**
     * Moves the window. Lives on the handle, never on the content. With [onTap] it also
     * tells a tap from a drag by the system touch slop.
     */
    private inner class MoveListener(private val onTap: (() -> Unit)? = null) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private val touchSlop = ViewConfiguration.get(appContext).scaledTouchSlop

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
                    // Claim the gesture: declining here is what breaks dragging.
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && dx * dx + dy * dy < touchSlop * touchSlop) return true
                    dragging = true
                    params.x = startX + dx.roundToInt()
                    params.y = startY + dy.roundToInt()
                    applyLayout(params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    when {
                        !dragging && event.action == MotionEvent.ACTION_UP && onTap != null -> onTap()
                        !dragging -> Unit
                        isCompact -> {
                            bubbleX = params.x
                            bubbleY = params.y
                            onCompactMoved(params.x, params.y)
                        }
                        else -> rememberGeometry(params)
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** Resizes from the bottom-right corner, clamped so the window cannot vanish or fill the screen. */
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
                        .coerceIn(dp(size.minWidthDp), dp(size.maxWidthDp))
                    params.height = (startHeight + (event.rawY - downY).roundToInt())
                        .coerceIn(dp(size.minHeightDp), dp(size.maxHeightDp))
                    applyLayout(params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    rememberGeometry(params)
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

    companion object {
        private const val TAG = "FloatingWindow"

        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
