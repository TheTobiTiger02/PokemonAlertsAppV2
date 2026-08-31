package com.example.pokemonalertsv2.ui.alerts

import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import android.os.Build
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A picture-in-picture window never receives touch events, so the only controls we can
 * offer inside it are the system action buttons. Phones hand out three slots, which is
 * why the middle and trailing buttons change meaning with the mode instead of every
 * command getting its own slot.
 */
enum class MapPipMode {
    /** Camera pinned to the user, trailing buttons zoom. */
    FOLLOW,

    /** Camera steps between nearby alerts, trailing buttons walk the list. */
    BROWSE;

    fun toggled(): MapPipMode = if (this == FOLLOW) BROWSE else FOLLOW
}

enum class MapPipCommand {
    TOGGLE_MODE,
    PREVIOUS,
    NEXT;

    companion object {
        fun fromName(value: String?): MapPipCommand? =
            entries.firstOrNull { it.name == value }
    }
}

internal const val ACTION_MAP_PIP_CONTROL = "com.example.pokemonalertsv2.MAP_PIP_CONTROL"
internal const val EXTRA_MAP_PIP_COMMAND = "extra_map_pip_command"

/** What each slot does, kept separate from the Android types so it can be unit tested. */
internal data class MapPipActionSpec(
    val command: MapPipCommand,
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    val enabled: Boolean
)

/**
 * @param canStep whether there is anything to browse; the stepping buttons are emitted
 *   disabled rather than dropped so the toolbar does not jump around as alerts expire.
 */
