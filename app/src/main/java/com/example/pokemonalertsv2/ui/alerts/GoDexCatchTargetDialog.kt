package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import kotlinx.coroutines.launch

internal enum class GoDexCatchTargetKind(val label: String) {
    DIRECT("This Pokémon"),
    EVOLUTION("Evolution"),
    FORM_CHANGE("Form change")
}

internal data class GoDexCatchOption(
    val entryKey: String,
    val displayName: String,
    val kind: GoDexCatchTargetKind,
    val distance: Int = 0
) {
    val supportingLabel: String
        get() = when (kind) {
            GoDexCatchTargetKind.DIRECT -> kind.label
            GoDexCatchTargetKind.EVOLUTION ->
                if (distance <= 1) "Evolution" else "$distance evolutions away"
            GoDexCatchTargetKind.FORM_CHANGE -> "Form change"
        }
}

internal fun goDexCatchOptions(
    pokemonName: String,
    matchResult: GoDexMatchResult
): List<GoDexCatchOption> = buildList {
    if (matchResult.status == GoDexMatchStatus.NEEDED) {
        matchResult.matchedEntryKey?.let { key ->
            add(GoDexCatchOption(key, pokemonName, GoDexCatchTargetKind.DIRECT))
        }
    }
    matchResult.evolutionTargets.forEach { target ->
        add(
            GoDexCatchOption(
                entryKey = target.entryKey,
                displayName = target.displayName,
                kind = GoDexCatchTargetKind.EVOLUTION,
                distance = target.distance
            )
        )
    }
    matchResult.formChangeTargets.forEach { target ->
        add(
            GoDexCatchOption(
                entryKey = target.entryKey,
                displayName = target.displayName,
                kind = GoDexCatchTargetKind.FORM_CHANGE,
                distance = target.distance
            )
        )
    }
}.distinctBy(GoDexCatchOption::entryKey)

internal fun initialGoDexTargetSelection(entryKeys: List<String>): String? =
    entryKeys.singleOrNull()

