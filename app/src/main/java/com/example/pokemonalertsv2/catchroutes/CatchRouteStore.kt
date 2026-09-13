package com.example.pokemonalertsv2.catchroutes

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Entity(tableName = "setups") data class CatchSetupEntity(@PrimaryKey val id: String, val name: String, val settings: String, val updatedAt: Long)
@Entity(tableName = "active_session") data class CatchSessionEntity(@PrimaryKey val id: Int = 1, val payload: String)
@Dao interface CatchRouteDao {
    @Query("SELECT * FROM setups ORDER BY updatedAt DESC") fun setups(): Flow<List<CatchSetupEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(entity: CatchSetupEntity)
    @Query("DELETE FROM setups WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM active_session WHERE id = 1") fun session(): Flow<CatchSessionEntity?>
    @Query("SELECT * FROM active_session WHERE id = 1") suspend fun currentSession(): CatchSessionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun writeSession(entity: CatchSessionEntity)
    @Query("DELETE FROM active_session") suspend fun clearSession()
}
@Database(entities = [CatchSetupEntity::class, CatchSessionEntity::class], version = 1, exportSchema = true)
abstract class CatchRouteDatabase : RoomDatabase() { abstract fun routes(): CatchRouteDao }

class CatchRouteStore private constructor(context: Context) {
    private val db = Room.databaseBuilder(context.applicationContext, CatchRouteDatabase::class.java, "catch_routes.db").build()
    private val dao = db.routes()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val setups: Flow<List<CatchSetupEntity>> = dao.setups()
    val session: Flow<CatchSession?> = dao.session().map { decodeSession(it) }
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
    companion object {
        @Volatile private var instance: CatchRouteStore? = null
        fun get(context: Context): CatchRouteStore = instance ?: synchronized(this) { instance ?: CatchRouteStore(context).also { instance = it } }
    }
}
