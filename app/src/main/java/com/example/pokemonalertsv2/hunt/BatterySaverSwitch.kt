package com.example.pokemonalertsv2.hunt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * The one Battery Saver switch. Hunts and catch routes share its setting: whichever is running
 * blacks the screen out when the phone lies face down.
 */
@Composable
internal fun BatterySaverSwitch(description: String, explanation: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val huntRepository = remember(context) { HuntRepository.getInstance(context) }
    val enabled by huntRepository.batterySaverEnabled.collectAsStateWithLifecycle(initialValue = false)
    var problem by remember { mutableStateOf<String?>(null) }
    Column(modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Battery Saver", modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    problem = if (on) HuntBatterySaver.unavailableReason(context) else null
                    if (problem == null) scope.launch { huntRepository.setBatterySaverEnabled(on) }
                },
                modifier = Modifier.semantics { contentDescription = description }
            )
        }
        Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        problem?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (!android.provider.Settings.canDrawOverlays(context)) {
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")))
                }) { Text("Allow display over other apps") }
            }
        }
    }
}
