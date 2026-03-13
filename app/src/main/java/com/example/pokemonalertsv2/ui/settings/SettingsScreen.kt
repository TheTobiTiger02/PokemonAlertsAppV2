package com.example.pokemonalertsv2.ui.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.ui.theme.AuroraGradientEnd
import com.example.pokemonalertsv2.ui.theme.AuroraGradientMid
import com.example.pokemonalertsv2.ui.theme.AuroraGradientStart
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val raidsNotifications by viewModel.raidsNotifications.collectAsStateWithLifecycle(initialValue = true)
    val spawnsNotifications by viewModel.spawnsNotifications.collectAsStateWithLifecycle(initialValue = true)
    val questsNotifications by viewModel.questsNotifications.collectAsStateWithLifecycle(initialValue = true)
    val hundosNotifications by viewModel.hundosNotifications.collectAsStateWithLifecycle(initialValue = true)
    val pvpNotifications by viewModel.pvpNotifications.collectAsStateWithLifecycle(initialValue = true)
    val nundosNotifications by viewModel.nundosNotifications.collectAsStateWithLifecycle(initialValue = true)
    val kecleonNotifications by viewModel.kecleonNotifications.collectAsStateWithLifecycle(initialValue = true)
    val rocketNotifications by viewModel.rocketNotifications.collectAsStateWithLifecycle(initialValue = true)
    val notificationVibrate by viewModel.notificationVibrate.collectAsStateWithLifecycle(initialValue = true)
    val silenceUntil by viewModel.silenceUntil.collectAsStateWithLifecycle(initialValue = 0L)
    
    // Excluded types for granular filtering
    val excludedHundoTypes by viewModel.excludedHundoTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedNundoTypes by viewModel.excludedNundoTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedPvpTypes by viewModel.excludedPvpTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedSpawnTypes by viewModel.excludedSpawnTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedRocketTypes by viewModel.excludedRocketTypes.collectAsStateWithLifecycle(initialValue = emptySet())
    val excludedRaidTiers by viewModel.excludedRaidTiers.collectAsStateWithLifecycle(initialValue = emptySet())

    val containerGradient = remember {
        Brush.verticalGradient(
            listOf(
                AuroraGradientStart,
                AuroraGradientMid,
                AuroraGradientEnd.copy(alpha = 0.85f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(containerGradient)
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        FilledIconButton(onClick = onBackClick, shape = CircleShape) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        scrolledContainerColor = MaterialTheme.colorScheme.primary
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingsSection(title = "Notifications") {
                    SwitchSetting(
                        title = "Enable Notifications",
                        subtitle = "Receive alerts for new Pokemon nearby",
                        checked = notificationsEnabled,
                        onCheckedChange = { viewModel.updateNotificationsEnabled(it) }
                    )
                    
                    if (notificationsEnabled) {
                        SwitchSetting(
                            title = "Raids",
                            subtitle = "Notifications for raid battles",
                            checked = raidsNotifications,
                            onCheckedChange = { viewModel.updateRaidsNotifications(it) }
                        )
                        if (raidsNotifications) {
                            TypeFilterSection(
                                title = "Exclude Raid Tiers",
                                types = listOf("1", "3", "5", "Mega", "Elite", "Primal"),
                                excludedTypes = excludedRaidTiers,
                                onToggleType = { viewModel.toggleExcludedRaidTier(it) }
                            )
                        }
                        
                        SwitchSetting(
                            title = "Spawns",
                            subtitle = "Notifications for wild spawns",
                            checked = spawnsNotifications,
                            onCheckedChange = { viewModel.updateSpawnsNotifications(it) }
                        )
                        if (spawnsNotifications) {
                            TypeFilterSection(
                                title = "Exclude Pokemon Types",
                                types = POKEMON_TYPES,
                                excludedTypes = excludedSpawnTypes,
                                onToggleType = { viewModel.toggleExcludedSpawnType(it) }
                            )
                        }
                        
                        SwitchSetting(
                            title = "Quests",
                            subtitle = "Notifications for field research",
                            checked = questsNotifications,
                            onCheckedChange = { viewModel.updateQuestsNotifications(it) }
                        )
                        
                        SwitchSetting(
                            title = "Hundos",
                            subtitle = "Notifications for 100% IV Pokémon",
                            checked = hundosNotifications,
                            onCheckedChange = { viewModel.updateHundosNotifications(it) }
                        )
                        if (hundosNotifications) {
                            TypeFilterSection(
                                title = "Exclude Pokemon Types",
                                types = POKEMON_TYPES,
                                excludedTypes = excludedHundoTypes,
                                onToggleType = { viewModel.toggleExcludedHundoType(it) }
                            )
                        }
                        
                        SwitchSetting(
                            title = "PvP",
                            subtitle = "Notifications for PvP ranked Pokémon",
                            checked = pvpNotifications,
                            onCheckedChange = { viewModel.updatePvpNotifications(it) }
                        )
                        if (pvpNotifications) {
                            TypeFilterSection(
                                title = "Exclude Pokemon Types",
                                types = POKEMON_TYPES,
                                excludedTypes = excludedPvpTypes,
                                onToggleType = { viewModel.toggleExcludedPvpType(it) }
                            )
                        }
                        
                        SwitchSetting(
                            title = "Nundos",
                            subtitle = "Notifications for 0% IV Pokémon",
                            checked = nundosNotifications,
                            onCheckedChange = { viewModel.updateNundosNotifications(it) }
                        )
                        if (nundosNotifications) {
                            TypeFilterSection(
                                title = "Exclude Pokemon Types",
                                types = POKEMON_TYPES,
                                excludedTypes = excludedNundoTypes,
                                onToggleType = { viewModel.toggleExcludedNundoType(it) }
                            )
                        }
                        
                        SwitchSetting(
                            title = "Kecleon",
                            subtitle = "Notifications for Kecleon sightings",
                            checked = kecleonNotifications,
                            onCheckedChange = { viewModel.updateKecleonNotifications(it) }
                        )
                        
                        SwitchSetting(
                            title = "Rocket",
                            subtitle = "Notifications for Team GO Rocket",
                            checked = rocketNotifications,
                            onCheckedChange = { viewModel.updateRocketNotifications(it) }
                        )
                        if (rocketNotifications) {
                            TypeFilterSection(
                                title = "Exclude Grunt Types",
                                types = ROCKET_GRUNT_TYPES,
                                excludedTypes = excludedRocketTypes,
                                onToggleType = { viewModel.toggleExcludedRocketType(it) }
                            )
                        }
                        
                        SwitchSetting(
                            title = "Vibration",
                            subtitle = "Vibrate when receiving notifications",
                            checked = notificationVibrate,
                            onCheckedChange = { viewModel.updateNotificationVibrate(it) }
                        )
                        
                        // Silence notifications section
                        SilenceNotificationsCard(
                            silenceUntil = silenceUntil,
                            onSilenceFor = { minutes -> viewModel.silenceNotificationsFor(minutes) },
                            onClearSilence = { viewModel.clearNotificationSilence() }
                        )
                    }
                }
                
                SettingsSection(title = "About") {
                   Text(
                       text = "PokemonAlerts v1.0.0",
                       style = MaterialTheme.typography.bodyMedium,
                       color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                       modifier = Modifier.padding(vertical = 8.dp)
                   )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            fontWeight = FontWeight.Bold
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.1f), MaterialTheme.shapes.medium)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SwitchSetting(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SilenceNotificationsCard(
    silenceUntil: Long,
    onSilenceFor: (Int) -> Unit,
    onClearSilence: () -> Unit
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val isSilenced = silenceUntil > now
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSilenced) 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = if (isSilenced) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Silence Notifications",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            if (isSilenced) {
                val remainingMinutes = ((silenceUntil - now) / 60000).toInt()
                val hours = remainingMinutes / 60
                val minutes = remainingMinutes % 60
                
                val timeText = when {
                    hours > 0 && minutes > 0 -> "$hours hr $minutes min"
                    hours > 0 -> "$hours hr"
                    else -> "$minutes min"
                }
                
                Text(
                    text = "Silenced for $timeText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
                
                FilledTonalButton(
                    onClick = onClearSilence,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Clear Silence")
                }
            } else {
                Text(
                    text = "Temporarily silence all notifications",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Quick duration buttons - 2x2 grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSilenceFor(30) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("30 min")
                        }
                        OutlinedButton(
                            onClick = { onSilenceFor(60) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("1 hour")
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSilenceFor(120) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("2 hours")
                        }
                        OutlinedButton(
                            onClick = { onSilenceFor(480) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("8 hours")
                        }
                    }
                }
                
                // Custom time section
                var showCustomDialog by remember { mutableStateOf(false) }
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    
                    OutlinedButton(
                        onClick = { showCustomDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Set Custom Duration or Time")
                    }
                }
                
                if (showCustomDialog) {
                    CustomSilenceDialog(
                        onDismiss = { showCustomDialog = false },
                        onSilenceFor = { minutes ->
                            onSilenceFor(minutes)
                            showCustomDialog = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomSilenceDialog(
    onDismiss: () -> Unit,
    onSilenceFor: (Int) -> Unit
) {
    val context = LocalContext.current
    var showDurationPicker by remember { mutableStateOf(false) }
    var showDateTimePicker by remember { mutableStateOf(false) }
    
    if (showDurationPicker) {
        DurationPickerDialog(
            onDismiss = { showDurationPicker = false },
            onConfirm = { totalMinutes ->
                onSilenceFor(totalMinutes)
                showDurationPicker = false
            }
        )
        return
    }
    
    if (showDateTimePicker) {
        DateTimePickerDialog(
            onDismiss = { showDateTimePicker = false },
            onConfirm = { minutes ->
                onSilenceFor(minutes)
                showDateTimePicker = false
            }
        )
        return
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null
            )
        },
        title = {
            Text("Custom Silence Duration")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Choose how you want to set the silence duration:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Duration option
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDurationPicker = true }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Set Duration",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose hours and minutes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Until time option
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDateTimePicker = true }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Until Specific Time",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pick a date and time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DurationPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedHours by remember { mutableStateOf(1) }
    var selectedMinutes by remember { mutableStateOf(0) }
    
    val hoursList = remember { (0..24).toList() }
    val minutesList = remember { (0..59).toList() }
    val hoursListState = rememberLazyListState(initialFirstVisibleItemIndex = 1)
    val minutesListState = rememberLazyListState(initialFirstVisibleItemIndex = 0)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null
            )
        },
        title = {
            Text("Select Duration")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hour and Minute selectors side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    // Hour selector - scrollable list
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .width(100.dp)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (selectedHours == 1) "hour" else "hours",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier
                                    .height(180.dp)
                                    .fillMaxWidth(),
                                state = hoursListState,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(hoursList) { hour ->
                                    val isSelected = hour == selectedHours
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = String.format("%02d", hour),
                                            style = if (isSelected) 
                                                MaterialTheme.typography.titleLarge 
                                            else 
                                                MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedHours = hour }
                                                .padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Minute selector - scrollable list
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .width(100.dp)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "minutes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier
                                    .height(180.dp)
                                    .fillMaxWidth(),
                                state = minutesListState,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(minutesList) { minute ->
                                    val isSelected = minute == selectedMinutes
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = String.format("%02d", minute),
                                            style = if (isSelected) 
                                                MaterialTheme.typography.titleLarge 
                                            else 
                                                MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedMinutes = minute }
                                                .padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Quick presets
                Text(
                    text = "Quick select:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1 to 0, 2 to 0, 4 to 0).forEach { (hours, minutes) ->
                            FilledTonalButton(
                                onClick = { 
                                    selectedHours = hours
                                    selectedMinutes = minutes
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (selectedHours == hours && selectedMinutes == minutes)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text("${hours}h")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(8 to 0, 12 to 0, 24 to 0).forEach { (hours, minutes) ->
                            FilledTonalButton(
                                onClick = { 
                                    selectedHours = hours
                                    selectedMinutes = minutes
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (selectedHours == hours && selectedMinutes == minutes)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text("${hours}h")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { 
                    val totalMinutes = selectedHours * 60 + selectedMinutes
                    if (totalMinutes > 0) {
                        onConfirm(totalMinutes)
                    }
                },
                enabled = selectedHours > 0 || selectedMinutes > 0
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DateTimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedHour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(0) }
    var showingDatePicker by remember { mutableStateOf(true) }
    var showCustomDatePicker by remember { mutableStateOf(false) }
    
    if (showCustomDatePicker) {
        CustomDatePickerDialog(
            onDismiss = { showCustomDatePicker = false },
            onDateSelected = { year, month, day ->
                selectedDate = Calendar.getInstance().apply {
                    set(year, month, day)
                }
                showCustomDatePicker = false
            }
        )
    }
    
    if (showingDatePicker) {
        // Date Picker Dialog
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null
                )
            },
            title = {
                Text("Select Date")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val today = Calendar.getInstance()
                    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    val dayAfter = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }
                    
                    // Quick select cards
                    listOf(
                        "Today" to today,
                        "Tomorrow" to tomorrow,
                        "Day After Tomorrow" to dayAfter
                    ).forEach { (label, date) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSameDay(selectedDate, date))
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDate = date.clone() as Calendar }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = String.format(
                                            "%02d/%02d/%04d",
                                            date.get(Calendar.DAY_OF_MONTH),
                                            date.get(Calendar.MONTH) + 1,
                                            date.get(Calendar.YEAR)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSameDay(selectedDate, date)) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    
                    // Custom date button
                    OutlinedButton(
                        onClick = { showCustomDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Custom Date")
                    }
                    
                    // Show currently selected date
                    Text(
                        text = "Selected: ${String.format(
                            "%02d/%02d/%04d",
                            selectedDate.get(Calendar.DAY_OF_MONTH),
                            selectedDate.get(Calendar.MONTH) + 1,
                            selectedDate.get(Calendar.YEAR)
                        )}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(onClick = { showingDatePicker = false }) {
                    Text("Next")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    } else {
        // Time Picker Dialog
        val hoursList = remember { (0..23).toList() }
        val minutesList = remember { (0..59).toList() }
        val hoursListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedHour)
        val minutesListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedMinute)
        
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null
                )
            },
            title = {
                Text("Select Time")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Hour picker - scrollable list
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(100.dp)
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "hours",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                LazyColumn(
                                    modifier = Modifier
                                        .height(180.dp)
                                        .fillMaxWidth(),
                                    state = hoursListState,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    items(hoursList) { hour ->
                                        val isSelected = hour == selectedHour
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.primaryContainer 
                                            else 
                                                Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = String.format("%02d", hour),
                                                style = if (isSelected) 
                                                    MaterialTheme.typography.titleLarge 
                                                else 
                                                    MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) 
                                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                                else 
                                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedHour = hour }
                                                    .padding(vertical = 8.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Minute picker - scrollable list
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(100.dp)
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "minutes",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                LazyColumn(
                                    modifier = Modifier
                                        .height(180.dp)
                                        .fillMaxWidth(),
                                    state = minutesListState,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    items(minutesList) { minute ->
                                        val isSelected = minute == selectedMinute
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.primaryContainer 
                                            else 
                                                Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = String.format("%02d", minute),
                                                style = if (isSelected) 
                                                    MaterialTheme.typography.titleLarge 
                                                else 
                                                    MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) 
                                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                                else 
                                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedMinute = minute }
                                                    .padding(vertical = 8.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        val targetCalendar = selectedDate.clone() as Calendar
                        targetCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                        targetCalendar.set(Calendar.MINUTE, selectedMinute)
                        targetCalendar.set(Calendar.SECOND, 0)
                        targetCalendar.set(Calendar.MILLISECOND, 0)
                        
                        val durationMinutes = ((targetCalendar.timeInMillis - System.currentTimeMillis()) / 60000).toInt()
                        if (durationMinutes > 0) {
                            onConfirm(durationMinutes)
                        }
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showingDatePicker = true }) {
                    Text("Back")
                }
            }
        )
    }
}

@Composable
private fun CustomDatePickerDialog(
    onDismiss: () -> Unit,
    onDateSelected: (Int, Int, Int) -> Unit
) {
    val today = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableStateOf(today.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(today.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableStateOf(today.get(Calendar.DAY_OF_MONTH)) }
    
    val years = remember { (today.get(Calendar.YEAR)..today.get(Calendar.YEAR) + 1).toList() }
    val months = remember { (0..11).toList() }
    
    val daysInMonth = remember(selectedYear, selectedMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val days = remember(daysInMonth) { (1..daysInMonth).toList() }
    
    val yearListState = rememberLazyListState(initialFirstVisibleItemIndex = years.indexOf(selectedYear))
    val monthListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedMonth)
    val dayListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedDay - 1)
    
    val monthNames = remember {
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null
            )
        },
        title = {
            Text("Select Date")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Scrollable pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    // Day picker
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .width(80.dp)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Day",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier
                                    .height(180.dp)
                                    .fillMaxWidth(),
                                state = dayListState,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(days) { day ->
                                    val isSelected = day == selectedDay
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = String.format("%02d", day),
                                            style = if (isSelected) 
                                                MaterialTheme.typography.titleLarge 
                                            else 
                                                MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedDay = day }
                                                .padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Month picker
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .width(80.dp)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Month",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier
                                    .height(180.dp)
                                    .fillMaxWidth(),
                                state = monthListState,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(months) { month ->
                                    val isSelected = month == selectedMonth
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = monthNames[month],
                                            style = if (isSelected) 
                                                MaterialTheme.typography.titleLarge 
                                            else 
                                                MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { 
                                                    selectedMonth = month
                                                    // Adjust day if it exceeds new month's days
                                                    val newDaysInMonth = Calendar.getInstance().apply {
                                                        set(Calendar.YEAR, selectedYear)
                                                        set(Calendar.MONTH, month)
                                                    }.getActualMaximum(Calendar.DAY_OF_MONTH)
                                                    if (selectedDay > newDaysInMonth) {
                                                        selectedDay = newDaysInMonth
                                                    }
                                                }
                                                .padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Year picker
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .width(80.dp)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Year",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier
                                    .height(180.dp)
                                    .fillMaxWidth(),
                                state = yearListState,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(years) { year ->
                                    val isSelected = year == selectedYear
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = year.toString(),
                                            style = if (isSelected) 
                                                MaterialTheme.typography.titleLarge 
                                            else 
                                                MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedYear = year }
                                                .padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Selected date display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "${selectedDay} ${monthNames[selectedMonth]} ${selectedYear}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onDateSelected(selectedYear, selectedMonth, selectedDay) }
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

// Pokemon type constants for filtering
private val POKEMON_TYPES = listOf(
    "Normal", "Fire", "Water", "Electric", "Grass", "Ice",
    "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug",
    "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy"
)

// Rocket grunt type constants
private val ROCKET_GRUNT_TYPES = listOf(
    "Normal", "Fire", "Water", "Electric", "Grass", "Ice",
    "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug",
    "Rock", "Ghost", "Dragon", "Dark", "Fairy", "Mixed"
)

/**
 * Expandable filter section showing type chips that can be toggled to exclude
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeFilterSection(
    title: String,
    types: List<String>,
    excludedTypes: Set<String>,
    onToggleType: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (excludedTypes.isNotEmpty()) {
                    Text(
                        text = "${excludedTypes.size} excluded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        AnimatedVisibility(visible = expanded) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                types.forEach { type ->
                    val isExcluded = excludedTypes.any { it.equals(type, ignoreCase = true) }
                    FilterChip(
                        selected = isExcluded,
                        onClick = { onToggleType(type) },
                        label = { Text(type) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }
            }
        }
    }
}
