@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.pokemonalertsv2.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.data.*

/** Starting point for a new limit when the surface itself has no default distance. */
private const val DEFAULT_SEED_LIMIT_KM = 10

/** Renders a limit the way the rest of the studio talks about distance. */
internal fun distanceLabel(km: Int): String = if (km <= 0) "Unlimited" else "$km km"

/**
 * Editor for limits that are narrower or wider than the surface default.
 * Precedence shown to the user matches [AlertFilterMatcher]: species beats type beats default.
 */
@Composable
internal fun DistanceOverridesDialog(
    definition: FilterDefinition,
    speciesCandidates: List<String>,
    artwork: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (DistanceOverrides) -> Unit
) {
    var overrides by remember(definition) { mutableStateOf(definition.distanceOverrides) }
    var addingSpecies by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(12.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(16.dp)) {
                Text("Distance limits", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Default is ${distanceLabel(definition.maxDistanceKm)}. A species limit wins over an alert type limit, which wins over the default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("By alert type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    FilterAlertType.entries.forEach { type ->
                        DistanceLimitRow(
                            label = type.label,
                            km = overrides.perType[type.name],
                            fallbackLabel = distanceLabel(definition.maxDistanceKm),
                            onChange = { overrides = overrides.withType(type, it) }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("By species", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { addingSpecies = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Add species") }
                    }
                    if (overrides.perSpecies.isEmpty()) {
                        Text("No species limits yet. A species limit applies wherever that species appears, including as a quest reward.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    overrides.perSpecies.entries.sortedBy { it.key }.forEach { (token, km) ->
                        DistanceLimitRow(
                            label = displayNameFor(token, speciesCandidates),
                            km = km,
                            fallbackLabel = distanceLabel(definition.maxDistanceKm),
                            onRemove = { overrides = overrides.copy(perSpecies = overrides.perSpecies - token) },
                            onChange = { overrides = overrides.copy(perSpecies = if (it == null) overrides.perSpecies - token else overrides.perSpecies + (token to it)) }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(overrides) }) { Text("Done") }
                }
            }
        }
    }

    if (addingSpecies) {
        SpeciesPickerDialog(
            candidates = speciesCandidates,
            artwork = artwork,
            already = overrides.perSpecies.keys,
            onDismiss = { addingSpecies = false },
            onPick = { species ->
                // Seed at the surface default so the row starts where the user already is; when
                // the default is unlimited there is no meaningful anchor, so start at 10 km.
                overrides = overrides.withSpecies(species, definition.maxDistanceKm.takeIf { it > 0 } ?: DEFAULT_SEED_LIMIT_KM)
                addingSpecies = false
            }
        )
    }
}

/** Falls back to the stored token when the catalog no longer lists the species. */
private fun displayNameFor(token: String, candidates: List<String>): String =
    candidates.firstOrNull { normalizeFilterToken(it) == token }
        ?: token.split(" ").joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) }

@Composable
private fun DistanceLimitRow(
    label: String,
    km: Int?,
    fallbackLabel: String,
    onChange: (Int?) -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Surface(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (km == null) "Uses default — $fallbackLabel" else distanceLabel(km),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (km == null) {
                    TextButton(onClick = { onChange(DEFAULT_SEED_LIMIT_KM) }, modifier = Modifier.semantics { contentDescription = "Set a limit for $label" }) { Text("Set limit") }
                } else {
                    TextButton(onClick = { onChange(null) }, modifier = Modifier.semantics { contentDescription = "Use default for $label" }) { Text("Use default") }
                }
                onRemove?.let {
                    IconButton(onClick = it, modifier = Modifier.semantics { contentDescription = "Remove $label limit" }) { Icon(Icons.Default.Close, null) }
                }
            }
            if (km != null) {
                Slider(
                    value = km.toFloat(),
                    onValueChange = { onChange(kotlin.math.round(it).toInt()) },
                    valueRange = 0f..MAX_FILTER_DISTANCE_KM.toFloat(),
                    modifier = Modifier.semantics { contentDescription = "$label distance limit" }
                )
            }
        }
    }
}

/** Single-choice species picker, reusing the catalog artwork the studio already loads. */
@Composable
private fun SpeciesPickerDialog(
    candidates: List<String>,
    artwork: Map<String, String>,
    already: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val display = remember(candidates, query, already) {
        val needle = normalizeFilterToken(query)
        candidates.asSequence()
            .filter { normalizeFilterToken(it) !in already }
            .filter { needle.isEmpty() || normalizeFilterToken(it).contains(needle) }
            .sorted()
            .take(300)
            .toList()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(12.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(16.dp)) {
                Text("Add species limit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), label = { Text("Search species") }, singleLine = true)
                LazyColumn(Modifier.weight(1f)) {
                    items(display, key = { it }) { value ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onPick(value) }, modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(artwork[normalizeFilterToken(value)], contentDescription = null, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(value, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}
