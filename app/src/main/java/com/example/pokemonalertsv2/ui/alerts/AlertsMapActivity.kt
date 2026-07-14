package com.example.pokemonalertsv2.ui.alerts

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.pokemonalertsv2.MainActivity

class AlertsMapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(MainActivity.createMapIntent(this))
        finish()
    }
}
