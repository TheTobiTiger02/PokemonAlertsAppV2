package com.example.pokemonalertsv2.catchroutes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full-screen map for drawing a walking area: each tap adds a corner. Used by catch routes and hunt
 * mode. [onDone] receives the corners, or an empty list when the limit was cleared.
 */
@Composable
internal fun CatchAreaPicker(
    initial: List<CatchPoint>,
    center: CatchPoint,
    title: String,
    onDismiss: () -> Unit,
    onDone: (List<CatchPoint>) -> Unit,
) {
    var corners by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onDone(if (corners.isArea()) corners else emptyList()) },
                        enabled = corners.isEmpty() || corners.isArea(), modifier = Modifier.testTag("area_done")) { Text("Done") }
                }
                // Controls sit under the title: a dialog's bottom edge can end up under the gesture bar.
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(when (corners.size) {
                        0 -> "Tap the map to add corners"
                        1, 2 -> "${corners.size} corner${if (corners.size == 1) "" else "s"} · add at least ${3 - corners.size} more"
                        else -> "${corners.size} corners"
                    }, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { corners = corners.dropLast(1) }, enabled = corners.isNotEmpty()) { Text("Undo") }
                    TextButton(onClick = { corners = emptyList() }, enabled = corners.isNotEmpty()) { Text("Clear") }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            CatchRouteMapView(ctx).also { view ->
                                view.update(CatchRouteSettings(start = center, area = if (initial.isArea()) initial else emptyList()), null, null)
                                view.onReady = { if (initial.isArea()) view.fit() else view.centerOn(center) }
                            }
                        },
                        update = { view ->
                            view.onPick = { p -> corners = corners + p }
                            view.editArea(corners)
                        },
                        onRelease = { it.destroy() },
                        modifier = Modifier.fillMaxSize().testTag("area_map"),
                    )
                }
            }
        }
    }
}
