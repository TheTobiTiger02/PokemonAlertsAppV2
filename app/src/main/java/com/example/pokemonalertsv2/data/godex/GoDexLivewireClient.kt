package com.example.pokemonalertsv2.data.godex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.io.IOException

class GoDexLivewireClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun setCaught(
        url: String,
        cookies: String,
        entryKey: String,
        caught: Boolean
    ): GoDexWriteResult = withContext(Dispatchers.IO) {
        val session = GoDexHttpSession(cookies)
        try {
            val normalizedUrl = GoDexImporter.validateAnyCollectionUrl(url)
            val inspection = inspectEntry(normalizedUrl, entryKey, session)
                ?: return@withContext GoDexWriteResult.PermanentFailure(
                    "The selected Pokémon is no longer present in this GoDex checklist.",
                    session.cookieHeader()
                )

            if (inspection.caught == caught) {
                return@withContext GoDexWriteResult.AlreadyApplied(session.cookieHeader())
            }
            if (inspection.caught == null) {
                return@withContext GoDexWriteResult.RetryableFailure(
                    "GoDex did not expose the current checklist state. The change remains queued.",
                    refreshedCookies = session.cookieHeader()
                )
            }

            val payload = buildTogglePayload(inspection, entryKey)
            val request = Request.Builder()
                .url(LIVEWIRE_UPDATE_URL)
                .header("Content-Type", "application/json")
                .header("X-Livewire", "true")
                .header("X-CSRF-TOKEN", inspection.csrfToken)
                .header("Referer", normalizedUrl)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val updateResponse = execute(request, session)
            if (updateResponse.requiresReauthentication) {
                return@withContext reauthenticationResult(session)
            }
            if (!updateResponse.isSuccessful) {
                return@withContext GoDexWriteResult.RetryableFailure(
                    "GoDex returned HTTP ${updateResponse.code} while saving the checklist.",
                    refreshedCookies = session.cookieHeader()
                )
            }

            val responseState = caughtStateFromLivewireResponse(updateResponse.body, entryKey)
            val verifiedState = responseState
                ?: inspectEntry(normalizedUrl, entryKey, session)?.caught

            if (verifiedState == caught) {
                GoDexWriteResult.Applied(session.cookieHeader())
            } else {
                GoDexWriteResult.RetryableFailure(
                    "GoDex did not confirm the requested checklist state.",
                    refreshedCookies = session.cookieHeader()
                )
            }
        } catch (error: GoDexAuthenticationException) {
            GoDexWriteResult.ReauthenticationRequired(
                error.message ?: SESSION_EXPIRED_MESSAGE,
                error.refreshedCookies
            )
        } catch (error: IOException) {
            GoDexWriteResult.RetryableFailure(
                "Could not reach GoDex. The change remains queued.",
                error,
                session.cookieHeader()
            )
        } catch (error: Exception) {
            GoDexWriteResult.RetryableFailure(
                error.message ?: "GoDex returned an unexpected response.",
                error,
                session.cookieHeader()
            )
        }
    }

    private fun inspectEntry(
        url: String,
        entryKey: String,
        session: GoDexHttpSession
    ): EntryInspection? {
        val initialResponse = execute(Request.Builder().url(url).get().build(), session)
        requireAuthenticated(initialResponse, session)
        if (!initialResponse.isSuccessful) {
            throw IOException("GoDex returned HTTP ${initialResponse.code}")
        }

        val document = Jsoup.parse(initialResponse.body, initialResponse.finalUrl)
        val csrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content")
            ?.takeIf(String::isNotBlank)
            ?: document.selectFirst("script[data-csrf]")?.attr("data-csrf")
                ?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("GoDex did not provide a synchronization token")

        document.selectFirst("[wire:key=\"$entryKey\"]")?.let { card ->
            val snapshot = (listOf(card) + card.parents())
                .firstOrNull { it.hasAttr("wire:snapshot") }
                ?.attr("wire:snapshot")
                ?.takeIf(String::isNotBlank)
                ?: return@let
            return EntryInspection(
                csrfToken = csrfToken,
                snapshot = snapshot,
                method = clickMethod(card.outerHtml()),
                caught = caughtStateFromSnapshot(snapshot, entryKey)
            )
        }

        val lazyComponents = document.getAllElements().mapNotNull { element ->
            val snapshot = element.attr("wire:snapshot").takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val mountParam = LAZY_PARAM_REGEX.find(element.attr("x-init"))?.groupValues
                ?.drop(1)?.firstOrNull(String::isNotBlank)
                ?: return@mapNotNull null
            GoDexLazyComponent(snapshot, mountParam)
        }

        for (component in lazyComponents) {
            val response = execute(
                Request.Builder()
                    .url(LIVEWIRE_UPDATE_URL)
                    .header("Content-Type", "application/json")
                    .header("X-Livewire", "true")
                    .header("X-CSRF-TOKEN", csrfToken)
                    .header("Referer", url)
                    .post(lazyPayload(component, csrfToken).toRequestBody(JSON_MEDIA_TYPE))
                    .build(),
                session
            )
            requireAuthenticated(response, session)
            if (!response.isSuccessful) continue

            val componentResponse = runCatching {
                json.parseToJsonElement(response.body).jsonObject["components"]
                    ?.jsonArray?.singleOrNull()?.jsonObject
            }.getOrNull() ?: continue
            val html = componentResponse["effects"]?.jsonObject
                ?.get("html")?.jsonPrimitive?.content.orEmpty()
            if (!html.contains("wire:key=\"$entryKey\"") &&
                !html.contains("wire:key='$entryKey'")
            ) {
                continue
            }
            val snapshot = componentResponse["snapshot"]?.jsonPrimitive?.content
                ?: continue
            val cardHtml = Jsoup.parseBodyFragment(html)
                .selectFirst("[wire:key=\"$entryKey\"]")
                ?.outerHtml()
                ?: html
            return EntryInspection(
                csrfToken = csrfToken,
                snapshot = snapshot,
                method = clickMethod(cardHtml),
                caught = caughtStateFromSnapshot(snapshot, entryKey)
            )
        }
        return null
    }

    private fun buildTogglePayload(inspection: EntryInspection, entryKey: String): String =
        buildJsonObject {
            put("_token", inspection.csrfToken)
            put("components", buildJsonArray {
                add(buildJsonObject {
                    put("snapshot", inspection.snapshot)
                    put("updates", buildJsonObject {})
                    put("calls", buildJsonArray {
                        add(buildJsonObject {
                            put("path", "")
                            if (inspection.method == "togglePokemon" ||
                                inspection.method == "togglePokemonInCollection"
                            ) {
                                val parts = entryKey.split("-")
                                put("method", "togglePokemonInCollection")
                                put("params", buildJsonArray {
                                    add(JsonPrimitive(parts.getOrNull(0) ?: entryKey))
                                    add(JsonPrimitive(parts.getOrNull(1) ?: "none"))
                                })
                            } else {
                                put("method", inspection.method)
                                put("params", buildJsonArray { add(JsonPrimitive(entryKey)) })
                            }
                        })
                    })
                })
            })
        }.toString()

    private fun lazyPayload(component: GoDexLazyComponent, csrfToken: String): String = buildJsonObject {
        put("_token", csrfToken)
        put("components", buildJsonArray {
            add(buildJsonObject {
                put("snapshot", component.snapshot)
                put("updates", buildJsonObject {})
                put("calls", buildJsonArray {
                    add(buildJsonObject {
                        put("path", "")
                        put("method", "__lazyLoad")
                        put("params", buildJsonArray {
                            add(JsonPrimitive(component.mountParam))
                        })
                    })
                })
            })
        })
    }.toString()

    private fun caughtStateFromLivewireResponse(body: String, entryKey: String): Boolean? {
        val snapshot = runCatching {
            json.parseToJsonElement(body).jsonObject["components"]
                ?.jsonArray?.singleOrNull()?.jsonObject
                ?.get("snapshot")?.jsonPrimitive?.content
        }.getOrNull() ?: return null
        return caughtStateFromSnapshot(snapshot, entryKey)
    }

    private fun caughtStateFromSnapshot(snapshot: String, entryKey: String): Boolean? {
        val data = runCatching {
            json.parseToJsonElement(snapshot).jsonObject["data"]?.jsonObject
        }.getOrNull() ?: return null
        val tuple = data["pokemonsCaught"] as? JsonArray ?: return null
        val genders = tuple.firstOrNull() as? JsonObject ?: return null
        return caughtKeys(genders).contains(entryKey)
    }

    private fun caughtKeys(genders: JsonObject): Set<String> = buildSet {
        genders.forEach { (gender, value) ->
            val tuple = value as? JsonArray ?: return@forEach
            val caught = tuple.firstOrNull() as? JsonObject ?: return@forEach
            caught.keys.forEach { pokemonKey -> add("$pokemonKey-${gender.lowercase()}") }
        }
    }

    private fun clickMethod(html: String): String {
        val card = Jsoup.parseBodyFragment(html).getAllElements()
            .firstOrNull { element ->
                element.hasAttr("wire:click") ||
                    element.hasAttr("@click") ||
                    element.hasAttr("x-on:click")
            }
        val value = card?.attr("wire:click").takeUnless(String?::isNullOrBlank)
            ?: card?.attr("@click").takeUnless(String?::isNullOrBlank)
            ?: card?.attr("x-on:click").takeUnless(String?::isNullOrBlank)
        return value?.let { METHOD_NAME_REGEX.find(it)?.groupValues?.get(1) } ?: "toggle"
    }

    private fun execute(request: Request, session: GoDexHttpSession): GoDexHttpResponse =
        client.newCall(session.decorate(request)).execute().use(session::consume)

    private fun requireAuthenticated(response: GoDexHttpResponse, session: GoDexHttpSession) {
        if (response.requiresReauthentication) {
            throw GoDexAuthenticationException(
                SESSION_EXPIRED_MESSAGE,
                session.cookieHeader()
            )
        }
    }

    private fun reauthenticationResult(session: GoDexHttpSession) =
        GoDexWriteResult.ReauthenticationRequired(
            SESSION_EXPIRED_MESSAGE,
            session.cookieHeader()
        )

    private data class EntryInspection(
        val csrfToken: String,
        val snapshot: String,
        val method: String,
        val caught: Boolean?
    )

    private companion object {
        const val LIVEWIRE_UPDATE_URL = "https://godex.site/livewire/update"
        const val SESSION_EXPIRED_MESSAGE =
            "Your GoDex session expired. Sign in again to resume pending changes."
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val LAZY_PARAM_REGEX =
            Regex("__lazyLoad\\(&#0*39;([^&]+)&#0*39;\\)|__lazyLoad\\('([^']+)'\\)")
        val METHOD_NAME_REGEX =
            Regex("(?:wire:click|x-on:click|@click)?\\s*(?:\\\$wire\\.)?([a-zA-Z0-9_]+)\\(")
    }
}
