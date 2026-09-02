package com.example.pokemonalertsv2.data.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import com.example.pokemonalertsv2.BuildConfig
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.AlertPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Reads and writes a settings backup through the Storage Access Framework. */
class SettingsBackupRepository(private val context: Context) {

    private val dataStore get() = context.applicationContext.alertPreferencesDataStore

    suspend fun exportTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val preferences = dataStore.data.first()
            val text = SettingsBackup.export(
                preferences = preferences,
                exportedAtMillis = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME
            )
            context.contentResolver.openOutputStream(uri, "wt")
                ?.use { it.write(text.toByteArray()) }
                ?: error("Could not open the chosen file for writing.")
            SettingsBackup.parse(text).getOrThrow().entries.size
        }
    }

    /**
     * Reads and validates a backup without writing anything, so the user can be shown what
     * they are about to change. Mirrors the prepare/commit split the Poke Genie import
     * already uses: a bad file should never half-apply.
     */
    suspend fun prepareImport(uri: Uri): Result<SettingsBackup.Backup> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().decodeToString() }
                    ?: error("Could not open the chosen file.")
                SettingsBackup.parse(text).getOrThrow()
            }
        }

    suspend fun commitImport(backup: SettingsBackup.Backup): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                var applied = 0
                dataStore.edit { prefs -> applied = SettingsBackup.apply(backup, prefs) }
                // Persist a unified filter document when an older backup contains only
                // the legacy per-setting keys. A newer document wins automatically.
                AlertPreferences(dataStore).updateFilterStateDocument { it }
                applied
            }
        }

    companion object {
        fun suggestedFileName(nowMillis: Long = System.currentTimeMillis()): String {
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date(nowMillis))
            return "pokemon-alerts-settings-$stamp.json"
        }
    }
}
