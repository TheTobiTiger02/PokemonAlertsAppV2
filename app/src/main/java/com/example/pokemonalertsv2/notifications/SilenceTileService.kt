package com.example.pokemonalertsv2.notifications

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Quick Settings tile for the existing "silence notifications" setting: tap to silence alerts
 * for an hour, tap again to end it early. The same value Settings › Notifications edits.
 */
class SilenceTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences by lazy { AlertPreferences(applicationContext.alertPreferencesDataStore) }

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render(preferences.silenceUntil.first()) }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val now = System.currentTimeMillis()
            val until = if (preferences.silenceUntil.first() > now) 0L else now + SILENCE_MILLIS
            preferences.updateSilenceUntil(until)
            render(until)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(silenceUntil: Long) {
        val tile = qsTile ?: return
        val active = silenceUntil > System.currentTimeMillis()
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_silence_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_poke_notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) {
                getString(R.string.tile_silence_until, TIME.format(Instant.ofEpochMilli(silenceUntil).atZone(ZoneId.systemDefault())))
            } else {
                getString(R.string.tile_silence_off)
            }
        }
        tile.updateTile()
    }

    private companion object {
        const val SILENCE_MILLIS = 60 * 60 * 1000L
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
