package com.example.pokemonalertsv2.data.godex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
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

    suspend fun toggleCaught(
        url: String,
        cookies: String,
        entryKey: String,
        caught: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = GoDexImporter.validateAnyCollectionUrl(url)

            // Step 1: Fetch initial page to get CSRF token and lazy components
            val getRequest = Request.Builder()
                .url(normalizedUrl)
                .addHeader("Cookie", cookies)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K)")
                .get()
                .build()

            var finalUrl = normalizedUrl
            val initialHtml = client.newCall(getRequest).execute().use { response ->
                finalUrl = response.request.url.toString()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to fetch initial page: ${response.code}"))
                }
                response.body?.string() ?: ""
            }

            android.util.Log.d("GoDexClient", "Fetched initial page. Requested: $normalizedUrl, Final: $finalUrl, HTML length: ${initialHtml.length}")
            val document = Jsoup.parse(initialHtml, finalUrl)
            val pageTitle = document.title()
            android.util.Log.d("GoDexClient", "Page title: $pageTitle")

            val csrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content")
                ?.takeIf { it.isNotBlank() }
                ?: document.selectFirst("script[data-csrf]")?.attr("data-csrf")
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(IllegalStateException("CSRF token not found. Final URL: $finalUrl, Title: $pageTitle"))

            var targetSnapshot: String? = null
            var targetMethod: String = "toggle" // Default fallback

            // Check if the target entryKey is already present in the initial HTML document
            // (e.g. if the checklist regions are already rendered directly instead of lazy-loaded)
            val cardElement = document.selectFirst("[wire:key=$entryKey]")
                ?: document.selectFirst("[wire:key=\"$entryKey\"]")
                ?: document.selectFirst("[wire:key='$entryKey']")

            if (cardElement != null) {
                android.util.Log.d("GoDexClient", "Found entry $entryKey directly in the initial HTML")
                val snapshotElement = (listOf(cardElement) + cardElement.parents()).firstOrNull { it.hasAttr("wire:snapshot") }
                targetSnapshot = snapshotElement?.attr("wire:snapshot")
                
                val clickVal = cardElement.attr("wire:click").takeIf { it.isNotBlank() }
                    ?: cardElement.attr("@click").takeIf { it.isNotBlank() }
                    ?: cardElement.attr("x-on:click").takeIf { it.isNotBlank() }
                    ?: cardElement.selectFirst("[wire:click]")?.attr("wire:click")
                    ?: cardElement.selectFirst("[@click]")?.attr("@click")
                    ?: cardElement.selectFirst("[x-on:click]")?.attr("x-on:click")
                
                android.util.Log.d("GoDexClient", "Found direct click value: $clickVal")
                if (clickVal != null) {
                    METHOD_NAME_REGEX.find(clickVal)?.groupValues?.get(1)?.let {
                        targetMethod = it
                    }
                }
                android.util.Log.d("GoDexClient", "Using direct targetMethod: $targetMethod")
            } else {
                // If not found in the initial page, find lazy components and load them
                val lazyComponents = document.getAllElements().mapNotNull { element ->
                    val snapshot = element.attr("wire:snapshot").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val init = element.attr("x-init")
                    val mountParam = LAZY_PARAM_REGEX.find(init)?.groupValues
                        ?.drop(1)?.firstOrNull { it.isNotBlank() } ?: return@mapNotNull null
                    GoDexLazyComponent(snapshot, mountParam)
                }

                if (lazyComponents.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("No region components found and card not in initial page"))
                }

                // Step 2: Load the region containing the entryKey
                for (component in lazyComponents) {
                    val payload = buildJsonObject {
                        put("_token", csrfToken)
                        put("components", buildJsonArray {
                            add(buildJsonObject {
                                put("snapshot", component.snapshot)
                                put("updates", buildJsonObject {})
                                put("calls", buildJsonArray {
                                    add(buildJsonObject {
                                        put("path", "")
                                        put("method", "__lazyLoad")
                                        put("params", buildJsonArray { add(JsonPrimitive(component.mountParam)) })
                                    })
                                })
                            })
                        })
                    }.toString()

                    val postRequest = Request.Builder()
                        .url("https://godex.site/livewire/update")
                        .addHeader("Cookie", cookies)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("X-Livewire", "true")
                        .addHeader("X-CSRF-TOKEN", csrfToken)
                        .addHeader("Referer", normalizedUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K)")
                        .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                    val responseBody = client.newCall(postRequest).execute().use { response ->
                        if (response.isSuccessful) response.body?.string() else null
                    } ?: continue

                    // Check if the loaded region contains our entryKey
                    val responseJson = json.parseToJsonElement(responseBody).jsonObject
                    val componentResponse = responseJson["components"]?.jsonArray?.singleOrNull()?.jsonObject
                    val htmlSnippet = componentResponse?.get("effects")?.jsonObject?.get("html")?.jsonPrimitive?.content ?: ""

                    if (htmlSnippet.contains("wire:key=\"$entryKey\"") || htmlSnippet.contains("wire:key='$entryKey'")) {
                        android.util.Log.d("GoDexClient", "Found entry $entryKey in HTML snippet: $htmlSnippet")
                        // Extract latest snapshot
                        targetSnapshot = componentResponse?.get("snapshot")?.jsonPrimitive?.content
                        
                        // Dynamically parse method name from the card HTML
                        val cardDoc = Jsoup.parseBodyFragment(htmlSnippet)
                        val innerCardElement = cardDoc.selectFirst("[wire:key=$entryKey]")
                        if (innerCardElement != null) {
                            val clickVal = innerCardElement.attr("wire:click").takeIf { it.isNotBlank() }
                                ?: innerCardElement.attr("@click").takeIf { it.isNotBlank() }
                                ?: innerCardElement.attr("x-on:click").takeIf { it.isNotBlank() }
                                ?: innerCardElement.selectFirst("[wire:click]")?.attr("wire:click")
                                ?: innerCardElement.selectFirst("[@click]")?.attr("@click")
                                ?: innerCardElement.selectFirst("[x-on:click]")?.attr("x-on:click")
                            
                            android.util.Log.d("GoDexClient", "Found click value: $clickVal")
                            if (clickVal != null) {
                                METHOD_NAME_REGEX.find(clickVal)?.groupValues?.get(1)?.let {
                                    targetMethod = it
                                }
                            }
                        }
                        android.util.Log.d("GoDexClient", "Using targetMethod: $targetMethod")
                        break
                    }
                }
            }

            if (targetSnapshot == null) {
                return@withContext Result.failure(IllegalStateException("Target Pokémon not found in checklist"))
            }

            // Step 3: Send the toggle caught update request
            val updatePayload = buildJsonObject {
                put("_token", csrfToken)
                put("components", buildJsonArray {
                    add(buildJsonObject {
                        put("snapshot", targetSnapshot)
                        put("updates", buildJsonObject {})
                        put("calls", buildJsonArray {
                            add(buildJsonObject {
                                put("path", "")
                                if (targetMethod == "togglePokemon" || targetMethod == "togglePokemonInCollection") {
                                    val parts = entryKey.split("-")
                                    val pokedexId = parts.getOrNull(0) ?: entryKey
                                    val gender = parts.getOrNull(1) ?: "none"
                                    put("method", "togglePokemonInCollection")
                                    put("params", buildJsonArray {
                                        add(JsonPrimitive(pokedexId))
                                        add(JsonPrimitive(gender))
                                    })
                                } else {
                                    put("method", targetMethod)
                                    put("params", buildJsonArray { add(JsonPrimitive(entryKey)) })
                                }
                            })
                        })
                    })
                })
            }.toString()

            val finalPostRequest = Request.Builder()
                .url("https://godex.site/livewire/update")
                .addHeader("Cookie", cookies)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Livewire", "true")
                .addHeader("X-CSRF-TOKEN", csrfToken)
                .addHeader("Referer", normalizedUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K)")
                .post(updatePayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(finalPostRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                android.util.Log.d("GoDexClient", "Step 3 response code: ${response.code}, body: $body")
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Failed to toggle caught state on GoDex. HTTP code: ${response.code}, body: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val LAZY_PARAM_REGEX = Regex("__lazyLoad\\(&#0*39;([^&]+)&#0*39;\\)|__lazyLoad\\('([^']+)'\\)")
        private val METHOD_NAME_REGEX = Regex("(?:wire:click|x-on:click|@click)?\\s*(?:\\\$wire\\.)?([a-zA-Z0-9_]+)\\(")
    }
}
