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

/** How the alert picture is shown: full width when expanded, as a small icon, or not at all. */
enum class NotificationImageStyle(val labelRes: Int) {
    LARGE(R.string.notification_image_large),
    THUMBNAIL(R.string.notification_image_thumbnail),
    NONE(R.string.notification_image_none)
}

/** Building blocks of a notification title, rendered in declaration order. */
enum class NotificationTitlePart(val labelRes: Int) {
    GODEX_MARKER(R.string.notification_title_godex),
    CATEGORY(R.string.notification_title_category),
    SUBJECT(R.string.notification_title_subject),
    IV(R.string.notification_field_iv),
    CP(R.string.notification_field_cp),
    VENUE(R.string.notification_field_venue)
}

@Serializable
data class NotificationCategoryContent(
    val collapsed: Set<String>? = null,
    val expanded: Set<String>? = null,
    val image: String? = null,
    val title: Set<String>? = null
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

    fun imageStyle(category: FilterAlertType): NotificationImageStyle =
        NotificationImageStyle.entries.firstOrNull { it.name == categories[category.name]?.image }
            ?: NotificationImageStyle.LARGE

    fun withImageStyle(category: FilterAlertType, style: NotificationImageStyle): NotificationContentPreferences {
        val current = categories[category.name] ?: NotificationCategoryContent()
        return copy(categories = categories + (category.name to current.copy(image = style.name)))
    }

    fun titleParts(category: FilterAlertType): Set<NotificationTitlePart> {
        val available = notificationTitleParts(category)
        return categories[category.name]?.title
            ?.let { names -> available.filterTo(linkedSetOf()) { it.name in names } }
            ?: defaultTitleParts(category)
    }

    fun withTitlePart(
        category: FilterAlertType,
        part: NotificationTitlePart,
        enabled: Boolean
    ): NotificationContentPreferences {
        if (part !in notificationTitleParts(category)) return this
        val current = categories[category.name] ?: NotificationCategoryContent()
        val stored = titleParts(category).mapTo(linkedSetOf()) { it.name }
        val changed = if (enabled) stored + part.name else stored - part.name
        return copy(categories = categories + (category.name to current.copy(title = changed)))
    }

    /** True once the user changed anything for [category], so Settings can mark it. */
    fun isCustomized(category: FilterAlertType): Boolean = categories[category.name]
        ?.let { it.collapsed != null || it.expanded != null || it.image != null || it.title != null } == true

    fun reset(category: FilterAlertType): NotificationContentPreferences =
        copy(categories = categories - category.name)
}

fun notificationTitleParts(category: FilterAlertType): Set<NotificationTitlePart> {
    val fields = notificationFields(category, expanded = false)
    return NotificationTitlePart.entries.filterTo(linkedSetOf()) { part ->
        when (part) {
            NotificationTitlePart.GODEX_MARKER -> category == FilterAlertType.HUNDO
            NotificationTitlePart.IV -> NotificationField.IV in fields
            NotificationTitlePart.CP -> NotificationField.CP in fields
            else -> true
        }
    }
}

/** Encounter alerts, where IV and CP are the point of the notification. */
private val IV_CATEGORIES = setOf(
    FilterAlertType.SPAWN, FilterAlertType.HUNDO, FilterAlertType.NUNDO, FilterAlertType.PVP
)
private val ENCOUNTER_CATEGORIES = IV_CATEGORIES + FilterAlertType.COMMON

private fun defaultTitleParts(category: FilterAlertType): Set<NotificationTitlePart> =
    notificationTitleParts(category).intersect(
        setOf(NotificationTitlePart.GODEX_MARKER, NotificationTitlePart.CATEGORY, NotificationTitlePart.SUBJECT) +
            if (category in ENCOUNTER_CATEGORIES) setOf(NotificationTitlePart.CP) else emptySet()
    )

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
        // Weather and area make the line under the big picture long; raids keep weather
        // because it decides the boost.
        val hidden = buildSet {
            add(NotificationField.AREA)
            if (category != FilterAlertType.RAID) add(NotificationField.WEATHER)
            if (category != FilterAlertType.OTHER && category != FilterAlertType.TAPPABLE) add(NotificationField.DESCRIPTION)
        }
        return available - hidden
    }
    val defaults = buildSet {
        addAll(listOf(NotificationField.DISTANCE, NotificationField.TIME_LEFT, NotificationField.CP))
        when (category) {
            FilterAlertType.RAID -> addAll(listOf(NotificationField.WEATHER, NotificationField.CATCH_CP, NotificationField.VENUE))
            FilterAlertType.QUEST, FilterAlertType.ROCKET -> add(NotificationField.VENUE)
            in IV_CATEGORIES -> add(NotificationField.IV)
            else -> Unit
        }
    }
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
