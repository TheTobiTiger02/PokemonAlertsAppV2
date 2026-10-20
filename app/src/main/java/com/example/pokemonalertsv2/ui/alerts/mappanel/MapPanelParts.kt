@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts.mappanel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.ui.alerts.MAP_TOP_CHROME_HEIGHT
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.ui.components.keepScrollInSheet

/**
 * Which sheet the map's top bar opened: [FILTERS] answers "what is on the map", [LAYERS] answers
 * "how does it look".
 */
internal enum class MapPanelScope { FILTERS, LAYERS }

/**
 * Hosts one of the map's sheets: a bottom sheet on phones, a panel beside the map from 840dp.
 *
 * The sheet opens fully expanded. At partial expansion its own drag handling took the first
 * vertical gesture to grow itself, so the first swipe over the content scrolled nothing.
 * [tall] gives the sheet most of the screen whatever its content, for sheets built around lists.
 */
@Composable
internal fun MapPanelContainer(
    useSidePanel: Boolean,
    testTag: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tall: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    if (useSidePanel) {
        Surface(
            modifier = modifier
                .width(380.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = MAP_TOP_CHROME_HEIGHT, end = 16.dp, bottom = 24.dp)
                .testTag(testTag),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.then(if (tall) Modifier.fillMaxHeight() else Modifier).padding(vertical = Spacing.lg),
                content = content
            )
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(testTag)
        ) {
            Column(
                modifier = Modifier
                    .then(if (tall) Modifier.fillMaxHeight(TallSheetFraction) else Modifier)
                    .keepScrollInSheet(),
                content = content
            )
        }
    }
}

/** Share of the window a tall sheet fills; the rest keeps a strip of map in view above it. */
private const val TallSheetFraction = 0.9f
