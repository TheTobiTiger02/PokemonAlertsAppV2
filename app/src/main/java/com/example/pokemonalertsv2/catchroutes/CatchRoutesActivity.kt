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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.Color
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
import com.example.pokemonalertsv2.data.PokemonAlertsApi
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
        var editing by remember { mutableStateOf(false) }
        var areaOpen by remember { mutableStateOf(false) }
        var details by remember { mutableStateOf<List<SpawnpointSelection>?>(null) }
        val spawnpointDetails = remember { SpawnAvailabilityRepository(PokemonAlertsApi.catchRoutesService) }
        var deleteSetup by remember { mutableStateOf<CatchSetupEntity?>(null) }
        var mapFailed by remember { mutableStateOf(false) }
        var mapView by remember { mutableStateOf<CatchRouteMapView?>(null) }
        LaunchedEffect(pip) { delay(500); mapView?.fit() }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
        val plan = active?.displayItinerary ?: model.itinerary
        val settings = active?.itinerary?.settings ?: model.settings
        val encounters = active?.remaining ?: plan?.encounters.orEmpty()
        // Same grouping and order as the numbered stops on the map.
        val stops = remember(plan) { catchStops(plan?.encounters.orEmpty()) }
        val formState = rememberLazyListState()
        LaunchedEffect(model.itinerary) { if (model.itinerary != null) { editing = false; formState.scrollToItem(0) } }
        val showSettings = active == null && (plan == null || editing)
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(if (pip) PaddingValues(0.dp) else padding)) {
                if (!pip) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { finish() }) { Text("Back") }
                    Text("Catch routes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = ::smallMap, enabled = plan != null) { Text("Small map") }
                }
                Box(Modifier.fillMaxWidth().weight(if (pip) 1f else if (showSettings) 0.8f else 1.25f)) {
                    AndroidView(factory = { ctx -> CatchRouteMapView(ctx).also { mapView = it; it.onFailure = { mapFailed = true } } },
                        modifier = Modifier.fillMaxSize().testTag("catch_route_map"),
                        update = { view ->
                            view.onPick = if (active == null && showSettings) { p -> if (picking == "end") model.edit(model.settings.copy(end = p)) else model.startPoint(p) } else null
                            // A tap on a spawnpoint opens its details; up to five when they overlap.
                            view.onSpawnpointTap = { ids ->
                                val byId = spawnpointSelections(plan?.encounters.orEmpty()).associateBy { it.pointId }
                                details = ids.mapNotNull { byId[it] }.take(5).ifEmpty { null }
                            }
                            view.update(settings, plan, location, active?.progressMeters)
                            view.select(details?.map { it.pointId }?.toSet().orEmpty())
                        }, onRelease = { it.destroy() })
                    if (!pip && plan != null) FilledTonalButton(onClick = { mapView?.fit() }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Fit") }
                    if (mapFailed) Surface(Modifier.align(Alignment.Center)) { Text("Map unavailable. Check connection.", Modifier.padding(12.dp)) }
                    if (pip) Surface(Modifier.align(Alignment.TopCenter)) { Text("${active?.availabilityReadout ?: "${encounters.size} potential"} · ${active?.caught ?: 0} caught", Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall) }
                    else if (showSettings) Surface(Modifier.align(Alignment.BottomCenter).padding(8.dp), tonalElevation = 4.dp, shape = MaterialTheme.shapes.small) {
                        Text(if (picking == "end") "Tap the map to set the finish" else "Tap the map to set the start", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (!pip) LazyColumn(Modifier.testTag("catch_route_form").fillMaxWidth().weight(1f).padding(horizontal = 16.dp), state = formState,
                    verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)) {
                    if (active != null) item { SessionCard(active!!, settings, refreshing, notice) }
                    else if (plan != null && !editing) item {
                        RouteSummary(plan, encounters, onStart = {
                            if (!fineLocation() || (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@CatchRoutesActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) { pendingFollow = true; permissions.launch(permissionNames()) }
                            else model.follow()
                        }, onEdit = { editing = true })
                        model.recommendation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    }
                    if (showSettings) item {
                        RouteSettingsForm(settings, saved.size, advanced, picking, showSaved,
                            onAdvanced = { advanced = !advanced }, onPicking = { picking = it }, onShowSaved = { showSaved = !showSaved },
                            onArea = { areaOpen = true }, onBack = if (plan != null) { { editing = false } } else null)
                    }
                    if (showSettings && showSaved) items(saved, key = { it.id }) { setup ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { model.load(setup); showSaved = false }, modifier = Modifier.weight(1f)) { Text(setup.name) }
                            TextButton(onClick = { deleteSetup = setup }) { Text("Delete") }
                        }
                    }
                    if (plan != null && !showSettings) {
                        item { Text(if (active?.needsRefresh == true) "Timing needs refresh" else "Stops", style = MaterialTheme.typography.titleMedium) }
                        itemsIndexed(stops) { index, group ->
                            val visited = active != null && group.all { e -> active!!.wasVisited(e.opportunity) }
                            val previousMeters = stops.getOrNull(index - 1)?.first()?.meters ?: 0.0
                            StopRow(index + 1, group, previousMeters, plan, settings, visited, next = active != null && !visited &&
                                stops.take(index).all { g -> g.all { e -> active!!.wasVisited(e.opportunity) } },
                                modifier = Modifier.testTag("catch_stop_$index")) { details = spawnpointSelections(group) }
                        }
                    }
                }
            }
        }
        details?.let { selections ->
            SpawnpointDetailSheet(selections, plan, loadDetail = { spawnpointDetails.detail(it) }, onDismiss = { details = null })
        }
        if (areaOpen) CatchAreaPicker(initial = settings.area, center = settings.start, title = "Route area",
            onDismiss = { areaOpen = false }, onDone = { model.edit(model.settings.copy(area = it)); areaOpen = false })
        deleteSetup?.let { setup -> AlertDialog(onDismissRequest = { deleteSetup = null }, title = { Text("Delete ${setup.name}?") }, confirmButton = { TextButton(onClick = { model.delete(setup); deleteSetup = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteSetup = null }) { Text("Cancel") } }) }
    }

    /** The planned route at a glance: what it catches, how far, when you are back, and the two things to do next. */
    @Composable private fun RouteSummary(plan: CatchItinerary, encounters: List<CatchEncounter>, onStart: () -> Unit, onEdit: () -> Unit) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${encounters.size}", style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("catch_route_result"))
                    Text(" Pokémon", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.weight(1f))
                    Text("about ${String.format(Locale.getDefault(), "%.1f", encounters.sumOf { it.opportunity.expectedCatch })} expected",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                }
                val minutes = (plan.finishAtMillis - plan.settings.startAtMillis) / 60_000
                Text("${String.format(Locale.getDefault(), "%.1f", plan.distanceMeters / 1000)} km · $minutes min · back ${time(plan.finishAtMillis)}" +
                    if (plan.waits.isNotEmpty()) " · ${plan.waits.size} short waits" else "", style = MaterialTheme.typography.titleSmall)
                val events = encounters.count { it.opportunity.eventOnly }
                Text("${encounters.count { it.opportunity.observed }} seen live · ${encounters.count { !it.opportunity.observed }} predicted" +
                    (if (events > 0) " · $events event" else "") + (if (plan.settings.area.isArea()) " · inside your area" else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (plan.warnings.isNotEmpty()) {
                    var open by remember { mutableStateOf(false) }
                    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) { Text(if (open) "Hide notes" else "${plan.warnings.size} notes about the data") }
                    if (open) plan.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStart, modifier = Modifier.weight(1f).testTag("follow_catch_route")) { Text("Start guidance") }
                    OutlinedButton(onClick = onEdit) { Text("Edit route") }
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable private fun SessionCard(session: CatchSession, settings: CatchRouteSettings, refreshing: Boolean, notice: String?) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(session.itinerary.settings.name, style = MaterialTheme.typography.titleLarge)
                if (session.finished) Text("Session finished", style = MaterialTheme.typography.titleMedium)
                Text("${session.visits.count { !it.skipped }} visited · ${session.caught} caught · ${session.availabilityReadout}")
                if (session.needsRefresh && !session.finished) Text("Timing needs refreshing before visits resume.", color = MaterialTheme.colorScheme.error)
                if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.controller.caught(1) }, modifier = Modifier.weight(1f)) { Text("+ Catch") }
                    OutlinedButton(onClick = { model.controller.pause() }, enabled = !session.finished) { Text(if (session.paused) "Resume" else "Pause") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { model.controller.caught(-1) }) { Text("− Catch") }
                    TextButton(onClick = { model.controller.skip() }) { Text("Skip stop") }
                    TextButton(onClick = { model.controller.undo() }) { Text("Undo") }
                    TextButton(onClick = { model.controller.recalculate() }, enabled = !refreshing && !session.finished) { Text("Recalculate") }
                    TextButton(onClick = {
                        if (!Settings.canDrawOverlays(this@CatchRoutesActivity)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        else startService(Intent(this@CatchRoutesActivity, CatchRouteService::class.java).setAction("map"))
                    }) { Text("Floating map") }
                    TextButton(onClick = { lifecycleScope.launch { model.controller.stop() } }) { Text("Stop route") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = settings.spacialRend, onCheckedChange = { model.controller.setRadius(it) }); Spacer(Modifier.width(8.dp)); Text("Spacial Rend · ${settings.radius.toInt()} m") }
            }
        }
    }

    /** One numbered stop: when you get there, what it holds, how far from the previous stop, and any wait. */
    @Composable private fun StopRow(number: Int, group: List<CatchEncounter>, previousMeters: Double, plan: CatchItinerary,
        settings: CatchRouteSettings, visited: Boolean, next: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
        val first = group.first()
        val wait = plan.waits.firstOrNull { kotlin.math.abs(it.meters - first.meters) <= settings.radius * 2 }
        val badge = when { next -> Color(0xFFE8710A); visited -> Color(0xFF9AA3B2); else -> Color(0xFF1E4FD8) }
        Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, tonalElevation = if (next) 3.dp else 1.dp, modifier = modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).background(badge, CircleShape), contentAlignment = Alignment.Center) {
                    Text("$number", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val events = group.count { it.opportunity.eventOnly }
                    Text("${time(first.arrivalMillis)} · ${group.size} Pokémon" + if (events > 0) " · $events event" else "",
                        style = MaterialTheme.typography.titleSmall, color = if (visited) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
                    val walk = (first.meters - previousMeters).coerceAtLeast(0.0)
                    val margin = group.minOf { it.opportunity.despawnAt - it.arrivalMillis } / 60_000
                    Text("${walk.toInt()} m walk · earliest despawn in $margin min" +
                        (wait?.let { " · wait ${it.millis / 60_000}:${String.format(Locale.US, "%02d", it.millis / 1000 % 60)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (visited) "Done" else "Details", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    /** Where, when and what: the route settings, grouped, with the recommendation shortcuts beside the time. */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable private fun RouteSettingsForm(settings: CatchRouteSettings, savedCount: Int, advanced: Boolean, picking: String, showSaved: Boolean,
        onAdvanced: () -> Unit, onPicking: (String) -> Unit, onShowSaved: () -> Unit, onArea: () -> Unit, onBack: (() -> Unit)?) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ Back to route") }
            SettingsCard("Where") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { useLocation() }, label = { Text("Use my location") })
                    FilterChip(selected = picking == "start", onClick = { onPicking("start") }, label = { Text("Pick start on map") })
                }
                Text(if (model.hasStart) "Start ${coordinate(settings.start)}" else "Choose where you start", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CatchFinish.entries.forEach { finish -> FilterChip(selected = settings.finish == finish, onClick = { model.edit(settings.copy(finish = finish)); onPicking(if (finish == CatchFinish.PIN) "end" else "start") }, label = { Text(when (finish) { CatchFinish.ROUND_TRIP -> "Back to start"; CatchFinish.ANYWHERE -> "End anywhere"; CatchFinish.PIN -> "End at pin" }) }) }
                }
                if (settings.finish == CatchFinish.PIN) Text("Finish ${settings.end?.let(::coordinate) ?: "· tap the map"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (settings.area.isArea()) "Stays inside your area (${settings.area.size} corners)" else "No area limit", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (settings.area.isArea()) TextButton(onClick = { model.edit(settings.copy(area = emptyList())) }) { Text("Remove") }
                    OutlinedButton(onClick = onArea) { Text(if (settings.area.isArea()) "Edit area" else "Limit to area") }
                }
            }
            SettingsCard("When") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = model.useNow, onClick = { model.schedule(null) }, label = { Text("Now") })
                    FilterChip(selected = !model.useNow, onClick = ::pickTime, label = { Text(if (model.useNow) "Later…" else time(settings.startAtMillis)) })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                    listOf(30, 60, 90, 120).forEach { minutes -> FilterChip(selected = settings.durationMinutes == minutes, onClick = { model.edit(settings.copy(durationMinutes = minutes)) }, label = { Text("$minutes min") }) }
                    OutlinedTextField(value = settings.durationMinutes.toString(), onValueChange = { model.edit(settings.copy(durationMinutes = it.toIntOrNull() ?: 0)) },
                        label = { Text("Minutes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.width(110.dp))
                }
                Text("Not sure? Let the app compare:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = model::recommendStart, enabled = !model.busy, modifier = Modifier.weight(1f).testTag("recommend_start")) { Text("Best start spot") }
                    OutlinedButton(onClick = model::recommendTime, enabled = !model.busy && model.hasStart, modifier = Modifier.weight(1f).testTag("recommend_time")) { Text("Best start time") }
                }
            }
            SettingsCard("What") {
                SwitchRow("Event spawns", "Spawnpoints that only spawn during Spotlight Hours, Community Days and similar events", settings.includeEventSpawns) { model.edit(settings.copy(includeEventSpawns = it)) }
                SwitchRow("Spacial Rend", "Catch range ${settings.radius.toInt()} m", settings.spacialRend) { model.edit(settings.copy(spacialRend = it)) }
                TextButton(onClick = onAdvanced, contentPadding = PaddingValues(0.dp)) { Text(if (advanced) "Hide advanced" else "Advanced settings") }
                if (advanced) {
                    Text("Walking pace: ${String.format(Locale.getDefault(), "%.1f", settings.speedMps * 3.6)} km/h")
                    Slider(value = settings.speedMps.toFloat(), onValueChange = { model.edit(settings.copy(speedMps = it.toDouble())) }, valueRange = 0.5f..2.5f)
                    CatchPrediction.entries.forEach { prediction ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = settings.prediction == prediction, onClick = { model.edit(settings.copy(prediction = prediction)) })
                            Text(when (prediction) { CatchPrediction.AUTOMATIC -> "Automatic (learned timing)"; CatchPrediction.THIRTY_MINUTES -> "Assume 30-minute spawns"; CatchPrediction.SIXTY_MINUTES -> "Assume 60-minute spawns"; CatchPrediction.SUPPORTED_ONLY -> "Supported windows only" })
                        }
                    }
                    Text("Wait at a spawnpoint for it to appear", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 2, 5, 10).forEach { minutes ->
                            FilterChip(selected = settings.maxWaitMinutes == minutes, onClick = { model.edit(settings.copy(maxWaitMinutes = minutes)) },
                                label = { Text(if (minutes == 0) "Never" else "Up to $minutes min") })
                        }
                    }
                    OutlinedTextField(value = settings.name, onValueChange = { model.edit(settings.copy(name = it.take(80))) }, label = { Text("Route name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("catch_route_error")) }
            if (model.retryAt > now()) Text("Retry in ${(model.retryAt - now()) / 1000 + 1}s")
            if (model.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(model.progress, style = MaterialTheme.typography.bodySmall); TextButton(onClick = model::cancel) { Text("Cancel") } }
            else Button(onClick = model::generate, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("generate_catch_route"), enabled = model.retryAt <= now()) { Text("Generate route") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { model.save() }, enabled = model.hasStart) { Text("Save setup") }
                TextButton(onClick = { model.save(true) }, enabled = model.hasStart) { Text("Duplicate") }
                TextButton(onClick = onShowSaved) { Text(if (showSaved) "Hide saved" else "Saved ($savedCount)") }
            }
            if (!model.busy && model.progress.isNotBlank()) Text(model.progress, style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                content()
            }
        }
    }

    @Composable private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    private fun now() = System.currentTimeMillis()
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
