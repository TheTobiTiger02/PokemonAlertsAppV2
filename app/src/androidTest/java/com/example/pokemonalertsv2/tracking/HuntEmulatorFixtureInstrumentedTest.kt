package com.example.pokemonalertsv2.tracking

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.hunt.HuntRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly opt-in fixture for host-driven GPS/lifecycle playback; never a production entry point. */
@RunWith(AndroidJUnit4::class)
class HuntEmulatorFixtureInstrumentedTest {
    @Test fun prepare() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("enableHuntFixture") == "true")
        val context: Context = ApplicationProvider.getApplicationContext()
        val hunts = HuntRepository.getInstance(context)
        val journeys = ArrivalTrackingRepository.getInstance(context)
        hunts.stop()
        journeys.stopTracking()
        context.stopService(Intent(context, ArrivalTrackingService::class.java))
        val preferences = AlertPreferences(context.alertPreferencesDataStore)
        val repository = PokemonAlertsRepository.create(context)
        repository.setOnboardingCompleted(true)
        args.getString("mapStyle")?.let { preferences.updateMapStylePreference(MapStylePreference.valueOf(it)) }
        val target = PokemonAlert(name = "Playback Pikachu", pokemon = "Pikachu", type = listOf("Hundo"),
            cp = 938, latitude = 49.738, longitude = 8.603, area = "HuntPlaybackFixture")
        val next = target.copy(name = "Playback Eevee", pokemon = "Eevee", latitude = 49.7392, longitude = 8.6055)
        listOf(target, next).forEach {
            preferences.removeDismissedAlert(it.uniqueId)
            repository.processIncomingAlert(it)
        }
        preferences.forgetCaughtAlert()
        if (args.getString("active") == "true") {
            hunts.start("Playback", FilterDefinition(areas = FilterSelection.only(listOf("HuntPlaybackFixture"))))
            if (args.getString("standby") != "true") {
                journeys.startTracking(target)
                hunts.setTarget(target.uniqueId)
            }
        }
    }
}
