package com.example.pokemonalertsv2.data.pokegenie

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.VisibleForTesting
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.PokeGenieDao
import com.example.pokemonalertsv2.data.database.PokeGenieMonEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of importing a CSV, for the confirmation message. */
data class PokeGenieImportSummary(
    val fileName: String?,
    val importedCount: Int,
    val skippedCount: Int,
    val ignoredColumnCount: Int
)

sealed interface PokeGenieImportResult {
    data class Success(val summary: PokeGenieImportSummary) : PokeGenieImportResult
    data class Failure(val message: String) : PokeGenieImportResult
}

class PokeGenieRepository @VisibleForTesting internal constructor(
    private val dao: PokeGenieDao,
    private val preferences: RaidCounterPreferences,
    private val openStream: suspend (Uri) -> java.io.InputStream?,
    private val resolveFileName: suspend (Uri) -> String?
) {

    suspend fun count(): Int = withContext(Dispatchers.IO) { dao.count() }

    suspend fun index(): PokeGenieIndex = withContext(Dispatchers.IO) {
        PokeGenieMatcher.index(dao.getAll().map { it.toOwned() })
    }

    /** Every scanned Pokemon, for the local raid simulator to rank. */
    suspend fun ownedForSimulation(): List<OwnedPokemon> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toOwned() }
    }

    /**
     * Reads, parses and stores a Poke Genie export.
     *
     * Must run while the picking activity is alive: `ACTION_OPEN_DOCUMENT` grants read
     * access for that lifetime only, so this cannot be deferred to WorkManager.
     */
    suspend fun importFromUri(uri: Uri): PokeGenieImportResult = withContext(Dispatchers.IO) {
        val stream = runCatching { openStream(uri) }.getOrNull()
            ?: return@withContext PokeGenieImportResult.Failure(
                "Couldn't open that file. Try exporting it again from Poké Genie."
            )

        val parsed = stream.use { input ->
            runCatching { PokeGenieCsvParser.parse(input.reader(Charsets.UTF_8)) }
                .getOrElse {
                    return@withContext PokeGenieImportResult.Failure(
                        "Couldn't read that file. Make sure it's the CSV exported from " +
                            "Poké Genie's scan history."
                    )
                }
        }

        when (parsed) {
            is PokeGenieParseResult.Failure -> PokeGenieImportResult.Failure(parsed.userMessage())
            is PokeGenieParseResult.Success -> {
                val now = System.currentTimeMillis()
                val entities = parsed.rows.map { it.toEntity(now) }
                dao.replaceAll(entities)

                val fileName = runCatching { resolveFileName(uri) }.getOrNull()
                preferences.recordPokeGenieImport(
                    fileName = fileName,
                    rowCount = entities.size,
                    // Every row that produced at least one candidate id can be looked up.
                    matchedCount = entities.count { it.matchKey.isNotEmpty() },
                    timestamp = now
                )
                PokeGenieImportResult.Success(
                    PokeGenieImportSummary(
                        fileName = fileName,
                        importedCount = entities.size,
                        skippedCount = parsed.skippedLineCount,
                        ignoredColumnCount = parsed.unmappedHeaders.size
                    )
                )
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clear()
        preferences.clearPokeGenie()
    }

    companion object {
        @Volatile
        private var INSTANCE: PokeGenieRepository? = null

        fun getInstance(context: Context): PokeGenieRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun create(appContext: Context): PokeGenieRepository = PokeGenieRepository(
            dao = AppDatabase.getDatabase(appContext).pokeGenieDao(),
            preferences = RaidCounterPreferences(appContext.alertPreferencesDataStore),
            openStream = { uri -> appContext.contentResolver.openInputStream(uri) },
            resolveFileName = { uri -> appContext.displayNameOf(uri) }
        )

        private fun Context.displayNameOf(uri: Uri): String? =
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && cursor.columnCount > 0) cursor.getString(0) else null
                }
    }
}

private fun PokeGenieParseResult.Failure.userMessage(): String = when (reason) {
    PokeGenieParseResult.Reason.EMPTY_FILE -> "That file is empty."
    PokeGenieParseResult.Reason.NO_DATA_ROWS -> "That file has no scans in it."
    PokeGenieParseResult.Reason.NO_NAME_COLUMN ->
        "That doesn't look like a Poké Genie export — it has no Name column." +
            (detail?.let { "\n$it" } ?: "")
}

private fun PokeGenieRow.toEntity(importedAt: Long): PokeGenieMonEntity {
    val keys = PokeGenieMatcher.matchKeysFor(this)
    return PokeGenieMonEntity(
        scanIndex = scanIndex,
        displayName = name,
        form = form,
        pokedexNumber = pokedexNumber,
        matchKey = keys.firstOrNull().orEmpty(),
        altMatchKeys = keys.drop(1).joinToString("\n"),
        cp = cp,
        hp = hp,
        atkIv = atkIv,
        defIv = defIv,
        staIv = staIv,
        levelMin = levelMin,
        levelMax = levelMax,
        level = level,
        quickMove = quickMove,
        chargeMove = chargeMove,
        chargeMove2 = chargeMove2,
        gender = gender,
        shadowState = shadowState.name,
        lucky = lucky,
        favorite = favorite,
        importedAt = importedAt
    )
}

private fun PokeGenieMonEntity.toOwned(): OwnedPokemon {
    val keys = buildList {
        if (matchKey.isNotEmpty()) add(matchKey)
        if (altMatchKeys.isNotEmpty()) addAll(altMatchKeys.split("\n").filter { it.isNotEmpty() })
    }
    return OwnedPokemon(
        displayName = displayName,
        form = form,
        level = level,
        atkIv = atkIv,
        defIv = defIv,
        staIv = staIv,
        cp = cp,
        quickMove = quickMove,
        chargeMove = chargeMove,
        chargeMove2 = chargeMove2,
        shadow = shadowState == ShadowState.SHADOW.name,
        lucky = lucky,
        matchKeys = keys
    )
}
