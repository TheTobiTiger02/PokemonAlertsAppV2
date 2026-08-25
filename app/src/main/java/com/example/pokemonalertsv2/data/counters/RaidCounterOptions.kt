package com.example.pokemonalertsv2.data.counters

import androidx.compose.runtime.Immutable
import java.text.Normalizer
import java.util.Locale

/**
 * Weather as Pokebattler names it.
 *
 * Every token here was verified against the live API with a clean, single-valued query.
 * Pokebattler rejects anything else with HTTP 404, and notably has no `EXTREME` and no
 * `SUNNY`: the neutral case is `NO_WEATHER`, and the game's sunny and clear weather are
 * one condition, [CLEAR].
 */
enum class PokebattlerWeather(val apiValue: String, val label: String) {
    NONE("NO_WEATHER", "No boost"),
    CLEAR("CLEAR", "Clear / sunny"),
    RAINY("RAINY", "Rain"),
    PARTLY_CLOUDY("PARTLY_CLOUDY", "Partly cloudy"),
    OVERCAST("OVERCAST", "Cloudy"),
    WINDY("WINDY", "Windy"),
    SNOW("SNOW", "Snow"),
    FOG("FOG", "Fog");

    companion object {
        /**
         * Maps the alert feed's weather text onto a Pokebattler token.
         *
         * The feed decorates the value with emoji ("Partly Cloudy [emoji]", "Cloudy [emoji]"),
         * so normalize before matching. Returns null for anything unrecognised — the
         * caller then keeps the user's own default rather than guessing.
         */
        fun fromAlertWeather(raw: String?): PokebattlerWeather? {
            val token = normalize(raw) ?: return null
            return when {
                // Must precede the bare "cloudy" check.
                token.contains("partly cloudy") || token.contains("partlycloudy") -> PARTLY_CLOUDY
                token.contains("overcast") || token.contains("cloudy") -> OVERCAST
                // The game shows these separately but Pokebattler has one token for both.
                token.contains("sunny") || token.contains("clear") -> CLEAR
                token.contains("rain") -> RAINY
                token.contains("wind") -> WINDY
                token.contains("snow") -> SNOW
                token.contains("fog") -> FOG
                else -> null
            }
        }

        private fun normalize(value: String?): String? = value
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            ?.replace(Regex("""\p{M}+"""), "")
            ?.lowercase(Locale.ROOT)
            ?.replace(Regex("""[^a-z0-9]+"""), " ")
            ?.trim()
            ?.replace(Regex("""\s+"""), " ")
            ?.takeIf { it.isNotEmpty() }
    }
}

/** Friendship bonus. Pokebattler models six levels, 0 through 5. */
enum class PokebattlerFriendship(val apiValue: String, val label: String) {
    NONE("FRIENDSHIP_LEVEL_0", "Not friends"),
    GOOD("FRIENDSHIP_LEVEL_1", "Good friend"),
    GREAT("FRIENDSHIP_LEVEL_2", "Great friend"),
    ULTRA("FRIENDSHIP_LEVEL_3", "Ultra friend"),
    BEST("FRIENDSHIP_LEVEL_4", "Best friend"),
    /** Level 5 is the game's "Forever Friend" tier; lucky is a separate, unrelated flag. */
    FOREVER("FRIENDSHIP_LEVEL_5", "Forever Friend")
}

/**
 * How reliably the attackers dodge, as a success percentage.
 *
 * `DODGE_NONE` does not exist; never dodging is `DODGE_0`. `DODGE_SPECIALS` is an
 * *attack* strategy (see [PokebattlerAttackStrategy]) and is rejected here.
 */
enum class PokebattlerDodge(val apiValue: String, val label: String) {
    NONE("DODGE_0", "No dodging"),
    QUARTER("DODGE_25", "Dodge 25%"),
    HALF("DODGE_50", "Dodge 50%"),
    THREE_QUARTERS("DODGE_75", "Dodge 75%"),
    REALISTIC("DODGE_REACTION_TIME", "Realistic dodging"),
    PERFECT("DODGE_100", "Perfect dodging")
}

/** Verified tokens only; Pokebattler has no `WIN_RATE` sort and 404s on it. */
enum class PokebattlerSort(val apiValue: String, val label: String) {
    OVERALL("OVERALL", "Overall"),
    ESTIMATOR("ESTIMATOR", "Time to win"),
    TIME("TIME", "Fastest"),
    POWER("POWER", "Power"),
    TDO("TDO", "Total damage")
}

enum class PokebattlerAttackStrategy(val apiValue: String, val label: String) {
    CINEMATIC("CINEMATIC_ATTACK_WHEN_POSSIBLE", "Standard"),
    DODGE_SPECIALS("DODGE_SPECIALS", "Dodge specials"),
    DODGE_WEAVE("DODGE_WEAVE_CAUTIOUS", "Dodge and weave")
}

/** Everything the user can tune about a counters query. Mirrors the website's controls. */
@Immutable
data class RaidCounterOptions(
    val attackerLevel: Int = DEFAULT_ATTACKER_LEVEL,
    val weather: PokebattlerWeather = PokebattlerWeather.NONE,
    val friendship: PokebattlerFriendship = PokebattlerFriendship.NONE,
    val dodge: PokebattlerDodge = PokebattlerDodge.NONE,
    val sort: PokebattlerSort = PokebattlerSort.OVERALL,
    val attackStrategy: PokebattlerAttackStrategy = PokebattlerAttackStrategy.CINEMATIC,
    val includeMegas: Boolean = true,
    val includeShadow: Boolean = true,
    val includeLegendary: Boolean = true
) {
    companion object {
        const val DEFAULT_ATTACKER_LEVEL = 40
        /** Pokebattler accepts half levels but the UI offers whole ones only. */
        val ATTACKER_LEVELS: List<Int> = (20..51).toList()
    }
}
