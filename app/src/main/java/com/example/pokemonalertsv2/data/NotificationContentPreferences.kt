package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** Stable names are persisted so future fields and categories can be ignored by older apps. */
enum class NotificationField(val labelRes: Int, val shortLabelRes: Int, val icon: String) {
    WEATHER(R.string.notification_field_weather, R.string.notification_short_weather, "🌤"),
    DISTANCE(R.string.notification_field_distance, R.string.notification_short_distance, "📍"),
    WALKING_TIME(R.string.notification_field_walking_time, R.string.notification_short_walking_time, "🚶"),
    TIME_LEFT(R.string.notification_field_time_left, R.string.notification_short_time_left, "⏱"),
    CP(R.string.notification_field_cp, R.string.notification_short_cp, "⚔"),
    CATCH_CP(R.string.notification_field_catch_cp, R.string.notification_short_catch_cp, "🔵"),
    IV(R.string.notification_field_iv, R.string.notification_short_iv, "📊"),
    FORM(R.string.notification_field_form, R.string.notification_short_form, "✨"),
    PVP_RANK(R.string.notification_field_pvp, R.string.notification_short_pvp, "🏆"),
    VENUE(R.string.notification_field_venue, R.string.notification_short_venue, "🏛"),
    AREA(R.string.notification_field_area, R.string.notification_short_area, "🗺"),
    TASK(R.string.notification_field_task, R.string.notification_short_task, "📋"),
    REWARD(R.string.notification_field_reward, R.string.notification_short_reward, "🎁"),
    AR(R.string.notification_field_ar, R.string.notification_short_ar, "📷"),
    GRUNT(R.string.notification_field_grunt, R.string.notification_short_grunt, "🚀"),
    MOVES(R.string.notification_field_moves, R.string.notification_short_moves, "💥"),
    GODEX(R.string.notification_field_godex, R.string.notification_short_godex, "📘"),
    TEAM(R.string.notification_field_team, R.string.notification_short_team, "👥"),
    DESCRIPTION(R.string.notification_field_description, R.string.notification_short_description, "📝")
}

/**
 * How one detail is written. STANDARD is the wording the app has always used
 * ("CP 2500", "12 min left"), so an untouched install renders exactly as before.
 */
enum class NotificationFieldStyle(val labelRes: Int) {
    STANDARD(R.string.notification_style_standard),
    VALUE(R.string.notification_style_value),
    LABEL(R.string.notification_style_label),
    ICON(R.string.notification_style_icon)
}

/** How the alert picture is shown: full width when expanded, as a small icon, or not at all. */
enum class NotificationImageStyle(val labelRes: Int) {
    LARGE(R.string.notification_image_large),
    THUMBNAIL(R.string.notification_image_thumbnail),
    NONE(R.string.notification_image_none)
}

/** Building blocks of a notification title. Their order is the user's; declaration order is the default. */
enum class NotificationTitlePart(val labelRes: Int) {
    GODEX_MARKER(R.string.notification_title_godex),
    CATEGORY(R.string.notification_title_category),
    SUBJECT(R.string.notification_title_subject),
    IV(R.string.notification_field_iv),
    CP(R.string.notification_field_cp),
    VENUE(R.string.notification_field_venue)
}

/**
 * One notification layout. Every property is optional: null means "inherit",
 * first from the shared default layout and then from the built-in default.
 *
 * - [title] and [collapsed] are ordered names.
 * - [expanded] is ordered lines; each line is an ordered group of fields that
 *   share one line in the expanded notification.
 * - [styles] maps a field name to a [NotificationFieldStyle] name.
 */
@Serializable
data class NotificationLayout(
    val title: List<String>? = null,
    val collapsed: List<String>? = null,
    val expanded: List<List<String>>? = null,
    val image: String? = null,
    val styles: Map<String, String>? = null
) {
    val isEmpty: Boolean get() = title == null && collapsed == null && expanded == null && image == null && styles.isNullOrEmpty()
}

/**
 * Notification content settings, version 2: ordered layouts per alert type
 * plus one shared default layout that every type inherits from until it is
 * customized. Version 1 stored unordered sets; [NotificationContentCodec]
 * migrates it on read.
 *
 * Functions that take `category: FilterAlertType?` treat null as the shared
 * default layout.
 */
