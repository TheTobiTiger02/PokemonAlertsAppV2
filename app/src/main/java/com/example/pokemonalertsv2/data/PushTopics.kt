package com.example.pokemonalertsv2.data

import kotlinx.serialization.Serializable

/**
 * The server's FCM topic scheme, as published by `GET /api/push-topics`.
 *
 * Every topic string the app subscribes to comes from this payload verbatim. The slug rules
 * are documented by the backend but deliberately not reimplemented here: the base topic is
 * operator-editable, so a locally derived name would silently drift the day it is renamed.
 */
@Serializable
data class PushTopicCatalog(
    val schemaVersion: Int = 0,
    val baseTopic: String = "",
    val legacyTopic: String = "",
    val fanoutEnabled: Boolean = false,
    val maxTopicsPerCondition: Int = 0,
    val dedupeKey: String? = null,
    val types: List<PushTopicType> = emptyList(),
    val areas: List<PushTopicArea> = emptyList(),
    val areaTypes: List<PushTopicAreaType> = emptyList(),
    val species: List<PushTopicSpecies> = emptyList()
) {
    /** The only schema this build knows how to narrow against. */
    val isUsable: Boolean
        get() = schemaVersion == SUPPORTED_SCHEMA_VERSION &&
            fanoutEnabled &&
            legacyTopic.isNotBlank() &&
            types.isNotEmpty() &&
            areas.isNotEmpty()

    fun areaType(areaValue: String, typeValue: String): PushTopicAreaType? = areaTypes.firstOrNull {
        it.area.equals(areaValue, ignoreCase = true) && it.type.equals(typeValue, ignoreCase = true)
    }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

@Serializable
data class PushTopicType(
    val value: String = "",
    val topic: String = ""
)

@Serializable
data class PushTopicArea(
    val value: String = "",
    val group: String? = null,
    val topic: String = "",
    /** Absent when [value] is itself the group, in which case [topic] already is the group topic. */
    val groupTopic: String? = null
) {
    /**
     * The topic that catches this area's alerts *and* the bare group-labelled ones.
     *
     * A `Darmstadt-North` spawn reaches both the zone topic and the group topic, but an older
     * alert carrying the bare label `Darmstadt` reaches only the group topic. Subscribing at
     * group granularity is the one choice that cannot miss an alert the app would have shown.
     */
    val coveringTopic: String get() = groupTopic?.takeIf { it.isNotBlank() } ?: topic
}

@Serializable
data class PushTopicAreaType(
    val area: String = "",
    val type: String = "",
    val topic: String = ""
)

@Serializable
data class PushTopicSpecies(
    val pokedexId: Int = 0,
    val name: String = "",
    val topic: String = ""
)
