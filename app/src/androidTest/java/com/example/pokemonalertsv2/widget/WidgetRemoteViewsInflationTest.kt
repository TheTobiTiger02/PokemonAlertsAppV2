package com.example.pokemonalertsv2.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRemoteViewsInflationTest {
    @Test
    fun everyWidgetLayoutUsesRemoteViewsSupportedClasses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parent = FrameLayout(context)
        listOf(
            R.layout.widget_alerts,
            R.layout.widget_alerts_compact,
            R.layout.widget_alert_item,
            R.layout.widget_alert_loading,
            R.layout.widget_nearby_radar
        ).forEach { layoutId ->
            RemoteViews(context.packageName, layoutId).apply(context, parent)
        }
    }

    @Test
    fun radarLayoutInflatesAllFocusedInteractionControlsAtFortyEightDp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parent = FrameLayout(context)
        val root = RemoteViews(context.packageName, R.layout.widget_nearby_radar)
            .apply(context, parent)
        val expectedSizePx = (48 * context.resources.displayMetrics.density).toInt()

        listOf(
            R.id.radar_toggle_view,
            R.id.radar_refresh,
            R.id.radar_next,
            R.id.radar_navigate,
            R.id.radar_dismiss
        ).forEach { viewId ->
            val control = root.findViewById<android.view.View>(viewId)
            assertNotNull(control)
            assertEquals(expectedSizePx, control.layoutParams.width)
            assertEquals(expectedSizePx, control.layoutParams.height)
        }
    }

    @Test
    fun radarSelectionAndViewModeSurviveStoreRecreationAndCanBeRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appWidgetId = 987_654
        val expected = RadarWidgetState(
            selectedAlertId = "Buizel|2099-01-01T10:00:00Z",
            viewMode = RadarViewMode.OVERVIEW
        )

        try {
            RadarWidgetStateStore.save(context, appWidgetId, expected)

            assertEquals(expected, RadarWidgetStateStore.get(context, appWidgetId))

            RadarWidgetStateStore.remove(context, appWidgetId)
            val removed = RadarWidgetStateStore.get(context, appWidgetId)
            assertNull(removed.selectedAlertId)
            assertEquals(RadarViewMode.FOCUS, removed.viewMode)
        } finally {
            RadarWidgetStateStore.remove(context, appWidgetId)
        }
    }

    @Test
    fun radarCarouselAndModeIntentsTargetTheExactWidget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appWidgetId = 321

        listOf(
            NearbyRadarWidgetProvider.ACTION_NEXT_ALERT,
            NearbyRadarWidgetProvider.ACTION_TOGGLE_VIEW
        ).forEach { action ->
            val intent = radarWidgetActionIntent(context, appWidgetId, action)

            assertEquals(action, intent.action)
            assertEquals(
                ComponentName(context, NearbyRadarWidgetProvider::class.java),
                intent.component
            )
            assertEquals(
                appWidgetId,
                intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
            )
        }
    }
}
