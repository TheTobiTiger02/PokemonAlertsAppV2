package com.example.pokemonalertsv2.data.godex

import com.example.pokemonalertsv2.data.checklists.ChecklistKind
import com.example.pokemonalertsv2.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/** One collection of the signed-in goDex account, as its home page lists it. */
data class GoDexAccountCollection(
    /** goDex's own number for it, from `/collection/{id}`. */
    val collectionId: String,
    /** The owner page, `https://godex.site/collection/{id}`: readable and writable with the session. */
    val url: String,
    val title: String,
    val image: String,
    val kind: ChecklistKind,
    val caught: Int,
    val total: Int,
    /** Its share link when the collection is public, to recognise one already added by link. */
    val publicUrl: String?
) {
    /** The one kind of collection that can be the app's Hundo collection. */
    val isHundoDex: Boolean get() = ChecklistKind.isGoDexHundoDex(image)
}

data class GoDexAccountCollectionsResult(
    val collections: List<GoDexAccountCollection>,
    val refreshedCookies: String
)

/**
 * Reads the signed-in account's collections from the goDex home page. Signed in, the page holds
 * a `collections-container` with one card per collection (`a[dusk=collection-{id}]` with the
 * collection image, title and "caught / total"). Signed out, goDex answers the same address
 * with its landing page and sign-in buttons, so the container is the sign-in marker.
 */
class GoDexAccountCollections(private val client: OkHttpClient = Http.base) {
    suspend fun fetch(sessionCookies: String): GoDexAccountCollectionsResult = withContext(Dispatchers.IO) {
        val session = GoDexHttpSession(sessionCookies)
        val response = client.newCall(session.decorate(Request.Builder().url(HOME_URL).get().build())).execute().use(session::consume)
        if (response.requiresReauthentication) throw expired(session)
        check(response.isSuccessful) { "GoDex returned HTTP ${response.code}" }
        val collections = parse(response.body) ?: throw expired(session)
        GoDexAccountCollectionsResult(collections, session.cookieHeader())
    }

    private fun expired(session: GoDexHttpSession) =
        GoDexAuthenticationException("Your GoDex session expired. Sign in again to see your collections.", session.cookieHeader())

    companion object {
        const val HOME_URL = "https://godex.site/"
        private val COLLECTION_PATH = Regex("/collection/([0-9]+)/?$")
        private val COUNTS = Regex("([0-9]+)\\s*/\\s*([0-9]+)")

        /** The account's collections in the order goDex shows them, or null when the page is not signed in. */
        fun parse(html: String): List<GoDexAccountCollection>? {
            val document = Jsoup.parse(html, HOME_URL)
            val container = document.selectFirst("[dusk=collections-container]") ?: return null
            return container.select("a[dusk^=collection-][href*=/collection/]").mapNotNull { link ->
                val url = link.absUrl("href").substringBefore('?').trimEnd('/')
                val id = COLLECTION_PATH.find(url)?.groupValues?.get(1) ?: return@mapNotNull null
                val image = link.selectFirst("img[src*=/images/collections/]") ?: return@mapNotNull null
                val src = image.absUrl("src")
                val counts = link.select("span").firstNotNullOfOrNull { COUNTS.find(it.text()) }
                GoDexAccountCollection(
                    collectionId = id,
                    url = url,
                    title = link.selectFirst("div.text-xl")?.text()?.trim()?.ifBlank { null } ?: image.attr("alt").ifBlank { "GoDex collection" },
                    image = src,
                    kind = ChecklistKind.fromGoDexImage(src),
                    caught = counts?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                    total = counts?.groupValues?.get(2)?.toIntOrNull() ?: 0,
                    publicUrl = document.selectFirst("input[dusk=public-link-$id]")?.attr("value")
                        ?.trim()?.trimEnd('/')?.takeIf { it.contains("/public-collection/") }
                )
            }.distinctBy { it.collectionId }
        }

        /** goDex's number of an owner collection link, or null for a share link. */
        fun collectionIdOf(url: String?): String? = url?.let { COLLECTION_PATH.find(it.substringBefore('?').trimEnd('/'))?.groupValues?.get(1) }
    }
}
