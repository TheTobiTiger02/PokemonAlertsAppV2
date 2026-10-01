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
fun speciesArtwork(species: List<PokemonSpeciesEntity>): Map<String, String> {
    val artwork = species.associateTo(LinkedHashMap()) { normalizeFilterToken(it.name) to it.imageUrl }
    species.asSequence()
        .filter { it.id <= LAST_NATIONAL_DEX && '-' in it.name }
        .sortedBy { it.id }
        .forEach { artwork.putIfAbsent(normalizeFilterToken(it.name.substringBefore('-')), it.imageUrl) }
    return artwork
}
