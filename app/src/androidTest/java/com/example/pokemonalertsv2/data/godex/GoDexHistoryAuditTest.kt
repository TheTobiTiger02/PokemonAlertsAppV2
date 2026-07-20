package com.example.pokemonalertsv2.data.godex

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsApi
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException

@RunWith(AndroidJUnit4::class)
class GoDexHistoryAuditTest {
    @Test
    fun allHistoricalHundosHaveKnownFormsOrApprovedExceptions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = AppDatabase.getDatabase(context).goDexEntryDao().getAll()
        assumeTrue("A synced GoDex catalog is required for this audit", entries.isNotEmpty())

        val alerts = try {
            PokemonAlertsApi.service.getHistory(type = "Hundo").data
        } catch (error: Throwable) {
            if (error is IOException || error is HttpException) {
                assumeNoException("The Hundo history endpoint is unavailable", error)
                return@runBlocking
            }
            throw error
        }
        assumeTrue("The Hundo history endpoint returned no alerts", alerts.isNotEmpty())

        val unknownGroups = alerts
            .asSequence()
            .filter { alert ->
                GoDexMatcher.match(alert, entries, configured = true).status == GoDexMatchStatus.UNKNOWN
            }
            .groupBy { alert ->
                UnknownFormKey(alert.pokedexId, alert.pokemonForm, alert.gender)
            }

        unknownGroups.forEach { (key, groupedAlerts) ->
            val candidates = entries
                .filter { it.pokedexId == key.pokedexId }
                .joinToString { it.auditLabel() }
                .ifBlank { "none" }
            Log.i(
                TAG,
                "count=${groupedAlerts.size}, key=$key, candidates=[$candidates]"
            )
        }

        val unexpected = unknownGroups.filterKeys { key -> !key.isApprovedException() }
        assertTrue(
            "Unexpected GoDex UNKNOWN groups:\n${unexpected.toAuditText(entries)}",
            unexpected.isEmpty()
        )
    }

    private data class UnknownFormKey(
        val pokedexId: Int?,
        val pokemonForm: String?,
        val gender: String?
    ) {
        fun isApprovedException(): Boolean {
            val normalizedForm = pokemonForm?.trim()
            val isCostumePikachu = pokedexId == 25 && !normalizedForm.isNullOrEmpty()
            val isWinterDelibird = pokedexId == 225 &&
                normalizedForm.equals("Winter 2020", ignoreCase = true)
            return isCostumePikachu || isWinterDelibird
        }
    }

    private fun Map<UnknownFormKey, List<PokemonAlert>>.toAuditText(
        goDexEntries: List<GoDexEntryEntity>
    ): String = this.entries.joinToString(separator = "\n") { (key, alerts) ->
        val candidates = goDexEntries
            .filter { it.pokedexId == key.pokedexId }
            .joinToString { it.auditLabel() }
            .ifBlank { "none" }
        "count=${alerts.size}, key=$key, candidates=[$candidates]"
    }

    private fun GoDexEntryEntity.auditLabel(): String =
        "$entryKey(form=$formSlug, gender=$gender, name=$displayName)"

    private companion object {
        const val TAG = "GODEX_AUDIT"
    }
}
