package com.example.pokemonalertsv2.hunt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import java.util.Date

/**
 * Every hunt that ended with something worth a summary: the totals across them, and each
 * one's own summary a tap away. Its own screen so Back from it returns to Tools.
 */
class HuntHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by remember { AlertPreferences(alertPreferencesDataStore).themeMode }
                .collectAsStateWithLifecycle(initialValue = 0)
            PokemonAlertsV2Theme(darkTheme = AppThemeMode.fromStored(themeMode).resolveDark(isSystemInDarkTheme())) {
                HuntHistoryScreen(onBack = ::finish)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HuntHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { HuntRepository.getInstance(context) }
    val history by repository.history.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var opened by remember { mutableStateOf<HuntSummary?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag("hunt_history"),
        topBar = {
            TopAppBar(
                title = { Text("Hunt history") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Clear history") },
                                    onClick = {
                                        menuOpen = false
                                        confirmClear = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No hunts yet. Finished hunts show up here with what you caught.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            return@Scaffold
        }
        val totals = remember(history) { huntHistoryTotals(history) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(totals.hunts.toString(), "Hunts", Modifier.weight(1f))
                    StatTile(totals.caught.toString(), "Caught", Modifier.weight(1f))
                    StatTile(formatHuntDistance(totals.distanceMeters), "Walked", Modifier.weight(1f))
                }
            }
            item {
                val shinies = remember(history) { history.sumOf { hunt -> hunt.catches.count { it.shiny } } }
                Text(
                    text = "${formatHuntDuration(totals.activeMillis)} hunted in total" +
                        if (shinies > 0) " · $shinies shiny" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            val months = history.groupBy { monthLabel(it.startedAtMillis) }
            months.forEach { (month, hunts) ->
                item(key = "month-$month") {
                    Text(
                        text = month,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
                    )
                }
                items(hunts, key = { it.startedAtMillis }) { hunt ->
                    HuntHistoryRow(hunt, onClick = { opened = hunt })
                }
            }
        }
    }

    opened?.let { summary ->
        HuntSummarySheet(
            summary,
            doneLabel = "Close",
            title = DateFormat.getDateInstance(DateFormat.FULL).format(Date(summary.startedAtMillis))
        ) { opened = null }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear hunt history?") },
            text = { Text("Every past hunt and its catches are removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { repository.clearHistory() }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HuntHistoryRow(hunt: HuntSummary, onClick: () -> Unit) {
    val track = remember(hunt.track) { hunt.trackPoints }
    val catchPoints = remember(hunt.catches) { hunt.catches.mapNotNull { it.point } }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("hunt_history_row")
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateBadge(hunt.startedAtMillis)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = hunt.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = huntTimeRange(hunt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (track.size >= 2 || catchPoints.size >= 2) {
                    HuntPathThumbnail(track, catchPoints, Modifier.size(width = 72.dp, height = 56.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatPill("${hunt.catches.size} caught")
                StatPill(formatHuntDistance(hunt.distanceMeters), icon = Icons.Filled.LocationOn)
                StatPill(formatHuntDuration(hunt.activeMillis))
                hunt.watch?.averageHeartRate?.let {
                    StatPill("$it", icon = Icons.Filled.Favorite, tint = HeartRed,
                        container = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
            }
            if (hunt.catches.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatchSpriteStack(hunt.catches, Modifier.weight(1f, fill = false))
                    val shinies = hunt.catches.count { it.shiny }
                    val hundos = hunt.catches.count { it.isHundo }
                    if (shinies > 0) StatPill("$shinies", icon = Icons.Filled.Star, tint = Color.Black, container = ShinyGold)
                    if (hundos > 0) StatPill("$hundos × 100%", tint = Color.White, container = HundoPink)
                }
            }
        }
    }
}

/** The hunt's day as a calendar tile: "SAT" over "9". */
@Composable
private fun DateBadge(millis: Long) {
    val day = remember(millis) { SimpleDateFormat("d", Locale.getDefault()).format(Date(millis)) }
    val weekday = remember(millis) { SimpleDateFormat("EEE", Locale.getDefault()).format(Date(millis)).uppercase(Locale.getDefault()) }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            Modifier.size(width = 52.dp, height = 56.dp).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(weekday, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(day, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** "November 2026", for the history's month headers. */
internal fun monthLabel(millis: Long): String =
    SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(millis))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
