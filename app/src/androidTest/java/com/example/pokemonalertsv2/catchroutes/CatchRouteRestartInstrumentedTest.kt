package com.example.pokemonalertsv2.catchroutes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run Seed then Restore in separate instrumentation invocations to cross a real process restart. */
@RunWith(AndroidJUnit4::class)
class CatchRouteRestartSeedTest {
    @Test fun seed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CatchRouteController.get(context).stop()
        val now = System.currentTimeMillis()
        val p = CatchPoint(49.7408, 8.6201)
        val o = SpawnOpportunity("restart-cycle", "restart-point", p, now - 1000, now + 600_000, "observed_encounter")
        val plan = CatchItinerary(CatchRouteSettings(name = "Process restart fixture", start = p, startAtMillis = now),
            listOf(CatchPathPosition(p, 0.0)), listOf(CatchEncounter(o, now, 0.0)), listOf(p))
        CatchRouteStore.get(context).write(CatchSession(plan, visits = listOf(CatchVisit(o, now))))
    }
}

@RunWith(AndroidJUnit4::class)
class CatchRouteRestartRestoreTest {
    @Test fun restoredSessionKeepsItsRouteAndCountsVisits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = CatchRouteController.get(context)
        controller.ready()
        try {
            val restored = controller.session.value!!
            assertEquals("Process restart fixture", restored.itinerary.settings.name)
            // A restart no longer blanks the route or asks for a new one: its stops are timed in
            // absolute instants, and anything that despawned meanwhile shows as out of date.
            assertEquals("restart-cycle", restored.visits.single().opportunity.id)
            assertEquals("restart-cycle", restored.itinerary.encounters.single().opportunity.id)
        } finally { controller.stop() }
    }
}
