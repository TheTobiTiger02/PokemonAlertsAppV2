package com.example.pokemonalertsv2.notifications

import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.NotificationContentPreferences
import com.example.pokemonalertsv2.data.NotificationField
import com.example.pokemonalertsv2.data.NotificationTitlePart
import com.example.pokemonalertsv2.data.RaidTierParser
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.notificationContentCategory
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.ui.alerts.buildPipCpText
import com.example.pokemonalertsv2.ui.alerts.currentWeatherDisplay
import com.example.pokemonalertsv2.ui.alerts.displayCp
import com.example.pokemonalertsv2.ui.alerts.questAlertPresentation
import com.example.pokemonalertsv2.ui.alerts.weatherTransitionLabel
import com.example.pokemonalertsv2.util.AppText
import com.example.pokemonalertsv2.util.TimeUtils

internal data class NotificationContentInput(
    val alert: PokemonAlert,
    val distanceText: String? = null,
    val walkingText: String? = null,
    val inRange: Boolean = false,
    val status: String? = null,
    val nowMillis: Long = System.currentTimeMillis(),
    val endMillis: Long? = TimeUtils.parseEndTimeToMillis(alert.endTime),
    val goDexText: String? = null,
    val raidWatch: Boolean = false,
    val teamText: String? = null,
    val rangeHint: String? = null,
    val goDexStatus: GoDexMatchStatus? = null
)

internal data class NotificationContent(
    val title: String,
    val collapsed: String,
    val expanded: String,
    val chip: String,
    val showCountdown: Boolean
)

/** The same field selection is used by native notifications and the Settings previews. */
internal fun renderNotificationContent(
    input: NotificationContentInput,
    preferences: NotificationContentPreferences = NotificationContentPreferences()
): NotificationContent {
    val alert = input.alert
    val category = notificationContentCategory(alert)
    val titleParts = preferences.titleParts(category)
    // A fact already in the title is not repeated underneath it.
    val inTitle = TITLE_FIELDS.filterKeys { it in titleParts }.values.toSet()
    val collapsedFields = preferences.fields(category, expanded = false)
    val expandedFields = preferences.fields(category, expanded = true)
    val remaining = input.endMillis?.minus(input.nowMillis)
    val values = notificationFieldValues(input, remaining)

    fun lines(selected: Set<NotificationField>, expanded: Boolean): List<String> {
        val fields = selected - inTitle
        // "In range" stands in for the travel details, so it follows their selection.
        val status = buildList {
            input.status.clean()?.let(::add)
            if (input.inRange && fields.any { it in TRAVEL_FIELDS }) add(AppText.get(R.string.alerts_in_range))
            if (remaining != null && remaining <= 0) {
                add(AppText.get(if (category == FilterAlertType.RAID) R.string.raidwatch_raid_ended else R.string.notification_content_ended))
            }
        }.distinct()
        val order = if (input.raidWatch && expanded) {
            listOf(NotificationField.TEAM) + NotificationField.entries.filterNot { it == NotificationField.TEAM }
        } else NotificationField.entries
        val detail = order.filter { it in fields }.mapNotNull { field ->
            if (input.inRange && field in TRAVEL_FIELDS) null else values[field].clean()
        }
        val team = values[NotificationField.TEAM].clean()
        val ordered = if (input.raidWatch && expanded && NotificationField.TEAM in fields && team != null) {
            listOf(team) + status + detail.filterNot { it == team }
        } else status + detail
        return ordered.distinct().ifEmpty { listOf(category.label) }
    }

    return NotificationContent(
        title = notificationTitle(alert, titleParts, input.goDexStatus),
        collapsed = lines(collapsedFields, expanded = false).joinToString(" • ") { it.replace(WHITESPACE, " ") },
        expanded = lines(expandedFields, expanded = true).joinToString("\n"),
        chip = notificationChip(input, collapsedFields, remaining),
        showCountdown = NotificationField.TIME_LEFT in collapsedFields && NotificationField.TIME_LEFT in expandedFields
    )
}

/** Optional facts live in the selected details, so a title cannot leak a hidden field. */
internal fun notificationIdentity(alert: PokemonAlert): String {
    val category = notificationContentCategory(alert)
    val name = alert.pokemon.clean() ?: alert.cleanPokemonName.clean() ?: category.label
    return when (category) {
        FilterAlertType.RAID -> AppText.get(R.string.notification_content_raid_title, name)
        FilterAlertType.QUEST -> AppText.get(R.string.notification_content_research)
        FilterAlertType.ROCKET -> AppText.get(R.string.alerts_team_go_rocket)
        FilterAlertType.KECLEON -> "Kecleon"
        FilterAlertType.WEATHER -> AppText.get(R.string.notification_content_weather_title, name)
        else -> name
    }
}

