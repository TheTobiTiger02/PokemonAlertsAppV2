package com.example.pokemonalertsv2.catchroutes

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Entity(tableName = "setups") data class CatchSetupEntity(@PrimaryKey val id: String, val name: String, val settings: String, val updatedAt: Long)
@Entity(tableName = "active_session") data class CatchSessionEntity(@PrimaryKey val id: Int = 1, val payload: String)
/** A drawn walking area kept for reuse by catch routes and hunt mode; [corners] is a JSON list of points. */
@Entity(tableName = "areas") data class CatchAreaEntity(@PrimaryKey val id: String, val name: String, val corners: String, val updatedAt: Long)
/** One finished route; [payload] is a [CatchRouteSummary] as JSON. */
@Entity(tableName = "history") data class CatchHistoryEntity(@PrimaryKey val id: String, val endedAt: Long, val payload: String)
@Dao interface CatchRouteDao {
    @Query("SELECT * FROM setups ORDER BY updatedAt DESC") fun setups(): Flow<List<CatchSetupEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(entity: CatchSetupEntity)
    @Query("DELETE FROM setups WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM active_session WHERE id = 1") fun session(): Flow<CatchSessionEntity?>
    @Query("SELECT * FROM active_session WHERE id = 1") suspend fun currentSession(): CatchSessionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun writeSession(entity: CatchSessionEntity)
    @Query("DELETE FROM active_session") suspend fun clearSession()
    @Query("SELECT * FROM areas ORDER BY updatedAt DESC") fun areas(): Flow<List<CatchAreaEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveArea(entity: CatchAreaEntity)
    @Query("DELETE FROM areas WHERE id = :id") suspend fun deleteArea(id: String)
    @Query("SELECT * FROM history ORDER BY endedAt DESC") fun history(): Flow<List<CatchHistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addHistory(entity: CatchHistoryEntity)
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY endedAt DESC LIMIT :keep)") suspend fun trimHistory(keep: Int)
    @Query("DELETE FROM history WHERE id = :id") suspend fun deleteHistory(id: String)
    @Query("DELETE FROM history") suspend fun clearHistory()
}
@Database(entities = [CatchSetupEntity::class, CatchSessionEntity::class, CatchAreaEntity::class, CatchHistoryEntity::class], version = 2, exportSchema = true)
abstract class CatchRouteDatabase : RoomDatabase() { abstract fun routes(): CatchRouteDao }

/** Adds saved areas and route history; existing setups and a running session are kept. */
internal val CATCH_ROUTES_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `areas` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `corners` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `history` (`id` TEXT NOT NULL, `endedAt` INTEGER NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`id`))")
    }
}

/** A saved area as the pickers list it. */
data class SavedCatchArea(val id: String, val name: String, val corners: List<CatchPoint>)

class CatchRouteStore private constructor(context: Context) {
    private val db = Room.databaseBuilder(context.applicationContext, CatchRouteDatabase::class.java, "catch_routes.db")
        .addMigrations(CATCH_ROUTES_MIGRATION_1_2).build()
    private val dao = db.routes()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val setups: Flow<List<CatchSetupEntity>> = dao.setups()
    val session: Flow<CatchSession?> = dao.session().map { decodeSession(it) }
    val areas: Flow<List<SavedCatchArea>> = dao.areas().map { rows ->
        rows.mapNotNull { row -> runCatching { SavedCatchArea(row.id, row.name, json.decodeFromString<List<CatchPoint>>(row.corners)) }.getOrNull() }
    }
    val history: Flow<List<CatchRouteSummary>> = dao.history().map { rows ->
        rows.mapNotNull { runCatching { json.decodeFromString<CatchRouteSummary>(it.payload) }.getOrNull() }
    }
    private fun decodeSession(row: CatchSessionEntity?): CatchSession? = row?.let { runCatching { json.decodeFromString<CatchSession>(it.payload) }.getOrNull() }
    fun decodeSetup(entity: CatchSetupEntity): CatchRouteSettings = json.decodeFromString(entity.settings)
    suspend fun save(settings: CatchRouteSettings, id: String? = null): String {
        val key = id ?: UUID.randomUUID().toString()
        dao.save(CatchSetupEntity(key, settings.name.trim().ifBlank { "Catch route" }, json.encodeToString(settings), System.currentTimeMillis()))
        return key
    }
    suspend fun delete(id: String) = dao.delete(id)
    suspend fun current(): CatchSession? = decodeSession(dao.currentSession())
    suspend fun write(session: CatchSession) = dao.writeSession(CatchSessionEntity(payload = json.encodeToString(session)))
    suspend fun clear() = dao.clearSession()
    suspend fun saveArea(name: String, corners: List<CatchPoint>, id: String? = null): String {
        val key = id ?: UUID.randomUUID().toString()
        dao.saveArea(CatchAreaEntity(key, name.trim().ifBlank { "Area" }, json.encodeToString(corners), System.currentTimeMillis()))
        return key
    }
    suspend fun deleteArea(id: String) = dao.deleteArea(id)
    suspend fun addHistory(summary: CatchRouteSummary) {
        dao.addHistory(CatchHistoryEntity(summary.id, summary.endedAtMillis, json.encodeToString(summary)))
        dao.trimHistory(CATCH_ROUTE_HISTORY_CAP)
    }
    /** Rewrites one history entry in place, e.g. once the watch's data has been read for it. */
    suspend fun updateHistory(summary: CatchRouteSummary) =
        dao.addHistory(CatchHistoryEntity(summary.id, summary.endedAtMillis, json.encodeToString(summary)))
    suspend fun deleteHistory(id: String) = dao.deleteHistory(id)
    suspend fun clearHistory() = dao.clearHistory()
    companion object {
        @Volatile private var instance: CatchRouteStore? = null
        fun get(context: Context): CatchRouteStore = instance ?: synchronized(this) { instance ?: CatchRouteStore(context).also { instance = it } }
    }
}
