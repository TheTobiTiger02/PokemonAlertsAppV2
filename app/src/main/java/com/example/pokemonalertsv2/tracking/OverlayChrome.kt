package com.example.pokemonalertsv2.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible

/** What a floating map window is walking to, as its banner or dock shows it. */
internal data class OverlayGuidance(
    /** A stop number or letter in a round badge; null for none. */
    val badge: String?,
    val title: String,
    /** The big figure: how far, or the state ("In range"); null leaves only [title]. */
    val headline: String?,
    val detail: String,
    val progress: Float? = null,
    /** Behind or out of date: the line is tinted so it reads at a glance over the game. */
    val warning: Boolean = false,
)

/** One button of a floating window: an icon, or a glyph where the app has no icon. */
internal class OverlayAction(
    val description: String,
    val icon: Int? = null,
    val glyph: String? = null,
    val label: String = description,
    val onClick: () -> Unit,
)

/**
 * The parts the floating windows build their controls from: the Catch Route window's banner and
 * labelled bar, the hunt map's dock, and the round buttons both use. Plain Views: the windows
 * have no Compose host.
 * Everything painted registers with [register], so a theme change repaints it.
 */
internal class OverlayChrome(
    private val context: Context,
    private val window: FloatingWindow,
    private val register: ((OverlayColors) -> Unit) -> Unit,
) {
    fun dp(value: Int) = window.dp(value)

    private fun <T : View> T.painted(block: T.(OverlayColors) -> Unit): T = also { view -> register { view.block(it) } }

    private fun oval() = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private fun rounded(radiusDp: Int) = GradientDrawable().apply { cornerRadius = dp(radiusDp).toFloat() }

    /** The texts a banner or dock shows guidance in; [bind] fills whichever of them it built. */
    inner class GuidanceViews(
        private val badge: TextView?,
        private val headline: TextView?,
        private val title: TextView,
        private val detail: TextView?,
        private val progress: ProgressBar?,
        private val tinted: View?,
        /** The tinted view's own colour when there is no warning. */
        private val base: (OverlayColors) -> Int,
    ) {
        private var warning = false

        init { tinted?.let { view -> register { colors -> paintTint(view, colors) } } }

        fun bind(g: OverlayGuidance, colors: OverlayColors) {
            badge?.apply { isVisible = g.badge != null; text = g.badge }
            // With no headline line of its own (the dock), the figure leads the detail.
            val detailText = if (headline == null) listOfNotNull(g.headline, g.detail.takeIf { it.isNotBlank() }).joinToString(" · ") else g.detail
            headline?.apply { isVisible = g.headline != null; text = g.headline }
            title.text = g.title
            detail?.apply { isVisible = detailText.isNotBlank(); text = detailText }
            progress?.apply {
                isVisible = g.progress != null
                this.progress = ((g.progress ?: 0f) * 1000).toInt()
            }
            warning = g.warning
            tinted?.let { paintTint(it, colors) }
        }

        private fun paintTint(view: View, colors: OverlayColors) {
            val color = if (warning) WARNING else base(colors)
            when (val bg = view.background) {
                is GradientDrawable -> bg.setColor(color)
                else -> view.setBackgroundColor(color)
            }
        }
    }

    /** Drags the window by [view]; a press that does not travel is [onTap]. */
    private fun handle(view: View, onTap: () -> Unit) {
        window.dragOrTapWith(view, onTap)
        // The touch listener takes every touch; this is what TalkBack's double tap reaches.
        view.setOnClickListener { onTap() }
    }

    private fun text(sizeSp: Float, bold: Boolean = false, lines: Int = 1) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    private fun badge(sizeDp: Int, inverse: Boolean) = text(if (sizeDp >= 34) 15f else 12f, bold = true).apply {
        gravity = Gravity.CENTER
        background = oval()
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { marginEnd = dp(8) }
        painted { colors ->
            setTextColor(if (inverse) colors.primary else colors.onPrimary)
            (background as GradientDrawable).setColor(if (inverse) colors.onPrimary else colors.primary)
        }
    }

    private fun progressBar(onPrimary: Boolean) = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        isIndeterminate = false
        painted { colors ->
            progressTintList = ColorStateList.valueOf(if (onPrimary) colors.onPrimary else colors.primary)
            progressBackgroundTintList = ColorStateList.valueOf(if (onPrimary) colors.onPrimary and 0x55FFFFFF else colors.divider)
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
    }

    /** How a [circle] is drawn: on the map, in the route's blue, or on the blue banner. */
    enum class Look { MAP, FILLED, BANNER }

    /** A round button of [sizeDp]; [look] says what it sits on. */
    @SuppressLint("ClickableViewAccessibility")
    fun circle(action: OverlayAction, sizeDp: Int = 32, look: Look = Look.MAP): View {
        val view: View = if (action.icon != null) ImageView(context).apply {
            setImageResource(action.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val inset = (sizeDp * 0.24f).toInt().coerceAtLeast(5)
            setPadding(dp(inset), dp(inset), dp(inset), dp(inset))
        } else text(if (sizeDp >= 32) 14f else 12f, bold = true).apply {
            text = action.glyph
            gravity = Gravity.CENTER
        }
        return view.apply {
            contentDescription = action.description
            background = oval()
            elevation = if (look == Look.MAP) dp(2).toFloat() else 0f
            isClickable = true
            setOnClickListener { action.onClick() }
            painted { colors ->
                val (fill, ink) = when (look) {
                    Look.BANNER -> (colors.onPrimary and 0x2EFFFFFF) to colors.onPrimary
                    Look.FILLED -> colors.primary to colors.onPrimary
                    Look.MAP -> colors.control to colors.onSurface
                }
                (background as GradientDrawable).apply {
                    setColor(fill)
                    if (look == Look.MAP) setStroke(dp(1), colors.outline) else setStroke(0, 0)
                }
                (this as? ImageView)?.imageTintList = ColorStateList.valueOf(ink)
                (this as? TextView)?.setTextColor(ink)
            }
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { marginStart = dp(4) }
        }
    }

    /** Repaints a [circle] as on or off, for Follow. */
    fun setCircleOn(view: View, on: Boolean, colors: OverlayColors) {
        (view.background as? GradientDrawable)?.setColor(if (on) colors.primary else colors.control)
        val ink = if (on) colors.onPrimary else colors.onSurface
        (view as? ImageView)?.imageTintList = ColorStateList.valueOf(ink)
        (view as? TextView)?.setTextColor(ink)
    }

    /** A row of buttons laid out by the caller, with no strip behind it. */
    fun row(vararg buttons: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        buttons.forEach { addView(it) }
    }

    /**
     * The Catch Route window's banner: the figure that matters in large type on the route's
     * blue, like a turn instruction, and the window's handle. [trailing] sits on its right.
     */
    fun banner(heightDp: Int, vararg trailing: View): Pair<View, GuidanceViews> {
        val badge = badge(32, inverse = true)
        val headline = text(18f, bold = true).painted { setTextColor(it.onPrimary) }
        val title = text(12f, bold = true).painted { setTextColor(it.onPrimary) }
        val detail = text(11f).painted { setTextColor(it.onPrimary and 0xD9FFFFFF.toInt()) }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(headline)
            addView(title)
            addView(detail)
        }
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(6), 0)
            addView(badge)
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            trailing.forEach { addView(it) }
        }
        val progress = progressBar(onPrimary = true)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(progress)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp))
            window.dragWith(this)
        }
        return column to GuidanceViews(badge, headline, title, detail, progress, column, { it.primary })
    }

    /**
     * The hunt map's card along the bottom: the target beside its badge, [actions] at the end of
     * the row, and the window's handle; a tap that does not drag is [onTap].
     */
    fun dock(heightDp: Int, vararg actions: View, onTap: () -> Unit): Pair<View, GuidanceViews> {
        val badge = badge(34, inverse = false)
        val title = text(13f, bold = true).painted { setTextColor(it.onSurface) }
        val detail = text(11f).painted { setTextColor(it.muted) }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(detail)
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(badge)
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            actions.forEach { addView(it) }
        }
        val progress = progressBar(onPrimary = false)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(8), dp(6))
            background = GradientDrawable().apply {
                val r = dp(16).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            elevation = dp(6).toFloat()
            addView(top)
            addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)).apply { topMargin = dp(4) })
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp), Gravity.BOTTOM)
            handle(this, onTap)
        }
        // The name has the first line to itself; how far leads the line under it.
        return card to GuidanceViews(badge, null, title, detail, progress, card, { it.panel })
    }

    /** The Catch Route window's bottom bar: icon over label, evenly spread. */
    fun labelBar(heightDp: Int, vararg actions: OverlayAction): Pair<LinearLayout, List<TextView>> {
        val labels = mutableListOf<TextView>()
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            painted { setBackgroundColor(it.bar) }
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp), Gravity.BOTTOM)
            actions.forEach { action ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isClickable = true
                    contentDescription = action.description
                    background = TypedValue().let { value ->
                        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                        context.getDrawable(value.resourceId)
                    }
                    setOnClickListener { action.onClick() }
                    if (action.icon != null) addView(ImageView(context).apply {
                        setImageResource(action.icon)
                        painted { imageTintList = ColorStateList.valueOf(it.primary) }
                    }, LinearLayout.LayoutParams(dp(18), dp(18)))
                    else addView(text(14f, bold = true).apply { text = action.glyph; gravity = Gravity.CENTER; painted { setTextColor(it.primary) } })
                    addView(text(10f).apply {
                        text = action.label
                        gravity = Gravity.CENTER
                        painted { setTextColor(it.onSurface) }
                        labels += this
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }
        }
        return bar to labels
    }

    companion object {
        /** Dark amber: a pace warning in the blue banner's place, still readable under white text. */
        const val WARNING = 0xFFB45309.toInt()
    }
}
