package com.example.pokemonalertsv2.data.counters

/**
 * Cache identity for one counters query.
 *
 * Every input that changes the server's answer must appear here, or the cache will
 * serve one option set's results under another's. Deliberately human-readable rather
 * than hashed so cached rows can be read in a database inspector.
 *
 * `dodgeStrategy` is deliberately absent: Pokebattler ignores it, so including it would
 * split the cache into identical copies. The version prefix was bumped to `v4` when it
 * was removed so pre-existing rows age out rather than being read under the new key.
 */
fun raidCounterCacheKey(
    bossPokemonId: String,
    raidLevel: String,
    attacker: AttackerSpec,
    options: RaidCounterOptions,
    bossMoveset: RaidBossMoveset? = null
): String = listOf(
    "v4",
    bossPokemonId,
    raidLevel,
    attacker.pathSegment,
    options.attackStrategy.apiValue,
    PokebattlerUrls.DEFAULT_DEFENSE_STRATEGY,
    options.sort.apiValue,
    options.weather.apiValue,
    options.friendship.apiValue,
    options.attackerLevel.toString(),
    options.includeMegas.toString(),
    options.includeShadow.toString(),
    options.includeLegendary.toString(),
    bossMoveset?.cacheToken() ?: "AVERAGE"
).joinToString("|")

/**
 * Identity for one anonymous, exact-level Pokébattler response used to score a mixed roster.
 * Keep this separate from the generic list key so a personal result can never reuse a
 * level-40/perfect-IV response (or a response for another boss moveset).
 */
fun personalResponseCacheKey(
    bossPokemonId: String,
    raidLevel: String,
    attacker: AttackerSpec,
    options: RaidCounterOptions,
    bossMoveset: RaidBossMoveset? = null
): String = listOf(
    "v4-personal",
    bossPokemonId,
    raidLevel,
    attacker.pathSegment,
    options.attackStrategy.apiValue,
    PokebattlerUrls.DEFAULT_DEFENSE_STRATEGY,
    options.sort.apiValue,
    options.weather.apiValue,
    options.friendship.apiValue,
    options.attackerLevel.toString(),
    options.includeMegas.toString(),
    options.includeShadow.toString(),
    options.includeLegendary.toString(),
    bossMoveset?.cacheToken() ?: "AVERAGE"
).joinToString("|")
