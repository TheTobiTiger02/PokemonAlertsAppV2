package com.example.pokemonalertsv2.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.HundoCP
import com.example.pokemonalertsv2.data.NotificationContentPreferences
import com.example.pokemonalertsv2.data.NotificationField
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonMoves
import com.example.pokemonalertsv2.data.PvpRanking
import com.example.pokemonalertsv2.data.notificationFields
import com.example.pokemonalertsv2.notifications.NotificationContentInput
import com.example.pokemonalertsv2.notifications.renderNotificationContent
import com.example.pokemonalertsv2.ui.components.PanelHeader
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelIcon
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.PanelSwitchRow

private enum class NotificationPreviewKind(val labelRes: Int) {
    INCOMING(R.string.notification_content_incoming),
    JOURNEY(R.string.notification_content_journey),
    RAID_WATCH(R.string.notification_content_raid_watch)
}

@Composable
internal fun NotificationContentSettings(
    preferences: NotificationContentPreferences,
    onFieldChange: (FilterAlertType, Boolean, NotificationField, Boolean) -> Unit,
    onReset: (FilterAlertType) -> Unit,
    onBack: () -> Unit
) {
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var previewName by rememberSaveable { mutableStateOf(NotificationPreviewKind.INCOMING.name) }
    var inRange by rememberSaveable { mutableStateOf(false) }
    val category = categoryName?.let(FilterAlertType::valueOf)
    BackHandler(enabled = category != null) { categoryName = null }

    if (category == null) {
        PanelHeader(stringResource(R.string.notification_content_title), onBack = onBack)
        FilterAlertType.entries.forEach { entry ->
            PanelNavRow(
                title = entry.label,
                summary = stringResource(
                    R.string.notification_content_category_summary,
                    preferences.fields(entry, false).size,
                    preferences.fields(entry, true).size
                ),
                onClick = { categoryName = entry.name },
                modifier = Modifier.testTag("notification_category_${entry.name}"),
                icon = { PanelIcon(R.drawable.ic_poke_notification) }
            )
        }
        return
    }

    PanelHeader(
        category.label,
        subtitle = stringResource(R.string.notification_content_title),
        onBack = { categoryName = null }
    )
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false, true).forEach { detail ->
            FilterChip(
                selected = expanded == detail,
                onClick = { expanded = detail },
                label = { Text(stringResource(if (detail) R.string.notification_content_expanded else R.string.notification_content_collapsed)) },
                modifier = Modifier.testTag("notification_view_${if (detail) "expanded" else "collapsed"}")
            )
        }
    }
    val preview = NotificationPreviewKind.valueOf(previewName).let {
        if (it == NotificationPreviewKind.RAID_WATCH && category != FilterAlertType.RAID) NotificationPreviewKind.INCOMING else it
    }
    PanelSectionLabel(stringResource(R.string.notification_content_preview))
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NotificationPreviewKind.entries.filter { it != NotificationPreviewKind.RAID_WATCH || category == FilterAlertType.RAID }.forEach { kind ->
            FilterChip(
                selected = preview == kind,
                onClick = { previewName = kind.name },
                label = { Text(stringResource(kind.labelRes)) },
                modifier = Modifier.testTag("notification_preview_kind_${kind.name}")
            )
        }
    }
    val rendered = renderNotificationContent(
        notificationPreviewInput(category, preview == NotificationPreviewKind.JOURNEY, preview == NotificationPreviewKind.RAID_WATCH, inRange),
        preferences
    )
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(rendered.title, style = MaterialTheme.typography.titleMedium)
            Text(
                if (expanded) rendered.expanded else rendered.collapsed,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) 10 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("notification_preview_body")
            )
            if (preview != NotificationPreviewKind.INCOMING) {
                Text(rendered.chip, style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("notification_preview_chip"))
            }
        }
    }
    if (preview != NotificationPreviewKind.RAID_WATCH) {
        PanelSwitchRow(
            title = stringResource(R.string.alerts_in_range),
            checked = inRange,
            onCheckedChange = { inRange = it },
            subtitle = stringResource(R.string.notification_content_in_range_note),
            modifier = Modifier.testTag("notification_preview_in_range")
        )
    }
    notificationFields(category, expanded).forEach { field ->
        val subtitle = when (field) {
            NotificationField.DESCRIPTION -> stringResource(R.string.notification_content_description_note)
            NotificationField.TEAM -> stringResource(R.string.notification_content_team_note)
            else -> null
        }
        PanelSwitchRow(
            title = stringResource(field.labelRes),
            checked = field in preferences.fields(category, expanded),
            onCheckedChange = { onFieldChange(category, expanded, field, it) },
            subtitle = subtitle,
            modifier = Modifier.testTag("notification_field_${field.name}")
        )
    }
    TextButton(
        onClick = { onReset(category) },
        modifier = Modifier.padding(horizontal = 20.dp).testTag("notification_content_reset")
    ) { Text(stringResource(R.string.notification_content_reset)) }
}