@Composable
fun GoDexCatchTargetDialog(
    pokemonName: String,
    matchResult: GoDexMatchResult,
    onDismiss: () -> Unit,
    onConfirm: (entryKey: String) -> Unit
) {
    val options = remember(pokemonName, matchResult) {
        goDexCatchOptions(pokemonName, matchResult)
    }
    var selectedKey by remember(options) {
        mutableStateOf(initialGoDexTargetSelection(options.map(GoDexCatchOption::entryKey)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to GoDex checklist") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Which missing checklist entry does this Hundo fill?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                options.forEach { option ->
                    GoDexTargetRow(
                        title = option.displayName,
                        subtitle = option.supportingLabel,
                        selected = selectedKey == option.entryKey,
                        actionLabel = "Mark caught",
                        onClick = { selectedKey = option.entryKey }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedKey?.let(onConfirm) },
                enabled = selectedKey != null,
                modifier = Modifier.semantics {
                    contentDescription = "Confirm GoDex caught change"
                }
            ) {
                Text("Mark caught")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun GoDexUncatchTargetDialog(
    caughtEntries: List<GoDexEntryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (entryKey: String) -> Unit
) {
    var selectedKey by remember(caughtEntries) {
        mutableStateOf(initialGoDexTargetSelection(caughtEntries.map(GoDexEntryEntity::entryKey)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark as still needed") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Choose the exact GoDex entry to put back on your needed list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                caughtEntries.forEach { entry ->
                    val variant = buildList {
                        entry.formSlug?.let { add(it.replace('_', ' ')) }
                        entry.gender.takeUnless { it == "none" }?.let(::add)
                    }.joinToString(" \u2022 ")
                    GoDexTargetRow(
                        title = entry.displayName,
                        subtitle = variant.ifBlank { "Caught in GoDex" },
                        selected = selectedKey == entry.entryKey,
                        actionLabel = "Mark needed",
                        onClick = { selectedKey = entry.entryKey }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedKey?.let(onConfirm) },
                enabled = selectedKey != null,
                modifier = Modifier.semantics {
                    contentDescription = "Confirm GoDex needed change"
                }
            ) {
                Text("Mark needed")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun GoDexTargetRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (selected) "Selected" else actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

internal enum class GoDexCaughtActionPresentation {
    ICON,
    LABELED
}

/**
 * Shared exact-entry GoDex mutation control used by alert cards, details, and map sheets.
 */
@Composable
internal fun GoDexCaughtAction(
    alert: PokemonAlert,
    matchResult: GoDexMatchResult,
    modifier: Modifier = Modifier,
    presentation: GoDexCaughtActionPresentation = GoDexCaughtActionPresentation.ICON
) {
    val context = LocalContext.current
    val repository = remember(context) { GoDexRepository.getInstance(context) }
    val config by repository.config.collectAsState()
    val entries by repository.entries.collectAsState()
    val pendingEntryKeys by repository.pendingEntryKeys.collectAsState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var showCatchDialog by remember(alert.uniqueId) { mutableStateOf(false) }
    var showUncatchDialog by remember(alert.uniqueId) { mutableStateOf(false) }
    var uncatchEntries by remember(alert.uniqueId) {
        mutableStateOf<List<GoDexEntryEntity>>(emptyList())
    }

    if (!alert.hasType("hundo") || !config.hasWriteBackUrl) return

    val catchOptions = remember(alert.pokemon, matchResult) {
        goDexCatchOptions(alert.pokemon ?: "Pokémon", matchResult)
    }
    val caughtEntries = remember(alert, entries) {
        repository.getCaughtEntries(alert, entries)
    }
    val relatedKeys = remember(matchResult, caughtEntries) {
        buildSet {
            matchResult.matchedEntryKey?.let(::add)
            matchResult.evolutionTargets.mapTo(this) { it.entryKey }
            matchResult.formChangeTargets.mapTo(this) { it.entryKey }
            caughtEntries.mapTo(this) { it.entryKey }
        }
    }
    val isPending = pendingEntryKeys.any(relatedKeys::contains)
    val isCollected = matchResult.status == GoDexMatchStatus.COLLECTED
    val hasCatchAction = catchOptions.isNotEmpty()
    val hasUncatchAction = isCollected && caughtEntries.isNotEmpty()
    if (!hasCatchAction && !hasUncatchAction) return

    fun queueChange(entryKey: String, caught: Boolean) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch { repository.markAsCaught(entryKey, caught) }
    }

    val targetName = catchOptions.singleOrNull()?.displayName
        ?: caughtEntries.singleOrNull()?.displayName
    val actionLabel = when {
        isPending && config.sessionState == GoDexSessionState.REAUTH_REQUIRED ->
            "Queued \u2022 Sign in to sync"
        isPending -> "Sending to GoDex\u2026"
        hasUncatchAction -> "Mark as still needed"
        catchOptions.size == 1 -> "Mark ${targetName ?: "target"} caught"
        else -> "Choose caught target"
    }
    val contentDescription = when {
        isPending -> "GoDex checklist change pending"
        hasUncatchAction -> "Mark ${targetName ?: "Pokémon"} as needed in GoDex"
        catchOptions.size == 1 -> "Mark ${targetName ?: "Pokémon"} as caught in GoDex"
        else -> "Choose which GoDex entry to mark as caught"
    }
    val onClick = {
        when {
            hasUncatchAction -> {
                uncatchEntries = caughtEntries
                showUncatchDialog = true
            }
            hasCatchAction -> showCatchDialog = true
        }
    }

    val semanticsModifier = modifier.semantics {
        this.contentDescription = contentDescription
        stateDescription = when {
            isPending -> "Sync pending"
            hasUncatchAction -> "Caught"
            else -> "Needed"
        }
    }
    when (presentation) {
        GoDexCaughtActionPresentation.ICON -> {
            FilledIconButton(
                onClick = onClick,
                modifier = semanticsModifier.size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = when {
                        isPending -> MaterialTheme.colorScheme.tertiaryContainer
                        hasUncatchAction -> Color(0xFF2E7D32).copy(alpha = 0.16f)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = when {
                        isPending -> MaterialTheme.colorScheme.onTertiaryContainer
                        hasUncatchAction -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            ) {
                if (isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else {
                    Icon(
                        imageVector = if (hasUncatchAction) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Outlined.CheckCircle
                        },
                        contentDescription = null
                    )
                }
            }
        }
        GoDexCaughtActionPresentation.LABELED -> {
            FilledTonalButton(
                onClick = onClick,
                modifier = semanticsModifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isPending) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                )
            ) {
                if (isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (hasUncatchAction) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Outlined.CheckCircle
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = actionLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showCatchDialog) {
        GoDexCatchTargetDialog(
            pokemonName = alert.pokemon ?: "Pokémon",
            matchResult = matchResult,
            onDismiss = { showCatchDialog = false },
            onConfirm = { entryKey ->
                showCatchDialog = false
                queueChange(entryKey, true)
            }
        )
    }
    if (showUncatchDialog) {
        GoDexUncatchTargetDialog(
            caughtEntries = uncatchEntries,
            onDismiss = { showUncatchDialog = false },
            onConfirm = { entryKey ->
                showUncatchDialog = false
                queueChange(entryKey, false)
            }
        )
    }
}
