package com.example.pokemonalertsv2.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.PokemonAlertsApplication
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.NotificationContentPreferences
import com.example.pokemonalertsv2.data.NotificationImageStyle
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.notificationContentCategory
import com.example.pokemonalertsv2.ui.alerts.ALSBACH_LATITUDE
import com.example.pokemonalertsv2.ui.alerts.ALSBACH_LONGITUDE
import com.example.pokemonalertsv2.ui.alerts.getLastKnownLocation
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.validAlertCoordinates

/**
 * The Settings test notification. It goes through the same builder and picture pipeline as a
 * real alert, so what the user sees in the shade is exactly what a real alert would look like.
 */
internal object TestNotification {

    private const val NOTIFICATION_KEY = "settings_test_notification"

    /**
     * The newest cached alert of [category] with a location gives the test a real picture and
     * real facts; without one, the fixed sample borrows another cached alert's place and Pokémon.
     */
    suspend fun sampleInput(
        context: Context,
        category: FilterAlertType,
        inRange: Boolean,
        now: Long = System.currentTimeMillis()
    ): NotificationContentInput {
        val cached = runCatching { PokemonAlertsRepository.create(context).getLocalAlerts() }.getOrNull().orEmpty()
            .filter { validAlertCoordinates(it) != null }
            .sortedByDescending { TimeUtils.parseEndTimeToMillis(it.endTime) ?: 0L }
        val base = cached.firstOrNull { notificationContentCategory(it) == category }
        val sample = notificationPreviewInput(category = category, inRange = inRange, now = now, base = base)
        if (base != null) return sample
        // No alert of this kind: borrow a real place and Pokémon from any recent alert, so the
        // map shows the user's area with a sprite instead of an empty sample.
        val donor = cached.firstOrNull { !it.thumbnailUrl.isNullOrBlank() && !it.pokemon.isNullOrBlank() }
            ?: cached.firstOrNull()
        val location = if (donor == null) getLastKnownLocation(context) else null
        val showsPokemon = sample.alert.pokemon != null
        return sample.copy(
            alert = sample.alert.copy(
                latitude = donor?.latitude ?: ((location?.latitude ?: ALSBACH_LATITUDE) + 0.0025),
                longitude = donor?.longitude ?: location?.longitude ?: ALSBACH_LONGITUDE,
                pokemon = donor?.pokemon?.takeIf { showsPokemon } ?: sample.alert.pokemon,
                thumbnailUrl = donor?.thumbnailUrl?.takeIf { showsPokemon }
            )
        )
    }

    suspend fun image(context: Context, alert: PokemonAlert, style: NotificationImageStyle): Bitmap? =
        AlertNotifier.prepareAlertImage(context, PokemonAlertsApplication.imageLoader(context), alert, style)

    /** Returns false when notifications are not allowed, so the caller can say why nothing appeared. */
    suspend fun post(
        context: Context,
        category: FilterAlertType,
        inRange: Boolean,
        preferences: NotificationContentPreferences
    ): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        AlertNotifier.ensureChannel(context)
        val input = sampleInput(context, category, inRange)
        val content = renderNotificationContent(input, preferences)
        val style = preferences.imageStyle(category)
        val notification = AlertNotifier.buildAlertNotification(
            context = context,
            alert = input.alert,
            content = content,
            bitmap = image(context, input.alert, style),
            imageStyle = style,
            channelId = AlertNotifier.notificationChannelFor(input.alert)
        ).build()
        NotificationManagerCompat.from(context).notify(AlertNotificationIds.forAlert(NOTIFICATION_KEY), notification)
        return true
    }
}
