package com.example.pokemonalertsv2.tracking

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.hunt.huntInRangeChipText
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Where the live journey readout goes on this device.
 *
 * Exactly one surface, never two: the same distance in a status bar chip *and*
 * a floating pill is duplication the trainer has to read twice. Which one is
 * available is a property of the device, not a preference, so the choice is made
 * once here and every surface asks it rather than deciding for itself.
 */
enum class JourneyReadoutSurface {
    /** Android 16+: the real promoted-ongoing chip, with its own expanded card. */
    STATUS_BAR_CHIP,

    /** Below 16, with the overlay grant: a floating pill over other apps. */
    OVERLAY_PILL,

    /** Nothing else available: the floating map draws its own label. */
    MAP_LABEL
}

/**
 * The chip is strictly better when it exists — it is the platform's own surface,
 * it survives without an extra permission, and it cannot be covered by a
 * full-screen app — so it wins wherever the API level allows it. The pill is the
 * fallback below that, and the map label is the fallback to the fallback, for a
 * device that is both too old for the chip and has no overlay permission.
 *
 * @param overlayAllowed the user's preference; declining it drops through to the
 *   map rather than leaving the journey with no readout at all.
 */
fun resolveJourneyReadoutSurface(
    sdkInt: Int,
    canDrawOverlays: Boolean,
    overlayAllowed: Boolean
): JourneyReadoutSurface = when {
    sdkInt >= LIVE_NOTIFICATION_MIN_SDK -> JourneyReadoutSurface.STATUS_BAR_CHIP
    overlayAllowed && canDrawOverlays -> JourneyReadoutSurface.OVERLAY_PILL
    else -> JourneyReadoutSurface.MAP_LABEL
}

/**
 * Whether starting a hunt should open the picture-in-picture map. It never should.
 *
 * A hunt draws its map in an overlay window instead — see
 * [com.example.pokemonalertsv2.tracking.FloatingMapOverlay]. PiP is a visible
 * task, and Android hides the promoted-ongoing chip while the posting app's task
 * is visible, so a PiP map costs the trainer the very distance readout the hunt
 * exists to provide. The overlay window is not a task, so the map and the chip
 * coexist — verified on API 36.
 *
 * Kept as a named function rather than deleted at the call site so the reason is
 * written down where someone would otherwise re-add it.
 */
@Suppress("UNUSED_PARAMETER")
fun shouldOpenHuntPictureInPicture(surface: JourneyReadoutSurface): Boolean = false

/**
 * Whether the floating map should draw the journey label while it is open.
 *
 * Verified on an API 36 emulator: the status bar chip is hidden whenever the app's
 * task is visible, and a picture-in-picture window is a visible task — so while
 * that window is up the chip is *never* on screen, whatever the device. The map
 * therefore has to carry the readout itself, on every device except the one where
 * the pill is already doing it. (An overlay window is not a task and does not
 * suppress the chip, which is why the pill and the chip can coexist.)
 */
fun shouldLabelJourneyOnMap(surface: JourneyReadoutSurface): Boolean =
    surface != JourneyReadoutSurface.OVERLAY_PILL

/** Android 16 is where promoted ongoing notifications — and the chip — arrive. */
const val LIVE_NOTIFICATION_MIN_SDK = 36

/**
 * The one line every surface shows: how far while walking, then the fact that
 * decides your next tap once you are close enough to see the thing.
 *
 * Shared so the chip, the pill and the map label cannot drift apart; the
 * fallback wordings are passed in because two of the three callers have a
 * Context and one is a plain notification builder.
 */
fun journeyDetailText(
    alert: PokemonAlert,
    distanceMeters: Float?,
    inRange: Boolean,
    huntActive: Boolean,
    inRangeFallback: String,
    locatingFallback: String
): String = when {
    inRange -> (if (huntActive) huntInRangeChipText(alert) else null) ?: inRangeFallback
    distanceMeters != null -> formatJourneyDistance(distanceMeters)
    else -> locatingFallback
}

/** Metres up close, kilometres once the number stops reading at a glance. */
fun formatJourneyDistance(distanceMeters: Float): String =
    if (distanceMeters < 1_000f) {
        "${distanceMeters.roundToInt()} m"
    } else {
        String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1_000f)
    }