@Serializable
data class NotificationContentPreferences(
    val version: Int = VERSION,
    @SerialName("default") val shared: NotificationLayout? = null,
    val categories: Map<String, NotificationLayout> = emptyMap()
) {
    private fun layout(category: FilterAlertType?): NotificationLayout? =
        if (category == null) shared else categories[category.name]

    private fun withLayout(category: FilterAlertType?, layout: NotificationLayout): NotificationContentPreferences {
        val stored = layout.takeUnless { it.isEmpty }
        return if (category == null) copy(shared = stored)
        else copy(categories = if (stored == null) categories - category.name else categories + (category.name to stored))
    }

    private fun edit(category: FilterAlertType?, change: (NotificationLayout) -> NotificationLayout) =
        withLayout(category, change(layout(category) ?: NotificationLayout()))

    // ---------- Resolution ----------

    fun titleOrder(category: FilterAlertType?): List<NotificationTitlePart> {
        val available = notificationTitleParts(category)
        val stored = layout(category)?.title ?: shared?.title.takeIf { category != null }
        return stored?.let { names -> names.mapNotNull { name -> available.firstOrNull { it.name == name } }.distinct() }
            ?: defaultTitleParts(category).toList()
    }

    fun collapsedOrder(category: FilterAlertType?): List<NotificationField> {
        val available = notificationFields(category, expanded = false)
        val stored = layout(category)?.collapsed ?: shared?.collapsed.takeIf { category != null }
        return stored?.let { names -> names.mapNotNull { name -> available.firstOrNull { it.name == name } }.distinct() }
            ?: defaultNotificationFields(category, expanded = false).toList()
    }

    fun expandedLines(category: FilterAlertType?): List<List<NotificationField>> {
        val available = notificationFields(category, expanded = true)
        val stored = layout(category)?.expanded ?: shared?.expanded.takeIf { category != null }
        if (stored == null) return defaultNotificationFields(category, expanded = true).map { listOf(it) }
        val seen = mutableSetOf<NotificationField>()
        return stored.map { line ->
            line.mapNotNull { name -> available.firstOrNull { it.name == name } }.filter { seen.add(it) }
        }.filter { it.isNotEmpty() }
    }

    /**
     * True when the user arranged the expanded lines themselves: moved a detail or
     * joined two on one line. Only showing or hiding details keeps the built-in order.
     */
    fun hasCustomExpandedOrder(category: FilterAlertType?): Boolean {
        val lines = expandedLines(category)
        return lines.any { it.size > 1 } || lines.flatten() != lines.flatten().sortedBy { it.ordinal }
    }

    fun fields(category: FilterAlertType?, expanded: Boolean): Set<NotificationField> =
        if (expanded) expandedLines(category).flatten().toCollection(linkedSetOf())
        else collapsedOrder(category).toCollection(linkedSetOf())

    fun titleParts(category: FilterAlertType?): Set<NotificationTitlePart> = titleOrder(category).toCollection(linkedSetOf())

    fun imageStyle(category: FilterAlertType?): NotificationImageStyle {
        val name = layout(category)?.image ?: shared?.image.takeIf { category != null }
        return NotificationImageStyle.entries.firstOrNull { it.name == name } ?: NotificationImageStyle.LARGE
    }

    fun style(category: FilterAlertType?, field: NotificationField): NotificationFieldStyle {
        val name = layout(category)?.styles?.get(field.name)
            ?: shared?.styles?.get(field.name).takeIf { category != null }
        return NotificationFieldStyle.entries.firstOrNull { it.name == name } ?: NotificationFieldStyle.STANDARD
    }

    // ---------- Editing ----------

    // Names this version does not know were written by a newer app; an edit here keeps them.

    fun withTitleOrder(category: FilterAlertType?, parts: List<NotificationTitlePart>): NotificationContentPreferences {
        val available = notificationTitleParts(category)
        return edit(category) {
            val unknown = it.title.orEmpty().filter { name -> NotificationTitlePart.entries.none { part -> part.name == name } }
            it.copy(title = parts.filter { part -> part in available }.distinct().map { part -> part.name } + unknown)
        }
    }

    fun withCollapsedOrder(category: FilterAlertType?, fields: List<NotificationField>): NotificationContentPreferences {
        val available = notificationFields(category, expanded = false)
        return edit(category) {
            val unknown = it.collapsed.orEmpty().filter(::isUnknownField)
            it.copy(collapsed = fields.filter { field -> field in available }.distinct().map { field -> field.name } + unknown)
        }
    }

    fun withExpandedLines(category: FilterAlertType?, lines: List<List<NotificationField>>): NotificationContentPreferences {
        val available = notificationFields(category, expanded = true)
        val seen = mutableSetOf<NotificationField>()
        val cleaned = lines.map { line -> line.filter { it in available && seen.add(it) }.map { it.name } }.filter { it.isNotEmpty() }
        return edit(category) {
            val unknown = it.expanded.orEmpty().flatten().filter(::isUnknownField).map { name -> listOf(name) }
            it.copy(expanded = cleaned + unknown)
        }
    }

    fun withImageStyle(category: FilterAlertType?, style: NotificationImageStyle): NotificationContentPreferences =
        edit(category) { it.copy(image = style.name) }

    fun withStyle(category: FilterAlertType?, field: NotificationField, style: NotificationFieldStyle): NotificationContentPreferences =
        edit(category) { current ->
            val styles = current.styles.orEmpty() - field.name
            current.copy(styles = if (style == NotificationFieldStyle.STANDARD) styles.ifEmpty { null } else styles + (field.name to style.name))
        }

    /** Shows or hides one detail; a shown detail joins the end, collapsed or on its own expanded line. */
    fun withField(
        category: FilterAlertType?,
        expanded: Boolean,
        field: NotificationField,
        enabled: Boolean
    ): NotificationContentPreferences {
        if (field !in notificationFields(category, expanded)) return this
        return if (expanded) {
            val lines = expandedLines(category).map { line -> line - field }.filter { it.isNotEmpty() }
            withExpandedLines(category, if (enabled) lines + listOf(listOf(field)) else lines)
        } else {
            val order = collapsedOrder(category) - field
            withCollapsedOrder(category, if (enabled) order + field else order)
        }
    }

    fun withTitlePart(
        category: FilterAlertType?,
        part: NotificationTitlePart,
        enabled: Boolean
    ): NotificationContentPreferences {
        if (part !in notificationTitleParts(category)) return this
        val order = titleOrder(category) - part
        // The GoDex marker is always written first, so it is listed first too.
        val next = when {
            !enabled -> order
            part == NotificationTitlePart.GODEX_MARKER -> listOf(part) + order
            else -> order + part
        }
        return withTitleOrder(category, next)
    }

    /**
     * Writes the layout [source] resolves to into every target. A detail the
     * target does not offer is dropped when the target resolves it.
     */
    fun copyLayout(source: FilterAlertType?, targets: Collection<FilterAlertType?>): NotificationContentPreferences {
        val resolved = NotificationLayout(
            title = titleOrder(source).map { it.name },
            collapsed = collapsedOrder(source).map { it.name },
            expanded = expandedLines(source).map { line -> line.map { it.name } },
            image = imageStyle(source).name,
            styles = NotificationField.entries
                .mapNotNull { field -> style(source, field).takeIf { it != NotificationFieldStyle.STANDARD }?.let { field.name to it.name } }
                .toMap().ifEmpty { null }
        )
        return targets.filter { it != source }.fold(this) { prefs, target -> prefs.withLayout(target, resolved) }
    }

    /** True once the user changed anything for [category], so Settings can mark it. */
    fun isCustomized(category: FilterAlertType?): Boolean = layout(category)?.isEmpty == false

    fun reset(category: FilterAlertType?): NotificationContentPreferences =
        if (category == null) copy(shared = null) else copy(categories = categories - category.name)

    companion object {
        const val VERSION = 2
    }
}

