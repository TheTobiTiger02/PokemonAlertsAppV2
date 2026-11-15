package com.example.pokemonalertsv2.notifications

import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.NotificationContentPreferences
import com.example.pokemonalertsv2.data.NotificationField
import com.example.pokemonalertsv2.data.NotificationFieldStyle
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

/** The same layout is used by native notifications and the Settings previews. */
internal fun renderNotificationContent(
    input: NotificationContentInput,
    preferences: NotificationContentPreferences = NotificationContentPreferences()
): NotificationContent {
    val alert = input.alert
    val category = notificationContentCategory(alert)
    val titleOrder = preferences.titleOrder(category)
    // A fact already in the title is not repeated underneath it.
    val inTitle = TITLE_FIELDS.filterKeys { it in titleOrder }.values.toSet()
    val collapsedOrder = preferences.collapsedOrder(category)
    val expandedLines = preferences.expandedLines(category)
    val collapsedFields = collapsedOrder.toSet()
    val expandedFields = expandedLines.flatten().toSet()
    val remaining = input.endMillis?.minus(input.nowMillis)
    val values = notificationFieldValues(input, remaining)

    fun text(field: NotificationField): String? {
        if (input.inRange && field in TRAVEL_FIELDS) return null
        val value = values[field] ?: return null
        return value.styled(field, preferences.style(category, field))
    }

    // "In range" stands in for the travel details, so it follows their selection.
    fun status(fields: Set<NotificationField>): List<String> = buildList {
        input.status.clean()?.let(::add)
        if (input.inRange && fields.any { it in TRAVEL_FIELDS }) add(AppText.get(R.string.alerts_in_range))
        if (remaining != null && remaining <= 0) {
            add(AppText.get(if (category == FilterAlertType.RAID) R.string.raidwatch_raid_ended else R.string.notification_content_ended))
        }
    }.distinct()

    val collapsed = (status(collapsedFields - inTitle) + collapsedOrder.filterNot { it in inTitle }.mapNotNull(::text))
        .distinct().ifEmpty { listOf(category.label) }

    val lines = expandedLines.mapNotNull { line ->
        line.filterNot { it in inTitle }.mapNotNull(::text).distinct().joinToString(" • ").clean()
    }
    // Raid Watch leads with the suggested team, unless the user ordered the lines themselves.
    val team = if (NotificationField.TEAM in expandedFields - inTitle) text(NotificationField.TEAM) else null
    val leadWithTeam = input.raidWatch && team != null && !preferences.hasCustomExpandedOrder(category)
    val expandedStatus = status(expandedFields - inTitle)
    val expanded = (if (leadWithTeam) listOf(team!!) + expandedStatus + lines.filterNot { it == team }
        else expandedStatus + lines).distinct().ifEmpty { listOf(category.label) }

    return NotificationContent(
        title = notificationTitle(alert, titleOrder, input.goDexStatus),
        collapsed = collapsed.joinToString(" • ") { it.replace(WHITESPACE, " ") },
        expanded = expanded.joinToString("\n"),
        chip = notificationChip(input, collapsedFields, remaining),
        showCountdown = NotificationField.TIME_LEFT in collapsedFields && NotificationField.TIME_LEFT in expandedFields
    )
}

