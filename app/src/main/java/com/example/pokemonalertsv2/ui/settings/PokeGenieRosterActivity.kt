package com.example.pokemonalertsv2.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.counters.MIN_PERSONAL_LEVEL
import com.example.pokemonalertsv2.data.gamemaster.GameMasterRepository
import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieRepository
import com.example.pokemonalertsv2.ui.counters.CounterSprite
import com.example.pokemonalertsv2.ui.counters.MoveChips
import com.example.pokemonalertsv2.ui.counters.formatLevel
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Read-only browser for the imported Poké Genie roster.
 *
 * The import used to be a black box — a count, and a review dialog seen once — which is how
 * a missing base form went unnoticed for so long. This screen shows exactly what the counter
 * ranking will see, including the rows the app synthesized and the rows it will skip. Nothing
 * here edits or deletes: the CSV stays the source of truth.
 */
class PokeGenieRosterActivity : ComponentActivity() {

    private val viewModel: PokeGenieRosterViewModel by viewModels()
    private val repository by lazy { PokemonAlertsRepository.create(applicationContext) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by repository.observeThemeMode()
                .collectAsStateWithLifecycle(initialValue = 0)
            val darkTheme = AppThemeMode.fromStored(themeMode)
                .resolveDark(isSystemInDarkTheme())
            PokemonAlertsV2Theme(darkTheme = darkTheme) {
                val rows by viewModel.visible.collectAsStateWithLifecycle()
                val total by viewModel.total.collectAsStateWithLifecycle()
                val query by viewModel.query.collectAsStateWithLifecycle()
                val sort by viewModel.sort.collectAsStateWithLifecycle()
                val hideUnranked by viewModel.hideUnranked.collectAsStateWithLifecycle()
                val spriteUrls by viewModel.spriteUrls.collectAsStateWithLifecycle()
                val types by viewModel.types.collectAsStateWithLifecycle()
                val moveTypes by viewModel.moveTypes.collectAsStateWithLifecycle()

                Scaffold(
                    topBar = {
                        Column {
                            TopAppBar(
                                title = { Text("Imported Pokémon ($total)") },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                )
                            )
                            OutlinedTextField(
                                value = query,
                                onValueChange = viewModel::updateQuery,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                placeholder = { Text("Search name or move…") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    AnimatedContent(
                                        targetState = query.isNotEmpty(),
                                        transitionSpec = { appFadeThrough() },
                                        label = "roster_search_clear"
                                    ) { showClear ->
                                        if (showClear) {
                                            IconButton(onClick = { viewModel.updateQuery("") }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear")
                                            }
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors()
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RosterSort.entries.forEach { option ->
                                    FilterChip(
                                        selected = sort == option,
                                        onClick = { viewModel.updateSort(option) },
                                        label = { Text(option.label) }
                                    )
                                }
                                FilterChip(
                                    selected = hideUnranked,
                                    onClick = { viewModel.toggleHideUnranked() },
                                    label = { Text("L${MIN_PERSONAL_LEVEL.toInt()}+ only") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    if (rows.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (total == 0) {
                                    "No Poké Genie import yet. Import a CSV from settings."
                                } else {
                                    "Nothing matches those filters."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(rows, key = { it.key }) { entry ->
                                RosterRow(
                                    entry = entry,
                                    spriteUrls = spriteUrls[entry.pokemonId].orEmpty(),
                                    type = types[entry.pokemonId]?.firstOrNull(),
                                    moveTypes = moveTypes,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, PokeGenieRosterActivity::class.java)
    }
}

@Composable
private fun RosterRow(
    entry: RosterEntry,
    spriteUrls: List<String>,
    type: String?,
    moveTypes: Map<String, String>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            CounterSprite(urls = spriteUrls, size = 44.dp, type = type)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.statLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MoveChips(entry.moves, moveTypes)
                if (entry.synthesized) {
                    Text(
                        text = "Base form, added from your Mega",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (entry.belowRankingLevel) {
                    Text(
                        text = "Below L${MIN_PERSONAL_LEVEL.toInt()} — not ranked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** How the roster is ordered. */
enum class RosterSort(val label: String) {
    LEVEL("Level"),
    CP("CP"),
    IV("IV"),
    NAME("Name")
}

/** One roster row, with everything the list needs pre-derived. */
data class RosterEntry(
    val key: String,
    val owned: OwnedPokemon,
    val pokemonId: String
) {
    /**
     * A row synthesized by `MegaBaseExpander` has no CP: the mega's CP is wrong for the base
     * form and cannot be derived without base stats, so the expander leaves it out. That is
     * the only marker available, and it is a reliable one — Poké Genie always exports CP.
     */
    val synthesized: Boolean get() = owned.cp == null && owned.level != null

    val belowRankingLevel: Boolean get() = (owned.level ?: 0.0) < MIN_PERSONAL_LEVEL

    val title: String get() = buildString {
        if (owned.shadow) append("Shadow ")
        append(owned.displayName)
        owned.form?.takeIf { it.isNotBlank() && !it.equals("Normal", ignoreCase = true) }
            ?.let { append(" (").append(it).append(")") }
        if (owned.lucky) append(" ✨")
    }

    val statLine: String get() = buildString {
        owned.level?.let { append("L").append(formatLevel(it)) }
        val ivTotal = owned.ivTotal
        if (owned.atkIv != null && owned.defIv != null && owned.staIv != null) {
            if (isNotEmpty()) append(" · ")
            append("${owned.atkIv}/${owned.defIv}/${owned.staIv}")
            if (ivTotal != null) append(" (${ivTotal * 100 / 45}%)")
        }
        owned.cp?.let {
            if (isNotEmpty()) append(" · ")
            append("CP ").append(it)
        }
        if (isEmpty()) append("No stats recorded")
    }

    val moves: List<String> = listOfNotNull(owned.quickMove, owned.chargeMove, owned.chargeMove2)

    /** Everything the search box looks at. */
    val haystack: String = (listOf(owned.displayName, owned.form.orEmpty()) + moves)
        .joinToString(" ")
        .lowercase(Locale.ROOT)
}

class PokeGenieRosterViewModel(application: Application) : AndroidViewModel(application) {

    private val pokeGenie = PokeGenieRepository.getInstance(application)
    private val gameMaster = GameMasterRepository.getInstance(application)

    private val roster = MutableStateFlow<List<RosterEntry>>(emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(RosterSort.LEVEL)
    val sort: StateFlow<RosterSort> = _sort.asStateFlow()

    private val _hideUnranked = MutableStateFlow(false)
    val hideUnranked: StateFlow<Boolean> = _hideUnranked.asStateFlow()

    private val _spriteUrls = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val spriteUrls: StateFlow<Map<String, List<String>>> = _spriteUrls.asStateFlow()

    private val _types = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val types: StateFlow<Map<String, List<String>>> = _types.asStateFlow()

    private val _moveTypes = MutableStateFlow<Map<String, String>>(emptyMap())
    val moveTypes: StateFlow<Map<String, String>> = _moveTypes.asStateFlow()

    val total: StateFlow<Int> = roster
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val visible: StateFlow<List<RosterEntry>> =
        combine(roster, _query, _sort, _hideUnranked) { rows, query, sort, hideUnranked ->
            val needle = query.trim().lowercase(Locale.ROOT)
            rows.asSequence()
                .filter { !hideUnranked || !it.belowRankingLevel }
                .filter { needle.isEmpty() || it.haystack.contains(needle) }
                .sortedWith(comparatorFor(sort))
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val owned = runCatching { pokeGenie.ownedForSimulation() }.getOrDefault(emptyList())
            val entries = owned.mapIndexed { index, mon ->
                RosterEntry(
                    // The roster legitimately holds several copies of one species, so the
                    // import order is the only stable identity a row has.
                    key = "${mon.matchKeys.firstOrNull().orEmpty()}-$index",
                    owned = mon,
                    pokemonId = mon.matchKeys.firstOrNull().orEmpty()
                )
            }
            roster.value = entries

            val ids = entries.map { it.pokemonId }.filter { it.isNotEmpty() }.distinct()
            // Artwork is a nicety; a game-master cache that has not synced yet must not stop
            // the list from rendering.
            runCatching { gameMaster.spriteUrls(ids) }.getOrNull()?.let { _spriteUrls.value = it }
            runCatching { gameMaster.typesFor(ids) }.getOrNull()?.let { _types.value = it }
            runCatching { gameMaster.moveTypesByLabel() }.getOrNull()?.let { _moveTypes.value = it }
        }
    }

    fun updateQuery(value: String) { _query.value = value }

    fun updateSort(value: RosterSort) { _sort.value = value }

    fun toggleHideUnranked() { _hideUnranked.value = !_hideUnranked.value }

    private fun comparatorFor(sort: RosterSort): Comparator<RosterEntry> = when (sort) {
        RosterSort.LEVEL -> compareByDescending<RosterEntry> { it.owned.level ?: -1.0 }
            .thenByDescending { it.owned.ivTotal ?: -1 }
        RosterSort.CP -> compareByDescending<RosterEntry> { it.owned.cp ?: -1 }
            .thenByDescending { it.owned.level ?: -1.0 }
        RosterSort.IV -> compareByDescending<RosterEntry> { it.owned.ivTotal ?: -1 }
            .thenByDescending { it.owned.level ?: -1.0 }
        RosterSort.NAME -> compareBy<RosterEntry> { it.owned.displayName.lowercase(Locale.ROOT) }
            .thenByDescending { it.owned.level ?: -1.0 }
    }
}
