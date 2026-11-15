package com.example.pokemonalertsv2.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.NotificationContentPreferences
import com.example.pokemonalertsv2.data.NotificationField
import com.example.pokemonalertsv2.data.NotificationFieldStyle
import com.example.pokemonalertsv2.data.NotificationImageStyle
import com.example.pokemonalertsv2.data.NotificationTitlePart
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.notificationFields
import com.example.pokemonalertsv2.data.notificationTitleParts
import com.example.pokemonalertsv2.notifications.NotificationContent
import com.example.pokemonalertsv2.notifications.TestNotification
import com.example.pokemonalertsv2.notifications.notificationPreviewInput
import com.example.pokemonalertsv2.notifications.renderNotificationContent
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.PanelHeader
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.readableWidth
import com.example.pokemonalertsv2.ui.components.rememberShimmerBrush

/** A change to the stored notification content, applied by the caller. */
internal typealias NotificationContentEdit = (NotificationContentPreferences) -> NotificationContentPreferences

private enum class NotificationPreviewKind(val labelRes: Int) {
    INCOMING(R.string.notification_content_incoming),
    JOURNEY(R.string.notification_content_journey),
    RAID_WATCH(R.string.notification_content_raid_watch)
}

private enum class LayoutTab(val labelRes: Int) {
    TITLE(R.string.notification_section_title),
    COLLAPSED(R.string.notification_content_collapsed),
    EXPANDED(R.string.notification_content_expanded),
    PICTURE(R.string.notification_section_picture)
}

/** The shared default layout is previewed and tested on an encounter alert. */
private val SHARED_PREVIEW = FilterAlertType.SPAWN

private fun FilterAlertType.accent(): Color =
    Color((AlertCategory.entries.firstOrNull { it.name == name } ?: AlertCategory.GENERIC).accentArgb)

