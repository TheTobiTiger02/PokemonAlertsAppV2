package com.example.pokemonalertsv2.ui.counters

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.counters.CounterSourceId
import com.example.pokemonalertsv2.data.counters.CounterMetric
import com.example.pokemonalertsv2.data.counters.DecoratedCounter
import com.example.pokemonalertsv2.data.counters.PersonalCounter
import com.example.pokemonalertsv2.data.counters.PersonalMovesMode
import com.example.pokemonalertsv2.data.counters.PersonalRanking
import com.example.pokemonalertsv2.data.counters.PokebattlerFriendship
import com.example.pokemonalertsv2.data.counters.PokebattlerSort
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounter
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.RaidBossMoveset
import com.example.pokemonalertsv2.data.counters.prettifyMoveName
import java.util.Locale
import com.example.pokemonalertsv2.data.counters.toCounterMetric

/**
 * Best counters for a raid boss.
 *
 * Two modes. "Pokébattler" is its generic ranking of level-N attackers, with the ones the
 * user owns highlighted. "My Pokémon" joins a CSV or Pokébox roster to Pokébattler's own
 * server metrics; the former uses one exact-level request per distinct imported level.
 *
 * Kept out of `SharedComponents.kt`, which is already past 3000 lines, but deliberately
 * mirrors the card styling of its neighbours there. This file holds only the shell and the
 * state switch; the header lives in `CountersHeader.kt`, the battle setup in
 * `CountersSetup.kt`, the lists in `CounterRows.kt` and the shared pieces in
 * `CountersAtoms.kt`.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RaidCountersCard(
    state: RaidCountersUiState,
    actions: RaidCountersActions,
    modifier: Modifier = Modifier
) {
    if (!state.visible) return

    var setupSheetOpen by rememberSaveable { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Header(state)

            Spacer(modifier = Modifier.height(12.dp))
            SourceSelector(state, actions)

            Spacer(modifier = Modifier.height(10.dp))
            BattleSetupSummary(state, onOpen = { setupSheetOpen = true })
            // Pokébattler serves precomputed rankings only, and for a boss outside the
            // current raid rotation just one combination exists. Rather than show nothing,
            // the repository retries at that baseline — so say which settings were dropped
            // instead of quietly changing the numbers under the user.
            if (state.degradedOptions.isNotEmpty()) {
                Text(
                    text = "Pokébattler has no ranking for this boss at " +
                        state.degradedOptions.joinToString(" · ") +
                        ". Showing its level 40, no-friendship results.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (state.hasCounters && (state.isLoading || state.isStale || state.rateLimited)) {
                InlineRefreshStatus(state, actions)
            }
            Spacer(modifier = Modifier.height(10.dp))
            QuickControls(state, actions)
            Spacer(modifier = Modifier.height(14.dp))

            // Cross-fade rather than snap: loading -> list and a source switch are the two
            // transitions the user triggers most, and both used to swap the whole body in
            // one frame. The phase, not the state, is the target so that a metric change
            // inside a phase does not replay the transition.
            AnimatedContent(
                targetState = state.phase,
                transitionSpec = { appFadeThrough() },
                label = "counters_phase"
            ) { phase ->
                when (phase) {
                    CountersPhase.UNRESOLVED -> UnresolvedState(state, actions)
                    CountersPhase.LOADING -> CounterSkeletonList("Loading counters…")
                    CountersPhase.ERROR -> ErrorState(state, actions)
                    CountersPhase.PERSONAL -> PersonalContent(state, actions)
                    CountersPhase.GENERAL -> GeneralList(state, actions)
                    CountersPhase.EMPTY -> EmptyState(state)
                }
            }

            CountersFooter(state)
        }
    }

    // `expanded` used to grow the inline list; it is now purely "the full list is open".
    if (state.expanded) {
        ModalBottomSheet(
            onDismissRequest = actions.onToggleExpanded,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AllCountersSheet(state, actions)
        }
    }

    if (setupSheetOpen) {
        ModalBottomSheet(onDismissRequest = { setupSheetOpen = false }) {
            BattleSetupSheet(
                state = state,
                actions = actions,
                onDismiss = { setupSheetOpen = false }
            )
        }
    }
}

