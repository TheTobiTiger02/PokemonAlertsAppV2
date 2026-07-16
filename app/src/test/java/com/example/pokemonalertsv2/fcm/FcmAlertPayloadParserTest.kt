package com.example.pokemonalertsv2.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmAlertPayloadParserTest {

    @Test
    fun parse_decodesFullAlertJsonAndDisplayFields() {
        val payload = FcmAlertPayloadParser.parse(
            mapOf(
                "payloadVersion" to "1",
                "alertId" to "6215",
                "title" to "Alert name",
                "body" to "Short alert summary",
                "alert" to """
                    {
                      "id": 6215,
                      "name": "Rocket Grunt",
                      "description": "Full description",
                      "imageUrl": "https://example.com/image.png",
                      "longitude": 8.625169,
                      "latitude": 49.746908,
                      "endTime": "2026-05-21 18:56:37",
                      "type": ["Rocket"],
                      "thumbnailUrl": "https://example.com/thumb.png",
                      "pokemonRewards": [{"pokemon": "Pikachu"}],
                      "pokestop": "Partner-Gemeinde Alsbach-Haehnlein",
                      "area": "Alsbach",
                      "futureField": "ignored"
                    }
                """.trimIndent()
            )
        )

        assertNotNull(payload)
        assertEquals(6215, payload!!.alert.id)
        assertEquals("Rocket Grunt", payload.alert.name)
        assertEquals(listOf("Rocket"), payload.alert.type)
        assertEquals("Alsbach", payload.alert.area)
        assertEquals("Alert name", payload.title)
        assertEquals("Short alert summary", payload.body)
        assertEquals("1", payload.payloadVersion)
    }

    @Test
    fun parse_usesTopLevelAlertIdWhenFullAlertOmitsId() {
        val payload = FcmAlertPayloadParser.parse(
            mapOf(
                "alertId" to "6215",
                "alert" to """{"name":"Rocket Grunt","endTime":"2026-05-21 18:56:37"}"""
            )
        )

        assertEquals(6215, payload!!.alert.id)
    }

    @Test
    fun parse_fallsBackToTopLevelFieldsWhenAlertJsonIsMalformed() {
        val payload = FcmAlertPayloadParser.parse(
            mapOf(
                "alertId" to "6215",
                "title" to "Alert name",
                "body" to "Short alert summary",
                "name" to "Rocket Grunt",
                "type" to "Rocket,Hundo",
                "area" to "Alsbach",
                "latitude" to "49.746908",
                "longitude" to "8.625169",
                "endTime" to "2026-05-21 18:56:37",
                "alert" to "{ bad json"
            )
        )

        assertNotNull(payload)
        assertEquals(6215, payload!!.alert.id)
        assertEquals("Rocket Grunt", payload.alert.name)
        assertEquals(listOf("Rocket", "Hundo"), payload.alert.type)
        assertEquals(49.746908, payload.alert.latitude!!, 0.000001)
        assertEquals(8.625169, payload.alert.longitude!!, 0.000001)
    }

    @Test
    fun parse_toleratesMalformedFallbackNumbers() {
        val payload = FcmAlertPayloadParser.parse(
            mapOf(
                "alertId" to "not-a-number",
                "name" to "Rocket Grunt",
                "latitude" to "north",
                "longitude" to "east"
            )
        )

        assertNotNull(payload)
        assertNull(payload!!.alert.id)
        assertNull(payload.alert.latitude)
        assertNull(payload.alert.longitude)
    }

    @Test
    fun parse_returnsNullWithoutFullAlertOrFallbackName() {
        assertNull(FcmAlertPayloadParser.parse(mapOf("alert" to "{ bad json")))
    }

    @Test
    fun parse_fallsBackToTopLevelExpirationTimeAndDescription() {
        val payload = FcmAlertPayloadParser.parse(
            mapOf(
                "alertId" to "9999",
                "name" to "Charizard",
                "expirationTime" to "2026-05-21 23:45:00",
                "description" to "A very wild dragon-like Pokemon",
                "imageUrl" to "https://example.com/charizard.png",
                "thumbnailUrl" to "https://example.com/charizard_thumb.png",
                "latitude" to "37.7749",
                "longitude" to "-122.4194"
            )
        )

        assertNotNull(payload)
        assertEquals(9999, payload!!.alert.id)
        assertEquals("Charizard", payload.alert.name)
        assertEquals("2026-05-21 23:45:00", payload.alert.endTime)
        assertEquals("A very wild dragon-like Pokemon", payload.alert.description)
        assertEquals("https://example.com/charizard.png", payload.alert.imageUrl)
        assertEquals("https://example.com/charizard_thumb.png", payload.alert.thumbnailUrl)
        assertEquals(37.7749, payload.alert.latitude!!, 0.0001)
        assertEquals("-122.4194", payload.alert.longitude.toString())
    }

    @Test
    fun parse_patchesEmptyJsonFieldsWithTopLevelFields() {
        val payload = FcmAlertPayloadParser.parse(
            mapOf(
                "alertId" to "8888",
                "expirationTime" to "2026-05-21 23:45:00",
                "description" to "Patched description text",
                "imageUrl" to "https://example.com/patched_image.png",
                "thumbnailUrl" to "https://example.com/patched_thumb.png",
                "alert" to """
                    {
                      "id": 8888,
                      "name": "Pikachu",
                      "description": "",
                      "endTime": "",
                      "imageUrl": null,
                      "thumbnailUrl": ""
                    }
                """.trimIndent()
            )
        )

        assertNotNull(payload)
        assertEquals(8888, payload!!.alert.id)
        assertEquals("Pikachu", payload.alert.name)
        assertEquals("2026-05-21 23:45:00", payload.alert.endTime)
        assertEquals("Patched description text", payload.alert.description)
        assertEquals("https://example.com/patched_image.png", payload.alert.imageUrl)
        assertEquals("https://example.com/patched_thumb.png", payload.alert.thumbnailUrl)
    }

    @Test
    fun parse_decodesWeatherChangeDetailsAndAffectedAlerts() {
        val payload = FcmAlertPayloadParser.parse(
            mapOf(
                "alertId" to "10587",
                "alert" to """
                    {
                      "name": "Weather change",
                      "endTime": "2026-07-16 20:04:21",
                      "type": ["WeatherChange"],
                      "weatherFrom": "Partly Cloudy 🌤",
                      "weatherTo": "Cloudy ☁",
                      "affectedAlerts": [{
                        "name": "Zubat",
                        "pokemon": "Zubat",
                        "pokemonForm": null,
                        "cp": 239,
                        "type": ["PvP"],
                        "endTime": "2026-07-16 20:00:00",
                        "area": "Alsbach"
                      }]
                    }
                """.trimIndent()
            )
        )

        assertEquals("Partly Cloudy 🌤", payload!!.alert.weatherFrom)
        assertEquals("Cloudy ☁", payload.alert.weatherTo)
        assertEquals("Zubat", payload.alert.affectedAlerts.single().pokemon)
        assertEquals(239, payload.alert.affectedAlerts.single().cp)
    }

    @Test
    fun parse_fallbackToleratesMalformedWeatherAndInvalidationFields() {
        val payload = FcmAlertPayloadParser.parse(
            mapOf(
                "name" to "Weather change",
                "type" to "WeatherChange",
                "weatherFrom" to "Sunny",
                "weatherTo" to "Cloudy",
                "affectedAlerts" to "{not-json",
                "invalidatedAt" to "not-a-date",
                "invalidationReason" to "Weather changed",
                "invalidatedByAlertId" to "not-a-number"
            )
        )

        assertNotNull(payload)
        assertEquals("Sunny", payload!!.alert.weatherFrom)
        assertEquals("Cloudy", payload.alert.weatherTo)
        assertTrue(payload.alert.affectedAlerts.isEmpty())
        assertEquals("not-a-date", payload.alert.invalidatedAt)
        assertEquals("Weather changed", payload.alert.invalidationReason)
        assertNull(payload.alert.invalidatedByAlertId)
    }
}
