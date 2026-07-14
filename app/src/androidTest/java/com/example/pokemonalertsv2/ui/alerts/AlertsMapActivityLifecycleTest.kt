package com.example.pokemonalertsv2.ui.alerts

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.MapStylePreference
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertsMapActivityLifecycleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = AlertPreferences(context.alertPreferencesDataStore)
    private lateinit var originalStyle: MapStylePreference

    @Before
    fun rememberOriginalStyle() {
        originalStyle = runBlocking { preferences.mapStylePreference.first() }
    }

    @After
    fun restoreOriginalStyle() {
        runBlocking { preferences.updateMapStylePreference(originalStyle) }
    }

    @Test
    fun repeatedOpenStreetMapLaunchesCanCloseDuringInitialization() {
        runBlocking { preferences.updateMapStylePreference(MapStylePreference.OPENSTREETMAP) }

        repeat(3) {
            launchAndFinishMapActivity(settleBeforeFinishMillis = 100)
            SystemClock.sleep(CALLBACK_SETTLE_MILLIS)
        }
    }

    @Test
    fun abandonedOpenStreetMapCallbackIsSafeAfterSwitchingToGoogle() {
        runBlocking { preferences.updateMapStylePreference(MapStylePreference.OPENSTREETMAP) }

        ActivityScenario.launch<AlertsMapActivity>(mapIntent()).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            SystemClock.sleep(100)
            runBlocking {
                preferences.updateMapStylePreference(MapStylePreference.GOOGLE_STANDARD)
            }
            SystemClock.sleep(100)
            scenario.onActivity { it.finish() }
        }
        SystemClock.sleep(CALLBACK_SETTLE_MILLIS)
    }

    @Test
    fun savedGoogleStylesCanLaunchAndCloseFromWidgetActivity() {
        listOf(
            MapStylePreference.GOOGLE_STANDARD,
            MapStylePreference.GOOGLE_SATELLITE
        ).forEach { style ->
            runBlocking { preferences.updateMapStylePreference(style) }
            launchAndFinishMapActivity(settleBeforeFinishMillis = 100)
        }
    }

    private fun launchAndFinishMapActivity(settleBeforeFinishMillis: Long) {
        ActivityScenario.launch<AlertsMapActivity>(mapIntent()).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            SystemClock.sleep(settleBeforeFinishMillis)
            scenario.onActivity { it.finish() }
        }
    }

    private fun mapIntent() = Intent(context, AlertsMapActivity::class.java)

    private companion object {
        const val CALLBACK_SETTLE_MILLIS = 1_000L
    }
}
