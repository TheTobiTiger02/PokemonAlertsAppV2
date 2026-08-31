package com.example.pokemonalertsv2.ui.counters

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.counters.PokebattlerNameNormalizer
import com.example.pokemonalertsv2.data.counters.RaidCountersRepository
import com.example.pokemonalertsv2.data.gamemaster.GameMasterRepository
import com.example.pokemonalertsv2.raidwatch.ManualRaidBoss
import com.example.pokemonalertsv2.raidwatch.RaidWatchController
import com.example.pokemonalertsv2.raidwatch.createManualRaidAlert
import com.example.pokemonalertsv2.raidwatch.manualRaidTierLabel
import com.example.pokemonalertsv2.raidwatch.perfectCatchCp
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ManualRaidPickerUiState(
    val loading: Boolean = true,
    val bosses: List<ManualRaidBoss> = emptyList(),
    val error: String? = null,
    val startingPokemonId: String? = null
)

class ManualRaidViewModel(application: Application) : AndroidViewModel(application) {
    private val counters = RaidCountersRepository.getInstance(application)
    private val gameMaster = GameMasterRepository.getInstance(application)
    private val _uiState = MutableStateFlow(ManualRaidPickerUiState())
    val uiState: StateFlow<ManualRaidPickerUiState> = _uiState

    init { refresh() }

    fun refresh(force: Boolean = false) {
        if (_uiState.value.loading && _uiState.value.bosses.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching {
                val catalogue = counters.availableRaidBosses(forceRefresh = force)
                gameMaster.syncIfNeeded()
                val ids = catalogue.map { it.pokemonId }
                val baseIds = ids.map(PokebattlerNameNormalizer::baseSpeciesId)
                val species = gameMaster.simSpecies((ids + baseIds).distinct())
                val sprites = gameMaster.spriteUrls(ids)
                val dexNumbers = gameMaster.dexNumbersFor(ids)
                catalogue.map { entry ->
                    val stats = species[entry.pokemonId]
                        ?: species[PokebattlerNameNormalizer.baseSpeciesId(entry.pokemonId)]
                    ManualRaidBoss(
                        catalogue = entry,
                        tierLabel = manualRaidTierLabel(entry),
                        hundoCP = stats?.let(::perfectCatchCp),
                        pokedexId = dexNumbers[entry.pokemonId],
                        spriteUrls = sprites[entry.pokemonId].orEmpty()
                    )
                }
            }.onSuccess { bosses ->
                _uiState.value = ManualRaidPickerUiState(
                    loading = false,
                    bosses = bosses,
                    error = if (bosses.isEmpty()) "No current raid bosses were returned." else null
                )
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        loading = false,
                        error = "Couldn't load the current raid bosses. Check your connection and retry."
                    )
                }
            }
        }
    }

    fun setStarting(pokemonId: String?) {
        _uiState.update { it.copy(startingPokemonId = pokemonId) }
    }
}

class ManualRaidActivity : ComponentActivity() {
    private val viewModel: ManualRaidViewModel by viewModels()
    private val themeRepository by lazy { PokemonAlertsRepository.create(applicationContext) }
    private var pendingBoss: ManualRaidBoss? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val boss = pendingBoss.also { pendingBoss = null }
        if (granted && boss != null) {
            startRaidWatch(boss)
        } else {
            viewModel.setStarting(null)
            Toast.makeText(
                this,
                "Notification access is required to start a Raid Live Update.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by themeRepository.observeThemeMode()
                .collectAsStateWithLifecycle(initialValue = 0)
            PokemonAlertsV2Theme(
                darkTheme = AppThemeMode.fromStored(themeMode).resolveDark(isSystemInDarkTheme())
            ) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ManualRaidBossPickerScreen(
                    state = state,
                    onBack = { finish() },
                    onRetry = { viewModel.refresh(force = true) },
                    onBossSelected = ::requestStart
                )
            }
        }
    }

    private fun requestStart(boss: ManualRaidBoss) {
        if (viewModel.uiState.value.startingPokemonId != null) return
        viewModel.setStarting(boss.catalogue.pokemonId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingBoss = boss
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startRaidWatch(boss)
        }
    }

    private fun startRaidWatch(boss: ManualRaidBoss) {
        lifecycleScope.launch {
            val alert = createManualRaidAlert(boss)
            if (!RaidWatchController.start(applicationContext, alert)) {
                viewModel.setStarting(null)
                Toast.makeText(this@ManualRaidActivity, "Couldn't start Raid Live Update.", Toast.LENGTH_LONG).show()
                return@launch
            }
            startActivity(
                AlertDetailActivity.createIntent(this@ManualRaidActivity, alert, returnToAlerts = true)
                    .putExtra(AlertDetailActivity.EXTRA_OPEN_COUNTERS, true)
                    .putExtra(AlertDetailActivity.EXTRA_PREFER_PERSONAL_TEAM, true)
            )
            finish()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ManualRaidBossPickerScreen(
    state: ManualRaidPickerUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onBossSelected: (ManualRaidBoss) -> Unit
) {
    var search by remember { mutableStateOf(TextFieldValue()) }
    val query = search.text.trim()
    val visible = remember(state.bosses, query) {
        if (query.isBlank()) state.bosses else state.bosses.filter {
            it.catalogue.displayName.contains(query, ignoreCase = true) ||
                it.tierLabel.contains(query, ignoreCase = true)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Start Raid Live Update", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Choose a current raid boss",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRetry, enabled = !state.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh raid bosses")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("manual_raid_search"),
                label = { Text("Search boss or tier") },
                singleLine = true
            )
            when {
                state.loading && state.bosses.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Text("Loading current raid bosses…", modifier = Modifier.padding(top = 12.dp))
                    }
                }
                state.error != null && state.bosses.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) { Text(state.error, style = MaterialTheme.typography.bodyLarge) }
                }
                visible.isEmpty() -> {
                    Text(
                        "No raid bosses match “$query”.",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { "${it.catalogue.raidLevel}|${it.catalogue.pokemonId}" }) { boss ->
                        ManualRaidBossRow(
                            boss = boss,
                            starting = state.startingPokemonId == boss.catalogue.pokemonId,
                            enabled = state.startingPokemonId == null && boss.hundoCP != null,
                            onClick = { onBossSelected(boss) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualRaidBossRow(
    boss: ManualRaidBoss,
    starting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("manual_raid_boss_${boss.catalogue.pokemonId}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CounterSprite(urls = boss.spriteUrls, size = 52.dp, type = null) {
                Text("🛡️")
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    boss.catalogue.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${boss.tierLabel} raid" + boss.catalogue.bossCp?.let { " · Boss CP $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    boss.hundoCP?.formatted()?.let { "100% catch CP · $it" }
                        ?: "Catch CP unavailable · can't start",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (starting) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}
