package com.example.pokemonalertsv2.ui.settings

import com.example.pokemonalertsv2.util.AppText
import com.example.pokemonalertsv2.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.counters.PokebattlerAuthRepository
import com.example.pokemonalertsv2.data.counters.PokebattlerLoginProvider
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.RaidCounterSettings
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieImportCandidate
import com.example.pokemonalertsv2.ui.auth.PokebattlerLoginActivity
import com.example.pokemonalertsv2.ui.counters.CounterSetupPanel
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import java.text.DateFormat
import java.util.Date
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Defaults for the raid counters card, plus the Poke Genie import.
 *
 * Split out of `SettingsScreen.kt`, which is already very large, following the same
 * pattern as the GoDex collection screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RaidCountersSettingsContent(
    settings: RaidCounterSettings,
    onOptionsChanged: (RaidCounterOptions) -> Unit,
    onPrepareCsv: (android.net.Uri) -> Unit,
    pendingImport: PokeGenieImportCandidate?,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onClearPokeGenie: () -> Unit,
    importStatus: String?
) {
    val options = settings.options
    val context = LocalContext.current
    val auth = remember(context) { PokebattlerAuthRepository.getInstance(context) }
    val account by auth.account.collectAsStateWithLifecycle()
    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // The activity stores the session itself; the account flow delivers the result.
    }

    // The picker grants temporary access; prepare the bytes while that grant is valid, then
    // hold only the parsed candidate until the user confirms replacement.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPrepareCsv)
    }

    // The counters screen edits its setup in a panel; the defaults here use the same rows, so a
    // setting looks the same in both places.
    run {
        Column {
            Text(
                text = stringResource(R.string.settings_counter_defaults),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.settings_used_when_a_raid_opens_you),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CounterSetupPanel(
                options = options,
                onOptionsChanged = onOptionsChanged,
                scroll = false,
                // The page already has a 16dp margin; the panel brings its own 20dp gutter.
                modifier = Modifier.bleed(16.dp)
            )
        }
    }

    SettingsSection(title = stringResource(R.string.counters_my_pok_mon)) {
        Text(
            text = if (settings.pokeGenieCount > 0) {
                "Pokébattler scores each imported Pokémon at its recorded level and moves. " +
                    "IVs are intentionally ignored because anonymous Pokébattler by-level " +
                    "results use perfect IVs."
            } else {
                "Import your Poké Genie scan history to rank your Pokémon with Pokébattler."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (settings.pokeGenieCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.settings_x_pok_mon, settings.pokeGenieCount)) }
                )
                settings.pokeGenieImportedAtMillis.takeIf { it > 0 }?.let { millis ->
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
                            )
                        }
                    )
                }
            }
            settings.pokeGenieFileName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        importStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Three buttons no longer fit one line on a narrow screen, and a Row breaks "Clear"
        // across two lines mid-word rather than wrapping.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { picker.launch(POKE_GENIE_MIME_TYPES) }) {
                Text(if (settings.pokeGenieCount > 0) stringResource(R.string.settings_re_import_csv) else stringResource(R.string.settings_import_csv))
            }
            if (settings.pokeGenieCount > 0) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(PokeGenieRosterActivity.createIntent(context))
                    }
                ) { Text(stringResource(R.string.settings_view_roster)) }
                var confirmClear by remember { androidx.compose.runtime.mutableStateOf(false) }
                OutlinedButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.catchroutes_clear)) }
                if (confirmClear) {
                    AlertDialog(
                        onDismissRequest = { confirmClear = false },
                        title = { Text(stringResource(R.string.settings_clear_your_pok_genie_roster)) },
                        text = { Text(stringResource(R.string.settings_counters_go_back_to_generic_teams)) },
                        confirmButton = {
                            TextButton(onClick = { confirmClear = false; onClearPokeGenie() }) { Text(stringResource(R.string.catchroutes_clear)) }
                        },
                        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } }
                    )
                }
            }
        }

        Text(
            text = "In Poké Genie: Menu → Export/Backup → Export scan data to CSV, " +
                "then choose Pokémon Alerts. You can also pick the file here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    pendingImport?.let { candidate ->
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text(stringResource(R.string.settings_review_csv_import)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(candidate.fileName ?: "Poké Genie export")
                    Text(stringResource(R.string.settings_x_pok_mon_rows_are_ready, candidate.summary.importedCount))
                    if (candidate.summary.synthesizedBaseCount > 0) {
                        Text(
                            "${candidate.summary.synthesizedBaseCount} base forms were added " +
                                "for your megas, so both forms show up as counters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (candidate.summary.skippedCount > 0) {
                        Text(stringResource(R.string.settings_x_rows_will_be_skipped, candidate.summary.skippedCount))
                    }
                    // Named, not just counted: an unrecognised form is almost always a
                    // spelling this app has not learned yet, and the pair is what makes it
                    // reportable.
                    val unmatched = candidate.summary.unmatchedForms
                    if (unmatched.isNotEmpty()) {
                        val missed = candidate.summary.importedCount - candidate.summary.matchedCount
                        Text(
                            "$missed of ${candidate.summary.importedCount} won't be usable as " +
                                "counters — no Pokébattler data for them:",
                            color = MaterialTheme.colorScheme.error
                        )
                        unmatched.take(5).forEach { form ->
                            val label = form.form?.let { "${form.name} ($it)" } ?: form.name
                            Text(
                                if (form.count > 1) "• $label ×${form.count}" else "• $label",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (unmatched.size > 5) {
                            Text(
                                stringResource(R.string.settings_and_x_more, unmatched.size - 5),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (!candidate.summary.formsChecked) {
                        Text(
                            stringResource(R.string.settings_pok_mon_data_hasn_t_downloaded),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (settings.pokeGenieCount > 0) {
                        Text(
                            stringResource(R.string.settings_confirming_replaces_the_current_x_pok, settings.pokeGenieCount),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = onConfirmImport) {
                    Text(if (settings.pokeGenieCount > 0) AppText.get(R.string.settings_replace_roster) else stringResource(R.string.settings_import))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelImport) { Text(stringResource(R.string.mainscaffoldkt_cancel)) }
            }
        )

    }

    Text(
        text = stringResource(R.string.settings_pok_battler_pok_box_optional),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    Text(
        text = if (account == null) {
            "Sign in to rank the Pokémon saved in your Pokébattler Pokébox, using their real " +
                "IVs and movesets. There is no public Pokébox API, so this needs an account."
        } else {
            "Ranked from your Pokébox with its exact IVs and movesets."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    account?.let { linked ->
        Text(
            text = stringResource(R.string.settings_signed_in_as_x, linked.displayName ?: linked.userId),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    // Four sign-in providers do not fit one line on a narrow phone.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (account == null) {
            PokebattlerLoginProvider.entries.forEach { provider ->
                TextButton(
                    onClick = { signIn.launch(PokebattlerLoginActivity.intent(context, provider)) }
                ) { Text(provider.label) }
            }
        } else {
            var confirmSignOut by remember { androidx.compose.runtime.mutableStateOf(false) }
            TextButton(onClick = { confirmSignOut = true }) { Text(stringResource(R.string.settings_sign_out)) }
            if (confirmSignOut) {
                AlertDialog(
                    onDismissRequest = { confirmSignOut = false },
                    title = { Text(stringResource(R.string.settings_sign_out_of_pokebattler)) },
                    text = { Text(stringResource(R.string.settings_pok_box_counters_need_you_to)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmSignOut = false
                            auth.signOut()
                            // Without this the WebView would silently sign straight back in.
                            PokebattlerLoginActivity.clearWebSession()
                        }) { Text(stringResource(R.string.settings_sign_out)) }
                    },
                    dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.mainscaffoldkt_cancel)) } }
                )
            }
        }
    }
}

/** Lays the content out [horizontal] wider on each side than its parent's padding allows. */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
        )
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-horizontal.roundToPx(), 0) }
}

/**
 * The in-app picker stays broad for mislabeled exports. The external ACTION_VIEW filter is
 * intentionally narrower so unrelated text files do not offer this app.
 */
private val POKE_GENIE_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "application/csv",
    "text/plain",
    "*/*"
)
