package com.example.pokemonalertsv2.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.HorizontalDivider
import com.example.pokemonalertsv2.ui.components.readableWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.NotificationContentPreferences
import com.example.pokemonalertsv2.data.NotificationField
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
import com.example.pokemonalertsv2.ui.components.SegmentedChoice
import com.example.pokemonalertsv2.ui.components.SpringSegmentedRow
import com.example.pokemonalertsv2.ui.components.rememberShimmerBrush

private enum class NotificationPreviewKind(val labelRes: Int) {
    INCOMING(R.string.notification_content_incoming),
    JOURNEY(R.string.notification_content_journey),
    RAID_WATCH(R.string.notification_content_raid_watch)
}

/** Detail chips grouped by what they describe, so a long list reads at a glance. */
private val FIELD_GROUPS = listOf(
    R.string.notification_group_travel to listOf(
        NotificationField.DISTANCE, NotificationField.WALKING_TIME, NotificationField.TIME_LEFT
    ),
    R.string.notification_group_pokemon to listOf(
        NotificationField.CP, NotificationField.CATCH_CP, NotificationField.IV, NotificationField.FORM,
        NotificationField.PVP_RANK, NotificationField.MOVES, NotificationField.GODEX, NotificationField.TEAM
    ),
    R.string.notification_group_place to listOf(
        NotificationField.WEATHER, NotificationField.VENUE, NotificationField.AREA
    ),
    R.string.notification_group_extra to listOf(
        NotificationField.TASK, NotificationField.REWARD, NotificationField.AR,
        NotificationField.GRUNT, NotificationField.DESCRIPTION
    )
)

private fun FilterAlertType.accent(): Color =
    Color((AlertCategory.entries.firstOrNull { it.name == name } ?: AlertCategory.GENERIC).accentArgb)

@Composable
internal fun NotificationContentSettings(
    preferences: NotificationContentPreferences,
    onFieldChange: (FilterAlertType, Boolean, NotificationField, Boolean) -> Unit,
    onImageStyleChange: (FilterAlertType, NotificationImageStyle) -> Unit,
    onTitlePartChange: (FilterAlertType, NotificationTitlePart, Boolean) -> Unit,
    onSendTest: (FilterAlertType, Boolean, (Boolean) -> Unit) -> Unit,
    onReset: (FilterAlertType) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val category = categoryName?.let(FilterAlertType::valueOf)
    BackHandler(enabled = category != null) { categoryName = null }

    if (category == null) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).readableWidth().padding(bottom = 24.dp)) {
            PanelHeader(stringResource(R.string.notification_content_title), onBack = onBack)
            FilterAlertType.entries.forEach { entry ->
                val example = renderNotificationContent(notificationPreviewInput(entry), preferences).title
                PanelNavRow(
                    title = entry.label,
                    summary = stringResource(
                        R.string.notification_content_category_summary,
                        example,
                        stringResource(preferences.imageStyle(entry).labelRes)
                    ),
                    onClick = { categoryName = entry.name },
                    modifier = Modifier.testTag("notification_category_${entry.name}"),
                    active = preferences.isCustomized(entry),
                    icon = { CategoryBadge(entry) }
                )
            }
        }
        return
    }

    NotificationCategoryEditor(
        category = category,
        preferences = preferences,
        onFieldChange = onFieldChange,
        onImageStyleChange = onImageStyleChange,
        onTitlePartChange = onTitlePartChange,
        onSendTest = onSendTest,
        onReset = onReset,
        onBack = { categoryName = null },
        modifier = modifier
    )
}

@Composable
private fun CategoryBadge(category: FilterAlertType) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(category.accent().copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_poke_notification),
            contentDescription = null,
            tint = category.accent(),
            modifier = Modifier.size(20.dp)
        )
    }
}

private val TITLE_FIELD_PARTS = mapOf(
    NotificationField.IV to NotificationTitlePart.IV,
    NotificationField.CP to NotificationTitlePart.CP,
    NotificationField.VENUE to NotificationTitlePart.VENUE
)

