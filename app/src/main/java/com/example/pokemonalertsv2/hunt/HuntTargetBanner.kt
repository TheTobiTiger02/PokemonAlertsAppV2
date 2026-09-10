package com.example.pokemonalertsv2.hunt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.PokemonAlert

/**
 * Which alert the hunt is walking you to, on the map itself.
 *
 * The panel that starts a hunt is closed for almost all of it, and the status bar
 * chip is a handful of characters, so without this the map shows an emphasized
 * marker somewhere and no way to say what it is. Tapping frames it.
 *
 * Two lines, each capped: it sits over the map, and the map is the thing being read.
 */
@Composable
internal fun HuntTargetBanner(
    huntName: String,
    target: PokemonAlert?,
    distanceMeters: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .testTag("hunt_target_banner")
            .then(
                if (target == null) Modifier
                else Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = "Show the target on the map",
                    onClick = onClick
                )
            ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = if (target == null) "Hunting $huntName" else huntTargetTitle(target),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                // Standby wording matches the notification's, so a trainer glancing
                // between the two is not left wondering whether they disagree.
                text = target?.let { huntTargetDetail(it, distanceMeters) } ?: "Waiting for a match",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
