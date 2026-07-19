package com.example.pokemonalertsv2.data.godex

import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap

data class GoDexImportResult(
    val normalizedUrl: String,
    val collectionTitle: String,
    val entries: List<GoDexEntryEntity>
)

internal data class GoDexInitialPage(
    val csrfToken: String,
    val collectionTitle: String,
    val lazyComponents: List<GoDexLazyComponent>,
    val expectedTotalCount: Int,
    val expectedCollectedCount: Int
)

internal data class GoDexLazyComponent(val snapshot: String, val mountParam: String)

class GoDexImporter(
    private val client: OkHttpClient = defaultClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun import(url: String, sessionCookies: String = ""): GoDexImportResult = withContext(Dispatchers.IO) {
        val normalizedUrl = validateAnyCollectionUrl(url)
        val initialHtml = execute(
            Request.Builder().url(normalizedUrl).get().build(),
            sessionCookies
        )
        val initialPage = parseInitialPage(initialHtml, normalizedUrl)

        val regionEntries = coroutineScope {
            initialPage.lazyComponents.map { component ->
                async { loadRegion(normalizedUrl, initialPage.csrfToken, component, sessionCookies) }
            }.awaitAll()
        }
        val entries = regionEntries.flatten().distinctBy { it.entryKey }
        validateEntries(
            entries = entries,
            caughtKeys = entries.filterNot { it.needed }.mapTo(mutableSetOf()) { it.entryKey },
            expectedTotal = initialPage.expectedTotalCount,
            expectedCollected = initialPage.expectedCollectedCount,
            context = "GoDex collection"
        )
        require(entries.isNotEmpty()) { "GoDex returned no Pokémon entries" }
        GoDexImportResult(normalizedUrl, initialPage.collectionTitle, entries)
    }

    internal fun parseInitialPage(html: String, baseUrl: String): GoDexInitialPage {
        val document = Jsoup.parse(html, baseUrl)
        val csrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("script[data-csrf]")?.attr("data-csrf")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("GoDex did not provide a synchronization token")
        val collectionImage = document.selectFirst("img[src*='/images/collections/']")
            ?: throw IllegalStateException("This does not appear to be a GoDex collection")
        require(collectionImage.attr("src").contains("hundo", ignoreCase = true)) {
            "The selected GoDex collection is not a Hundo collection"
        }
        val title = collectionImage.attr("alt").ifBlank { "GoDex Hundo collection" }
        val lazyComponents = document.getAllElements().mapNotNull { element ->
            val snapshot = element.attr("wire:snapshot").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val init = element.attr("x-init")
            val mountParam = LAZY_PARAM_REGEX.find(init)?.groupValues
                ?.drop(1)?.firstOrNull { it.isNotBlank() } ?: return@mapNotNull null
            GoDexLazyComponent(snapshot, mountParam)
        }
        require(lazyComponents.size == EXPECTED_REGION_COUNT) {
            "GoDex returned ${lazyComponents.size} of $EXPECTED_REGION_COUNT expected collection regions"
        }
        val collectionCounts = document.getAllElements().mapNotNull { element ->
            val snapshot = element.attr("wire:snapshot").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val data = runCatching {
                json.parseToJsonElement(snapshot).jsonObject["data"]?.jsonObject
            }.getOrNull() ?: return@mapNotNull null
            val total = data.intValue("totalPokemons") ?: return@mapNotNull null
            val collected = data.intValue("caughtPokemons") ?: return@mapNotNull null
            total to collected
        }.singleOrNull() ?: throw IllegalStateException("GoDex collection totals are missing or ambiguous")
        return GoDexInitialPage(
            csrfToken = csrfToken,
            collectionTitle = title,
            lazyComponents = lazyComponents,
            expectedTotalCount = collectionCounts.first,
            expectedCollectedCount = collectionCounts.second
        )
    }

    private fun loadRegion(url: String, csrfToken: String, component: GoDexLazyComponent, cookies: String): List<GoDexEntryEntity> {
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
        val responseText = execute(
            Request.Builder()
                .url("https://godex.site/livewire/update")
                .header("Referer", url)
                .header("X-Livewire", "true")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            cookies
        )
        val componentResponse = json.parseToJsonElement(responseText).jsonObject["components"]
            ?.jsonArray?.singleOrNull()?.jsonObject
            ?: throw IllegalStateException("GoDex returned an invalid region response")
        val snapshot = componentResponse["snapshot"]?.jsonPrimitive?.content
            ?.let { json.parseToJsonElement(it).jsonObject }
            ?: throw IllegalStateException("GoDex region snapshot is missing")
        val html = componentResponse["effects"]?.jsonObject?.get("html")?.jsonPrimitive?.content
            ?: throw IllegalStateException("GoDex region entries are missing")
        val caughtKeys = caughtKeys(snapshot)
        val entries = parseEntries(html, caughtKeys)
        val data = snapshot["data"]?.jsonObject
            ?: throw IllegalStateException("GoDex region data is missing")
        validateEntries(
            entries = entries,
            caughtKeys = caughtKeys,
            expectedTotal = data.intValue("totalPokemonsCount")
                ?: throw IllegalStateException("GoDex region total is missing"),
            expectedCollected = data.intValue("pokemonsCaughtCount")
                ?: throw IllegalStateException("GoDex region collected count is missing"),
            context = "GoDex region ${data["region"]?.jsonPrimitive?.content ?: "unknown"}"
        )
        return entries
    }

    internal fun parseEntries(html: String, caughtKeys: Set<String>): List<GoDexEntryEntity> {
        val document = Jsoup.parseBodyFragment(html)
        val pokemonElements = document.getAllElements().filter { element ->
            element.hasAttr("wire:key") && element.selectFirst("img[dusk=pokemon-sprite]") != null
        }
        require(pokemonElements.isNotEmpty()) { "GoDex region contained no Pokemon entries" }
        val entries = pokemonElements.map { element ->
            val entryKey = element.attr("wire:key")
            val match = ENTRY_KEY_REGEX.matchEntire(entryKey)
                ?: throw IllegalArgumentException("Unrecognized GoDex Pokemon key: $entryKey")
            val pokedexId = match.groupValues[1].toInt()
            val formSlug = match.groupValues[3].ifBlank { null }?.lowercase()
            val gender = match.groupValues[4].lowercase()
            val displayName = element.selectFirst("img[dusk=pokemon-sprite]")?.attr("alt")
                ?.takeIf { it.isNotBlank() } ?: entryKey
            GoDexEntryEntity(
                entryKey = entryKey,
                pokedexId = pokedexId,
                formSlug = formSlug,
                gender = gender,
                displayName = displayName,
                needed = entryKey !in caughtKeys
            )
        }
        return entries.distinctBy { it.entryKey }
    }

    internal fun validateEntries(
        entries: List<GoDexEntryEntity>,
        caughtKeys: Set<String>,
        expectedTotal: Int,
        expectedCollected: Int,
        context: String
    ) {
        val entryKeys = entries.mapTo(mutableSetOf()) { it.entryKey }
        val missingCaughtKeys = caughtKeys - entryKeys
        require(missingCaughtKeys.isEmpty()) {
            "$context is missing ${missingCaughtKeys.size} collected entries"
        }
        require(entries.size == expectedTotal) {
            "$context returned ${entries.size} of $expectedTotal expected entries"
        }
        val collectedCount = entries.count { !it.needed }
        require(collectedCount == expectedCollected) {
            "$context returned $collectedCount of $expectedCollected expected collected entries"
        }
    }

    private fun caughtKeys(snapshot: JsonObject): Set<String> {
        val caughtTuple = snapshot["data"]?.jsonObject?.get("pokemonsCaught") as? JsonArray
            ?: return emptySet()
        val genders = caughtTuple.firstOrNull() as? JsonObject ?: return emptySet()
        return buildSet {
            genders.forEach { (gender, value) ->
                val pokemonTuple = value as? JsonArray ?: return@forEach
                val caught = pokemonTuple.firstOrNull() as? JsonObject ?: return@forEach
                caught.keys.forEach { pokemonKey -> add("$pokemonKey-${gender.lowercase()}") }
            }
        }
    }

    private fun JsonObject.intValue(key: String): Int? =
        get(key)?.jsonPrimitive?.content?.toIntOrNull()

    private fun execute(request: Request, cookies: String = ""): String {
        val requestWithHeaders = request.newBuilder()
            .apply {
                if (cookies.isNotBlank()) {
                    header("Cookie", cookies)
                }
                header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K)")
            }
            .build()
        return client.newCall(requestWithHeaders).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("GoDex returned HTTP ${response.code}")
            response.body?.string() ?: throw IllegalStateException("GoDex returned an empty response")
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val LAZY_PARAM_REGEX = Regex("__lazyLoad\\(&#0*39;([^&]+)&#0*39;\\)|__lazyLoad\\('([^']+)'\\)")
        private val ENTRY_KEY_REGEX = Regex(
            "^([0-9]{4})([A-Za-z]?)(?:_([A-Za-z0-9_]+))?-(none|male|female)$",
            RegexOption.IGNORE_CASE
        )
        private const val EXPECTED_REGION_COUNT = 11

        fun validateUrl(value: String): String {
            val parsed = value.trim().toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Enter a valid GoDex public collection URL")
            require(parsed.scheme == "https" && parsed.host.equals("godex.site", ignoreCase = true)) {
                "Only HTTPS godex.site collection URLs are supported"
            }
            require(Regex("^/public-collection/[A-Za-z0-9]+/?$").matches(parsed.encodedPath)) {
                "Use a GoDex public-collection share URL"
            }
            return parsed.newBuilder().query(null).fragment(null).build().toString().trimEnd('/')
        }

        fun validateAnyCollectionUrl(value: String): String {
            val parsed = value.trim().toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Enter a valid GoDex collection URL")
            require(parsed.scheme == "https" && parsed.host.equals("godex.site", ignoreCase = true)) {
                "Only HTTPS godex.site collection URLs are supported"
            }
            val path = parsed.encodedPath
            require(
                Regex("^/public-collection/[A-Za-z0-9_\\-]+/?$", RegexOption.IGNORE_CASE).matches(path) ||
                Regex("^/(?:collection|collections|c)/[A-Za-z0-9_\\-]+/?$", RegexOption.IGNORE_CASE).matches(path)
            ) {
                "Use a valid GoDex collection URL"
            }
            return parsed.newBuilder().query(null).fragment(null).build().toString().trimEnd('/')
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .cookieJar(InMemoryCookieJar())
            .build()
    }

    private class InMemoryCookieJar : CookieJar {
        private val cookies = ConcurrentHashMap<String, List<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies[url.host] = cookies
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies[url.host].orEmpty().filter { it.matches(url) }
    }
}
