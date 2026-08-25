package com.example.pokemonalertsv2.data.counters

/**
 * Which Pokémon Pokebattler should simulate as the attackers.
 *
 * This is the swappable path segment in the counters URL. Keeping it behind a type
 * is what lets the future "use my Pokébox" mode drop in without touching the
 * repository, the cache key, or the UI.
 */
sealed interface AttackerSpec {
    val pathSegment: String

    /** Generic level-N attackers — the only mode that works without a Pokebattler account. */
    data class Level(val level: Int) : AttackerSpec {
        override val pathSegment: String get() = "levels/$level"
    }

    /** A signed-in user's Pokébox. Requires an auth token; not wired up yet. */
    data class PokebattlerUser(val userId: String) : AttackerSpec {
        override val pathSegment: String get() = "users/$userId"
    }
}

/**
 * Builds `fight.pokebattler.com` request paths.
 *
 * Careful: Pokebattler inverts the usual words. The raid boss is the *defender* in
 * the path, and the ranked list of counters comes back in a field also called
 * `defenders`. See [PokebattlerCountersResponse].
 *
 * Every enum token here is validated server-side — an unknown value returns HTTP 404
 * rather than silently falling back to a default.
 */
object PokebattlerUrls {

    const val DEFAULT_DEFENSE_STRATEGY = "DEFENSE_RANDOM_MC"

    fun countersPath(
        bossPokemonId: String,
        raidLevel: String,
        attacker: AttackerSpec,
        attackStrategy: String,
        defenseStrategy: String = DEFAULT_DEFENSE_STRATEGY
    ): String = "raids/defenders/$bossPokemonId/levels/$raidLevel" +
        "/attackers/${attacker.pathSegment}" +
        "/strategies/$attackStrategy/$defenseStrategy"

    fun queryParams(options: RaidCounterOptions): Map<String, String> = linkedMapOf(
        "sort" to options.sort.apiValue,
        "weatherCondition" to options.weather.apiValue,
        "dodgeStrategy" to options.dodge.apiValue,
        "aggregation" to "AVERAGE",
        "randomAssistants" to "-1",
        "friendLevel" to options.friendship.apiValue,
        "includeLegendary" to options.includeLegendary.toString(),
        "includeShadow" to options.includeShadow.toString(),
        "includeMegas" to options.includeMegas.toString()
    )

    /** Where to send the user when we cannot render counters ourselves. */
    fun webUrl(bossPokemonId: String): String = "https://www.pokebattler.com/raids/$bossPokemonId"
}
