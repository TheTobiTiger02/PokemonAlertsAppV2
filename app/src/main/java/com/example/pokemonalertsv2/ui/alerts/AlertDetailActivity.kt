package com.example.pokemonalertsv2.ui.alerts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme

class AlertDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val alert = intent?.toPokemonAlert() ?: run {
            finish()
            return
        }
        setContent {
            PokemonAlertsV2Theme {
                AlertDetailScreen(alert = alert)
            }
        }
    }

    private fun Intent.toPokemonAlert(): PokemonAlert? {
        val name = getStringExtra(EXTRA_ALERT_NAME) ?: return null
        val description = getStringExtra(EXTRA_ALERT_DESCRIPTION) ?: ""
        val imageUrl = getStringExtra(EXTRA_ALERT_IMAGE_URL)
        val thumbnailUrl = getStringExtra(EXTRA_ALERT_THUMBNAIL_URL)
        val latitude = getDoubleExtra(EXTRA_ALERT_LATITUDE, Double.NaN)
        val longitude = getDoubleExtra(EXTRA_ALERT_LONGITUDE, Double.NaN)
        val endTime = getStringExtra(EXTRA_ALERT_END_TIME) ?: ""
        val type = getStringExtra(EXTRA_ALERT_TYPE)
        if (latitude.isNaN() || longitude.isNaN()) return null

        return PokemonAlert(
            name = name,
            description = description,
            imageUrl = imageUrl,
            longitude = longitude,
            latitude = latitude,
            endTime = endTime,
            type = type,
            thumbnailUrl = thumbnailUrl
        )
    }

    companion object {
        private const val EXTRA_ALERT_NAME = "extra_alert_name"
        private const val EXTRA_ALERT_DESCRIPTION = "extra_alert_description"
        private const val EXTRA_ALERT_IMAGE_URL = "extra_alert_image"
        private const val EXTRA_ALERT_LATITUDE = "extra_alert_latitude"
        private const val EXTRA_ALERT_LONGITUDE = "extra_alert_longitude"
        private const val EXTRA_ALERT_END_TIME = "extra_alert_end_time"
        private const val EXTRA_ALERT_TYPE = "extra_alert_type"
        private const val EXTRA_ALERT_THUMBNAIL_URL = "extra_alert_thumbnail"

        fun createIntent(context: Context, alert: PokemonAlert): Intent {
            return Intent(context, AlertDetailActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ALERT_NAME, alert.name)
                putExtra(EXTRA_ALERT_DESCRIPTION, alert.description)
                putExtra(EXTRA_ALERT_IMAGE_URL, alert.imageUrl)
                putExtra(EXTRA_ALERT_LATITUDE, alert.latitude)
                putExtra(EXTRA_ALERT_LONGITUDE, alert.longitude)
                putExtra(EXTRA_ALERT_END_TIME, alert.endTime)
                putExtra(EXTRA_ALERT_TYPE, alert.type)
                putExtra(EXTRA_ALERT_THUMBNAIL_URL, alert.thumbnailUrl)
            }
        }
    }
}
