package com.example.pokemonalertsv2.data.counters

/**
 * Cache identity for one counters query.
 *
 * Every input that changes the server's answer must appear here, or the cache will
 * serve one option set's results under another's. Deliberately human-readable rather
 * than hashed so cached rows can be read in a database inspector.
 */
fun raidCounterCacheKey(
    bossPokemonId: String,
    raidLevel: String,
    attacker: AttackerSpec,
    options: RaidCounterOptions
): String = listOf(
    bossPokemonId,
    raidLevel,
    attacker.pathSegment,
    options.attackStrategy.apiValue,
    PokebattlerUrls.DEFAULT_DEFENSE_STRATEGY,
    options.sort.apiValue,
    options.weather.apiValue,
    options.dodge.apiValue,
    options.friendship.apiValue,
    options.attackerLevel.toString(),
    options.includeMegas.toString(),
    options.includeShadow.toString(),
    options.includeLegendary.toString()
).joinToString("|")
