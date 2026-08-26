package com.example.pokemonalertsv2.data.pokegenie

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.database.PokeGenieDao
import com.example.pokemonalertsv2.data.database.PokeGenieMonEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class PokeGenieRepositoryTest {

    @Test
    fun invalidCsvNeverMutatesExistingRoster() = runBlocking {
        val dao = FakePokeGenieDao().apply { rows += existingRow() }
        val repository = repository(dao, "CP\n4724\n")

        val result = repository.prepareImport(Uri.parse("content://files/bad.csv"))

        assertTrue(result is PokeGeniePrepareResult.Failure)
        assertEquals(1, dao.getAll().size)
        assertEquals(0, dao.replaceCalls)
    }

    @Test
    fun previewIsReadOnlyUntilCommit() = runBlocking {
        val dao = FakePokeGenieDao().apply { rows += existingRow() }
        val repository = repository(dao, "Name,CP\nPikachu,500\n")

        val prepared = repository.prepareImport(Uri.parse("content://files/good.csv"))
        assertTrue(prepared is PokeGeniePrepareResult.Success)
        assertEquals(1, dao.getAll().size)
        assertEquals(0, dao.replaceCalls)

        val committed = repository.commitImport((prepared as PokeGeniePrepareResult.Success).candidate)
        assertTrue(committed is PokeGenieImportResult.Success)
        assertEquals(1, dao.getAll().size)
        assertEquals(1, dao.replaceCalls)
    }

    private fun repository(
        dao: FakePokeGenieDao,
        csv: String,
        speciesIds: Set<String> = emptySet()
    ) = PokeGenieRepository(
        dao = dao,
        preferences = RaidCounterPreferences(InMemoryPreferencesDataStore()),
        openStream = { ByteArrayInputStream(csv.toByteArray()) },
        resolveFileName = { "scan.csv" },
        speciesIds = { speciesIds }
    )

    @Test
    fun previewNamesRowsWithNoPokebattlerMatch() = runBlocking {
        val repository = repository(
            FakePokeGenieDao(),
            "Name,Form,CP\nZacian,Sword,5629\nGrumbleweed,Normal,10\n",
            speciesIds = setOf("ZACIAN", "ZACIAN_CROWNED_SWORD_FORM")
        )

        val prepared = repository.prepareImport(Uri.parse("content://files/good.csv"))

        val summary = (prepared as PokeGeniePrepareResult.Success).candidate.summary
        assertEquals(2, summary.importedCount)
        assertEquals(1, summary.matchedCount)
        assertEquals(listOf("Grumbleweed"), summary.unmatchedForms.map { it.name })
        assertTrue(summary.formsChecked)
    }

    @Test
    fun anUnsyncedGameMasterReportsNothingRatherThanAllClear() = runBlocking {
        val repository = repository(FakePokeGenieDao(), "Name,Form,CP\nGrumbleweed,Normal,10\n")

        val prepared = repository.prepareImport(Uri.parse("content://files/good.csv"))

        val summary = (prepared as PokeGeniePrepareResult.Success).candidate.summary
        assertEquals(emptyList<UnmatchedForm>(), summary.unmatchedForms)
        assertEquals(false, summary.formsChecked)
    }

    private fun existingRow() = PokeGenieMonEntity(
        displayName = "Mewtwo",
        form = null,
        pokedexNumber = 150,
        matchKey = "MEWTWO",
        altMatchKeys = "",
        scanIndex = 1,
        cp = 4000,
        hp = null,
        atkIv = 15,
        defIv = 15,
        staIv = 15,
        levelMin = 40.0,
        levelMax = 40.0,
        level = 40.0,
        quickMove = null,
        chargeMove = null,
        chargeMove2 = null,
        gender = null,
        shadowState = ShadowState.NORMAL.name,
        lucky = false,
        favorite = false,
        importedAt = 1L
    )

    private class FakePokeGenieDao : PokeGenieDao {
        var rows = mutableListOf<PokeGenieMonEntity>()
        var replaceCalls = 0

        /** Backs [countFlow] so a roster change is observable, as Room's would be. */
        private val counts = MutableStateFlow(0)

        override suspend fun getAll(): List<PokeGenieMonEntity> = rows.toList()
        override suspend fun count(): Int = rows.size
        override fun countFlow(): Flow<Int> = counts
        override suspend fun insertAll(mons: List<PokeGenieMonEntity>) {
            rows += mons
            counts.value = rows.size
        }
        override suspend fun clear() {
            rows.clear()
            counts.value = 0
        }
        override suspend fun replaceAll(mons: List<PokeGenieMonEntity>) {
            replaceCalls++
            rows = mons.toMutableList()
            counts.value = rows.size
        }
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
