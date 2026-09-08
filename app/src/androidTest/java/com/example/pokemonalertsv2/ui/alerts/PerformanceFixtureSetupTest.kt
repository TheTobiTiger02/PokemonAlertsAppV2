package com.example.pokemonalertsv2.ui.alerts

import android.graphics.Bitmap
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.data.database.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit opt-in only: the driver backs up app data and disconnects networking before seeding. */
class PerformanceFixtureSetupTest {
    @Test fun seedOfflineFixture() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("seedPerformanceFixture") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val count = arguments.getString("fixtureCount", "200").toInt()
        val image = File(context.filesDir, "performance-fixture.png")
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(80, 170, 120))
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val alerts = List(count) { index -> PokemonAlert(id = 8_000_000 + index,
            name = "Performance Pikachu $index", pokemon = "Pikachu", pokedexId = 25,
            type = listOf("Spawn"), latitude = 49.87 + index % 100 * 0.00003,
            longitude = 8.65 + index / 100 * 0.00003, endTime = "2099-01-01T00:00:00Z",
            imageUrl = image.toURI().toString(), thumbnailUrl = image.toURI().toString(),
            area = "Darmstadt", createdAt = "2026-09-07T08:00:00Z") }
        val db = AppDatabase.getDatabase(context)
        db.alertDao().replaceAll(alerts.map { it.toEntity() })
        db.historyAlertDao().replaceAll(alerts.map { it.copy(endTime = "2026-09-06T20:00:00Z").toHistoryEntity() })
        db.goDexEntryDao().replaceAll(List(if (count == 0) 0 else 1000) {
            GoDexEntryEntity("fixture-$it", it + 1, null, "none", "Fixture Pokemon $it", true,
                spriteUrl = image.toURI().toString())
        })
        // Original preferences are restored by the driver after the before/after runs.
        context.alertPreferencesDataStore.edit { it.clear() }
        AlertPreferences(context.alertPreferencesDataStore).setOnboardingCompleted(true)
        com.example.pokemonalertsv2.data.godex.GoDexPreferences(context.alertPreferencesDataStore)
            .saveSuccessfulSync("https://example.invalid/public-collection/performance", "Performance fixture", System.currentTimeMillis())
    }
}
