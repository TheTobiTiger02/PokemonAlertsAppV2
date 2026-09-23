package com.example.pokemonalertsv2.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.ALERT_DISTANCE_STEPS_METERS
import com.example.pokemonalertsv2.data.distanceLabel
import com.example.pokemonalertsv2.data.distanceStepIndex
import kotlin.math.roundToInt

/** The limits people actually pick; the slider below covers everything in between. */
private val DISTANCE_PRESETS_METERS = listOf(0, 300, 500, 1_000, 2_000, 5_000, 10_000)

/**
 * A straight-line distance limit: one-tap presets plus a slider for any other step.
 *
 * The slider snaps to [ALERT_DISTANCE_STEPS_METERS] by rounding its index rather than via
 * `steps`, which would draw one tick per step - 59 of them, a dotted line nobody can read.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DistanceLimitPicker(
    meters: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Maximum distance"
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DISTANCE_PRESETS_METERS.forEach { preset ->
                FilterChip(
                    selected = meters == preset,
                    onClick = { onChange(preset) },
                    label = { Text(if (preset == 0) "Any" else distanceLabel(preset)) }
                )
            }
        }
        DistanceStepSlider(meters = meters, onChange = onChange, contentDescription = contentDescription)
    }
}

/** A tick-free slider over [ALERT_DISTANCE_STEPS_METERS]; index 0 is Unlimited. */
@Composable
fun DistanceStepSlider(
    meters: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Maximum distance"
) {
    Slider(
        value = distanceStepIndex(meters).toFloat(),
        onValueChange = { value ->
            val index = value.roundToInt().coerceIn(ALERT_DISTANCE_STEPS_METERS.indices)
            val next = ALERT_DISTANCE_STEPS_METERS[index]
            if (next != meters) onChange(next)
        },
        valueRange = 0f..ALERT_DISTANCE_STEPS_METERS.lastIndex.toFloat(),
        modifier = modifier.semantics { this.contentDescription = contentDescription }
    )
}
