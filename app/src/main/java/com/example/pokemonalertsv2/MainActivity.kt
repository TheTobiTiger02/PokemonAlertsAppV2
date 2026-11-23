package com.example.pokemonalertsv2

import com.example.pokemonalertsv2.util.AppText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.ui.alerts.ACTION_MAP_PIP_CONTROL
import com.example.pokemonalertsv2.ui.alerts.EXTRA_MAP_PIP_COMMAND
import com.example.pokemonalertsv2.ui.alerts.MapPipCommand
import com.example.pokemonalertsv2.ui.alerts.MapPipMode
import com.example.pokemonalertsv2.ui.alerts.MapPipUiState
import com.example.pokemonalertsv2.ui.alerts.buildMapPipActions
import com.example.pokemonalertsv2.ui.alerts.PokemonAlertsViewModel
import com.example.pokemonalertsv2.ui.history.AlertHistoryViewModel
import com.example.pokemonalertsv2.ui.history.SpawnInsightsViewModel
import com.example.pokemonalertsv2.ui.motion.appSharedAxisX
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.navigation.DeepLinkTarget
import com.example.pokemonalertsv2.navigation.parseDeepLink
import com.example.pokemonalertsv2.ui.settings.SettingsDestination
import com.example.pokemonalertsv2.ui.settings.SettingsViewModel
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.example.pokemonalertsv2.data.PokemonSpeciesRepository
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieImportCandidate
import androidx.compose.runtime.LaunchedEffect
import com.example.pokemonalertsv2.util.InAppUpdateManager

class MainActivity : ComponentActivity() {

    private val alertsViewModel: PokemonAlertsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val historyViewModel: AlertHistoryViewModel by viewModels()
    private val insightsViewModel: SpawnInsightsViewModel by viewModels()
    private val backgroundLocationPermissionNeeded = MutableStateFlow(false)
    private val requestedRootTab = MutableStateFlow<Int?>(null)
    private val requestedSettingsDestination = MutableStateFlow<SettingsDestination?>(null)
    /** Set by the floating map's "Edit targets": open the map on the hunt's target picker. */
    private val requestedHuntSetup = MutableStateFlow(false)
    private var lastExternalCsvUri: String? = null
    private var permissionStep = PermissionStep.IDLE

    /**
     * Permission callbacks fire outside composition, so their results are pushed here and
     * shown on the app's own SnackbarHost rather than as a Toast that floats over whatever
     * system dialog is still on screen.
     */
    private val transientMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /**
     * This activity is the picture-in-picture window itself: `setAutoEnterEnabled` shrinks
     * whichever activity is on screen, and starting a separate one as the user leaves is
     * refused as a background activity launch mid-transition. While the window is up the
     * scaffold steps aside and only the map is composed.
     */
    private var inPictureInPicture by mutableStateOf(false)
    private var pipMode = MapPipMode.FOLLOW
    private var pipCanStep = false
    private var pipHunting = false
    private var pipHasTarget = false
    private var pipAutoEnter = false
    private var pipMapTabVisible = false
    private var maxPipActions = 0
    private var canUsePictureInPicture = false

