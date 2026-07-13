package com.example.pokemonalertsv2.widget

import android.content.Context
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.first

internal data class WidgetThemePalette(
    val dark: Boolean,
    @ColorInt val onSurface: Int,
    @ColorInt val onSurfaceVariant: Int,
    @ColorInt val primary: Int,
    @ColorInt val error: Int,
    @ColorInt val warning: Int,
    @ColorInt val fallbackBackground: Int,
    @ColorInt val fallbackAccent: Int,
    @DrawableRes val backgroundDrawable: Int,
    @DrawableRes val itemDrawable: Int,
    @DrawableRes val iconButtonDrawable: Int,
    @DrawableRes val badgeDrawable: Int,
    @DrawableRes val imageDrawable: Int,
    @DrawableRes val logoDrawable: Int
) {
    companion object {
        val Light = WidgetThemePalette(
            dark = false,
            onSurface = 0xFF16181D.toInt(),
            onSurfaceVariant = 0xFF5B6472.toInt(),
            primary = 0xFF0057D9.toInt(),
            error = 0xFFEF4444.toInt(),
            warning = 0xFFF59E0B.toInt(),
            fallbackBackground = 0xFFE5E7EB.toInt(),
            fallbackAccent = 0xFFD9E5FF.toInt(),
            backgroundDrawable = R.drawable.widget_bg_light,
            itemDrawable = R.drawable.widget_item_background_light,
            iconButtonDrawable = R.drawable.widget_icon_button_background_light,
            badgeDrawable = R.drawable.widget_badge_background_light,
            imageDrawable = R.drawable.widget_image_background_light,
            logoDrawable = R.drawable.ic_widget_pokeball_light
        )

        val Dark = WidgetThemePalette(
            dark = true,
            onSurface = 0xFFF2F4F8.toInt(),
            onSurfaceVariant = 0xFFB2BAC7.toInt(),
            primary = 0xFF7FA7FF.toInt(),
            error = 0xFFEF4444.toInt(),
            warning = 0xFFF59E0B.toInt(),
            fallbackBackground = 0xFF20252E.toInt(),
            fallbackAccent = 0xFF173D72.toInt(),
            backgroundDrawable = R.drawable.widget_bg_dark,
            itemDrawable = R.drawable.widget_item_background_dark,
            iconButtonDrawable = R.drawable.widget_icon_button_background_dark,
            badgeDrawable = R.drawable.widget_badge_background_dark,
            imageDrawable = R.drawable.widget_image_background_dark,
            logoDrawable = R.drawable.ic_widget_pokeball_dark
        )
    }
}

internal suspend fun resolveWidgetThemePalette(context: Context): WidgetThemePalette {
    val storedMode = AlertPreferences(context.alertPreferencesDataStore).themeMode.first()
    val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    return if (AppThemeMode.fromStored(storedMode).resolveDark(systemDark)) {
        WidgetThemePalette.Dark
    } else {
        WidgetThemePalette.Light
    }
}

internal fun RemoteViews.applyFullWidgetPalette(palette: WidgetThemePalette) {
    setBackgroundResource(R.id.widget_root_full, palette.backgroundDrawable)
    setBackgroundResource(R.id.tv_count, palette.badgeDrawable)
    setBackgroundResource(R.id.btn_map, palette.iconButtonDrawable)
    setBackgroundResource(R.id.btn_refresh, palette.iconButtonDrawable)
    setTextColors(palette.onSurface, R.id.tv_title, R.id.tv_empty_title)
    setTextColors(palette.onSurfaceVariant, R.id.tv_last_updated, R.id.tv_empty_subtitle)
    setTextColor(R.id.tv_count, palette.primary)
    setIconColor(palette.primary, R.id.btn_map, R.id.btn_refresh)
    setImageViewResource(R.id.img_logo, palette.logoDrawable)
}

internal fun RemoteViews.applyCompactWidgetPalette(palette: WidgetThemePalette) {
    setBackgroundResource(R.id.widget_root_compact, palette.backgroundDrawable)
    setBackgroundResource(R.id.compact_alert_content, palette.itemDrawable)
    setBackgroundResource(R.id.btn_refresh_compact, palette.iconButtonDrawable)
    setTextColors(palette.onSurface, R.id.tv_title_compact, R.id.tv_compact_alert_title)
    setTextColor(R.id.tv_compact_count, palette.onSurfaceVariant)
    setTextColor(R.id.tv_compact_countdown, palette.warning)
    setIconColor(palette.primary, R.id.btn_refresh_compact)
    setImageViewResource(R.id.img_logo_compact, palette.logoDrawable)
    setTextColor(R.id.tv_compact_meta, palette.onSurfaceVariant)
}

internal fun RemoteViews.applyItemWidgetPalette(palette: WidgetThemePalette) {
    setBackgroundResource(R.id.item_root, palette.itemDrawable)
    setBackgroundResource(R.id.item_image, palette.imageDrawable)
    setBackgroundResource(R.id.btn_navigate, palette.iconButtonDrawable)
    setBackgroundResource(R.id.btn_dismiss, palette.iconButtonDrawable)
    setTextColor(R.id.item_title, palette.onSurface)
    setTextColor(R.id.item_desc, palette.onSurfaceVariant)
    setTextColor(R.id.item_meta, palette.primary)
    setIconColor(palette.primary, R.id.btn_navigate)
    setIconColor(palette.error, R.id.btn_dismiss)
}

internal fun RemoteViews.applyLoadingWidgetPalette(palette: WidgetThemePalette) {
    setBackgroundResource(R.id.widget_loading_root, palette.itemDrawable)
    setTextColor(R.id.widget_loading_text, palette.onSurfaceVariant)
}

private fun RemoteViews.setBackgroundResource(viewId: Int, drawableId: Int) {
    setInt(viewId, "setBackgroundResource", drawableId)
}

private fun RemoteViews.setIconColor(@ColorInt color: Int, vararg viewIds: Int) {
    viewIds.forEach { setInt(it, "setColorFilter", color) }
}

private fun RemoteViews.setTextColors(@ColorInt color: Int, vararg viewIds: Int) {
    viewIds.forEach { setTextColor(it, color) }
}
