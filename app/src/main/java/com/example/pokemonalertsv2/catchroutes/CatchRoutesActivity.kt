package com.example.pokemonalertsv2.catchroutes

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

class CatchRoutesActivity : ComponentActivity() {
    internal val model: CatchRoutesViewModel by viewModels()
    private var pip by mutableStateOf(false)
    private var pendingFollow = false
    private var locating: CancellationTokenSource? = null
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (fineLocation()) {
            if (pendingFollow) {
                pendingFollow = false
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    model.report("Enable notifications to keep route guidance accessible while playing.")
                else model.follow()
            } else useLocation()
        } else model.report("Precise location is needed for live guidance. Map-picked routes can still be planned.")
    }
    private fun fineLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun permissionNames(): Array<String> = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) +
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    private fun useLocation(automatic: Boolean = false) {
        if (!fineLocation()) { permissions.launch(permissionNames()); return }
        locating?.cancel()
        val token = CancellationTokenSource().also { locating = it }
        try {
            LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                .addOnSuccessListener { l ->
                    if (automatic && model.hasStart) return@addOnSuccessListener
                    if (l != null && l.accuracy <= 50) model.startPoint(CatchPoint(l.latitude, l.longitude)) else model.report("Location unavailable. Pick a start on the map.")
                }
                .addOnFailureListener { model.report("Location unavailable. Pick a start on the map.") }
        } catch (_: SecurityException) { model.report("Location permission changed. Pick a start on the map.") }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { PokemonAlertsV2Theme { PlannerScreen() } }
        lifecycleScope.launch {
            model.controller.ready()
            if (!model.hasStart && model.controller.session.value == null && fineLocation()) useLocation(automatic = true)
            if (model.controller.session.value?.finished == false && fineLocation()) {
                ContextCompat.startForegroundService(this@CatchRoutesActivity, Intent(this@CatchRoutesActivity, CatchRouteService::class.java))
            }
        }
    }
    override fun onDestroy() { locating?.cancel(); super.onDestroy() }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig); pip = isInPictureInPictureMode
    }
    private fun smallMap() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(4, 3)).build()) }
                .onFailure { model.report("Small map is unavailable on this device.") }
        } else model.report("This device does not support picture-in-picture.")
    }
    private fun pickTime() {
        val current = if (model.useNow) ZonedDateTime.now() else Instant.ofEpochMilli(model.settings.startAtMillis).atZone(ZoneId.systemDefault())
        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                val local = LocalDateTime.of(year, month + 1, day, hour, minute)
                val offsets = ZoneId.systemDefault().rules.getValidOffsets(local)
                if (offsets.isEmpty()) model.report("That time does not exist because clocks change. Choose another time.")
                else model.schedule(local.toInstant(offsets.first()).toEpochMilli())
            }, current.hour, current.minute, true).show()
        }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable private fun PlannerScreen() {
        val active by model.controller.session.collectAsStateWithLifecycle()
        val location by model.controller.location.collectAsStateWithLifecycle()
        val notice by model.controller.message.collectAsStateWithLifecycle()
        val refreshing by model.controller.recalculating.collectAsStateWithLifecycle()
        val saved by model.store.setups.collectAsStateWithLifecycle(emptyList())
        var advanced by remember { mutableStateOf(false) }
        var picking by remember { mutableStateOf("start") }
        var showSaved by remember { mutableStateOf(false) }
        var details by remember { mutableStateOf<List<CatchEncounter>?>(null) }
        var deleteSetup by remember { mutableStateOf<CatchSetupEntity?>(null) }
        var mapFailed by remember { mutableStateOf(false) }
        var mapView by remember { mutableStateOf<CatchRouteMapView?>(null) }
        LaunchedEffect(pip) { delay(500); mapView?.fit() }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
        val plan = active?.itinerary ?: model.itinerary
        val settings = active?.itinerary?.settings ?: model.settings
        val encounters = active?.remaining ?: plan?.encounters.orEmpty()
        val groups = remember(encounters) { groupCatchEncounters(encounters) }
        val formState = rememberLazyListState()
        LaunchedEffect(model.itinerary) {
            if (model.itinerary != null && active == null) formState.animateScrollToItem(1 + if (showSaved) saved.size else 0)
        }
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(if (pip) PaddingValues(0.dp) else padding)) {
                if (!pip) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { finish() }) { Text("Back") }
                    Text("Catch routes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = ::smallMap, enabled = plan != null) { Text("Small map") }
                }
                Box(Modifier.fillMaxWidth().weight(if (pip) 1f else 0.85f)) {
                    AndroidView(factory = { ctx -> CatchRouteMapView(ctx).also { mapView = it; it.onFailure = { mapFailed = true } } },
                        modifier = Modifier.fillMaxSize().testTag("catch_route_map"),
                        update = { view ->
                            view.onPick = if (active == null) { p -> if (picking == "end") model.edit(model.settings.copy(end = p)) else model.startPoint(p) } else null
                            view.update(settings, plan, location)
                        }, onRelease = { it.destroy() })
                    if (!pip) TextButton(onClick = { mapView?.fit() }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text("Fit route") }
                    if (mapFailed) Surface(Modifier.align(Alignment.Center)) { Text("Map unavailable. Check connection.", Modifier.padding(12.dp)) }
                    if (pip) Surface(Modifier.align(Alignment.TopCenter)) { Text("${encounters.size} potential · ${active?.caught ?: 0} caught", Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall) }
                    else if (active == null) Surface(Modifier.align(Alignment.BottomCenter).padding(8.dp), tonalElevation = 4.dp, shape = MaterialTheme.shapes.small) {
                        Text("Tap map to set $picking", Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (!pip) LazyColumn(Modifier.fillMaxWidth().weight(1.15f).padding(horizontal = 16.dp), state = formState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    if (active != null) {
                        item {
                            Text(active!!.itinerary.settings.name, style = MaterialTheme.typography.titleLarge)
                            if (active!!.finished) Text("Session finished", style = MaterialTheme.typography.titleMedium)
                            Text("${active!!.visits.count { !it.skipped }} visited · ${active!!.caught} caught · ${encounters.size} remaining")
                            if (active!!.needsRefresh && !active!!.finished) Text("Timing needs refreshing before visits resume.", color = MaterialTheme.colorScheme.error)
                            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { model.controller.pause() }, enabled = !active!!.finished) { Text(if (active!!.paused) "Resume" else "Pause") }
                                OutlinedButton(onClick = { model.controller.caught(1) }) { Text("+ Catch") }
                                TextButton(onClick = { model.controller.caught(-1) }) { Text("− Catch") }
                                TextButton(onClick = { model.controller.skip() }) { Text("Skip group") }
                                TextButton(onClick = { model.controller.undo() }) { Text("Undo") }
                                TextButton(onClick = { model.controller.recalculate() }, enabled = !refreshing && !active!!.finished) { Text("Recalculate") }
                                TextButton(onClick = { lifecycleScope.launch { model.controller.stop() } }) { Text("Stop route") }
                                TextButton(onClick = {
                                    if (!Settings.canDrawOverlays(this@CatchRoutesActivity)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                                    else startService(Intent(this@CatchRoutesActivity, CatchRouteService::class.java).setAction("map"))
                                }) { Text("Floating map") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = settings.spacialRend, onCheckedChange = { model.controller.setRadius(it) }); Text("Spacial Rend · ${settings.radius.toInt()} m") }
                        }
                    } else {
                        item {
                            OutlinedTextField(value = settings.name, onValueChange = { model.edit(settings.copy(name = it.take(80))) }, label = { Text("Route name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { useLocation() }) { Text("Use location") }
                                FilterChip(selected = picking == "start", onClick = { picking = "start" }, label = { Text("Pick start") })
                                if (settings.finish == CatchFinish.PIN) FilterChip(selected = picking == "end", onClick = { picking = "end" }, label = { Text("Pick finish") })
                            }
                            Text(if (model.hasStart) "Start: ${coordinate(settings.start)}" else "Choose your starting location", style = MaterialTheme.typography.bodySmall)
                            if (settings.finish == CatchFinish.PIN) Text("Finish: ${settings.end?.let(::coordinate) ?: "Tap map to choose"}", style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CatchFinish.entries.forEach { finish -> FilterChip(selected = settings.finish == finish, onClick = { model.edit(settings.copy(finish = finish)); picking = if (finish == CatchFinish.PIN) "end" else "start" }, label = { Text(when (finish) { CatchFinish.ROUND_TRIP -> "Return to start"; CatchFinish.ANYWHERE -> "Finish anywhere"; CatchFinish.PIN -> "Finish at pin" }) }) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = settings.durationMinutes.toString(), onValueChange = { model.edit(settings.copy(durationMinutes = it.toIntOrNull() ?: 0)) }, label = { Text("Minutes (10–360)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                                TextButton(onClick = ::pickTime) { Text(if (model.useNow) "Schedule" else time(settings.startAtMillis)) }
                                if (!model.useNow) TextButton(onClick = { model.schedule(null) }) { Text("Start now") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = settings.spacialRend, onCheckedChange = { model.edit(settings.copy(spacialRend = it)) }); Text("Spacial Rend · ${settings.radius.toInt()} m") }
                            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced" else "Advanced settings") }
                            if (advanced) {
                                Text("Walking pace: ${String.format(Locale.getDefault(), "%.1f", settings.speedMps * 3.6)} km/h")
                                Slider(value = settings.speedMps.toFloat(), onValueChange = { model.edit(settings.copy(speedMps = it.toDouble())) }, valueRange = 0.5f..2.5f)
                                CatchPrediction.entries.forEach { prediction ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = settings.prediction == prediction, onClick = { model.edit(settings.copy(prediction = prediction)) })
                                        Text(when (prediction) { CatchPrediction.THIRTY_MINUTES -> "Assume 30-minute spawns"; CatchPrediction.SIXTY_MINUTES -> "Assume 60-minute spawns"; CatchPrediction.SUPPORTED_ONLY -> "Supported windows only" })
                                    }
                                }
                                Text("Despawn time alone does not establish spawn time. Assumptions are predictions, not live confirmation.", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Potential encounters · walking time only · no planned waiting", style = MaterialTheme.typography.bodySmall)
                            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("catch_route_error")) }
                            if (model.retryAt > now) Text("Retry in ${(model.retryAt - now) / 1000 + 1}s")
                            if (model.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(model.progress); TextButton(onClick = model::cancel) { Text("Cancel") } }
                            else Button(onClick = model::generate, modifier = Modifier.fillMaxWidth().testTag("generate_catch_route"), enabled = model.retryAt <= now) { Text("Generate route") }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { model.save() }, enabled = model.hasStart) { Text("Save setup") }
                                TextButton(onClick = { model.save(true) }, enabled = model.hasStart) { Text("Duplicate") }
                                TextButton(onClick = { showSaved = !showSaved }) { Text("Saved (${saved.size})") }
                            }
                            if (!model.busy && model.progress.isNotBlank()) Text(model.progress, style = MaterialTheme.typography.bodySmall)
                        }
                        if (showSaved) items(saved, key = { it.id }) { setup ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { model.load(setup); showSaved = false }, modifier = Modifier.weight(1f)) { Text(setup.name) }
                                TextButton(onClick = { deleteSetup = setup }) { Text("Delete") }
                            }
                        }
                    }
                    if (plan != null) {
                        item {
                            HorizontalDivider()
                            Text("${encounters.size} potential encounters", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("catch_route_result"))
                            Text("${encounters.count { it.opportunity.observed }} observed · ${encounters.count { !it.opportunity.observed }} predicted")
                            Text("${String.format(Locale.getDefault(), "%.2f", plan.distanceMeters / 1000)} km · finish ${time(plan.finishAtMillis)}")
                            plan.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (active == null) Button(onClick = {
                                if (!fineLocation() || (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@CatchRoutesActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) { pendingFollow = true; permissions.launch(permissionNames()) }
                                else model.follow()
                            }, modifier = Modifier.fillMaxWidth().testTag("follow_catch_route")) { Text("Start guidance · replace current journey") }
                        }
                        items(groups) { group ->
                            OutlinedCard(onClick = { details = group }, modifier = Modifier.fillMaxWidth()) {
                                val first = group.first()
                                Column(Modifier.padding(12.dp)) {
                                    Text("${group.size} Pokémon opportunities · ${time(first.arrivalMillis)}", style = MaterialTheme.typography.titleSmall)
                                    val margin = group.minOf { it.opportunity.despawnAt - it.arrivalMillis } / 1000
                                    Text("${first.meters.toInt()} m along route · ${margin / 60}m ${margin % 60}s minimum expiry margin", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        details?.let { group -> AlertDialog(onDismissRequest = { details = null }, confirmButton = { TextButton(onClick = { details = null }) { Text("Close") } }, title = { Text("Spawn timing evidence") }, text = {
            LazyColumn { items(group) { e -> Column(Modifier.padding(bottom = 12.dp)) {
                Text("${time(e.opportunity.availableFrom)}–${time(e.opportunity.despawnAt)} · ${e.opportunity.basis.replace('_', ' ')}")
                Text("Source: ${e.opportunity.source ?: "unknown"} · expiry evidence: ${e.opportunity.despawnBasis ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
                Text("Catalogue: ${e.opportunity.catalogueSeenAt ?: "unknown"}\nLast live observation: ${e.opportunity.liveLastSeenAt ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
                if (e.opportunity.timingConflict) Text("Conflicting timing evidence", color = MaterialTheme.colorScheme.error)
                plan?.sources?.firstOrNull { it.source == e.opportunity.source }?.let { source ->
                    Text("${source.coverageKind ?: "Unknown coverage"} · refreshed ${source.refreshedAt ?: "unknown"} · ${source.returned ?: "?"} returned / ${source.dropped ?: "?"} dropped", style = MaterialTheme.typography.bodySmall)
                }
                e.opportunity.lowerBoundSeconds?.let { Text("Observed duration lower bound: ${it / 60} minutes. Not an exact start.") }
                Text(e.opportunity.uncertainty.joinToString("\n") { it.replace('_', ' ') }, style = MaterialTheme.typography.bodySmall)
            } } }
        }) }
        deleteSetup?.let { setup -> AlertDialog(onDismissRequest = { deleteSetup = null }, title = { Text("Delete ${setup.name}?") }, confirmButton = { TextButton(onClick = { model.delete(setup); deleteSetup = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteSetup = null }) { Text("Cancel") } }) }
    }
    private fun coordinate(p: CatchPoint) = String.format(Locale.US, "%.5f, %.5f", p.latitude, p.longitude)
    private fun time(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
}

internal fun groupCatchEncounters(encounters: List<CatchEncounter>): List<List<CatchEncounter>> {
    val groups = mutableListOf<MutableList<CatchEncounter>>()
    encounters.forEach { e ->
        val group = groups.lastOrNull()
        if (group == null || e.meters - group.first().meters > 25 || e.arrivalMillis - group.first().arrivalMillis > 30_000) groups += mutableListOf(e) else group += e
    }
    return groups
}
