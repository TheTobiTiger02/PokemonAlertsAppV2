package com.example.pokemonalertsv2

import android.app.Application
import androidx.work.Configuration
import com.example.pokemonalertsv2.notifications.AlertNotifier
import com.example.pokemonalertsv2.work.AlertAlarmScheduler
import com.example.pokemonalertsv2.work.AlertWorker

class PokemonAlertsApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        AlertNotifier.ensureChannel(this)
        AlertWorker.schedule(this)
        AlertWorker.triggerImmediateSync(this)
        AlertAlarmScheduler.prime(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
