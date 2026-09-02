package com.example.pokemonalertsv2.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.AlertFilterMatcher
import com.example.pokemonalertsv2.data.BuiltInFilterProfiles
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterCatalogRepository
import com.example.pokemonalertsv2.data.FilterAssignment
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterMatchContext
import com.example.pokemonalertsv2.util.WalkingRouteRepository
import com.example.pokemonalertsv2.util.CachedLocationProvider
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import com.example.pokemonalertsv2.data.FilterProfile
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.data.FilterStateDocument
import com.example.pokemonalertsv2.data.FilterSurface
import com.example.pokemonalertsv2.data.MAX_FILTER_PROFILE_NAME
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.data.NotificationPreset
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.counters.RaidCounterSettings
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieImportResult
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieImportCandidate
import com.example.pokemonalertsv2.data.pokegenie.PokeGeniePrepareResult
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieRepository
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieImportUiState
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.countAlertsByCategory
import com.example.pokemonalertsv2.ui.alerts.toCategorySelection
import com.example.pokemonalertsv2.ui.alerts.toStoredNames
import com.example.pokemonalertsv2.ui.godex.GoDexWebSessionCookies
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.TravelTime
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.pokemonalertsv2.data.DEFAULT_QUIET_HOURS_START
import com.example.pokemonalertsv2.data.DEFAULT_QUIET_HOURS_END

class SettingsViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val preferences = AlertPreferences(application.alertPreferencesDataStore)
    private val alertRepository by lazy(LazyThreadSafetyMode.NONE) {
        PokemonAlertsRepository.create(application)
    }
    private val goDexRepository by lazy(LazyThreadSafetyMode.NONE) {
        GoDexRepository.getInstance(application)
    }

    private val raidCounterPreferences = RaidCounterPreferences(application.alertPreferencesDataStore)
    private val pokeGenieRepository by lazy(LazyThreadSafetyMode.NONE) {
        PokeGenieRepository.getInstance(application)
    }
    private val filterCatalogRepository = FilterCatalogRepository.getInstance(application)

    val filterableAlerts: StateFlow<List<PokemonAlert>> = alertRepository.alerts
        .map { alerts -> alerts.filter { !it.isInvalidated && (TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE) > System.currentTimeMillis() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _filterPreviewContexts = MutableStateFlow<Map<String, FilterMatchContext>>(emptyMap())
    val filterPreviewContexts: StateFlow<Map<String, FilterMatchContext>> = _filterPreviewContexts
    private val _filterCatalog = MutableStateFlow(filterCatalogRepository.cached() ?: FilterCatalog())
    val filterCatalog: StateFlow<FilterCatalog> = _filterCatalog
    val filterSpecies = com.example.pokemonalertsv2.data.PokemonSpeciesRepository.getInstance(application)
        .searchSpecies("")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _requestedFilterEditor = MutableStateFlow<FilterSurface?>(null)
    val requestedFilterEditor: StateFlow<FilterSurface?> = _requestedFilterEditor

    fun requestFilterEditor(surface: FilterSurface) { _requestedFilterEditor.value = surface }
    fun consumeRequestedFilterEditor() { _requestedFilterEditor.value = null }

    val filterStateDocument: StateFlow<FilterStateDocument> = preferences.filterStateDocument
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterStateDocument())

    val feedFilterDefinition: StateFlow<FilterDefinition> = filterStateDocument
        .map { it.feed.resolve(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterDefinition())

    val mapFilterDefinition: StateFlow<FilterDefinition> = filterStateDocument
        .map { it.map.resolve(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterDefinition())

    val notificationFilterDefinition: StateFlow<FilterDefinition> = filterStateDocument
        .map { it.notifications.resolve(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterDefinition())

    val filterPreviewCounts: StateFlow<Map<FilterSurface, Int>> = combine(
        filterableAlerts,
        filterStateDocument,
        filterPreviewContexts
    ) { alerts, document, contexts ->
        FilterSurface.entries.associateWith { surface ->
            val definition = document.assignment(surface).resolve(document)
            alerts.count { AlertFilterMatcher.matches(it, definition, contexts[it.uniqueId] ?: FilterMatchContext()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val raidCounterSettings: StateFlow<RaidCounterSettings> = raidCounterPreferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RaidCounterSettings())

    private val _pokeGenieImportStatus = MutableStateFlow<String?>(null)

    /** Result of the last CSV import, shown under the import button. */
    val pokeGenieImportStatus: StateFlow<String?> = _pokeGenieImportStatus

    private val _pendingPokeGenieImport = MutableStateFlow<PokeGenieImportCandidate?>(null)
    private val _pokeGenieImportUiState = MutableStateFlow<PokeGenieImportUiState>(PokeGenieImportUiState.Idle)
    private var prepareJob: Job? = null
    private var preparingUri: String? = null

    init {
        viewModelScope.launch {
            filterableAlerts.collect { alerts ->
                val location = CachedLocationProvider.get(application)
                val routes = location?.let { WalkingRouteRepository.getInstance().getWalkingRoutes(it, alerts) }.orEmpty()
                _filterPreviewContexts.value = alerts.associate { alert ->
                    val direct = location?.let { origin ->
                        val lat = alert.latitude ?: return@let null
                        val lon = alert.longitude ?: return@let null
                        WalkingRouteUtils.straightLineDistanceMeters(origin.latitude, origin.longitude, lat, lon)
                    }
                    val info = WalkingRouteUtils.buildRouteDisplayInfo(direct, routes[alert.uniqueId])
                    alert.uniqueId to FilterMatchContext(info.effectiveDistanceMeters, info.walkingDurationSeconds)
                }
                _filterCatalog.value = filterCatalogRepository.offlineCatalog(alerts)
            }
        }
        viewModelScope.launch {
            runCatching { com.example.pokemonalertsv2.data.PokemonSpeciesRepository.getInstance(application).syncIfNeeded() }
            val alerts = runCatching { alertRepository.getLocalAlerts() }.getOrElse { emptyList() }
            _filterCatalog.value = runCatching { filterCatalogRepository.refresh() }
                .getOrElse { filterCatalogRepository.offlineCatalog(alerts) }
                .let { remote ->
                    // A successful response is still augmented with local species and currently
                    // observed values, so the selectors remain useful on a quiet scanner.
                    runCatching { filterCatalogRepository.offlineCatalog(alerts) }.getOrDefault(remote)
                }
        }
        savedStateHandle.get<String>(PENDING_IMPORT_URI_KEY)
            ?.let(Uri::parse)
            ?.let(::preparePokeGenieImport)
    }

    /** Parsed content waiting for explicit replacement confirmation. */
    val pendingPokeGenieImport: StateFlow<PokeGenieImportCandidate?> = _pendingPokeGenieImport
    val pokeGenieImportUiState: StateFlow<PokeGenieImportUiState> = _pokeGenieImportUiState

    fun updateRaidCounterDefaults(options: RaidCounterOptions) {
        viewModelScope.launch { raidCounterPreferences.updateDefaults(options) }
    }


    fun importPokeGenieCsv(uri: android.net.Uri) {
        // Keep the old entry point source-compatible, but route it through the same explicit
        // preview flow so no caller can replace the roster without confirmation.
        preparePokeGenieImport(uri)
    }

    fun preparePokeGenieImport(uri: Uri) {
        val uriKey = uri.toString()
        if (preparingUri == uriKey &&
            _pokeGenieImportUiState.value !is PokeGenieImportUiState.Error &&
            _pokeGenieImportUiState.value !is PokeGenieImportUiState.Imported
        ) return
        preparingUri = uriKey
        savedStateHandle[PENDING_IMPORT_URI_KEY] = uriKey
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch {
            _pokeGenieImportStatus.value = "Reading CSV..."
            _pokeGenieImportUiState.value = PokeGenieImportUiState.Reading
            when (val result = pokeGenieRepository.prepareImport(uri)) {
                is PokeGeniePrepareResult.Success -> {
                    _pendingPokeGenieImport.value = result.candidate
                    _pokeGenieImportUiState.value = PokeGenieImportUiState.Preview(result.candidate)
                    _pokeGenieImportStatus.value = null
                }
                is PokeGeniePrepareResult.Failure -> {
                    _pendingPokeGenieImport.value = null
                    _pokeGenieImportUiState.value = PokeGenieImportUiState.Error(result.message)
                    _pokeGenieImportStatus.value = result.message
                    preparingUri = null
                    savedStateHandle.remove<String>(PENDING_IMPORT_URI_KEY)
                }
            }
        }
    }

    fun commitPokeGenieImport() {
        val candidate = _pendingPokeGenieImport.value ?: return
        viewModelScope.launch {
            _pokeGenieImportStatus.value = "Importing..."
            _pokeGenieImportUiState.value = PokeGenieImportUiState.Reading
            _pokeGenieImportStatus.value = when (val result = pokeGenieRepository.commitImport(candidate)) {
                is PokeGenieImportResult.Success -> {
                    _pendingPokeGenieImport.value = null
                    _pokeGenieImportUiState.value = PokeGenieImportUiState.Imported(result.summary)
                    preparingUri = null
                    savedStateHandle.remove<String>(PENDING_IMPORT_URI_KEY)
                    importSuccessMessage(result.summary)
                }
                is PokeGenieImportResult.Failure -> {
                    _pokeGenieImportUiState.value = PokeGenieImportUiState.Error(result.message)
                    result.message
                }
            }
        }
    }

    fun cancelPokeGenieImport() {
        _pendingPokeGenieImport.value = null
        _pokeGenieImportUiState.value = PokeGenieImportUiState.Idle
        preparingUri = null
        savedStateHandle.remove<String>(PENDING_IMPORT_URI_KEY)
    }

    private fun importSuccessMessage(summary: com.example.pokemonalertsv2.data.pokegenie.PokeGenieImportSummary): String =
        buildString {
            append("Imported ${summary.importedCount} Pokémon")
            if (summary.skippedCount > 0) append(", skipped ${summary.skippedCount} rows")
            if (summary.synthesizedBaseCount > 0) {
                append(", ${summary.synthesizedBaseCount} base forms added for megas")
            }
            append(".")
            val missed = summary.importedCount - summary.matchedCount
            if (missed > 0) {
                append(" $missed had no Pokébattler match")
                summary.unmatchedForms.firstOrNull()?.let { first ->
                    val label = first.form?.let { "${first.name} ($it)" } ?: first.name
                    append(if (summary.unmatchedForms.size > 1) " (e.g. $label)" else " ($label)")
                }
                append(".")
            }
        }

    fun clearPokeGenie() {
        viewModelScope.launch {
            pokeGenieRepository.clear()
            _pendingPokeGenieImport.value = null
            _pokeGenieImportUiState.value = PokeGenieImportUiState.Idle
            _pokeGenieImportStatus.value = null
            preparingUri = null
            savedStateHandle.remove<String>(PENDING_IMPORT_URI_KEY)
        }
    }

    val goDexConfig get() = goDexRepository.config
    val goDexEntries get() = goDexRepository.entries
    val goDexSyncUiState get() = goDexRepository.syncUiState
    val goDexPendingEntryKeys get() = goDexRepository.pendingEntryKeys

    val onboardingCompleted: StateFlow<Boolean?> = preferences.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themeMode: StateFlow<Int> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val sortPreference: StateFlow<SortPreference> = preferences.sortPreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortPreference.POSTED_TIME)
    
    val notificationsEnabled: StateFlow<Boolean> = preferences.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val raidsNotifications: StateFlow<Boolean> = preferences.raidsNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val spawnsNotifications: StateFlow<Boolean> = preferences.spawnsNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val questsNotifications: StateFlow<Boolean> = preferences.questsNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val hundosNotifications: StateFlow<Boolean> = preferences.hundosNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val pvpNotifications: StateFlow<Boolean> = preferences.pvpNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val nundosNotifications: StateFlow<Boolean> = preferences.nundosNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val kecleonNotifications: StateFlow<Boolean> = preferences.kecleonNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val rocketNotifications: StateFlow<Boolean> = preferences.rocketNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val notificationVibrate: StateFlow<Boolean> = preferences.notificationVibrate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val silenceUntil: StateFlow<Long> = preferences.silenceUntil
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val quietHoursEnabled: StateFlow<Boolean> = preferences.quietHoursEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val quietHoursStartMinute: StateFlow<Int> = preferences.quietHoursStartMinute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_QUIET_HOURS_START)

    val quietHoursEndMinute: StateFlow<Int> = preferences.quietHoursEndMinute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_QUIET_HOURS_END)

    fun updateQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.updateQuietHoursEnabled(enabled) }
    }

    fun updateQuietHours(startMinute: Int, endMinute: Int) {
        viewModelScope.launch { preferences.updateQuietHours(startMinute, endMinute) }
    }
        
    val selectedArea: StateFlow<String> = preferences.selectedArea
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "All")
        
    val maxDistance: StateFlow<Int> = preferences.maxDistance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        
    val snoozeDuration: StateFlow<Int> = preferences.snoozeDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
    
    // Excluded type preferences
    val excludedHundoTypes: StateFlow<Set<String>> = preferences.excludedHundoTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedNundoTypes: StateFlow<Set<String>> = preferences.excludedNundoTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedPvpTypes: StateFlow<Set<String>> = preferences.excludedPvpTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedSpawnTypes: StateFlow<Set<String>> = preferences.excludedSpawnTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedRocketTypes: StateFlow<Set<String>> = preferences.excludedRocketTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedRaidTiers: StateFlow<Set<String>> = preferences.excludedRaidTiers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun completeOnboarding() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
        }
    }

    fun updateThemeMode(mode: Int) {
        viewModelScope.launch {
            preferences.updateThemeMode(mode.coerceIn(0, 2))
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun updateSortPreference(preference: SortPreference) {
        viewModelScope.launch {
            preferences.updateSortPreference(preference)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateNotificationsEnabled(enabled)
        }
    }
    
    fun updateRaidsNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateRaidsNotifications(enabled)
            updateUnifiedNotificationType(FilterAlertType.RAID, enabled)
        }
    }
    
    fun updateSpawnsNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateSpawnsNotifications(enabled)
            updateUnifiedNotificationType(FilterAlertType.SPAWN, enabled)
            updateUnifiedNotificationType(FilterAlertType.RARE, enabled)
        }
    }
    
    fun updateQuestsNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateQuestsNotifications(enabled)
            updateUnifiedNotificationType(FilterAlertType.QUEST, enabled)
        }
    }
    
    fun updateHundosNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateHundosNotifications(enabled)
            updateUnifiedNotificationType(FilterAlertType.HUNDO, enabled)
        }
    }
    
    fun updatePvpNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updatePvpNotifications(enabled)
            updateUnifiedNotificationType(FilterAlertType.PVP, enabled)
        }
    }
    
    fun updateNundosNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateNundosNotifications(enabled)
            updateUnifiedNotificationType(FilterAlertType.NUNDO, enabled)
        }
    }
    
    fun updateKecleonNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateKecleonNotifications(enabled)
            updateUnifiedNotificationType(FilterAlertType.KECLEON, enabled)
        }
    }
    
    fun updateRocketNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateRocketNotifications(enabled)
            updateUnifiedNotificationType(FilterAlertType.ROCKET, enabled)
        }
    }

    private suspend fun updateUnifiedNotificationType(type: FilterAlertType, enabled: Boolean) {
        preferences.updateFilterStateDocument { document ->
            val current = document.notifications.resolve(document)
            val enabledTypes = FilterAlertType.entries
                .filter { current.alertTypes.contains(it.name) }
                .mapTo(linkedSetOf()) { it.name }
            if (enabled) enabledTypes += type.name else enabledTypes -= type.name
            val selection = when (enabledTypes.size) {
                0 -> FilterSelection.None
                FilterAlertType.entries.size -> FilterSelection.All
                else -> FilterSelection.only(enabledTypes)
            }
            document.withAssignment(
                FilterSurface.NOTIFICATIONS,
                FilterAssignment.local(current.copy(alertTypes = selection))
            )
        }
    }
    
    fun updateNotificationVibrate(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateNotificationVibrate(enabled)
        }
    }
    
    fun silenceNotificationsFor(durationMinutes: Int) {
        viewModelScope.launch {
            val silenceUntil = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
            preferences.updateSilenceUntil(silenceUntil)
        }
    }
    
    fun clearNotificationSilence() {
        viewModelScope.launch {
            preferences.updateSilenceUntil(0L)
        }
    }
    
    fun updateSelectedArea(area: String) {
        viewModelScope.launch {
            preferences.updateSelectedArea(area)
            val selection = if (area.equals("All", ignoreCase = true)) FilterSelection.All
            else FilterSelection.only(listOf(area))
            preferences.updateFilterStateDocument { document ->
                val feed = document.feed.resolve(document).copy(areas = selection)
                val notifications = document.notifications.resolve(document).copy(areas = selection)
                document
                    .withAssignment(FilterSurface.FEED, FilterAssignment.local(feed))
                    .withAssignment(FilterSurface.NOTIFICATIONS, FilterAssignment.local(notifications))
            }
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun applyNotificationPreset(preset: NotificationPreset) {
        viewModelScope.launch {
            preferences.applyNotificationPreset(preset)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }
    
    fun updateMaxDistance(distance: Int) {
        viewModelScope.launch {
            preferences.updateMaxDistance(distance)
            preferences.updateFilterStateDocument { document ->
                val feed = document.feed.resolve(document).copy(maxDistanceKm = distance.coerceIn(0, 50))
                val notifications = document.notifications.resolve(document).copy(maxDistanceKm = distance.coerceIn(0, 50))
                document
                    .withAssignment(FilterSurface.FEED, FilterAssignment.local(feed))
                    .withAssignment(FilterSurface.NOTIFICATIONS, FilterAssignment.local(notifications))
            }
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun updateSnoozeDuration(minutes: Int) {
        viewModelScope.launch {
            preferences.updateSnoozeDuration(minutes)
        }
    }

    //region Filters hub — per-surface category selections with a shared live tally.

    fun applySurfaceFilter(surface: FilterSurface, definition: FilterDefinition) {
        viewModelScope.launch {
            preferences.updateFilterStateDocument { document ->
                document.withAssignment(surface, FilterAssignment.local(definition))
            }
            applyLegacyProfileSort(surface, definition)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun applyProfile(surface: FilterSurface, profile: FilterProfile, linked: Boolean) {
        viewModelScope.launch {
            preferences.updateFilterStateDocument { document ->
                val assignment = if (linked) FilterAssignment.linked(profile)
                else FilterAssignment.local(profile.definition)
                document.withAssignment(surface, assignment)
            }
            applyLegacyProfileSort(surface, profile.definition)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun applyLinkedFilter(surface: FilterSurface, profile: FilterProfile, definition: FilterDefinition) {
        viewModelScope.launch {
            preferences.updateFilterStateDocument { document ->
                val updated = profile.copy(definition = definition, updatedAtMillis = System.currentTimeMillis())
                document.copy(profiles = document.profiles.filterNot { it.id == updated.id } + updated)
                    .withAssignment(surface, FilterAssignment.linked(updated))
            }
            applyLegacyProfileSort(surface, definition)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    private suspend fun applyLegacyProfileSort(surface: FilterSurface, definition: FilterDefinition) {
        if (surface != FilterSurface.FEED) return
        val sort = definition.feedSort?.let { name -> SortPreference.entries.firstOrNull { it.name == name } } ?: return
        preferences.updateSortPreference(sort)
    }

    fun saveFilterProfile(name: String, definition: FilterDefinition, existingId: String? = null) {
        val cleanName = name.trim().take(MAX_FILTER_PROFILE_NAME)
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            preferences.updateFilterStateDocument { document ->
                val id = existingId ?: java.util.UUID.randomUUID().toString()
                val uniqueName = uniqueProfileName(cleanName, document.profiles, id)
                val profile = FilterProfile(
                    id = id,
                    name = uniqueName,
                    definition = definition,
                    updatedAtMillis = System.currentTimeMillis()
                )
                document.copy(profiles = document.profiles.filterNot { it.id == id } + profile)
            }
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun deleteFilterProfile(profileId: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val document = filterStateDocument.value
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(android.content.ComponentName(context, AlertsWidgetProvider::class.java)).toList() +
                manager.getAppWidgetIds(android.content.ComponentName(context, com.example.pokemonalertsv2.widget.NearbyRadarWidgetProvider::class.java)).toList()
            widgetIds.forEach { id ->
                val configuration = com.example.pokemonalertsv2.widget.WidgetConfigurationStore.get(context, id)
                configuration.filterAssignment?.takeIf { it.profileId == profileId }?.let { assignment ->
                    com.example.pokemonalertsv2.widget.WidgetConfigurationStore.save(context, id, configuration.copy(filterAssignment = FilterAssignment.local(assignment.resolve(document))))
                }
            }
            preferences.updateFilterStateDocument { it.deleteProfilePreservingConsumers(profileId) }
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun linkedWidgetConsumers(profileId: String): List<String> {
        val context = getApplication<Application>()
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        return listOf(AlertsWidgetProvider::class.java, com.example.pokemonalertsv2.widget.NearbyRadarWidgetProvider::class.java)
            .flatMap { provider -> manager.getAppWidgetIds(android.content.ComponentName(context, provider)).toList() }
            .filter { id -> com.example.pokemonalertsv2.widget.WidgetConfigurationStore.get(context, id).filterAssignment?.profileId == profileId }
            .map { "Widget #$it" }
    }

    private fun uniqueProfileName(
        requested: String,
        profiles: List<FilterProfile>,
        replacingId: String
    ): String {
        val names = (profiles.filterNot { it.id == replacingId } + BuiltInFilterProfiles.all).map { it.name.lowercase() }.toSet()
        if (requested.lowercase() !in names) return requested
        var suffix = 2
        while (true) {
            val ending = " ($suffix)"
            val candidate = requested.take((MAX_FILTER_PROFILE_NAME - ending.length).coerceAtLeast(1)) + ending
            if (candidate.lowercase() !in names) return candidate
            suffix++
        }
    }

    private fun selectionFromMuted(categories: Set<AlertCategory>): FilterSelection {
        if (categories.isEmpty()) return FilterSelection.All
        val mutedNames = categories.map { it.name }.toSet()
        val enabled = FilterAlertType.entries.filterNot { type -> type.name in mutedNames }.map { it.name }
        return if (enabled.isEmpty()) FilterSelection.None else FilterSelection.only(enabled)
    }

    fun allAvailableProfiles(): List<FilterProfile> = BuiltInFilterProfiles.all + filterStateDocument.value.profiles

    val feedFilterCategories: StateFlow<Set<AlertCategory>> = preferences.feedCategories
        .map { stored -> stored.toCategorySelection() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val mapFilterCategories: StateFlow<Set<AlertCategory>> = preferences.mapCategories
        .map { stored -> stored.toCategorySelection() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val mapShowDismissed: StateFlow<Boolean> = preferences.mapShowDismissed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val maxWalkingMinutes: StateFlow<Int> = preferences.maxWalkingMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TravelTime.NO_LIMIT)

    val showMapCountdowns: StateFlow<Boolean> = preferences.showMapCountdowns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showSpawnRadius: StateFlow<Boolean> = preferences.showSpawnRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val spacialRendEnabled: StateFlow<Boolean> = preferences.spacialRendEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val filterCategoryCounts: StateFlow<Map<AlertCategory, Int>> = alertRepository.alerts
        .map { alerts ->
            val now = System.currentTimeMillis()
            countAlertsByCategory(
                alerts.filter { alert ->
                    (TimeUtils.parseEndTimeToMillis(alert.endTime) ?: Long.MAX_VALUE) > now
                }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** [categories] holds the muted categories; empty = show everything. */
    fun updateFeedFilterCategories(categories: Set<AlertCategory>) {
        viewModelScope.launch {
            preferences.updateFeedCategories(categories.toStoredNames())
            preferences.updateFilterStateDocument { document ->
                val current = document.feed.resolve(document)
                document.withAssignment(
                    FilterSurface.FEED,
                    FilterAssignment.local(current.copy(alertTypes = selectionFromMuted(categories)))
                )
            }
        }
    }

    fun updateMapFilterCategories(categories: Set<AlertCategory>) {
        viewModelScope.launch {
            preferences.updateMapCategories(categories.toStoredNames())
            preferences.updateFilterStateDocument { document ->
                val current = document.map.resolve(document)
                document.withAssignment(
                    FilterSurface.MAP,
                    FilterAssignment.local(current.copy(alertTypes = selectionFromMuted(categories)))
                )
            }
        }
    }

    fun updateMapShowDismissed(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateMapShowDismissed(enabled)
        }
    }

    fun updateMaxWalkingMinutes(minutes: Int) {
        viewModelScope.launch {
            preferences.updateMaxWalkingMinutes(minutes)
            preferences.updateFilterStateDocument { document ->
                val feed = document.feed.resolve(document).copy(maxWalkingMinutes = minutes.coerceIn(0, 240))
                val notifications = document.notifications.resolve(document).copy(maxWalkingMinutes = minutes.coerceIn(0, 240))
                document
                    .withAssignment(FilterSurface.FEED, FilterAssignment.local(feed))
                    .withAssignment(FilterSurface.NOTIFICATIONS, FilterAssignment.local(notifications))
            }
        }
    }

    fun updateShowMapCountdowns(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateShowMapCountdowns(enabled)
        }
    }

    fun updateShowSpawnRadius(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateShowSpawnRadius(enabled)
        }
    }

    fun updateSpacialRendEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateSpacialRendEnabled(enabled)
        }
    }

    //endregion

    fun connectGoDex(url: String) {
        viewModelScope.launch {
            runCatching { goDexRepository.connect(url.trim()) }
        }
    }

    fun syncGoDex() {
        viewModelScope.launch {
            runCatching { goDexRepository.syncConfigured() }
        }
    }

    fun refreshGoDexForPageEntry() {
        viewModelScope.launch {
            runCatching { goDexRepository.refreshForPageEntry() }
        }
    }

    fun updateGoDexNotificationFilter(enabled: Boolean) {
        viewModelScope.launch {
            goDexRepository.setNotificationFilterEnabled(enabled)
        }
    }

    fun setGoDexEntryCaught(entryKey: String, caught: Boolean) {
        viewModelScope.launch {
            goDexRepository.markAsCaught(entryKey, caught)
        }
    }

    fun disconnectGoDex() {
        viewModelScope.launch {
            goDexRepository.disconnect()
        }
    }

    fun clearGoDexSession() {
        viewModelScope.launch {
            goDexRepository.saveSessionCookies("")
            GoDexWebSessionCookies.clearGoDexSessionCookies()
        }
    }
    
    fun toggleExcludedHundoType(type: String) {
        viewModelScope.launch {
            val current = excludedHundoTypes.value
            val updated = if (type in current) current - type else current + type
            preferences.updateExcludedHundoTypes(updated)
        }
    }
    
    fun toggleExcludedNundoType(type: String) {
        viewModelScope.launch {
            val current = excludedNundoTypes.value
            val updated = if (type in current) current - type else current + type
            preferences.updateExcludedNundoTypes(updated)
        }
    }
    
    fun toggleExcludedPvpType(type: String) {
        viewModelScope.launch {
            val current = excludedPvpTypes.value
            val updated = if (type in current) current - type else current + type
            preferences.updateExcludedPvpTypes(updated)
        }
    }
    
    fun toggleExcludedSpawnType(type: String) {
        viewModelScope.launch {
            val current = excludedSpawnTypes.value
            val updated = if (type in current) current - type else current + type
            preferences.updateExcludedSpawnTypes(updated)
        }
    }
    
    fun toggleExcludedRocketType(type: String) {
        viewModelScope.launch {
            val current = excludedRocketTypes.value
            val updated = if (type in current) current - type else current + type
            preferences.updateExcludedRocketTypes(updated)
        }
    }
    
    fun toggleExcludedRaidTier(tier: String) {
        viewModelScope.launch {
            val current = excludedRaidTiers.value
            val updated = if (tier in current) current - tier else current + tier
            preferences.updateExcludedRaidTiers(updated)
        }
    }

    private companion object {
        const val PENDING_IMPORT_URI_KEY = "raid_counters_pending_import_uri"
    }
}