/** Builds the title from the selected parts, e.g. "🎯 Hundo · Pikachu" or "5★ Raid · Mewtwo". */
internal fun notificationTitle(
    alert: PokemonAlert,
    parts: Set<NotificationTitlePart>,
    goDexStatus: GoDexMatchStatus? = null
): String {
    val category = notificationContentCategory(alert)
    val subject = notificationSubject(alert, category)
    val marker = when (goDexStatus) {
        GoDexMatchStatus.NEEDED -> "🎯"
        GoDexMatchStatus.EVOLUTION_NEEDED,
        GoDexMatchStatus.FORM_CHANGE_NEEDED,
        GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED -> "🧬"
        else -> null
    }?.takeIf { NotificationTitlePart.GODEX_MARKER in parts }
    val segments = NotificationTitlePart.entries.filter { it in parts }.mapNotNull { part ->
        when (part) {
            NotificationTitlePart.GODEX_MARKER -> null
            NotificationTitlePart.CATEGORY -> notificationCategoryTag(alert, category)
            NotificationTitlePart.SUBJECT -> subject
            NotificationTitlePart.IV -> (if (alert.isWeatherChange) alert.newIv else alert.formattedIv).clean()
                ?.let { AppText.get(R.string.notification_content_iv, it) }
            NotificationTitlePart.CP -> alert.displayCp?.let { AppText.get(R.string.notification_content_cp, it) }
            NotificationTitlePart.VENUE -> alert.gym.clean() ?: alert.pokestop.clean()
        }.clean()
    }.distinctBy { it.lowercase() }.ifEmpty { listOf(subject ?: category.label) }
    return listOfNotNull(marker, segments.joinToString(" · ")).joinToString(" ")
}

/** The short singular tag that says what kind of alert this is. */
internal fun notificationCategoryTag(alert: PokemonAlert, category: FilterAlertType = notificationContentCategory(alert)): String =
    when (category) {
        FilterAlertType.SPAWN -> AppText.get(R.string.notification_tag_spawn)
        FilterAlertType.RAID -> RaidTierParser.parse(alert)?.displayLabel
            ?.let { tier -> AppText.get(R.string.notification_tag_raid_tier, if (tier.all(Char::isDigit)) "$tier★" else tier) }
            ?: AppText.get(R.string.notification_tag_raid)
        FilterAlertType.QUEST -> AppText.get(R.string.notification_tag_quest)
        FilterAlertType.ROCKET -> AppText.get(R.string.notification_tag_rocket)
        FilterAlertType.KECLEON -> "Kecleon"
        FilterAlertType.HUNDO -> AppText.get(R.string.notification_tag_hundo)
        FilterAlertType.NUNDO -> AppText.get(R.string.notification_tag_nundo)
        FilterAlertType.PVP -> AppText.get(R.string.notification_tag_pvp)
        FilterAlertType.COMMON -> AppText.get(R.string.notification_tag_common)
        FilterAlertType.TAPPABLE -> AppText.get(R.string.notification_tag_tappable)
        FilterAlertType.WEATHER -> AppText.get(R.string.notification_tag_weather)
        FilterAlertType.OTHER -> AppText.get(R.string.notification_tag_other)
    }

/** What the alert is about: the Pokémon, the quest reward or the grunt type. */
private fun notificationSubject(alert: PokemonAlert, category: FilterAlertType): String? {
    val name = alert.pokemon.clean() ?: alert.cleanPokemonName.clean()
    if (alert.isSpeciesReplacement) {
        val old = alert.oldSpecies.clean()
        val new = alert.newSpecies.clean()
        if (old != null && new != null) return "$old → $new"
    }
    return when (category) {
        FilterAlertType.QUEST -> questAlertPresentation(alert)?.reward.clean() ?: alert.questReward.clean()
            ?: AppText.get(R.string.notification_content_research)
        FilterAlertType.ROCKET -> alert.gruntType.clean()?.replaceFirstChar { it.uppercaseChar() }
            ?.let { AppText.get(R.string.notification_content_grunt, it) }
            ?: AppText.get(R.string.alerts_team_go_rocket)
        FilterAlertType.KECLEON -> "Kecleon"
        FilterAlertType.TAPPABLE -> name ?: alert.questReward.clean()
        else -> name
    }
}

private val TITLE_FIELDS = mapOf(
    NotificationTitlePart.IV to NotificationField.IV,
    NotificationTitlePart.CP to NotificationField.CP,
    NotificationTitlePart.VENUE to NotificationField.VENUE
)
private val TRAVEL_FIELDS = setOf(NotificationField.DISTANCE, NotificationField.WALKING_TIME)
private val WHITESPACE = Regex("\\s+")