private fun isUnknownField(name: String): Boolean = NotificationField.entries.none { it.name == name }

fun notificationTitleParts(category: FilterAlertType?): Set<NotificationTitlePart> {
    val fields = notificationFields(category, expanded = false)
    return NotificationTitlePart.entries.filterTo(linkedSetOf()) { part ->
        when (part) {
            NotificationTitlePart.GODEX_MARKER -> category == null || category == FilterAlertType.HUNDO
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

/** The shared default layout starts from an encounter alert, the most common kind. */
private val SHARED_BASELINE = FilterAlertType.SPAWN

private fun defaultTitleParts(category: FilterAlertType?): Set<NotificationTitlePart> =
    notificationTitleParts(category).intersect(
        setOf(NotificationTitlePart.GODEX_MARKER, NotificationTitlePart.CATEGORY, NotificationTitlePart.SUBJECT) +
            if ((category ?: SHARED_BASELINE) in ENCOUNTER_CATEGORIES) setOf(NotificationTitlePart.CP) else emptySet()
    )

/** The details an alert type offers. Null is the shared default layout, which offers all of them. */
fun notificationFields(category: FilterAlertType?, expanded: Boolean): Set<NotificationField> {
    if (category == null) return NotificationField.entries.filterTo(linkedSetOf()) { it != NotificationField.TEAM || expanded }
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

private fun defaultNotificationFields(category: FilterAlertType?, expanded: Boolean): Set<NotificationField> {
    val type = category ?: SHARED_BASELINE
    val available = notificationFields(category, expanded)
    if (expanded) {
        // Weather and area make the line under the big picture long; raids keep weather
        // because it decides the boost.
        val shown = notificationFields(type, expanded = true) - buildSet {
            add(NotificationField.AREA)
            if (type != FilterAlertType.RAID) add(NotificationField.WEATHER)
            if (type != FilterAlertType.OTHER && type != FilterAlertType.TAPPABLE) add(NotificationField.DESCRIPTION)
        }
        return available.intersect(shown)
    }
    val defaults = buildSet {
        addAll(listOf(NotificationField.DISTANCE, NotificationField.TIME_LEFT, NotificationField.CP))
        when (type) {
            FilterAlertType.RAID -> addAll(listOf(NotificationField.WEATHER, NotificationField.CATCH_CP, NotificationField.VENUE))
            FilterAlertType.QUEST, FilterAlertType.ROCKET -> add(NotificationField.VENUE)
            in IV_CATEGORIES -> add(NotificationField.IV)
            else -> Unit
        }
    }
    return available.intersect(defaults.intersect(notificationFields(type, expanded = false)))
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

/** Version 1: unordered selections per alert type. Read only, to migrate. */
@Serializable
private data class NotificationCategoryContentV1(
    val collapsed: Set<String>? = null,
    val expanded: Set<String>? = null,
    val image: String? = null,
    val title: Set<String>? = null
)

@Serializable
private data class NotificationContentPreferencesV1(
    val version: Int = 1,
    val categories: Map<String, NotificationCategoryContentV1> = emptyMap()
)

internal object NotificationContentCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun decode(raw: String?): NotificationContentPreferences = raw?.let {
        runCatching {
            val element = json.parseToJsonElement(it) as JsonObject
            val version = element["version"]?.jsonPrimitive?.int ?: 1
            if (version >= NotificationContentPreferences.VERSION) json.decodeFromJsonElement<NotificationContentPreferences>(element)
            else migrate(json.decodeFromJsonElement<NotificationContentPreferencesV1>(element))
        }.getOrNull()
    } ?: NotificationContentPreferences()

    fun encode(value: NotificationContentPreferences): String = json.encodeToString(value)

    /**
     * Version 1 had no order, so the rendered order was declaration order.
     * Writing that order down keeps every customized notification identical.
     */
    private fun migrate(v1: NotificationContentPreferencesV1): NotificationContentPreferences =
        NotificationContentPreferences(
            categories = v1.categories.mapValues { (_, content) ->
                NotificationLayout(
                    title = content.title?.let { names -> NotificationTitlePart.entries.filter { it.name in names }.map { it.name } },
                    collapsed = content.collapsed?.let { names -> NotificationField.entries.filter { it.name in names }.map { it.name } },
                    expanded = content.expanded?.let { names -> NotificationField.entries.filter { it.name in names }.map { listOf(it.name) } },
                    image = content.image
                )
            }.filterValues { !it.isEmpty }
        )
}
