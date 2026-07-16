package com.example.pokemonalertsv2.fcm

import com.example.pokemonalertsv2.data.AffectedAlert
import com.example.pokemonalertsv2.data.PokemonAlert
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class FcmAlertPayload(
    val alert: PokemonAlert,
    val title: String?,
    val body: String?,
    val payloadVersion: String?
)

object FcmAlertPayloadParser {
    private const val KEY_ALERT = "alert"
    private const val KEY_ALERT_ID = "alertId"
    private const val KEY_TITLE = "title"
    private const val KEY_BODY = "body"
    private const val KEY_PAYLOAD_VERSION = "payloadVersion"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun parse(data: Map<String, String>): FcmAlertPayload? {
        val alertId = data[KEY_ALERT_ID].parseIntOrNull()
        val parsedAlert = data[KEY_ALERT]
            ?.takeIf { it.isNotBlank() }
            ?.let { alertJson ->
                runCatching { json.decodeFromString<PokemonAlert>(alertJson) }.getOrNull()
            }
            ?.withFallbackId(alertId)

        var alert = parsedAlert ?: fallbackAlert(data, alertId) ?: return null

        // Robustly patch empty/blank values from the top-level keys if parsed from JSON
        val endTimeFallback = data["expirationTime"].takeIfNotBlank() ?: data["endTime"].takeIfNotBlank()
        val descriptionFallback = data["description"].takeIfNotBlank() ?: data[KEY_BODY].takeIfNotBlank()
        val imageUrlFallback = data["imageUrl"].takeIfNotBlank()
        val thumbnailUrlFallback = data["thumbnailUrl"].takeIfNotBlank()
        val weatherFromFallback = data["weatherFrom"].takeIfNotBlank()
        val weatherToFallback = data["weatherTo"].takeIfNotBlank()
        val affectedAlertsFallback = data["affectedAlerts"].toAffectedAlerts()
        val invalidatedAtFallback = data["invalidatedAt"].takeIfNotBlank()
        val invalidationReasonFallback = data["invalidationReason"].takeIfNotBlank()
        val invalidatedByAlertIdFallback = data["invalidatedByAlertId"].parseIntOrNull()

        if (alert.endTime.isBlank() && endTimeFallback != null) {
            alert = alert.copy(endTime = endTimeFallback)
        }
        if (alert.description.isBlank() && descriptionFallback != null) {
            alert = alert.copy(description = descriptionFallback)
        }
        if (alert.imageUrl.isNullOrBlank() && imageUrlFallback != null) {
            alert = alert.copy(imageUrl = imageUrlFallback)
        }
        if (alert.thumbnailUrl.isNullOrBlank() && thumbnailUrlFallback != null) {
            alert = alert.copy(thumbnailUrl = thumbnailUrlFallback)
        }
        if (alert.weatherFrom.isNullOrBlank() && weatherFromFallback != null) {
            alert = alert.copy(weatherFrom = weatherFromFallback)
        }
        if (alert.weatherTo.isNullOrBlank() && weatherToFallback != null) {
            alert = alert.copy(weatherTo = weatherToFallback)
        }
        if (alert.affectedAlerts.isEmpty() && affectedAlertsFallback.isNotEmpty()) {
            alert = alert.copy(affectedAlerts = affectedAlertsFallback)
        }
        if (alert.invalidatedAt.isNullOrBlank() && invalidatedAtFallback != null) {
            alert = alert.copy(invalidatedAt = invalidatedAtFallback)
        }
        if (alert.invalidationReason.isNullOrBlank() && invalidationReasonFallback != null) {
            alert = alert.copy(invalidationReason = invalidationReasonFallback)
        }
        if (alert.invalidatedByAlertId == null && invalidatedByAlertIdFallback != null) {
            alert = alert.copy(invalidatedByAlertId = invalidatedByAlertIdFallback)
        }

        return FcmAlertPayload(
            alert = alert,
            title = data[KEY_TITLE].takeIfNotBlank() ?: data["name"].takeIfNotBlank(),
            body = data[KEY_BODY].takeIfNotBlank() ?: data["description"].takeIfNotBlank(),
            payloadVersion = data[KEY_PAYLOAD_VERSION].takeIfNotBlank()
        )
    }

    private fun fallbackAlert(data: Map<String, String>, alertId: Int?): PokemonAlert? {
        val name = data["name"].takeIfNotBlank()
            ?: data[KEY_TITLE].takeIfNotBlank()
            ?: return null

        return PokemonAlert(
            id = alertId,
            name = name,
            description = data["description"] ?: data[KEY_BODY].orEmpty(),
            longitude = data["longitude"].parseDoubleOrNull(),
            latitude = data["latitude"].parseDoubleOrNull(),
            endTime = data["expirationTime"] ?: data["endTime"].orEmpty(),
            type = data["type"].toTypeList(),
            area = data["area"].takeIfNotBlank(),
            imageUrl = data["imageUrl"].takeIfNotBlank(),
            thumbnailUrl = data["thumbnailUrl"].takeIfNotBlank(),
            weatherFrom = data["weatherFrom"].takeIfNotBlank(),
            weatherTo = data["weatherTo"].takeIfNotBlank(),
            affectedAlerts = data["affectedAlerts"].toAffectedAlerts(),
            invalidatedAt = data["invalidatedAt"].takeIfNotBlank(),
            invalidationReason = data["invalidationReason"].takeIfNotBlank(),
            invalidatedByAlertId = data["invalidatedByAlertId"].parseIntOrNull()
        )
    }

    private fun PokemonAlert.withFallbackId(alertId: Int?): PokemonAlert {
        return if (id == null && alertId != null) copy(id = alertId) else this
    }

    private fun String?.takeIfNotBlank(): String? = this?.takeIf { it.isNotBlank() }

    private fun String?.parseIntOrNull(): Int? = this?.trim()?.toIntOrNull()

    private fun String?.parseDoubleOrNull(): Double? = this?.trim()?.toDoubleOrNull()

    private fun String?.toTypeList(): List<String>? {
        return this
            ?.split(',')
            ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun String?.toAffectedAlerts(): List<AffectedAlert> {
        val encoded = takeIfNotBlank() ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<AffectedAlert>>(encoded)
        }.getOrDefault(emptyList())
    }
}