/**
 * The preview, the Away / In range switch and the test button stay pinned at the top; the
 * choices scroll underneath, so every change is visible in the preview as it is made.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationCategoryEditor(
    category: FilterAlertType,
    preferences: NotificationContentPreferences,
    onFieldChange: (FilterAlertType, Boolean, NotificationField, Boolean) -> Unit,
    onImageStyleChange: (FilterAlertType, NotificationImageStyle) -> Unit,
    onTitlePartChange: (FilterAlertType, NotificationTitlePart, Boolean) -> Unit,
    onSendTest: (FilterAlertType, Boolean, (Boolean) -> Unit) -> Unit,
    onReset: (FilterAlertType) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    var previewExpanded by rememberSaveable { mutableStateOf(true) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    var inRange by rememberSaveable { mutableStateOf(false) }
    var previewName by rememberSaveable { mutableStateOf(NotificationPreviewKind.INCOMING.name) }
    val imageStyle = preferences.imageStyle(category)
    val kinds = NotificationPreviewKind.entries.filter { it != NotificationPreviewKind.RAID_WATCH || category == FilterAlertType.RAID }
    val preview = NotificationPreviewKind.valueOf(previewName).takeIf { it in kinds } ?: NotificationPreviewKind.INCOMING
    // Raid Watch outlives location tracking, so it never claims a range.
    val showRange = preview != NotificationPreviewKind.RAID_WATCH

    // The newest real alert of this category, so the preview shows a real picture.
    val now = remember(category) { System.currentTimeMillis() }
    var sample by remember(category) { mutableStateOf<PokemonAlert?>(null) }
    var picture by remember(category) { mutableStateOf<ImageBitmap?>(null) }
    var pictureLoading by remember(category) { mutableStateOf(true) }
    LaunchedEffect(category) {
        sample = TestNotification.sampleInput(context, category, inRange = false, now = now).alert
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
            category,
            journey = preview == NotificationPreviewKind.JOURNEY,
            raidWatch = preview == NotificationPreviewKind.RAID_WATCH,
            inRange = inRange && showRange,
            now = now,
            base = sample
        ),
        preferences
    )
    val sent = stringResource(R.string.notification_test_sent)
    val blocked = stringResource(R.string.notification_test_no_permission)
    val report: (Boolean) -> Unit = { ok -> Toast.makeText(context, if (ok) sent else blocked, Toast.LENGTH_SHORT).show() }

    Column(modifier.fillMaxSize().readableWidth()) {
        PanelHeader(
            category.label,
            subtitle = stringResource(R.string.notification_content_title),
            onBack = onBack
        )

        // Pinned -------------------------------------------------------------------------
        Row(
            Modifier.padding(horizontal = PanelGutter).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showRange) {
                SpringSegmentedRow(selectedIndex = if (inRange) 1 else 0, segmentCount = 2, modifier = Modifier.weight(1f)) {
                    listOf(false, true).forEach { state ->
                        SegmentedChoice(
                            label = stringResource(if (state) R.string.alerts_in_range else R.string.notification_state_away),
                            selected = inRange == state,
                            transparent = true,
                            modifier = Modifier.weight(1f).testTag("notification_preview_state_${if (state) "in_range" else "away"}"),
                            onClick = { inRange = state }
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            FilledTonalButton(
                onClick = { onSendTest(category, inRange && showRange, report) },
                modifier = Modifier.testTag("notification_test_send")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.notification_test_send), maxLines = 1)
            }
        }
        NotificationMockup(
            content = rendered,
            accent = category.accent(),
            expanded = previewExpanded,
            onToggle = { previewExpanded = !previewExpanded },
            live = preview != NotificationPreviewKind.INCOMING,
            imageStyle = if (preview == NotificationPreviewKind.INCOMING) imageStyle else NotificationImageStyle.NONE,
            picture = picture,
            pictureLoading = pictureLoading
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        // Scrolling choices ---------------------------------------------------------------
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            PanelSectionLabel(stringResource(R.string.notification_section_preview_as))
            SpringSegmentedRow(
                selectedIndex = kinds.indexOf(preview),
                segmentCount = kinds.size,
                modifier = Modifier.padding(horizontal = PanelGutter)
            ) {
                kinds.forEach { kind ->
                    SegmentedChoice(
                        label = stringResource(kind.labelRes),
                        selected = preview == kind,
                        transparent = true,
                        modifier = Modifier.weight(1f).testTag("notification_preview_kind_${kind.name}"),
                        onClick = { previewName = kind.name }
                    )
                }
            }

            // Title ---------------------------------------------------------------------
            PanelSectionLabel(stringResource(R.string.notification_section_title))
            val titleParts = preferences.titleParts(category)
            FlowRow(Modifier.padding(horizontal = PanelGutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                notificationTitleParts(category).forEach { part ->
                    val selected = part in titleParts
                    FilterChip(
                        selected = selected,
                        onClick = { onTitlePartChange(category, part, !selected) },
                        label = { Text(stringResource(part.labelRes)) },
                        leadingIcon = { if (selected) ChipCheck() },
                        modifier = Modifier.testTag("notification_title_${part.name}")
                    )
                }
            }

            // Picture -------------------------------------------------------------------
            PanelSectionLabel(stringResource(R.string.notification_section_picture))
            val styles = NotificationImageStyle.entries
            SpringSegmentedRow(
                selectedIndex = styles.indexOf(imageStyle),
                segmentCount = styles.size,
                modifier = Modifier.padding(horizontal = PanelGutter)
            ) {
                styles.forEach { style ->
                    SegmentedChoice(
                        label = stringResource(style.labelRes),
                        selected = style == imageStyle,
                        transparent = true,
                        modifier = Modifier.weight(1f).testTag("notification_image_${style.name}"),
                        onClick = { onImageStyleChange(category, style) }
                    )
                }
            }
            SupportingText(stringResource(R.string.notification_picture_note))

            // Details -------------------------------------------------------------------
            PanelSectionLabel(stringResource(R.string.notification_section_details))
            SpringSegmentedRow(
                selectedIndex = if (detailsExpanded) 1 else 0,
                segmentCount = 2,
                modifier = Modifier.padding(horizontal = PanelGutter)
            ) {
                listOf(false, true).forEach { detail ->
                    SegmentedChoice(
                        label = stringResource(if (detail) R.string.notification_content_expanded else R.string.notification_content_collapsed),
                        selected = detailsExpanded == detail,
                        transparent = true,
                        modifier = Modifier.weight(1f).testTag("notification_view_${if (detail) "expanded" else "collapsed"}"),
                        onClick = {
                            detailsExpanded = detail
                            // Show the view being edited.
                            previewExpanded = detail
                        }
                    )
                }
            }
            SupportingText(
                stringResource(if (detailsExpanded) R.string.notification_details_expanded_hint else R.string.notification_details_collapsed_hint)
            )
            val available = notificationFields(category, detailsExpanded)
            val selectedFields = preferences.fields(category, detailsExpanded)
            val inTitle = TITLE_FIELD_PARTS.filterValues { it in titleParts }.keys
            FIELD_GROUPS.forEach { (labelRes, group) ->
                val fields = group.filter { it in available }
                if (fields.isEmpty()) return@forEach
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = PanelGutter, end = PanelGutter, top = 12.dp, bottom = 4.dp)
                )
                FlowRow(Modifier.padding(horizontal = PanelGutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fields.forEach { field ->
                        val selected = field in selectedFields
                        FilterChip(
                            selected = selected,
                            // A fact in the title is never repeated below it.
                            enabled = field !in inTitle,
                            onClick = { onFieldChange(category, detailsExpanded, field, !selected) },
                            label = { Text(stringResource(field.labelRes)) },
                            leadingIcon = { if (selected) ChipCheck() },
                            modifier = Modifier.testTag("notification_field_${field.name}")
                        )
                    }
                }
                val titled = fields.filter { it in inTitle }
                if (titled.isNotEmpty()) {
                    SupportingText(
                        stringResource(R.string.notification_field_in_title, titled.joinToString { context.getString(it.labelRes) })
                    )
                }
                val note = when {
                    NotificationField.DISTANCE in fields -> R.string.notification_content_in_range_note
                    NotificationField.TEAM in fields -> R.string.notification_content_team_note
                    NotificationField.DESCRIPTION in fields -> R.string.notification_content_description_note
                    else -> null
                }
                note?.let { SupportingText(stringResource(it)) }
            }

            TextButton(
                onClick = { onReset(category) },
                modifier = Modifier.padding(horizontal = PanelGutter - 12.dp, vertical = 8.dp).testTag("notification_content_reset")
            ) { Text(stringResource(R.string.notification_content_reset)) }
        }
    }
}

@Composable
private fun ChipCheck() {
    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
}

@Composable
private fun SupportingText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = PanelGutter, end = PanelGutter, top = 6.dp)
    )
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
            .padding(horizontal = PanelGutter, vertical = 12.dp)
            .fillMaxWidth()
            .testTag("notification_preview_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            Modifier.clickable(onClick = onToggle).animateContentSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_poke_notification),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
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
                        maxLines = if (!expanded) 1 else if (bigPicture) 2 else 12,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("notification_preview_body")
                    )
                }
                if (icon) {
                    Spacer(Modifier.width(12.dp))
                    PreviewPicture(picture, pictureLoading, Modifier.size(44.dp), RoundedCornerShape(10.dp))
                }
            }
            if (bigPicture) {
                PreviewPicture(
                    picture, pictureLoading,
                    Modifier.fillMaxWidth().aspectRatio(2f).testTag("notification_preview_picture"),
                    RoundedCornerShape(16.dp)
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
