package com.example.pokemonalertsv2.data.pokegenie

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Immutable
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.PokeGenieDao
import com.example.pokemonalertsv2.data.database.PokeGenieMonEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.FileInputStream

/** Outcome of importing a CSV, for the confirmation message. */
data class PokeGenieImportSummary(
    val fileName: String?,
    val importedCount: Int,
    val skippedCount: Int,
    val ignoredColumnCount: Int
)

/** Parsed CSV content held until the user confirms replacement of the current roster. */
data class PokeGenieImportCandidate(
    val fileName: String?,
    val rows: List<PokeGenieRow>,
    val summary: PokeGenieImportSummary
)

sealed interface PokeGeniePrepareResult {
    data class Success(val candidate: PokeGenieImportCandidate) : PokeGeniePrepareResult
    data class Failure(val message: String) : PokeGeniePrepareResult
}

/** UI lifecycle for an external or in-app CSV open. */
@Immutable
sealed interface PokeGenieImportUiState {
    data object Idle : PokeGenieImportUiState
    data object Reading : PokeGenieImportUiState
    data class Preview(val candidate: PokeGenieImportCandidate) : PokeGenieImportUiState
    data class Error(val message: String) : PokeGenieImportUiState
    data class Imported(val summary: PokeGenieImportSummary) : PokeGenieImportUiState
}

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

    /** Live roster size, so an import taken while a raid card is open is picked up. */
    fun countFlow(): Flow<Int> = dao.countFlow().distinctUntilChanged()

    suspend fun index(): PokeGenieIndex = withContext(Dispatchers.IO) {
        PokeGenieMatcher.index(dao.getAll().map { it.toOwned() })
    }

    /** Every scanned Pokemon, for the local raid simulator to rank. */
    suspend fun ownedForSimulation(): List<OwnedPokemon> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toOwned() }
    }

    /** Imported rows used as identity/move constraints for server-backed Pokébattler scoring. */
    suspend fun ownedForPokebattler(): List<OwnedPokemon> = ownedForSimulation()

    /** Reads and validates a CSV without changing the stored roster. */
    suspend fun prepareImport(uri: Uri): PokeGeniePrepareResult = withContext(Dispatchers.IO) {
        val stream = runCatching { openStream(uri) }.getOrNull()
            ?: return@withContext PokeGeniePrepareResult.Failure(
                "Couldn't open that file. Try exporting it again from Poké Genie."
            )

        val parsed = stream.use { input ->
            runCatching { PokeGenieCsvParser.parse(input.reader(Charsets.UTF_8)) }
                .getOrElse {
                    return@withContext PokeGeniePrepareResult.Failure(
                        "Couldn't read that file. Make sure it's the CSV exported from " +
                            "Poké Genie's scan history."
                    )
                }
        }
        when (parsed) {
            is PokeGenieParseResult.Failure ->
                PokeGeniePrepareResult.Failure(parsed.userMessage())
            is PokeGenieParseResult.Success -> {
                val fileName = runCatching { resolveFileName(uri) }.getOrNull()
                val summary = PokeGenieImportSummary(
                    fileName = fileName,
                    importedCount = parsed.rows.size,
                    skippedCount = parsed.skippedLineCount,
                    ignoredColumnCount = parsed.unmappedHeaders.size
                )
                PokeGeniePrepareResult.Success(
                    PokeGenieImportCandidate(fileName, parsed.rows, summary)
                )
            }
        }
    }

    /** Commits a previously prepared candidate as one transactional replacement. */
    suspend fun commitImport(candidate: PokeGenieImportCandidate): PokeGenieImportResult =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val entities = candidate.rows.map { it.toEntity(now) }
                dao.replaceAll(entities)
                preferences.recordPokeGenieImport(
                    fileName = candidate.fileName,
                    rowCount = entities.size,
                    matchedCount = entities.count { it.matchKey.isNotEmpty() },
                    timestamp = now
                )
                PokeGenieImportResult.Success(
                    candidate.summary.copy(importedCount = entities.size)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                PokeGenieImportResult.Failure("Couldn't replace the current roster. Nothing was changed.")
            }
        }

    /**
     * Reads, parses and stores a Poke Genie export.
     *
     * Must run while the picking activity is alive: `ACTION_OPEN_DOCUMENT` grants read
     * access for that lifetime only, so this cannot be deferred to WorkManager.
     */
    suspend fun importFromUri(uri: Uri): PokeGenieImportResult = when (val prepared = prepareImport(uri)) {
        is PokeGeniePrepareResult.Failure -> PokeGenieImportResult.Failure(prepared.message)
        is PokeGeniePrepareResult.Success -> commitImport(prepared.candidate)
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
            openStream = { uri -> appContext.openPokeGenieStream(uri) },
            resolveFileName = { uri -> appContext.displayNameOf(uri) }
        )

        private fun Context.openPokeGenieStream(uri: Uri): java.io.InputStream? = when {
            uri.scheme.equals("file", ignoreCase = true) ->
                uri.path?.let(::FileInputStream)
            else -> contentResolver.openInputStream(uri)
        }

        private fun Context.displayNameOf(uri: Uri): String? =
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && cursor.columnCount > 0) cursor.getString(0) else null
                }
                ?: uri.lastPathSegment?.substringAfterLast('/')
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
    // Derived rather than read back from matchKey/altMatchKeys: the stored keys are a
    // snapshot of the form aliases as they were at import, so a fix to the alias table
    // would otherwise only reach people who re-imported their CSV. The columns are still
    // written, because the Room index on matchKey is part of the schema.
    val keys = PokeGenieMatcher.matchKeysFor(
        PokeGenieRow(
            name = displayName,
            form = form,
            shadowState = ShadowState.entries.firstOrNull { it.name == shadowState }
                ?: ShadowState.NORMAL
        )
    ).ifEmpty {
        buildList {
            if (matchKey.isNotEmpty()) add(matchKey)
            if (altMatchKeys.isNotEmpty()) {
                addAll(altMatchKeys.split("\n").filter { it.isNotEmpty() })
            }
        }
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
