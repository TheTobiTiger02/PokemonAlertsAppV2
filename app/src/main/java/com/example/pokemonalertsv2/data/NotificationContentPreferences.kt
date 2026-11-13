package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stable names are persisted so future fields and categories can be ignored by older apps. */
enum class NotificationField(val labelRes: Int) {
    WEATHER(R.string.notification_field_weather),
    DISTANCE(R.string.notification_field_distance),
    WALKING_TIME(R.string.notification_field_walking_time),
    TIME_LEFT(R.string.notification_field_time_left),
    CP(R.string.notification_field_cp),
    CATCH_CP(R.string.notification_field_catch_cp),
    IV(R.string.notification_field_iv),
    FORM(R.string.notification_field_form),
    PVP_RANK(R.string.notification_field_pvp),
    VENUE(R.string.notification_field_venue),
    AREA(R.string.notification_field_area),
    TASK(R.string.notification_field_task),
    REWARD(R.string.notification_field_reward),
    AR(R.string.notification_field_ar),
    GRUNT(R.string.notification_field_grunt),
    MOVES(R.string.notification_field_moves),
    GODEX(R.string.notification_field_godex),
    TEAM(R.string.notification_field_team),
    DESCRIPTION(R.string.notification_field_description)
}

@Serializable
data class NotificationCategoryContent(
    val collapsed: Set<String>? = null,
    val expanded: Set<String>? = null
)

@Serializable
data class NotificationContentPreferences(
    val version: Int = 1,
    val categories: Map<String, NotificationCategoryContent> = emptyMap()
) {
    fun fields(category: FilterAlertType, expanded: Boolean): Set<NotificationField> {
        val selection = categories[category.name]
        val stored = if (expanded) selection?.expanded else selection?.collapsed
        val available = notificationFields(category, expanded)
        return stored?.let { names -> available.filterTo(linkedSetOf()) { it.name in names } }
            ?: defaultNotificationFields(category, expanded)
    }

    fun withField(
        category: FilterAlertType,
        expanded: Boolean,
        field: NotificationField,
        enabled: Boolean
    ): NotificationContentPreferences {
        if (field !in notificationFields(category, expanded)) return this
        val current = categories[category.name] ?: NotificationCategoryContent()
        val stored = (if (expanded) current.expanded else current.collapsed)
            ?: defaultNotificationFields(category, expanded).mapTo(linkedSetOf()) { it.name }
        val changed = if (enabled) stored + field.name else stored - field.name
        val selection = if (expanded) current.copy(expanded = changed) else current.copy(collapsed = changed)
        return copy(categories = categories + (category.name to selection))
    }

    fun reset(category: FilterAlertType): NotificationContentPreferences =
        copy(categories = categories - category.name)
}

fun notificationFields(category: FilterAlertType, expanded: Boolean): Set<NotificationField> {
    val common = setOf(
        NotificationField.WEATHER, NotificationField.DISTANCE, NotificationField.WALKING_TIME,
        NotificationField.TIME_LEFT, NotificationField.VENUE, NotificationField.AREA,
        NotificationField.DESCRIPTION
    )
    val specific = when (category) {
        FilterAlertType.RAID -> setOf(NotificationField.CATCH_CP, NotificationField.MOVES) +
            if (expanded) setOf(NotificationField.TEAM) else emptySet()
        FilterAlertType.QUEST, FilterAlertType.TAPPABLE ->
            setOf(NotificationField.TASK, NotificationField.REWARD, NotificationField.AR)
        FilterAlertType.ROCKET -> setOf(NotificationField.GRUNT)
        FilterAlertType.KECLEON -> emptySet()
        FilterAlertType.WEATHER -> setOf(NotificationField.CP, NotificationField.IV, NotificationField.FORM)
        else -> setOf(
            NotificationField.CP, NotificationField.IV, NotificationField.FORM,
            NotificationField.PVP_RANK
        ) + if (category == FilterAlertType.HUNDO) setOf(NotificationField.GODEX) else emptySet()
    }
    return NotificationField.entries.filterTo(linkedSetOf()) { it in common || it in specific }
}

private fun defaultNotificationFields(category: FilterAlertType, expanded: Boolean): Set<NotificationField> {
    val available = notificationFields(category, expanded)
    if (expanded) {
        return if (category == FilterAlertType.OTHER || category == FilterAlertType.TAPPABLE) available
        else available - NotificationField.DESCRIPTION
    }
    val defaults = setOf(
        NotificationField.DISTANCE, NotificationField.WALKING_TIME,
        NotificationField.TIME_LEFT, NotificationField.CP
    ) + if (category == FilterAlertType.RAID) {
        setOf(NotificationField.WEATHER, NotificationField.CATCH_CP)
    } else emptySet()
    return available.intersect(defaults)
}

/** Semantic alert kinds win over incidental IV data on, for example, a raid. */
fun notificationContentCategory(alert: PokemonAlert): FilterAlertType = when {
    alert.hasTypeContaining("raid") -> FilterAlertType.RAID
    alert.hasTypeContaining("quest") -> FilterAlertType.QUEST
    alert.hasTypeContaining("rocket") || alert.gruntType != null -> FilterAlertType.ROCKET
    alert.hasTypeContaining("kecleon") -> FilterAlertType.KECLEON
    alert.isTappable -> FilterAlertType.TAPPABLE
    alert.hasTypeContaining("weather") -> FilterAlertType.WEATHER
    alert.isPerfect || alert.hasTypeContaining("hundo") -> FilterAlertType.HUNDO
    alert.isNundo || alert.hasTypeContaining("nundo") -> FilterAlertType.NUNDO
    alert.hasTypeContaining("pvp") -> FilterAlertType.PVP
    alert.hasTypeContaining("common") || alert.hasTypeContaining("rare") -> FilterAlertType.COMMON
    alert.hasTypeContaining("spawn") -> FilterAlertType.SPAWN
    else -> FilterAlertType.OTHER
}

internal object NotificationContentCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun decode(raw: String?): NotificationContentPreferences = raw?.let {
        runCatching { json.decodeFromString<NotificationContentPreferences>(it) }.getOrNull()
    } ?: NotificationContentPreferences()

    fun encode(value: NotificationContentPreferences): String = json.encodeToString(value)
}
