package com.example.pokemonalertsv2.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.FilterStateDocument
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider
import kotlinx.coroutines.launch

/**
 * "Set: Weekend ▾" — switches the active filter set from wherever filters are
 * edited quickly (the feed and map sheets). Hidden while there is only one set,
 * so nothing changes for someone who never made a second one.
 */
@Composable
internal fun FilterSetSwitcher(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember(context) { AlertPreferences(context.applicationContext.alertPreferencesDataStore) }
    val document by remember(preferences) { preferences.filterStateDocument }.collectAsState(initial = FilterStateDocument())
    val sets = document.allSets()
    if (sets.size < 2) return
    val active = document.activeSet
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = { Text(stringResource(R.string.filter_sets_chip, active.name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.testTag("filter_set_switcher")
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sets.forEach { set ->
                DropdownMenuItem(
                    text = { Text(set.name) },
                    leadingIcon = { if (set.id == active.id) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        scope.launch {
                            preferences.updateFilterStateDocument { it.activateSet(set.id) }
                            AlertsWidgetProvider.requestUpdate(context)
                        }
                    },
                    modifier = Modifier.testTag("filter_set_switcher_${set.name}")
                )
            }
        }
    }
}
