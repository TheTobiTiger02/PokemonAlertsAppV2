package com.example.pokemonalertsv2.catchroutes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Nearby Pokémon GO routes, nearest first. Choosing one offers the two ways to use it: walk it exactly as
 * drawn, or let the optimizer plan near it.
 */
@Composable
internal fun GoRoutePicker(
    repository: GoRouteRepository,
    around: CatchPoint,
    onDismiss: () -> Unit,
    onWalk: (GoRouteRecord, reverse: Boolean) -> Unit,
    onGuide: (GoRouteRecord) -> Unit,
) {
    var routes by remember { mutableStateOf<List<GoRouteSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var reverse by remember { mutableStateOf(false) }
    var loadingDetail by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(around) {
        error = null
        routes = try { repository.nearby(around) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            error = e.message ?: "Routes could not be loaded."; emptyList()
        }
    }
    fun use(id: String, action: (GoRouteRecord) -> Unit) = scope.launch {
        loadingDetail = true; error = null
        try { action(repository.detail(id)) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            error = e.message ?: "That route could not be loaded."
        } finally { loadingDetail = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Text("Pokémon GO routes nearby", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                if (routes == null || loadingDetail) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("go_route_error")) }
                if (routes?.isEmpty() == true && error == null) Text("No routes start within 3 km of your start.", Modifier.padding(16.dp))
                LazyColumn(Modifier.fillMaxSize().testTag("go_route_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(routes.orEmpty(), key = { it.id }) { route ->
                        val open = selected == route.id
                        Column(Modifier.fillMaxWidth().clickable { selected = if (open) null else route.id; reverse = false }
                            .padding(horizontal = 16.dp, vertical = 10.dp).testTag("go_route_${route.id}")) {
                            Text(route.name, style = MaterialTheme.typography.titleSmall)
                            Text(listOfNotNull(
                                String.format(Locale.getDefault(), "%.1f km", route.distanceMeters / 1000),
                                "${(route.durationSeconds / 60).toInt()} min",
                                route.area,
                                String.format(Locale.getDefault(), "starts %.1f km away", catchDistance(route.start, around) / 1000),
                            ).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (open) {
                                if (route.reversible == 1) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = reverse, onCheckedChange = { reverse = it })
                                    Text("Walk it in reverse", style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    Button(onClick = { use(route.id) { onWalk(it, reverse && it.canReverse) } }, enabled = !loadingDetail,
                                        modifier = Modifier.weight(1f).testTag("go_route_walk")) { Text("Walk it as-is") }
                                    OutlinedButton(onClick = { use(route.id, onGuide) }, enabled = !loadingDetail,
                                        modifier = Modifier.weight(1f).testTag("go_route_guide")) { Text("Use as guide") }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
