package com.example.pokemonalertsv2.catchroutes

import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.pokemonalertsv2.data.PokemonAlertsApi
import com.example.pokemonalertsv2.ui.alerts.mapchrome.MapFitButton
import com.example.pokemonalertsv2.ui.alerts.mapchrome.MapLocateButton
import com.example.pokemonalertsv2.ui.alerts.mapchrome.MapLocateState
import com.example.pokemonalertsv2.ui.alerts.mapchrome.MapPillBar
import com.example.pokemonalertsv2.ui.alerts.mapchrome.MapPillTitle
import com.example.pokemonalertsv2.ui.alerts.mapchrome.TopBarHeight
import com.example.pokemonalertsv2.ui.theme.Spacing
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.first
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues

class CatchRoutesActivity : ComponentActivity() {
    internal val model: CatchRoutesViewModel by viewModels()
    private var pendingFollow = false
    private var locating: CancellationTokenSource? = null
    /** A finished route's summary to show, from the controller or from a notification tap. */
    private var summary by mutableStateOf<CatchRouteSummary?>(null)
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
                    if (l != null && l.accuracy <= 50) model.startPoint(CatchPoint(l.latitude, l.longitude), "Your location") else model.report("Location unavailable. Pick a start on the map.")
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
        handle(intent)
        lifecycleScope.launch {
            model.controller.lastSummary.collect { if (it != null) { summary = it; model.controller.lastSummary.value = null } }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
    /** A tap on "Route finished" opens its summary; "Walk again" in the history loads its settings. */
    private fun handle(intent: Intent?) {
        val summaryId = intent?.getStringExtra(CatchRouteNotifications.EXTRA_SUMMARY_ID)
        val againId = intent?.getStringExtra(CatchRouteHistoryActivity.EXTRA_WALK_AGAIN_ID)
        if (summaryId == null && againId == null) return
        intent.removeExtra(CatchRouteNotifications.EXTRA_SUMMARY_ID)
        intent.removeExtra(CatchRouteHistoryActivity.EXTRA_WALK_AGAIN_ID)
        lifecycleScope.launch {
            val history = model.store.history.first()
            summaryId?.let { id -> history.firstOrNull { it.id == id }?.let { summary = it } }
            againId?.let { id -> history.firstOrNull { it.id == id }?.let { walkAgain(it) } }
        }
    }
    private fun walkAgain(previous: CatchRouteSummary) {
        if (model.controller.session.value?.finished == false) { model.report("Stop the current route before walking another."); return }
        lifecycleScope.launch { if (model.controller.session.value != null) model.controller.stop() }
        model.walkAgain(previous.settings)
    }
    override fun onDestroy() { locating?.cancel(); super.onDestroy() }

    /**
     * The floating window is the one way to follow a route outside the app. Unlike
     * picture-in-picture it can be moved and resized, and it does not put a visible task on
     * screen, which is what hides the promoted status-bar chip.
     */
    private fun floatingWindow() {
        if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        else startService(Intent(this, CatchRouteService::class.java).setAction("map"))
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
        val outOfDate by model.controller.outOfDate.collectAsStateWithLifecycle()
        val saved by model.store.setups.collectAsStateWithLifecycle(emptyList())
        var picking by remember { mutableStateOf("start") }
        var editing by remember { mutableStateOf(false) }
        var areaOpen by remember { mutableStateOf(false) }
        var goRoutesOpen by remember { mutableStateOf(false) }
        var details by remember { mutableStateOf<List<SpawnpointSelection>?>(null) }
        val spawnpointDetails = remember { SpawnAvailabilityRepository(PokemonAlertsApi.catchRoutesService) }
        var deleteSetup by remember { mutableStateOf<CatchSetupEntity?>(null) }
        var mapFailed by remember { mutableStateOf(false) }
        var mapView by remember { mutableStateOf<CatchRouteMapView?>(null) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
        val plan = active?.itinerary ?: model.itinerary
        val settings = active?.itinerary?.settings ?: model.settings
        val encounters = active?.remaining ?: plan?.encounters.orEmpty()
        // Same grouping and order as the numbered stops on the map.
        val stops = remember(plan) { catchStops(plan?.encounters.orEmpty()) }
        LaunchedEffect(model.itinerary) { if (model.itinerary != null) editing = false }
        // While walking a route the map follows the walk; planning shows the whole route.
        val walking = active != null && active?.finished != true
        LaunchedEffect(mapView, walking) { if (walking) mapView?.follow() }
        val showSettings = active == null && (plan == null || editing)
        val startGuidance: () -> Unit = {
            if (!fineLocation() || (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@CatchRoutesActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) { pendingFollow = true; permissions.launch(permissionNames()) }
            else model.follow()
        }
        val plannerState = PlannerState(settings, plan, active, stops, encounters, saved, refreshing, outOfDate, notice, now)
        val actions = PlannerActions(
            useLocation = { useLocation() },
            pickTime = ::pickTime,
            pickOnMap = { finish -> picking = if (finish) "end" else "start" },
            openArea = { areaOpen = true },
            openGoRoutes = { goRoutesOpen = true },
            startGuidance = { startGuidance() },
            setEditing = { editing = it },
            floatingWindow = ::floatingWindow,
            openInMaps = ::openInGoogleMaps,
            stop = { lifecycleScope.launch { model.controller.stop() } },
            showStop = { details = spawnpointSelections(it) },
            deleteSetup = { deleteSetup = it },
        )
        // Follow's state, so the button shows when a pan has paused it.
        var following by remember { mutableStateOf(false) }
        val density = LocalDensity.current
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val routeMap: PlannerMap = { mapModifier, frame ->
            Box(mapModifier) {
                AndroidView(factory = { ctx -> CatchRouteMapView(ctx).also { view ->
                        mapView = view; view.onFailure = { mapFailed = true }; view.onFollowChanged = { following = it }
                    } },
                    modifier = Modifier.fillMaxSize().testTag("catch_route_map"),
                    update = { view ->
                        // Framing stays clear of the floating top bar and the collapsed sheet.
                        with(density) { view.setInsets(0, (statusBar + Spacing.xs + TopBarHeight + Spacing.sm).roundToPx(), 0, frame.bottomInset.roundToPx()) }
                        // A route walked as drawn fixes its start; only its finish-free walk is left to tap.
                        view.onPick = if (active == null && showSettings && !settings.fixed) { p -> if (picking == "end") model.edit(model.settings.copy(end = p)) else model.startPoint(p) } else null
                        // A tap on a spawnpoint opens its details; up to five when they overlap.
                        view.onSpawnpointTap = { ids ->
                            val byId = spawnpointSelections(plan?.encounters.orEmpty()).associateBy { it.pointId }
                            details = ids.mapNotNull { byId[it] }.take(5).ifEmpty { null }
                        }
                        view.update(settings, plan, location, active?.progressMeters, active?.takeIf { walking }?.nextAt(now))
                        // Walking: only what is still ahead. Planning: every planned spawnpoint.
                        view.setSpawns(active?.takeIf { walking }?.ahead(now)?.map { it.opportunity to (now >= it.opportunity.availableFrom) })
                        view.setNextStop(active?.takeIf { walking }?.let { catchNextStopIndex(it, now) })
                        view.select(details?.map { it.pointId }?.toSet().orEmpty())
                    }, onRelease = { it.destroy() })
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MapPillBar(Modifier.testTag("catch_route_top_bar")) {
                        IconButton(onClick = { finish() }, modifier = Modifier.padding(start = Spacing.xxs)) {
                            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        val (title, subtitle) = catchTopBarText(plan, active, stops, encounters.size, editing, now)
                        MapPillTitle(title, subtitle, Modifier.weight(1f))
                        val session = active
                        if (session != null) {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }, modifier = Modifier.padding(end = Spacing.xxs).testTag("catch_session_menu")) {
                                    Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = "Route actions")
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    WalkingMenuItems(model, session, plannerState, actions, frame.openOptions) { menu = false }
                                }
                            }
                        } else IconButton(onClick = { startActivity(Intent(this@CatchRoutesActivity, CatchRouteHistoryActivity::class.java)) },
                            modifier = Modifier.padding(end = Spacing.xxs).testTag("catch_route_history")) {
                            Icon(androidx.compose.material.icons.Icons.Filled.DateRange, contentDescription = "Route history")
                        }
                    }
                    if (showSettings && !settings.fixed) Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp, modifier = Modifier.padding(start = Spacing.xs)) {
                        Text(if (picking == "end") "Tap the map to set the finish" else "Tap the map to set the start",
                            Modifier.padding(horizontal = Spacing.md, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (mapFailed) Surface(Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.medium, shadowElevation = 2.dp) {
                    Text("Map unavailable. Check connection.", Modifier.padding(12.dp))
                }
                // Fit shows the whole route; Follow (a walk only) keeps you and the next stop in view.
                Column(Modifier.align(Alignment.BottomEnd).padding(end = Spacing.lg, bottom = frame.bottomInset + Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md), horizontalAlignment = Alignment.End) {
                    if (plan != null) MapFitButton(contentDescription = "Show whole route", onClick = { mapView?.overview() },
                        modifier = Modifier.testTag("catch_route_fit"))
                    if (walking) MapLocateButton(state = if (following) MapLocateState.FOLLOWING else MapLocateState.IDLE,
                        onClick = { mapView?.follow() })
                }
            }
        }
        SheetPlanner(model, plannerState, actions, showSettings, map = routeMap)
        details?.let { selections ->
            SpawnpointDetailSheet(selections, plan, loadDetail = { spawnpointDetails.detail(it) }, onDismiss = { details = null })
        }
        if (goRoutesOpen) GoRoutePicker(model.goRoutes, around = location ?: settings.start, onDismiss = { goRoutesOpen = false },
            onWalk = { record, reverse -> model.importFixed(record, reverse); goRoutesOpen = false; mapView?.fit() },
            onGuide = { record -> model.importGuide(record); goRoutesOpen = false; mapView?.fit() })
        if (areaOpen) CatchAreaPicker(initial = settings.area, center = settings.start, title = "Route area",
            onDismiss = { areaOpen = false }, onDone = { model.edit(model.settings.copy(area = it)); areaOpen = false })
        summary?.let { finished ->
            CatchRouteSummarySheet(finished, onDismiss = { summary = null }, onWalkAgain = { summary = null; walkAgain(finished) })
        }
        deleteSetup?.let { setup -> AlertDialog(onDismissRequest = { deleteSetup = null }, title = { Text("Delete ${setup.name}?") }, confirmButton = { TextButton(onClick = { model.delete(setup); deleteSetup = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteSetup = null }) { Text("Cancel") } }) }
    }

    /** The map's top bar: the route's name and where the walk stands, or the planner's title. */
    private fun catchTopBarText(plan: CatchItinerary?, active: CatchSession?, stops: List<List<CatchEncounter>>, pokemon: Int,
        editing: Boolean, now: Long): Pair<String, String?> {
        if (active != null) {
            val left = formatCatchKm((active.itinerary.distanceMeters - active.progressMeters).coerceAtLeast(0.0))
            if (active.finished) return active.itinerary.settings.name to "Finished · ${active.caught} caught"
            val next = catchNextStopIndex(active, now)
            val where = next?.let { "Stop ${it + 1} of ${stops.size}" } ?: "No stops ahead"
            return active.itinerary.settings.name to "$where · $left left · back ${catchClock(active.itinerary.finishAtMillis)}"
        }
        if (plan != null && !editing) return "Catch routes" to "$pokemon Pokémon · ${formatCatchKm(plan.distanceMeters)}"
        return "Catch routes" to null
    }

    private fun now() = System.currentTimeMillis()
    /**
     * Hands the route to Google Maps for following. Maps takes at most nine stops between origin and
     * destination, so the link is the simplified route: catch stops first, then the corners that keep its
     * shape. Maps walks its own streets between them.
     */
    private fun openInGoogleMaps(itinerary: CatchItinerary) {
        val url = catchRouteMapsUrl(itinerary) ?: return model.report("This route has no path to export yet.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { startActivity(Intent(intent).setPackage("com.google.android.apps.maps")) }
            .recoverCatching { startActivity(intent) }
            .onFailure { model.report("No app on this phone can open Google Maps links.") }
    }

    private fun coordinate(p: CatchPoint) = String.format(Locale.US, "%.5f, %.5f", p.latitude, p.longitude)
    private fun time(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
}

