package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity

@Composable
fun GoDexCatchTargetDialog(
    pokemonName: String,
    matchResult: GoDexMatchResult,
    onDismiss: () -> Unit,
    onConfirm: (entryKey: String) -> Unit
) {
    var selectedKey by remember {
        mutableStateOf(matchResult.matchedEntryKey ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "What did you catch this for?") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Select which Pokémon checklist item to mark as caught on GoDex:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Option 1: Base Pokemon (e.g. Bulbasaur)
                matchResult.matchedEntryKey?.let { baseKey ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedKey = baseKey }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedKey == baseKey),
                            onClick = { selectedKey = baseKey }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "$pokemonName (Base)")
                    }
                }

                // Option 2: Evolution Targets
                if (matchResult.evolutionTargets.isNotEmpty()) {
                    Text(
                        text = "Needed Evolutions:",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    matchResult.evolutionTargets.forEach { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedKey = target.entryKey }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedKey == target.entryKey),
                                onClick = { selectedKey = target.entryKey }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = target.displayName)
                        }
                    }
                }

                // Option 3: Form Change Targets
                if (matchResult.formChangeTargets.isNotEmpty()) {
                    Text(
                        text = "Needed Forms:",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    matchResult.formChangeTargets.forEach { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedKey = target.entryKey }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedKey == target.entryKey),
                                onClick = { selectedKey = target.entryKey }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = target.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedKey.isNotEmpty()) {
                        onConfirm(selectedKey)
                    }
                },
                enabled = selectedKey.isNotEmpty()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GoDexUncatchTargetDialog(
    pokemonName: String,
    caughtEntries: List<GoDexEntryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (entryKey: String) -> Unit
) {
    var selectedKey by remember {
        mutableStateOf(caughtEntries.firstOrNull()?.entryKey ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Remove Caught Status") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Select which Pokémon checklist item to mark as needed (uncaught) on GoDex:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                caughtEntries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedKey = entry.entryKey }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedKey == entry.entryKey),
                            onClick = { selectedKey = entry.entryKey }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = entry.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedKey.isNotEmpty()) {
                        onConfirm(selectedKey)
                    }
                },
                enabled = selectedKey.isNotEmpty()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