@Composable
internal fun NotificationContentSettings(
    preferences: NotificationContentPreferences,
    onEdit: (NotificationContentEdit) -> Unit,
    onSendTest: (FilterAlertType?, Boolean, (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // "" is the list, DEFAULT the shared layout, otherwise an alert type name.
    var target by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = target.isNotEmpty()) { target = "" }

    if (target.isEmpty()) {
        LayoutList(preferences, onOpen = { target = it }, onBack = onBack, modifier = modifier)
        return
    }
    NotificationLayoutEditor(
        category = FilterAlertType.entries.firstOrNull { it.name == target },
        preferences = preferences,
        onEdit = onEdit,
        onSendTest = onSendTest,
        onBack = { target = "" },
        modifier = modifier
    )
}

private const val DEFAULT_TARGET = "DEFAULT"

/** The default layout on top, then every alert type as a compact tile that says whether it follows it. */
@Composable
private fun LayoutList(
    preferences: NotificationContentPreferences,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier
) {
    val types = FilterAlertType.entries
    val following = types.count { !preferences.isCustomized(it) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).readableWidth().padding(bottom = 24.dp)) {
        PanelHeader(stringResource(R.string.notification_content_title), onBack = onBack)
        PanelNavRow(
            title = stringResource(R.string.notification_layout_default),
            summary = stringResource(R.string.notification_layout_default_summary, following, types.size),
            onClick = { onOpen(DEFAULT_TARGET) },
            modifier = Modifier.testTag("notification_category_DEFAULT"),
            active = preferences.isCustomized(null),
            icon = {
                Icon(painterResource(R.drawable.ic_poke_notification), contentDescription = null, modifier = Modifier.size(20.dp))
            }
        )
        PanelSectionLabel(stringResource(R.string.notification_layout_types))
        Text(
            stringResource(R.string.notification_layout_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = PanelGutter).padding(bottom = 8.dp)
        )
        types.chunked(2).forEach { pair ->
            Row(
                Modifier.padding(horizontal = PanelGutter - 4.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { type ->
                    TypeTile(type, customized = preferences.isCustomized(type), onClick = { onOpen(type.name) }, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TypeTile(type: FilterAlertType, customized: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp).testTag("notification_category_${type.name}"),
        shape = RoundedCornerShape(16.dp),
        color = if (customized) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(type.accent().copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_poke_notification), contentDescription = null, tint = type.accent(), modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(type.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (customized) R.string.notification_layout_custom else R.string.notification_layout_follows_default),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (customized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val TITLE_FIELD_PARTS = mapOf(
    NotificationField.IV to NotificationTitlePart.IV,
    NotificationField.CP to NotificationTitlePart.CP,
    NotificationField.VENUE to NotificationTitlePart.VENUE
)

/** One row of an editable list: a title part or a detail, and for expanded details whether it shares the line above. */
private data class LayoutItem(val name: String, val labelRes: Int, val field: NotificationField?, val joined: Boolean = false)

private fun NotificationTitlePart.item() = LayoutItem(name, labelRes, null)
private fun NotificationField.item(joined: Boolean = false) = LayoutItem(name, labelRes, this, joined)

private fun linesToItems(lines: List<List<NotificationField>>): List<LayoutItem> =
    lines.flatMap { line -> line.mapIndexed { index, field -> field.item(joined = index > 0) } }

private fun itemsToLines(items: List<LayoutItem>): List<List<NotificationField>> {
    val lines = mutableListOf<MutableList<NotificationField>>()
    items.forEach { item ->
        val field = item.field ?: return@forEach
        if (item.joined && lines.isNotEmpty()) lines.last() += field else lines += mutableListOf(field)
    }
    return lines
}

/**
 * The preview stays pinned at the top; below it, one tab per part of the
 * notification. Each tab is a short ordered list (drag, or use the move
 * actions) with the unused options underneath, so the whole layout fits on
 * about one screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationLayoutEditor(
    category: FilterAlertType?,
    preferences: NotificationContentPreferences,
    onEdit: (NotificationContentEdit) -> Unit,
    onSendTest: (FilterAlertType?, Boolean, (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    var tabName by rememberSaveable { mutableStateOf(LayoutTab.TITLE.name) }
    val tab = LayoutTab.valueOf(tabName)
    var previewExpanded by rememberSaveable { mutableStateOf(false) }
    var inRange by rememberSaveable { mutableStateOf(false) }
    var previewName by rememberSaveable { mutableStateOf(NotificationPreviewKind.INCOMING.name) }
    var copying by remember { mutableStateOf(false) }
    val previewCategory = category ?: SHARED_PREVIEW
    // The shared layout is previewed on a type that follows it.
    val previewPreferences = if (category == null) preferences.reset(SHARED_PREVIEW) else preferences
    val imageStyle = preferences.imageStyle(category)
    val kinds = NotificationPreviewKind.entries.filter { it != NotificationPreviewKind.RAID_WATCH || category == FilterAlertType.RAID }
    val preview = NotificationPreviewKind.valueOf(previewName).takeIf { it in kinds } ?: NotificationPreviewKind.INCOMING
    // Raid Watch outlives location tracking, so it never claims a range.
    val showRange = preview != NotificationPreviewKind.RAID_WATCH

    // The newest real alert of this category, so the preview shows a real picture.
    val now = remember(previewCategory) { System.currentTimeMillis() }
    var sample by remember(previewCategory) { mutableStateOf<PokemonAlert?>(null) }
    var picture by remember(previewCategory) { mutableStateOf<ImageBitmap?>(null) }
    var pictureLoading by remember(previewCategory) { mutableStateOf(true) }
    LaunchedEffect(previewCategory) {
        sample = TestNotification.sampleInput(context, previewCategory, inRange = false, now = now).alert
    }
    LaunchedEffect(sample, imageStyle) {
        val alert = sample ?: return@LaunchedEffect
        if (imageStyle == NotificationImageStyle.NONE) {
            pictureLoading = false
            return@LaunchedEffect
        }
        pictureLoading = picture == null
        // LARGE is the sharper render; the thumbnail view just scales it down.
        picture = TestNotification.image(context, alert, NotificationImageStyle.LARGE)?.asImageBitmap()
        pictureLoading = false
    }

    val rendered = renderNotificationContent(
        notificationPreviewInput(
            previewCategory,
            journey = preview == NotificationPreviewKind.JOURNEY,
            raidWatch = preview == NotificationPreviewKind.RAID_WATCH,
            inRange = inRange && showRange,
            now = now,
            base = sample
        ),
        previewPreferences
    )
    val sent = stringResource(R.string.notification_test_sent)
    val blocked = stringResource(R.string.notification_test_no_permission)
    val report: (Boolean) -> Unit = { ok -> Toast.makeText(context, if (ok) sent else blocked, Toast.LENGTH_SHORT).show() }
    val copied = stringResource(R.string.notification_copy_done)

    Column(modifier.fillMaxSize().readableWidth()) {
        PanelHeader(
            title = category?.label ?: stringResource(R.string.notification_layout_default),
            subtitle = stringResource(
                if (category == null) R.string.notification_layout_default_subtitle else R.string.notification_content_title
            ),
            onBack = onBack
        ) {
            IconButton(
                onClick = { onSendTest(category, inRange && showRange, report) },
                modifier = Modifier.testTag("notification_test_send")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.notification_test_send))
            }
            LayoutMenu(
                category = category,
                customized = preferences.isCustomized(category),
                onCopy = { copying = true },
                // A type that drops its own layout follows the shared one again; the
                // shared one returns to the built-in layout.
                onReset = { onEdit { it.reset(category) } }
            )
        }

        // Pinned preview ------------------------------------------------------------------
        NotificationMockup(
            content = rendered,
            accent = previewCategory.accent(),
            expanded = previewExpanded,
            onToggle = { previewExpanded = !previewExpanded },
            live = preview != NotificationPreviewKind.INCOMING,
            imageStyle = if (preview == NotificationPreviewKind.INCOMING) imageStyle else NotificationImageStyle.NONE,
            picture = picture,
            pictureLoading = pictureLoading
        )
        PreviewOptions(
            kinds = kinds,
            preview = preview,
            onPreview = { previewName = it.name },
            showRange = showRange,
            inRange = inRange,
            onInRange = { inRange = it }
        )

        // Sized to the labels, so longer German words ("Eingeklappt") are never clipped.
        PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp) {
            LayoutTab.entries.forEach { entry ->
                Tab(
                    selected = tab == entry,
                    onClick = {
                        tabName = entry.name
                        // Show the view being edited.
                        previewExpanded = entry == LayoutTab.EXPANDED || entry == LayoutTab.PICTURE
                    },
                    text = { Text(stringResource(entry.labelRes), maxLines = 1) },
                    modifier = Modifier.testTag("notification_tab_${entry.name}")
                )
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            val titleOrder = preferences.titleOrder(category)
            val inTitle = TITLE_FIELD_PARTS.filterValues { it in titleOrder }.keys
            when (tab) {
                LayoutTab.TITLE -> LayoutListEditor(
                    hint = stringResource(R.string.notification_hint_title),
                    shown = titleOrder.map { it.item() },
                    available = notificationTitleParts(category).map { it.item() },
                    lines = false,
                    category = category,
                    preferences = preferences,
                    inTitle = emptySet(),
                    onCommit = { items ->
                        val parts = items.mapNotNull { item -> NotificationTitlePart.entries.firstOrNull { it.name == item.name } }
                        onEdit { it.withTitleOrder(category, parts) }
                    },
                    onEdit = onEdit
                )
                LayoutTab.COLLAPSED -> LayoutListEditor(
                    hint = stringResource(R.string.notification_hint_collapsed),
                    shown = preferences.collapsedOrder(category).map { it.item() },
                    available = notificationFields(category, expanded = false).map { it.item() },
                    lines = false,
                    category = category,
                    preferences = preferences,
                    inTitle = inTitle,
                    onCommit = { items -> onEdit { it.withCollapsedOrder(category, items.mapNotNull(LayoutItem::field)) } },
                    onEdit = onEdit
                )
                LayoutTab.EXPANDED -> LayoutListEditor(
                    hint = stringResource(R.string.notification_hint_expanded),
                    shown = linesToItems(preferences.expandedLines(category)),
                    available = notificationFields(category, expanded = true).map { it.item() },
                    lines = true,
                    category = category,
                    preferences = preferences,
                    inTitle = inTitle,
                    onCommit = { items -> onEdit { it.withExpandedLines(category, itemsToLines(items)) } },
                    onEdit = onEdit
                )
                LayoutTab.PICTURE -> PicturePicker(imageStyle) { style -> onEdit { it.withImageStyle(category, style) } }
            }
        }
    }

    if (copying) {
        CopyLayoutDialog(
            source = category,
            onDismiss = { copying = false },
            onCopy = { targets ->
                copying = false
                onEdit { it.copyLayout(category, targets) }
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun LayoutMenu(
    category: FilterAlertType?,
    customized: Boolean,
    onCopy: () -> Unit,
    onReset: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("notification_layout_menu")) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.notification_menu_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.notification_menu_copy)) },
                onClick = { open = false; onCopy() },
                modifier = Modifier.testTag("notification_layout_copy")
            )
            if (category != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notification_menu_use_default)) },
                    enabled = customized,
                    onClick = { open = false; onReset() },
                    modifier = Modifier.testTag("notification_content_reset")
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notification_menu_reset_default)) },
                    enabled = customized,
                    onClick = { open = false; onReset() },
                    modifier = Modifier.testTag("notification_content_reset")
                )
            }
        }
    }
}

/** Preview options sit in one compact row under the mockup instead of two segmented rows. */
@Composable
private fun PreviewOptions(
    kinds: List<NotificationPreviewKind>,
    preview: NotificationPreviewKind,
    onPreview: (NotificationPreviewKind) -> Unit,
    showRange: Boolean,
    inRange: Boolean,
    onInRange: (Boolean) -> Unit
) {
    Row(
        Modifier.padding(horizontal = PanelGutter).padding(bottom = 4.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var open by remember { mutableStateOf(false) }
        Box {
            AssistChip(
                onClick = { open = true },
                label = { Text(stringResource(R.string.notification_preview_kind, stringResource(preview.labelRes))) },
                trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("notification_preview_kind")
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                kinds.forEach { kind ->
                    DropdownMenuItem(
                        text = { Text(stringResource(kind.labelRes)) },
                        onClick = { open = false; onPreview(kind) },
                        modifier = Modifier.testTag("notification_preview_kind_${kind.name}")
                    )
                }
            }
        }
        if (showRange) {
            FilterChip(
                selected = inRange,
                onClick = { onInRange(!inRange) },
                label = { Text(stringResource(R.string.alerts_in_range)) },
                modifier = Modifier.testTag("notification_preview_state_in_range")
            )
        }
    }
}

/**
 * The selected items as a reorderable list, then the unused ones. [lines] adds
 * the "same line as above" link that builds expanded lines.
 */
@Composable
private fun LayoutListEditor(
    hint: String,
    shown: List<LayoutItem>,
    available: List<LayoutItem>,
    lines: Boolean,
    category: FilterAlertType?,
    preferences: NotificationContentPreferences,
    inTitle: Set<NotificationField>,
    onCommit: (List<LayoutItem>) -> Unit,
    onEdit: (NotificationContentEdit) -> Unit
) {
    Text(
        hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = PanelGutter, end = PanelGutter, top = 12.dp)
    )
    PanelSectionLabel(stringResource(R.string.notification_layout_shown))
    if (shown.isEmpty()) {
        EmptyNote(stringResource(R.string.notification_layout_nothing_shown))
    }
    ReorderableColumn(
        items = shown,
        keyOf = LayoutItem::name,
        onCommit = { reordered -> onCommit(normalize(reordered)) }
    ) { item, index, count, handle, move ->
        ShownRow(
            item = item,
            first = index == 0,
            last = index == count - 1,
            lines = lines,
            inTitle = item.field != null && item.field in inTitle,
            style = item.field?.let { preferences.style(category, it) },
            handle = handle,
            onMove = move,
            onToggleJoin = {
                onCommit(normalize(shown.map { if (it.name == item.name) it.copy(joined = !it.joined) else it }))
            },
            onStyle = { next -> item.field?.let { field -> onEdit { it.withStyle(category, field, next) } } },
            onRemove = { onCommit(normalize(shown.filterNot { it.name == item.name })) }
        )
    }

    val hidden = available.filter { candidate -> shown.none { it.name == candidate.name } }
    PanelSectionLabel(stringResource(R.string.notification_layout_hidden))
    if (hidden.isEmpty()) EmptyNote(stringResource(R.string.notification_layout_all_shown))
    hidden.forEach { item ->
        val label = stringResource(item.labelRes)
        val addLabel = stringResource(R.string.notification_action_add, label)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = addLabel) { onCommit(normalize(shown + item.copy(joined = false))) }
                .padding(horizontal = PanelGutter)
                .testTag("notification_add_${item.name}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Add, contentDescription = addLabel, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The first row can never share a line with a row above it. */
private fun normalize(items: List<LayoutItem>): List<LayoutItem> =
    items.mapIndexed { index, item -> if (index == 0 && item.joined) item.copy(joined = false) else item }

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PanelGutter, vertical = 8.dp)
    )
}

// [handle] is the drag handle's gesture modifier, not this row's layout modifier.
@Suppress("ModifierParameter")
@Composable
private fun ShownRow(
    item: LayoutItem,
    first: Boolean,
    last: Boolean,
    lines: Boolean,
    inTitle: Boolean,
    style: NotificationFieldStyle?,
    handle: Modifier,
    onMove: (Int) -> Unit,
    onToggleJoin: () -> Unit,
    onStyle: (NotificationFieldStyle) -> Unit,
    onRemove: () -> Unit
) {
    val label = stringResource(item.labelRes)
    val moveUp = stringResource(R.string.notification_action_move_up)
    val moveDown = stringResource(R.string.notification_action_move_down)
    val joined = lines && item.joined && !first
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(start = if (joined) PanelGutter + 20.dp else PanelGutter - 8.dp, end = PanelGutter - 12.dp)
            .alpha(if (inTitle) 0.55f else 1f)
            .semantics {
                customActions = buildList {
                    if (!first) add(CustomAccessibilityAction(moveUp) { onMove(-1); true })
                    if (!last) add(CustomAccessibilityAction(moveDown) { onMove(1); true })
                }
            }
            .testTag("notification_shown_${item.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(handle.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_drag_handle),
                contentDescription = stringResource(R.string.notification_action_drag, label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (inTitle) {
                Text(
                    stringResource(R.string.notification_layout_in_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (style != null) {
            val styleLabel = stringResource(style.labelRes)
            val description = stringResource(R.string.notification_style_description, label, styleLabel)
            AssistChip(
                onClick = { onStyle(NotificationFieldStyle.entries[(style.ordinal + 1) % NotificationFieldStyle.entries.size]) },
                label = { Text(styleLabel, maxLines = 1) },
                modifier = Modifier.semantics { contentDescription = description }.testTag("notification_style_${item.name}")
            )
        }
        // The first line has nothing above to join; keep its chip aligned with the rest.
        if (lines && first) Spacer(Modifier.width(48.dp))
        if (lines && !first) {
            IconToggleButton(
                checked = item.joined,
                onCheckedChange = { onToggleJoin() },
                modifier = Modifier.testTag("notification_join_${item.name}")
            ) {
                Icon(
                    painterResource(R.drawable.ic_line_join),
                    contentDescription = stringResource(if (item.joined) R.string.notification_action_split else R.string.notification_action_join),
                    tint = if (item.joined) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.testTag("notification_remove_${item.name}")) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.notification_action_remove, label))
        }
    }
}

/**
 * A column whose rows reorder by dragging their handle. The order is kept
 * locally while a finger is down and committed once on release, so the stored
 * value is written once per drag rather than once per row passed.
 */
@Composable
private fun <T> ReorderableColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onCommit: (List<T>) -> Unit,
    row: @Composable (item: T, index: Int, count: Int, handle: Modifier, move: (Int) -> Unit) -> Unit
) {
    var local by remember(items) { mutableStateOf(items) }
    val currentItems by rememberUpdatedState(items)
    val commit by rememberUpdatedState(onCommit)
    var dragging by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    Column {
        local.forEachIndexed { index, item ->
            val itemKey = keyOf(item)
            key(itemKey) {
                val active = dragging == itemKey
                val handle = Modifier.pointerInput(itemKey) {
                    detectDragGestures(
                        onDragStart = { dragging = itemKey; offset = 0f },
                        onDragEnd = {
                            dragging = null
                            offset = 0f
                            if (local != currentItems) commit(local)
                        },
                        onDragCancel = { dragging = null; offset = 0f; local = currentItems },
                        onDrag = { change, amount ->
                            change.consume()
                            offset += amount.y
                            val list = local
                            val at = list.indexOfFirst { keyOf(it) == itemKey }
                            val height = (heights[itemKey] ?: return@detectDragGestures).toFloat()
                            if (offset > height / 2 && at < list.lastIndex) {
                                val below = heights[keyOf(list[at + 1])]?.toFloat() ?: height
                                local = list.toMutableList().apply { add(at + 1, removeAt(at)) }
                                offset -= below
                            } else if (offset < -height / 2 && at > 0) {
                                val above = heights[keyOf(list[at - 1])]?.toFloat() ?: height
                                local = list.toMutableList().apply { add(at - 1, removeAt(at)) }
                                offset += above
                            }
                        }
                    )
                }
                Box(
                    Modifier
                        .onSizeChanged { heights[itemKey] = it.height }
                        .zIndex(if (active) 1f else 0f)
                        .graphicsLayer { translationY = if (active) offset else 0f }
                        .background(if (active) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                ) {
                    row(item, index, local.size, handle) { delta ->
                        val target = (index + delta).coerceIn(0, local.lastIndex)
                        if (target != index) onCommit(local.toMutableList().apply { add(target, removeAt(index)) })
                    }
                }
            }
        }
    }
}

/** The three picture styles as tiles that sketch what each looks like. */
@Composable
private fun PicturePicker(selected: NotificationImageStyle, onSelect: (NotificationImageStyle) -> Unit) {
    Text(
        stringResource(R.string.notification_picture_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = PanelGutter, end = PanelGutter, top = 12.dp, bottom = 12.dp)
    )
    Row(Modifier.padding(horizontal = PanelGutter).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NotificationImageStyle.entries.forEach { style ->
            val isSelected = style == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(style) }
                    .testTag("notification_image_${style.name}"),
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PictureSketch(style)
                    Text(stringResource(style.labelRes), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PictureSketch(style: NotificationImageStyle) {
    val line = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val image = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    Column(Modifier.fillMaxWidth().height(64.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(6.dp).clip(CircleShape).background(line))
                Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(CircleShape).background(line))
            }
            if (style == NotificationImageStyle.THUMBNAIL) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(image))
            }
        }
        if (style == NotificationImageStyle.LARGE) {
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).background(image))
        }
    }
}

@Composable
private fun CopyLayoutDialog(
    source: FilterAlertType?,
    onDismiss: () -> Unit,
    onCopy: (List<FilterAlertType?>) -> Unit
) {
    val options: List<FilterAlertType?> = (listOf<FilterAlertType?>(null) + FilterAlertType.entries).filter { it != source }
    var chosen by remember { mutableStateOf(setOf<FilterAlertType?>()) }
    val typeOptions = options.filterNotNull()
    val allTypes = typeOptions.isNotEmpty() && chosen.containsAll(typeOptions)
    val defaultLabel = stringResource(R.string.notification_layout_default)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notification_copy_title, source?.label ?: defaultLabel)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.notification_copy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CheckRow(stringResource(R.string.notification_copy_all), allTypes, Modifier.testTag("notification_copy_all")) {
                    chosen = if (allTypes) chosen - typeOptions.toSet() else chosen + typeOptions
                }
                HorizontalDivider()
                options.forEach { option ->
                    CheckRow(option?.label ?: defaultLabel, option in chosen, Modifier.testTag("notification_copy_${option?.name ?: DEFAULT_TARGET}")) {
                        chosen = if (option in chosen) chosen - option else chosen + option
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCopy(chosen.toList()) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.testTag("notification_copy_confirm")
            ) { Text(stringResource(R.string.notification_copy_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Mirrors the shade: header, title and text, picture, and the action row when expanded. */
@Composable
private fun NotificationMockup(
    content: NotificationContent,
    accent: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    live: Boolean,
    imageStyle: NotificationImageStyle,
    picture: ImageBitmap?,
    pictureLoading: Boolean
) {
    val bigPicture = expanded && imageStyle == NotificationImageStyle.LARGE && (picture != null || pictureLoading)
    val icon = imageStyle != NotificationImageStyle.NONE && !bigPicture
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "notification_preview_arrow")
    Surface(
        modifier = Modifier
            .padding(horizontal = PanelGutter, vertical = 8.dp)
            .fillMaxWidth()
            .testTag("notification_preview_card"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            Modifier.clickable(onClick = onToggle).animateContentSize().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_poke_notification),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${stringResource(R.string.app_name)} · ${stringResource(R.string.notification_preview_now)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(90f + arrow)
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        content.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("notification_preview_title")
                    )
                    Text(
                        when {
                            !expanded -> content.collapsed
                            bigPicture -> content.expanded.replace("\n", " • ")
                            else -> content.expanded
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (!expanded) 1 else if (bigPicture) 2 else 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("notification_preview_body")
                    )
                }
                if (icon) {
                    Spacer(Modifier.width(12.dp))
                    PreviewPicture(picture, pictureLoading, Modifier.size(40.dp), RoundedCornerShape(10.dp))
                }
            }
            if (bigPicture) {
                // A strip of the real 2:1 picture, so the editor below keeps its room.
                PreviewPicture(
                    picture, pictureLoading,
                    Modifier.fillMaxWidth().aspectRatio(4f).testTag("notification_preview_picture"),
                    RoundedCornerShape(14.dp)
                )
            }
            if (live) {
                Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.18f)) {
                    Text(
                        content.chip,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag("notification_preview_chip")
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewPicture(picture: ImageBitmap?, loading: Boolean, modifier: Modifier, shape: RoundedCornerShape) {
    when {
        picture != null -> Image(
            picture,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape)
        )
        loading -> Box(modifier.clip(shape).background(rememberShimmerBrush()))
        else -> Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
