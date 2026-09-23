package com.example.pokemonalertsv2.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.example.pokemonalertsv2.data.*
import kotlin.math.roundToInt

@Composable
fun DistanceLimitControl(meters: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Unlimited distance", Modifier.weight(1f))
        Switch(checked = meters == 0, onCheckedChange = { onChange(if (it) 0 else 1000) })
    }
    if (meters > 0) {
        Text("Distance: ${distanceLabel(meters)}", style = MaterialTheme.typography.labelLarge)
        Slider(value = distanceStepIndex(meters).coerceAtLeast(1).toFloat(),
            onValueChange = { onChange(ALERT_DISTANCE_STEPS_METERS[it.roundToInt().coerceIn(1, ALERT_DISTANCE_STEPS_METERS.lastIndex)]) },
            valueRange = 1f..ALERT_DISTANCE_STEPS_METERS.lastIndex.toFloat())
    }
}