internal fun mapPipActionSpecs(
    mode: MapPipMode,
    canStep: Boolean,
    maxActions: Int
): List<MapPipActionSpec> {
    if (maxActions <= 0) return emptyList()
    val specs = when (mode) {
        MapPipMode.FOLLOW -> listOf(
            MapPipActionSpec(
                command = MapPipCommand.TOGGLE_MODE,
                iconRes = R.drawable.ic_pip_browse,
                titleRes = R.string.map_pip_action_browse,
                enabled = true
            ),
            MapPipActionSpec(
                command = MapPipCommand.PREVIOUS,
                iconRes = R.drawable.ic_pip_zoom_out,
                titleRes = R.string.map_pip_action_zoom_out,
                enabled = true
            ),
            MapPipActionSpec(
                command = MapPipCommand.NEXT,
                iconRes = R.drawable.ic_pip_zoom_in,
                titleRes = R.string.map_pip_action_zoom_in,
                enabled = true
            )
        )
        MapPipMode.BROWSE -> listOf(
            MapPipActionSpec(
                command = MapPipCommand.TOGGLE_MODE,
                iconRes = R.drawable.ic_my_location,
                titleRes = R.string.map_pip_action_follow,
                enabled = true
            ),
            MapPipActionSpec(
                command = MapPipCommand.PREVIOUS,
                iconRes = R.drawable.ic_pip_prev,
                titleRes = R.string.map_pip_action_previous_alert,
                enabled = canStep
            ),
            MapPipActionSpec(
                command = MapPipCommand.NEXT,
                iconRes = R.drawable.ic_pip_next,
                titleRes = R.string.map_pip_action_next_alert,
                enabled = canStep
            )
        )
    }
    return specs.take(maxActions)
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun buildMapPipActions(
    context: Context,
    mode: MapPipMode,
    canStep: Boolean,
    maxActions: Int
): List<RemoteAction> = mapPipActionSpecs(mode, canStep, maxActions).map { spec ->
    val title = context.getString(spec.titleRes)
    RemoteAction(
        Icon.createWithResource(context, spec.iconRes),
        title,
        title,
        mapPipCommandIntent(context, spec.command)
    ).apply { isEnabled = spec.enabled }
}

/**
 * Each command needs its own request code: sharing one would make the last
 * [PendingIntent] built overwrite the extras of the others.
 */
internal fun mapPipCommandIntent(context: Context, command: MapPipCommand): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        MAP_PIP_REQUEST_CODE_BASE + command.ordinal,
        Intent(ACTION_MAP_PIP_CONTROL)
            .setPackage(context.packageName)
            .putExtra(EXTRA_MAP_PIP_COMMAND, command.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

private const val MAP_PIP_REQUEST_CODE_BASE = 4200

/**
 * Nearest-first ordering for the browse cursor, so stepping walks outwards from wherever
 * the map is centred rather than following the feed order.
 *
 * Ties break on the alert id to keep the order stable between recompositions; without
 * that, two alerts at the same spot could swap places under the cursor.
 */
internal fun mapPipBrowseOrder(
    alerts: List<PokemonAlert>,
    originLatitude: Double,
    originLongitude: Double
): List<PokemonAlert> = alerts
    .mapNotNull { alert -> alert.mapCoordinatesOrNull()?.let { alert to it } }
    .sortedWith(
        compareBy(
            { (_, coordinates) ->
                mapPipDistanceMeters(
                    originLatitude,
                    originLongitude,
                    coordinates.latitude,
                    coordinates.longitude
                )
            },
            { (alert, _) -> alert.uniqueId }
        )
    )
    .map { (alert, _) -> alert }

/**
 * Steps the browse cursor, wrapping at both ends. An unknown (expired, filtered away)
 * current id restarts at the nearest alert rather than losing the cursor entirely.
 */
internal fun stepMapPipSelection(
    orderedIds: List<String>,
    currentId: String?,
    forward: Boolean
): String? {
    if (orderedIds.isEmpty()) return null
    val currentIndex = orderedIds.indexOf(currentId)
    if (currentIndex < 0) return orderedIds.first()
    val step = if (forward) 1 else -1
    val nextIndex = (currentIndex + step + orderedIds.size) % orderedIds.size
    return orderedIds[nextIndex]
}

internal fun mapPipDistanceMeters(
    latitude: Double,
    longitude: Double,
    otherLatitude: Double,
    otherLongitude: Double
): Double {
    val deltaLat = Math.toRadians(otherLatitude - latitude)
    val deltaLon = Math.toRadians(otherLongitude - longitude)
    val a = sin(deltaLat / 2).pow(2) +
        cos(Math.toRadians(latitude)) * cos(Math.toRadians(otherLatitude)) *
        sin(deltaLon / 2).pow(2)
    return 2 * MAP_PIP_EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
}

private const val MAP_PIP_EARTH_RADIUS_METERS = 6_371_000.0

/**
 * One line of context for the browse cursor, since the window has no room for a card.
 */
internal fun mapPipBrowseLabel(alert: PokemonAlert, nowMillis: Long): String = buildString {
    append(alert.pokemon?.takeIf(String::isNotBlank) ?: alert.cleanPokemonName)
    alert.displayCp?.let { append(" \u00b7 CP ").append(it) }
    append(" \u00b7 ").append(mapCountdownLabel(alert.endTime, nowMillis))
}

/** How the window should frame the user and the alert they are browsing. */
internal sealed interface MapPipFocus {
    /** Both points, with the camera left to work out the zoom that contains them. */
    data class Fit(
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double
    ) : MapPipFocus

    /** Close enough that bounds would slam the camera to maximum zoom. */
    data class Centre(
        val latitude: Double,
        val longitude: Double,
        val zoom: Double
    ) : MapPipFocus
}

/**
 * Separation below which fitting bounds is pointless: the two markers already overlap, and
 * the camera would zoom in as far as it goes.
 */
internal const val MAP_PIP_MIN_FIT_METERS = 50.0
internal const val MAP_PIP_CLOSE_ZOOM = 17.0

internal fun resolveMapPipFocus(
    userLatitude: Double,
    userLongitude: Double,
    alertLatitude: Double,
    alertLongitude: Double
): MapPipFocus {
    val separation = mapPipDistanceMeters(
        userLatitude,
        userLongitude,
        alertLatitude,
        alertLongitude
    )
    if (separation < MAP_PIP_MIN_FIT_METERS) {
        return MapPipFocus.Centre(
            latitude = (userLatitude + alertLatitude) / 2,
            longitude = (userLongitude + alertLongitude) / 2,
            zoom = MAP_PIP_CLOSE_ZOOM
        )
    }
    return MapPipFocus.Fit(
        south = min(userLatitude, alertLatitude),
        west = min(userLongitude, alertLongitude),
        north = max(userLatitude, alertLatitude),
        east = max(userLongitude, alertLongitude)
    )
}

/** How far the user has to move before the browse framing is redrawn. */
internal const val MAP_PIP_REFIT_METERS = 15.0
