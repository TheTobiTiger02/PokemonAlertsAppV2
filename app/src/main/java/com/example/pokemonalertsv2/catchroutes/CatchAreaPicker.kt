package com.example.pokemonalertsv2.catchroutes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * Full-screen map for drawing a walking area. Used by catch routes and hunt mode. Corners are tapped in,
 * dragged to move, tapped to remove, and pulled out of an edge's handle; or the whole outline is drawn
 * with a finger and becomes corners to fine-tune. Areas can be saved by name and reused by either mode.
 * [onDone] receives the corners, or an empty list when the limit was cleared.
 */
@Composable
internal fun CatchAreaPicker(
    initial: List<CatchPoint>,
    center: CatchPoint,
    title: String,
    onDismiss: () -> Unit,
    onDone: (List<CatchPoint>) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { CatchRouteStore.get(context) }
    val saved by store.areas.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var corners by remember { mutableStateOf(initial) }
    // Every edit is undoable, not just the last tap: drags, removals and drawings too.
    val history = remember { mutableStateListOf<List<CatchPoint>>() }
    fun change(next: List<CatchPoint>) { if (next != corners) { history += corners; corners = next } }
    var drawing by remember { mutableStateOf(false) }
    var savedOpen by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedCatchArea?>(null) }
    var view by remember { mutableStateOf<CatchRouteMapView?>(null) }
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
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !drawing, onClick = { drawing = false }, label = { Text("Corners") }, modifier = Modifier.testTag("area_mode_corners"))
                    FilterChip(selected = drawing, onClick = { drawing = true }, label = { Text("Draw") }, modifier = Modifier.testTag("area_mode_draw"))
                    TextButton(onClick = { corners = history.removeAt(history.lastIndex) }, enabled = history.isNotEmpty()) { Text("Undo") }
                    TextButton(onClick = { change(emptyList()) }, enabled = corners.isNotEmpty()) { Text("Clear") }
                    Box {
                        TextButton(onClick = { savedOpen = true }, modifier = Modifier.testTag("area_saved")) { Text("Saved (${saved.size})") }
                        DropdownMenu(expanded = savedOpen, onDismissRequest = { savedOpen = false }) {
                            if (saved.isEmpty()) DropdownMenuItem(text = { Text("No saved areas yet") }, onClick = { savedOpen = false }, enabled = false)
                            saved.forEach { area ->
                                DropdownMenuItem(text = { Text(area.name) }, onClick = {
                                    savedOpen = false; drawing = false; change(area.corners); view?.fitArea(area.corners)
                                }, trailingIcon = { TextButton(onClick = { savedOpen = false; deleting = area }) { Text("Delete") } })
                            }
                        }
                    }
                    TextButton(onClick = { naming = true }, enabled = corners.isArea(), modifier = Modifier.testTag("area_save")) { Text("Save") }
                }
                Text(when {
                    drawing -> "Draw the outline with one finger. It becomes corners you can adjust."
                    corners.isEmpty() -> "Tap the map to add corners, or switch to Draw"
                    corners.size < 3 -> "${corners.size} corner${if (corners.size == 1) "" else "s"} · add at least ${3 - corners.size} more"
                    else -> "${corners.size} corners · drag one to move it, tap it to remove, pull + to add"
                }, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            CatchRouteMapView(ctx).also { map ->
                                view = map
                                map.update(CatchRouteSettings(start = center, area = if (initial.isArea()) initial else emptyList()), null, null)
                                map.onReady = { if (initial.isArea()) map.fit() else map.centerOn(center) }
                            }
                        },
                        update = { map ->
                            map.onPick = { p -> if (!drawing) change(insertAreaCorner(corners, p)) }
                            map.onAreaChanged = { edited -> change(edited); drawing = false }
                            map.drawingArea = drawing
                            map.editArea(corners)
                        },
                        onRelease = { it.destroy() },
                        modifier = Modifier.fillMaxSize().testTag("area_map"),
                    )
                }
            }
        }
    }
    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { naming = false }, title = { Text("Save area") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true,
                modifier = Modifier.testTag("area_name")) },
            confirmButton = { TextButton(onClick = { val corners = corners; scope.launch { store.saveArea(name, corners) }; naming = false },
                enabled = name.isNotBlank(), modifier = Modifier.testTag("area_name_save")) { Text("Save") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } })
    }
    deleting?.let { area ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${area.name}?") },
            confirmButton = { TextButton(onClick = { scope.launch { store.deleteArea(area.id) }; deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

/**
 * Saved areas as one-tap chips, for the forms that hold an area: the selected chip is the area in use.
 * Nothing shows until an area has been saved in the picker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SavedAreaChips(current: List<CatchPoint>, onPick: (List<CatchPoint>) -> Unit) {
    val context = LocalContext.current
    val saved by remember(context) { CatchRouteStore.get(context).areas }.collectAsStateWithLifecycle(initialValue = emptyList())
    if (saved.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        saved.forEach { area ->
            FilterChip(selected = area.corners == current, onClick = { onPick(if (area.corners == current) emptyList() else area.corners) },
                label = { Text(area.name) })
        }
    }
}