private fun notificationFieldValues(input: NotificationContentInput, remaining: Long?): Map<NotificationField, String?> {
    val alert = input.alert
    val quest = questAlertPresentation(alert)
    val weather = if (alert.isWeatherChange) weatherTransitionLabel(alert) else {
        listOfNotNull(
            currentWeatherDisplay(alert)?.compactLabel,
            AppText.get(R.string.notification_content_boosted).takeIf { alert.isWeatherBoosted == true }
        ).joinToString(" • ").clean()
    }
    val bestRanking = alert.pvpRankings.orEmpty().filter { (it.rank ?: 0) > 0 }.minByOrNull { it.rank!! }
    val reward = quest?.reward ?: alert.questReward.clean() ?: alert.pokemonRewards.orEmpty()
        .mapNotNull { it.pokemon.clean() }.joinToString(", ").clean()
    return mapOf(
        NotificationField.WEATHER to weather,
        NotificationField.DISTANCE to listOfNotNull(input.rangeHint.clean(), input.distanceText.clean())
            .joinToString(" • ").clean(),
        NotificationField.WALKING_TIME to input.walkingText,
        NotificationField.TIME_LEFT to remaining?.takeIf { it > 0 }?.let {
            if (it < 60_000L) AppText.get(R.string.countdown_short_prefix, TimeUtils.formatDurationShort(it))
            else AppText.get(R.string.notification_content_minutes_left, (it + 59_999L) / 60_000L)
        },
        NotificationField.CP to alert.displayCp?.let { AppText.get(R.string.notification_content_cp, it) },
        NotificationField.CATCH_CP to buildPipCpText(alert),
        NotificationField.IV to (if (alert.isWeatherChange) alert.newIv else alert.formattedIv).clean()
            ?.let { AppText.get(R.string.notification_content_iv, it) },
        NotificationField.FORM to alert.pokemonForm,
        NotificationField.PVP_RANK to bestRanking?.let { "${it.league.orEmpty()} #${it.rank}".trim() },
        NotificationField.VENUE to (alert.gym.clean() ?: alert.pokestop.clean() ?: alert.pokemonLocation.clean()),
        NotificationField.AREA to alert.area,
        NotificationField.TASK to (quest?.task ?: alert.questTask),
        NotificationField.REWARD to reward?.let { AppText.get(R.string.notification_content_reward, it) },
        NotificationField.AR to AppText.get(R.string.notification_content_ar_required).takeIf { alert.requiresAR == true },
        NotificationField.GRUNT to alert.gruntType.clean()?.let { AppText.get(R.string.notification_content_grunt, it) },
        NotificationField.MOVES to alert.moves?.formatted(),
        NotificationField.GODEX to input.goDexText,
        NotificationField.TEAM to input.teamText.takeIf { input.raidWatch },
        NotificationField.DESCRIPTION to alert.description.clean()
            ?.takeUnless { it == alert.type?.joinToString(", ") }
    )
}

private fun notificationChip(
    input: NotificationContentInput,
    fields: Set<NotificationField>,
    remaining: Long?
): String {
    val alert = input.alert
    val catchCp = compactCatchCpText(alert)
    if (input.raidWatch) {
        if (NotificationField.CATCH_CP in fields && catchCp != null) return catchCp
        if (NotificationField.TIME_LEFT in fields && remaining != null && remaining > 0) {
            return TimeUtils.formatDurationShort(remaining)
        }
        return FilterAlertType.RAID.label
    }
    if (input.inRange) {
        if (NotificationField.CP in fields && alert.displayCp != null) {
            return AppText.get(R.string.notification_content_cp, alert.displayCp!!)
        }
        if (NotificationField.CATCH_CP in fields && catchCp != null) return catchCp
        if (NotificationField.VENUE in fields) {
            (alert.pokestop.clean() ?: alert.gym.clean())?.let { return it.take(18) }
        }
        return AppText.get(R.string.alerts_in_range)
    }
    input.status.clean()?.let { return it.take(18) }
    if (NotificationField.DISTANCE in fields) input.distanceText.clean()?.let {
        return it.removeSuffix(" walk").removeSuffix(" direct")
    }
    if (NotificationField.WALKING_TIME in fields) input.walkingText.clean()?.let { return it }
    return AppText.get(R.string.notification_content_on_the_way)
}

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

internal fun compactCatchCpText(alert: PokemonAlert): String? =
    listOfNotNull(alert.hundoCP?.level20, alert.hundoCP?.level25)
        .filter { it > 0 }.joinToString("/").clean()

internal fun goDexNotificationText(alert: PokemonAlert, result: GoDexMatchResult): String? {
    if (notificationContentCategory(alert) != FilterAlertType.HUNDO) return null
    return when (result.status) {
        GoDexMatchStatus.NEEDED -> AppText.get(R.string.notifications_needed_in_godex)
        GoDexMatchStatus.EVOLUTION_NEEDED -> "Collected • Evolution needed: ${result.compactEvolutionLabel ?: "evolution"}"
        GoDexMatchStatus.FORM_CHANGE_NEEDED -> "Collected • Form change needed: ${result.compactFormChangeLabel ?: "form"}"
        GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED ->
            "Collected • Evolution needed: ${result.compactEvolutionLabel ?: "evolution"}" +
                " • Form change needed: ${result.compactFormChangeLabel ?: "form"}"
        GoDexMatchStatus.COLLECTED -> AppText.get(R.string.notifications_already_collected_in_godex)
        GoDexMatchStatus.UNKNOWN -> AppText.get(R.string.notifications_godex_form_unknown)
        GoDexMatchStatus.NOT_CONFIGURED -> null
    }?.trim()?.removePrefix("• ")
}
