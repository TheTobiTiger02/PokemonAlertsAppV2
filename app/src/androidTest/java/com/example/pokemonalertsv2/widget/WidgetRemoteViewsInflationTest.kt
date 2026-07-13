package com.example.pokemonalertsv2.widget

import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.R
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
            R.layout.widget_alerts_focus,
            R.layout.widget_alerts_pair,
            R.layout.widget_alert_item,
            R.layout.widget_alert_loading
        ).forEach { layoutId ->
            RemoteViews(context.packageName, layoutId).apply(context, parent)
        }
    }
}
