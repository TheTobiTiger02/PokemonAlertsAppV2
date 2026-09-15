package com.example.pokemonalertsv2.catchroutes

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in: opens Catch routes with a generated plan and holds it open so a person (or adb) can
 * look at the map and the spawnpoint details. Pass `-e enableCatchFixture true`, optionally
 * `-e lat`, `-e lon`, `-e waitMinutes` and `-e holdSeconds`. Never runs in a normal test pass.
 */
class CatchRouteManualFixtureTest {
    @Test fun openPlannedRoute() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("enableCatchFixture") == "true")
        val start = CatchPoint(args.getString("lat")?.toDouble() ?: 49.8750, args.getString("lon")?.toDouble() ?: 8.6530)
        ActivityScenario.launch(CatchRoutesActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.model.startPoint(start)
                activity.model.edit(activity.model.settings.copy(start = start, durationMinutes = 30, finish = CatchFinish.ANYWHERE,
                    maxWaitMinutes = args.getString("waitMinutes")?.toInt() ?: 0))
                activity.model.generate()
            }
            SystemClock.sleep((args.getString("holdSeconds")?.toLong() ?: 120L) * 1000)
        }
    }
}