    /**
     * Buffered so a burst of button presses while the window is redrawing is replayed in
     * order rather than dropped on the floor.
     */
    private val pipCommands = MutableSharedFlow<MapPipCommand>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val pipCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_MAP_PIP_CONTROL) return
            val command = MapPipCommand.fromName(
                intent.getStringExtra(EXTRA_MAP_PIP_COMMAND)
            ) ?: return
            pipCommands.tryEmit(command)
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            pipCommandReceiver,
            IntentFilter(ACTION_MAP_PIP_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        runCatching { unregisterReceiver(pipCommandReceiver) }
        super.onStop()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    /** Auto-enter is armed only while the map is the tab the user would be leaving. */
    private fun updateMapPipAvailability(onMapTab: Boolean, autoEnter: Boolean) {
        if (onMapTab == pipMapTabVisible && autoEnter == pipAutoEnter) return
        pipMapTabVisible = onMapTab
        pipAutoEnter = autoEnter
        applyPipParams()
    }

    private fun updateMapPipState(state: MapPipUiState) {
        if (
            state.mode == pipMode &&
            state.canStep == pipCanStep &&
            state.hunting == pipHunting &&
            state.hasTarget == pipHasTarget
        ) {
            return
        }
        pipMode = state.mode
        pipCanStep = state.canStep
        pipHunting = state.hunting
        pipHasTarget = state.hasTarget
        applyPipParams()
    }

    private fun enterMapPictureInPicture() {
        if (!canUsePictureInPicture || isInPictureInPictureMode) return
        runCatching { enterPictureInPictureMode(buildPipParams()) }
    }

    private fun applyPipParams() {
        if (!canUsePictureInPicture) return
        runCatching { setPictureInPictureParams(buildPipParams()) }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(
                buildMapPipActions(
                    context = this,
                    mode = pipMode,
                    canStep = pipCanStep,
                    maxActions = maxPipActions,
                    hunting = pipHunting,
                    hasTarget = pipHasTarget
                )
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(false)
            builder.setAutoEnterEnabled(pipAutoEnter && pipMapTabVisible)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setTitle("Pokemon Alerts map")
            builder.setSubtitle("Live alerts and location")
        }
        return builder.build()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Below Android 12 there is no auto-enter flag, so this callback is all there is.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && pipAutoEnter && pipMapTabVisible) {
            enterMapPictureInPicture()
        }
    }

    private fun showMessage(text: String) {
        transientMessages.tryEmit(text)
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            
            if (fineLocationGranted || coarseLocationGranted) {
                showMessage("Location permission granted")
                requestBackgroundLocationStep()
            } else {
                showMessage("Location permission is needed for distance calculations and map features")
                finishPermissionFlow()
            }
        }

    private val backgroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            showMessage(
                if (isGranted) AppText.get(R.string.mainactivitykt_background_location_access_granted)
                else AppText.get(R.string.mainactivitykt_background_location_access_was_not_granted)
            )
            finishPermissionFlow()
        }

    private val backgroundLocationSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val granted = hasBackgroundLocationPermission()
            showMessage(
                if (granted) AppText.get(R.string.mainactivitykt_background_location_access_granted)
                else AppText.get(R.string.mainactivitykt_background_location_access_was_not_granted)
            )
            finishPermissionFlow()
        }

    private val unknownSourcesSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            lifecycleScope.launch {
                InAppUpdateManager.resumePendingInstall(this@MainActivity)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashView ->
            splashView.view.animate()
                .alpha(0f)
                .scaleX(1.035f)
                .scaleY(1.035f)
                .setDuration(240L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction(splashView::remove)
                .start()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        canUsePictureInPicture =
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        if (canUsePictureInPicture) maxPipActions = maxNumPictureInPictureActions
        // Re-prepare the URI after process recreation; preparation is read-only and the
        // candidate remains uncommitted until the user confirms it.
        lastExternalCsvUri = null
        // On recreation (theme, font size, rotation, process restore) the launch intent is
        // the same one already handled, and re-applying its tab or deep link would yank the
        // user off the screen they had navigated to since. Only the CSV import re-prepares.
        if (savedInstanceState == null) handleNavigationIntent(intent) else handleExternalCsvIntent(intent)

        lifecycleScope.launch(Dispatchers.IO) {
            PokemonSpeciesRepository.getInstance(applicationContext).syncIfNeeded()
        }

        lifecycleScope.launch {
            InAppUpdateManager.restorePendingInstall(this@MainActivity)
        }

        setContent {
            val showBackgroundLocationDialog by backgroundLocationPermissionNeeded.collectAsStateWithLifecycle()
            val onboardingCompleted by settingsViewModel.onboardingCompleted.collectAsStateWithLifecycle()
            val requestedTab by requestedRootTab.collectAsStateWithLifecycle()
            val requestedSettings by requestedSettingsDestination.collectAsStateWithLifecycle()
            val huntSetupRequested by requestedHuntSetup.collectAsStateWithLifecycle()
            val pendingPokeGenieImport by settingsViewModel.pendingPokeGenieImport
                .collectAsStateWithLifecycle(initialValue = null)
            
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val onboardingArea by settingsViewModel.selectedArea.collectAsStateWithLifecycle()
            val onboardingDistance by settingsViewModel.maxDistance.collectAsStateWithLifecycle()
            val darkTheme = AppThemeMode.fromStored(themeMode)
                .resolveDark(isSystemInDarkTheme())

            // Track whether we should show onboarding or the main app
            var showOnboarding by rememberSaveable { mutableStateOf<Boolean?>(null) }
            if (showOnboarding == null && onboardingCompleted != null) {
                showOnboarding = onboardingCompleted != true
            }

            // A pending CSV URI is restored by SettingsViewModel after process recreation.
            // Re-open the same destination so the user never has to hunt for the preview.
            LaunchedEffect(pendingPokeGenieImport) {
                if (pendingPokeGenieImport != null) {
                    requestedRootTab.value = NAV_SETTINGS_TAB_INDEX
                    requestedSettingsDestination.value = SettingsDestination.RAID_COUNTERS
                }
            }

            PokemonAlertsV2Theme(darkTheme = darkTheme) {
                // Keep splash while determining initial screen
                if (showOnboarding == null) {
                    return@PokemonAlertsV2Theme
                }

                AnimatedContent(
                    targetState = showOnboarding == true,
                    transitionSpec = { appSharedAxisX(forward = !targetState) },
                    label = "onboarding_to_app"
                ) { onboardingVisible ->
                    if (onboardingVisible) {
                        com.example.pokemonalertsv2.ui.onboarding.OnboardingScreen(
                            initialArea = onboardingArea,
                            initialMaxDistance = onboardingDistance,
                            onAreaChanged = settingsViewModel::updateSelectedArea,
                            onMaxDistanceChanged = settingsViewModel::updateMaxDistance,
                            onPresetSelected = settingsViewModel::applyNotificationPreset,
                            onFinish = {
                                settingsViewModel.completeOnboarding()
                                // A new user has just had the tour; the release notes would repeat it.
                                lifecycleScope.launch {
                                    AlertPreferences(alertPreferencesDataStore)
                                        .markWhatsNewSeen(com.example.pokemonalertsv2.ui.WHATS_NEW_RELEASE)
                                }
                                showOnboarding = false
                            }
                        )
                    } else {
                        MainScaffold(
                            messages = transientMessages,
                            alertsViewModel = alertsViewModel,
                            historyViewModelProvider = { historyViewModel },
                            insightsViewModelProvider = { insightsViewModel },
                            settingsViewModel = settingsViewModel,
                            requestedTab = requestedTab,
                            onRequestedTabConsumed = { requestedRootTab.value = null },
                            requestedSettingsDestination = requestedSettings,
                            onRequestedSettingsDestinationConsumed = {
                                requestedSettingsDestination.value = null
                            },
                            requestedHuntSetup = huntSetupRequested,
                            onRequestedHuntSetupConsumed = { requestedHuntSetup.value = false },
                            onManageLocationPermissions = ::restartLocationPermissionFlow,
                            onOpenUnknownSourcesSettings = {
                                unknownSourcesSettingsLauncher.launch(
                                    InAppUpdateManager.unknownSourcesSettingsIntent(this@MainActivity)
                                )
                            },
                            pictureInPictureMode = inPictureInPicture,
                            pipCommands = pipCommands,
                            onPipStateChanged = ::updateMapPipState,
                            onMapPipAvailabilityChanged = ::updateMapPipAvailability,
                            onEnterPictureInPicture = if (canUsePictureInPicture) {
                                ::enterMapPictureInPicture
                            } else {
                                null
                            }
                        )
                    }
                }

                if (showBackgroundLocationDialog && !inPictureInPicture) {
                    BackgroundLocationPermissionDialog(
                        onDismiss = {
                            backgroundLocationPermissionNeeded.value = false
                            finishPermissionFlow()
                        },
                        onOpenSettings = {
                            backgroundLocationPermissionNeeded.value = false
                            backgroundLocationPermissionLauncher.launch(
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    internal fun handleNavigationIntent(intent: Intent) {
        requestedTab(intent)?.let { requestedRootTab.value = it }
        if (intent.getBooleanExtra(EXTRA_OPEN_HUNT_EDITOR, false)) {
            // Consumed here, so a recreation that re-reads this intent opens nothing twice.
            intent.removeExtra(EXTRA_OPEN_HUNT_EDITOR)
            requestedHuntSetup.value = true
        }
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                if (handleDeepLink(uri.toString())) return
            }
        }
        handleExternalCsvIntent(intent)
    }

    internal fun handleExternalCsvIntent(intent: Intent): Boolean {
        val uri = externalCsvUri(intent) ?: return false
        if (!isSupportedCsvIntent(intent, uri)) return false
        val key = uri.toString()
        if (key == lastExternalCsvUri) return false
        lastExternalCsvUri = key
        runCatching {
            val read = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
            val write = intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0
            when {
                read && write -> contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                read -> contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                write -> contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        settingsViewModel.preparePokeGenieImport(uri)
        requestedRootTab.value = NAV_SETTINGS_TAB_INDEX
        requestedSettingsDestination.value = SettingsDestination.RAID_COUNTERS
        return true
    }

    internal fun pendingExternalCsvImportForTest(): PokeGenieImportCandidate? =
        settingsViewModel.pendingPokeGenieImport.value

    /**
     * Routes a `pokemonalerts://` link onto the same state the tab extras drive.
     * Returns true when the link was ours, so the CSV branch below is skipped.
     */
    private fun handleDeepLink(url: String): Boolean {
        when (val target = parseDeepLink(url) ?: return false) {
            is DeepLinkTarget.RootTab -> requestedRootTab.value = target.tabIndex
            is DeepLinkTarget.Event -> {
                com.example.pokemonalertsv2.events.EventDeepLink.open(target.eventId)
                requestedRootTab.value = EVENTS_TAB_INDEX
            }
            is DeepLinkTarget.Settings -> {
                requestedRootTab.value = NAV_SETTINGS_TAB_INDEX
                requestedSettingsDestination.value = target.destination
            }
            is DeepLinkTarget.Alert -> {
                // The alert has to be resolved from the repository before the detail screen
                // can flatten it into extras; fall back to the feed when it is gone
                // (expired, or never seen on this device).
                lifecycleScope.launch {
                    val alert = withContext(Dispatchers.IO) {
                        runCatching {
                            PokemonAlertsRepository.create(applicationContext)
                                .getLocalAlerts()
                                .firstOrNull {
                                    it.uniqueId == target.alertId || it.id?.toString() == target.alertId
                                }
                        }.getOrNull()
                    }
                    if (alert != null) {
                        startActivity(
                            AlertDetailActivity.createIntent(
                                this@MainActivity,
                                alert,
                                returnToAlerts = true
                            )
                        )
                    } else {
                        requestedRootTab.value = ALERTS_TAB_INDEX
                        showMessage("That alert is no longer available")
                    }
                }
            }
        }
        return true
    }

    private fun requestForegroundLocationStep() {
        permissionStep = PermissionStep.FOREGROUND_LOCATION
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationGranted && !coarseLocationGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        requestBackgroundLocationStep()
    }

    private fun requestBackgroundLocationStep() {
        permissionStep = PermissionStep.BACKGROUND_LOCATION
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasBackgroundLocationPermission()) {
            finishPermissionFlow()
            return
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            backgroundLocationPermissionNeeded.value = true
        }
    }

    private fun hasBackgroundLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun finishPermissionFlow() {
        permissionStep = PermissionStep.COMPLETE
    }

    private fun restartLocationPermissionFlow() {
        if (permissionStep.isRequestActive()) return
        requestForegroundLocationStep()
    }

    companion object {
        private const val EXTRA_INITIAL_TAB = "extra_initial_tab"
        private const val EXTRA_OPEN_HUNT_EDITOR = "extra_open_hunt_editor"
        private const val NAV_SETTINGS_TAB_INDEX = SETTINGS_TAB_INDEX

        internal fun createAlertsIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_INITIAL_TAB, ALERTS_TAB_INDEX)
            }

        internal fun createEventsIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_INITIAL_TAB, EVENTS_TAB_INDEX)
            }

        internal fun createMapIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_INITIAL_TAB, MAP_TAB_INDEX)
            }

        /** The map with the running hunt's target picker open: the floating map's "Edit targets". */
        internal fun createHuntEditorIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_TAB, MAP_TAB_INDEX)
                putExtra(EXTRA_OPEN_HUNT_EDITOR, true)
            }

        internal fun requestedTab(intent: Intent?): Int? =
            intent?.getIntExtra(EXTRA_INITIAL_TAB, -1)
                ?.let(::rootTabIndexOrNull)
    }
}
