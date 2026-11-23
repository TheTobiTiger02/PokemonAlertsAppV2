package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.checklists.ChecklistBadge
import com.example.pokemonalertsv2.data.checklists.ChecklistKind
import com.example.pokemonalertsv2.data.checklists.ChecklistMatcher
import com.example.pokemonalertsv2.data.checklists.ChecklistRepository
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.ui.checklists.kindLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The checklists that still need [alert]'s Pokémon (those switched on for alerts). */
@Composable
internal fun rememberChecklistBadges(alert: PokemonAlert): List<ChecklistBadge> {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { ChecklistRepository.getInstance(context) }
    val data by repository.alertData.collectAsStateWithLifecycle()
    if (data.checklists.isEmpty() || alert.pokedexId == null) return emptyList()
    val graph = remember(context) { GoDexRepository.getInstance(context).evolutionGraph }
    val badges by produceState(emptyList<ChecklistBadge>(), alert.uniqueId, alert.pokedexId, data) {
        value = withContext(Dispatchers.Default) { ChecklistMatcher.badges(alert, data.checklists, data.entries, graph) }
    }
    return badges
}

@Composable
private fun badgeText(badge: ChecklistBadge): String {
    val name = if (badge.kind == ChecklistKind.ANY) badge.title else kindLabel(badge.kind)
    return stringResource(if (badge.viaEvolution) R.string.checklists_badge_evolution else R.string.checklists_badge_needed, name)
}

@Composable
internal fun ChecklistBadgePills(badges: List<ChecklistBadge>) {
    badges.map { badgeText(it) }.distinct().forEach { text ->
        AlertPill(text = text, icon = Icons.Filled.Star, isPrimary = true)
    }
}

/** The badges as one line for the compact feed card, or null when there are none. */
@Composable
internal fun checklistBadgeLine(badges: List<ChecklistBadge>): String? {
    if (badges.isEmpty()) return null
    // Two Shiny checklists read as one "Shiny needed".
    return "★ " + badges.map { badgeText(it) }.distinct().joinToString(" · ")
}