/** Optional facts live in the selected details, so a title cannot leak a hidden field. */
internal fun notificationIdentity(alert: PokemonAlert): String {
    val category = notificationContentCategory(alert)
    val name = com.example.pokemonalertsv2.data.gametext.GameNames.pokemon(alert.pokemon.clean() ?: alert.cleanPokemonName.clean(), alert.pokedexId).clean() ?: category.label
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
    parts: Collection<NotificationTitlePart>,
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
    // The GoDex marker always leads; every other part follows the user's order.
    val segments = parts.distinct().mapNotNull { part ->
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
    val name = com.example.pokemonalertsv2.data.gametext.GameNames.pokemon(alert.pokemon.clean() ?: alert.cleanPokemonName.clean(), alert.pokedexId).clean()
    if (alert.isSpeciesReplacement) {
        val old = com.example.pokemonalertsv2.data.gametext.GameNames.pokemon(alert.oldSpecies.clean()).clean()
        val new = com.example.pokemonalertsv2.data.gametext.GameNames.pokemon(alert.newSpecies.clean()).clean()
        if (old != null && new != null) return "$old → $new"
    }
    return when (category) {
        FilterAlertType.QUEST -> questAlertPresentation(alert)?.reward.clean() ?: alert.questReward.clean()
            ?: AppText.get(R.string.notification_content_research)
        FilterAlertType.ROCKET -> com.example.pokemonalertsv2.data.gametext.GameNames.grunt(alert.gruntType.clean(), alert.gruntTypeId).clean()?.replaceFirstChar { it.uppercaseChar() }
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

/**
 * One detail, written two ways: [standard] is the wording the app has always
 * used ("CP 2500"), [value] is the bare fact ("2500") that the Value, Label
 * and Icon styles build on.
 */
private data class FieldText(val standard: String, val value: String)

private fun FieldText.styled(field: NotificationField, style: NotificationFieldStyle): String = when (style) {
    NotificationFieldStyle.STANDARD -> standard
    NotificationFieldStyle.VALUE -> value
    NotificationFieldStyle.LABEL -> AppText.get(R.string.notification_style_label_format, AppText.get(field.shortLabelRes), value)
    NotificationFieldStyle.ICON -> "${field.icon} $value"
}

private fun fieldText(standard: String?, value: String? = standard): FieldText? {
    val shown = standard.clean() ?: return null
    return FieldText(shown, value.clean() ?: shown)
}

private fun notificationFieldValues(input: NotificationContentInput, remaining: Long?): Map<NotificationField, FieldText?> {
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
    val iv = (if (alert.isWeatherChange) alert.newIv else alert.formattedIv).clean()
    val grunt = com.example.pokemonalertsv2.data.gametext.GameNames.grunt(alert.gruntType.clean(), alert.gruntTypeId).clean()
    val live = remaining?.takeIf { it > 0 }
    val arRequired = alert.requiresAR == true
    return mapOf(
        NotificationField.WEATHER to fieldText(weather),
        NotificationField.DISTANCE to fieldText(
            listOfNotNull(input.rangeHint.clean(), input.distanceText.clean()).joinToString(" • ").clean(),
            input.distanceText
        ),
        NotificationField.WALKING_TIME to fieldText(input.walkingText),
        NotificationField.TIME_LEFT to live?.let {
            fieldText(
                if (it < 60_000L) AppText.get(R.string.countdown_short_prefix, TimeUtils.formatDurationShort(it))
                else AppText.get(R.string.notification_content_minutes_left, (it + 59_999L) / 60_000L),
                if (it < 60_000L) TimeUtils.formatDurationShort(it)
                else AppText.get(R.string.notification_content_minutes, (it + 59_999L) / 60_000L)
            )
        },
        NotificationField.CP to alert.displayCp?.let { fieldText(AppText.get(R.string.notification_content_cp, it), it.toString()) },
        NotificationField.CATCH_CP to fieldText(buildPipCpText(alert), compactCatchCpText(alert)),
        NotificationField.IV to iv?.let { fieldText(AppText.get(R.string.notification_content_iv, it), it) },
        NotificationField.FORM to fieldText(com.example.pokemonalertsv2.data.gametext.GameNames.form(alert.pokemonForm, alert.formId)),
        NotificationField.PVP_RANK to fieldText(bestRanking?.let { "${it.league.orEmpty()} #${it.rank}".trim() }),
        NotificationField.VENUE to fieldText(alert.gym.clean() ?: alert.pokestop.clean() ?: alert.pokemonLocation.clean()),
        NotificationField.AREA to fieldText(alert.area),
        NotificationField.TASK to fieldText(quest?.task ?: alert.questTask),
        NotificationField.REWARD to reward?.let { fieldText(AppText.get(R.string.notification_content_reward, it), it) },
        NotificationField.AR to fieldText(
            AppText.get(R.string.notification_content_ar_required).takeIf { arRequired },
            AppText.get(R.string.notification_content_ar_value).takeIf { arRequired }
        ),
        NotificationField.GRUNT to grunt?.let { fieldText(AppText.get(R.string.notification_content_grunt, it), it) },
        NotificationField.MOVES to fieldText(with(com.example.pokemonalertsv2.data.gametext.GameNames) { moves(alert.moves?.fast, alert.moves?.charged, alert.moveIds?.fast, alert.moveIds?.charged) }
            .let { (fast, charged) -> listOfNotNull(fast, charged).takeIf { it.isNotEmpty() }?.joinToString(" / ") }),
        NotificationField.GODEX to fieldText(input.goDexText),
        NotificationField.TEAM to fieldText(input.teamText.takeIf { input.raidWatch }),
        NotificationField.DESCRIPTION to fieldText(
            alert.description.clean()?.takeUnless { it == alert.type?.joinToString(", ") }
        )
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
