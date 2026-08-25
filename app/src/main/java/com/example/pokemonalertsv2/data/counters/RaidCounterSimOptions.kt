package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.sim.WeatherBoost

/**
 * Adapters from the Pokebattler request options onto the local simulator.
 *
 * Kept out of the ViewModel so they stay pure and unit-testable.
 */

/** Weather as the local simulator models it. */
internal fun PokebattlerWeather.toWeatherBoost(): WeatherBoost = when (this) {
    PokebattlerWeather.NONE -> WeatherBoost.NONE
    PokebattlerWeather.CLEAR -> WeatherBoost.CLEAR
    PokebattlerWeather.RAINY -> WeatherBoost.RAINY
    PokebattlerWeather.PARTLY_CLOUDY -> WeatherBoost.PARTLY_CLOUDY
    PokebattlerWeather.OVERCAST -> WeatherBoost.OVERCAST
    PokebattlerWeather.WINDY -> WeatherBoost.WINDY
    PokebattlerWeather.SNOW -> WeatherBoost.SNOW
    PokebattlerWeather.FOG -> WeatherBoost.FOG
}

/**
 * Friendship damage bonus.
 *
 * Cross-checked against the live API as the ratio of 1/estimator for TYRANITAR_MEGA against
 * Lunala across levels 0..5: 1.000, 1.016, 1.055, 1.072, 1.088, 1.126. Those track the
 * standard game bonuses below, damped because the attacker is not the only damage source.
 */
internal val PokebattlerFriendship.damageMultiplier: Double
    get() = when (this) {
        PokebattlerFriendship.NONE -> 1.00
        PokebattlerFriendship.GOOD -> 1.03
        PokebattlerFriendship.GREAT -> 1.05
        PokebattlerFriendship.ULTRA -> 1.07
        PokebattlerFriendship.BEST -> 1.10
        // Inferred: level 5 has no published multiplier, and 1.13 is the value consistent
        // with the measured 1.126 ratio one step beyond Best Friend.
        PokebattlerFriendship.FOREVER -> 1.13
    }

/** Fraction of incoming damage avoided. */
internal val PokebattlerDodge.dodgeFraction: Double
    get() = when (this) {
        PokebattlerDodge.NONE -> 0.0
        PokebattlerDodge.QUARTER -> 0.25
        PokebattlerDodge.HALF -> 0.5
        PokebattlerDodge.THREE_QUARTERS -> 0.75
        PokebattlerDodge.REALISTIC -> 0.5
        PokebattlerDodge.PERFECT -> 0.75
    }
