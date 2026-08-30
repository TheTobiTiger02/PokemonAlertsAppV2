package com.example.pokemonalertsv2.ui.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.notifications.QuietHours
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.data.godex.GoDexConfig
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import com.example.pokemonalertsv2.data.godex.GoDexSyncUiState
import kotlinx.coroutines.launch
import com.example.pokemonalertsv2.ui.components.LinearModernBackground
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import java.util.Calendar
import java.text.DateFormat
import java.util.Date
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import android.widget.Toast
import com.example.pokemonalertsv2.util.InAppUpdateManager
import com.example.pokemonalertsv2.util.UpdateCheckSource
import com.example.pokemonalertsv2.util.UpdateState
import com.example.pokemonalertsv2.data.NotificationPreset
import com.example.pokemonalertsv2.data.NotificationCategoryState
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.ui.motion.appSharedAxisX
import com.example.pokemonalertsv2.ui.motion.appCollapseOut
import com.example.pokemonalertsv2.ui.motion.appExpandIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.pokemonalertsv2.data.backup.SettingsBackup
import com.example.pokemonalertsv2.data.backup.SettingsBackupRepository
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

@Composable
internal fun CustomSilenceDialog(
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
internal fun DurationPickerDialog(
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
                                items(hoursList, key = { it }) { hour ->
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
                                items(minutesList, key = { it }) { minute ->
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
internal fun DateTimePickerDialog(
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
                                    items(hoursList, key = { it }) { hour ->
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
                                    items(minutesList, key = { it }) { minute ->
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
internal fun CustomDatePickerDialog(
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
                                items(days, key = { it }) { day ->
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
                                items(months, key = { it }) { month ->
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
                                items(years, key = { it }) { year ->
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

internal fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
