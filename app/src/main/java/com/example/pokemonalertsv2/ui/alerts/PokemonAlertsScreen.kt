package com.example.pokemonalertsv2.ui.alerts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonAlertsRoute(
    viewModel: PokemonAlertsViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val highlightedAlert = remember(uiState.alerts, uiState.highlightedAlertId) {
        uiState.alerts.firstOrNull { it.uniqueId == uiState.highlightedAlertId }
    }

    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    PokemonAlertsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refreshAlerts,
        onAlertSelected = { alert ->
            viewModel.highlightAlert(alert.uniqueId)
        }
    )

    if (highlightedAlert != null) {
        AlertDetailDialog(alert = highlightedAlert, onDismiss = { viewModel.highlightAlert(null) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonAlertsScreen(
    uiState: AlertsUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onAlertSelected: (PokemonAlert) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_refresh),
                            contentDescription = stringResource(id = R.string.refresh_alerts)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading && uiState.alerts.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }

            when {
                uiState.isLoading && uiState.alerts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.alerts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = context.getString(R.string.no_alerts_message))
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.alerts, key = { it.uniqueId }) { alert ->
                            AlertCard(
                                alert = alert,
                                onOpenMaps = { openMapForAlert(context, alert) },
                                onShowDetails = { onAlertSelected(alert) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertCard(
    alert: PokemonAlert,
    onOpenMaps: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onShowDetails
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = alert.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            AlertImage(alert = alert)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = alert.description,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.alert_end_time, alert.endTime),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = onOpenMaps) {
                Text(text = stringResource(id = R.string.open_in_maps))
            }
        }
    }
}

@Composable
private fun AlertImage(alert: PokemonAlert, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageUrl by rememberUpdatedState(alert.imageUrl ?: alert.thumbnailUrl)
    if (imageUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Alert location image",
            placeholder = painterResource(id = R.drawable.ic_placeholder),
            error = painterResource(id = R.drawable.ic_placeholder),
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No image available")
        }
    }
}

@Composable
private fun AlertDetailDialog(alert: PokemonAlert, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.close))
            }
        },
        title = { Text(text = alert.name) },
        text = {
            AlertDetailContent(
                alert = alert,
                onOpenMaps = { openMapForAlert(context, alert) }
            )
        }
    )
}

@Composable
fun AlertDetailScreen(alert: PokemonAlert) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AlertDetailContent(
            alert = alert,
            onOpenMaps = { openMapForAlert(context, alert) }
        )
    }
}

@Composable
private fun AlertDetailContent(alert: PokemonAlert, onOpenMaps: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        AlertImage(alert = alert)
        Text(text = alert.description)
        alert.type?.takeIf { it.isNotBlank() }?.let { type ->
            Text(text = type, style = MaterialTheme.typography.labelMedium)
        }
        Text(
            text = stringResource(id = R.string.alert_end_time, alert.endTime),
            style = MaterialTheme.typography.labelMedium
        )
        FilledTonalButton(onClick = onOpenMaps) {
            Text(text = stringResource(id = R.string.open_in_maps))
        }
    }
}

private fun openMapForAlert(context: Context, alert: PokemonAlert) {
    val mapsIntent = Intent(Intent.ACTION_VIEW, alert.googleMapsUri)
    try {
        context.startActivity(mapsIntent)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_maps_app), Toast.LENGTH_SHORT).show()
    }
}