/** Fixed data makes a selection's effect visible without posting an alert or using live location. */
internal fun notificationPreviewInput(
    category: FilterAlertType,
    journey: Boolean = false,
    raidWatch: Boolean = false,
    inRange: Boolean = false
): NotificationContentInput {
    val now = 1_800_000_000_000L
    val encounter = category in setOf(FilterAlertType.SPAWN, FilterAlertType.HUNDO, FilterAlertType.NUNDO, FilterAlertType.PVP, FilterAlertType.COMMON)
    val alert = PokemonAlert(
        name = if (encounter) "Rookidee" else category.label,
        pokemon = when {
            category == FilterAlertType.RAID -> "Mewtwo"
            encounter || category == FilterAlertType.WEATHER -> "Rookidee"
            else -> null
        },
        type = listOf(when (category) {
            FilterAlertType.OTHER -> "Other"
            FilterAlertType.WEATHER -> "WeatherChange"
            else -> category.name
        }),
        cp = if (encounter) 419 else null,
        iv = when {
            category == FilterAlertType.NUNDO -> "0/0/0"
            category == FilterAlertType.HUNDO -> "15/15/15"
            encounter -> "10/12/14"
            else -> null
        },
        pokemonForm = if (encounter) "Normal" else null,
        currentWeather = "Windy",
        isWeatherBoosted = category == FilterAlertType.RAID,
        gym = if (category == FilterAlertType.RAID) "Central Gym" else null,
        pokestop = if (category in setOf(FilterAlertType.QUEST, FilterAlertType.ROCKET, FilterAlertType.KECLEON)) "Library Stop" else null,
        area = "Alsbach",
        moves = if (category == FilterAlertType.RAID) PokemonMoves("Confusion", "Psystrike") else null,
        hundoCP = if (category == FilterAlertType.RAID) HundoCP(2387, 2984) else null,
        pvpRankings = if (category == FilterAlertType.PVP) listOf(PvpRanking(league = "Great League", rank = 1)) else null,
        questTask = if (category == FilterAlertType.QUEST) "Make 3 Great Throws" else null,
        questReward = if (category in setOf(FilterAlertType.QUEST, FilterAlertType.TAPPABLE)) "Rare Candy ×3" else null,
        requiresAR = category == FilterAlertType.QUEST,
        gruntType = if (category == FilterAlertType.ROCKET) "Dragon" else null,
        weatherFrom = if (category == FilterAlertType.WEATHER) "Cloudy" else null,
        weatherTo = if (category == FilterAlertType.WEATHER) "Windy" else null,
        newCp = if (category == FilterAlertType.WEATHER) 593 else null,
        newIv = if (category == FilterAlertType.WEATHER) "10/12/14" else null,
        description = "Additional alert details",
        endTime = (now + 20 * 60_000L).toString()
    )
    return NotificationContentInput(
        alert = alert,
        distanceText = if (journey) "320 m walk" else "320 m",
        walkingText = "4 min walk",
        inRange = inRange && !raidWatch,
        nowMillis = now,
        goDexText = "Needed in GoDex".takeIf { category == FilterAlertType.HUNDO },
        raidWatch = raidWatch,
        teamText = "1. Tyranitar ×2 · Bite › Brutal Swing\n2. Hydreigon ×2 · Bite › Brutal Swing\n3. Gengar ×2 · Shadow Claw › Shadow Ball".takeIf { raidWatch }
    )
}
