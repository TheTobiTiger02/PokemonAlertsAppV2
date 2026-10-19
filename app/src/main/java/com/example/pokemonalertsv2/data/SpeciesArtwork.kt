package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.data.database.PokemonSpeciesEntity

/** Highest national Pokédex number; PokeAPI numbers alternate forms from 10001 up. */
private const val LAST_NATIONAL_DEX = 1025

/**
 * Sprite URL per normalized species name, for the species pickers.
 *
 * PokeAPI names a species' default form after the form ("Shaymin-land", "Thundurus-incarnate",
 * "Urshifu-single-strike"), so the plain names the filter catalog uses found no artwork and no
 * Dex number for about forty species. Default forms are therefore also filed under the part
 * before the first hyphen, unless that name already belongs to a species of its own.
 */
/**
 * Artwork for one catalog species name.
 *
 * [url] is the best sprite found and [fallbackUrl] the base species' sprite to show if that one
 * fails to load (PokeAPI lists some new forms before their sprite exists). [dex] is always the
 * base species' number, never PokeAPI's 10001+ form id. [form] names the variant for a badge.
 */
data class SpeciesArt(
    val url: String?,
    val fallbackUrl: String?,
    val dex: Int?,
    val form: String?
)

private val REGION_SUFFIXES = mapOf(
    "hisuian" to "hisui",
    "alolan" to "alola",
    "galarian" to "galar",
    "paldean" to "paldea"
)
private val PREFIX_FORMS = listOf("shadow", "purified")
private val DEX_IN_URL = Regex("""/(\d+)\.png""")

/**
 * Resolves a catalog name such as "Arcanine Hisuian", "Hisuian Arcanine", "Mega Charizard X" or
 * "Shadow Kirlia" against [artwork], whose keys are PokeAPI names ("arcanine hisui",
 * "charizard mega x") normalized by [normalizeFilterToken].
 */
fun speciesArt(name: String, artwork: Map<String, String>): SpeciesArt {
    val key = normalizeFilterToken(name)
    val words = key.split(' ').filter(String::isNotEmpty).toMutableList()
    val labels = mutableListOf<String>()
    var suffix: String? = null

    PREFIX_FORMS.forEach { word -> if (words.remove(word)) labels += word.capitalized() }
    REGION_SUFFIXES.forEach { (adjective, region) ->
        if (words.remove(adjective)) {
            suffix = region
            labels += adjective.capitalized()
        }
    }
    when (words.firstOrNull()) {
        "mega" -> {
            words.removeAt(0)
            val variant = words.lastOrNull()?.takeIf { words.size > 1 && (it == "x" || it == "y") }
            if (variant != null) words.removeAt(words.lastIndex)
            suffix = listOfNotNull("mega", variant).joinToString(" ")
            labels += listOfNotNull("Mega", variant?.uppercase()).joinToString(" ")
        }
        "primal" -> {
            words.removeAt(0)
            suffix = "primal"
            labels += "Primal"
        }
    }

    val base = words.joinToString(" ")
    val baseUrl = artwork[base]
    val formUrl = suffix?.let { artwork["$base $it"] } ?: artwork[key]?.takeIf { labels.isEmpty() }
    val url = formUrl ?: baseUrl ?: artwork[key]
    return SpeciesArt(
        url = url,
        fallbackUrl = baseUrl?.takeIf { it != url },
        dex = (baseUrl ?: url)?.let { DEX_IN_URL.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?.takeIf { it <= LAST_NATIONAL_DEX },
        form = labels.joinToString(" ").ifEmpty { null }
    )
}

private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }

fun speciesArtwork(species: List<PokemonSpeciesEntity>): Map<String, String> {
    val artwork = species.associateTo(LinkedHashMap()) { normalizeFilterToken(it.name) to it.imageUrl }
    species.asSequence()
        .filter { it.id <= LAST_NATIONAL_DEX && '-' in it.name }
        .sortedBy { it.id }
        .forEach { artwork.putIfAbsent(normalizeFilterToken(it.name.substringBefore('-')), it.imageUrl) }
    return artwork
}
