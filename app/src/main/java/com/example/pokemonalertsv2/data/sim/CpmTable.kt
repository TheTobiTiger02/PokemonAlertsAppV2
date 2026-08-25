package com.example.pokemonalertsv2.data.sim

import kotlin.math.roundToInt

/**
 * Combat Power Multiplier per Pokemon level.
 *
 * These are game constants: a Pokemon's effective stats are
 * `(base + iv) * cpm(level)`. Levels go in half steps from 1 to 51 (levels above 40
 * require XL candy).
 */
object CpmTable {

    const val MIN_LEVEL = 1.0
    const val MAX_LEVEL = 51.0

    /** Nearest tabulated CPM, clamped to the valid range and snapped to a half level. */
    fun forLevel(level: Double): Double {
        val snapped = snap(level)
        return VALUES[snapped] ?: VALUES.getValue(MAX_LEVEL)
    }

    /** Rounds to the nearest half level within [MIN_LEVEL]..[MAX_LEVEL]. */
    fun snap(level: Double): Double {
        val clamped = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
        return (clamped * 2).roundToInt() / 2.0
    }

    private val VALUES: Map<Double, Double> = mapOf(
        1.0 to 0.094,
        1.5 to 0.135137432,
        2.0 to 0.16639787,
        2.5 to 0.192650919,
        3.0 to 0.21573247,
        3.5 to 0.236572661,
        4.0 to 0.25572005,
        4.5 to 0.273530381,
        5.0 to 0.29024988,
        5.5 to 0.306057377,
        6.0 to 0.3210876,
        6.5 to 0.335445036,
        7.0 to 0.34921268,
        7.5 to 0.362457751,
        8.0 to 0.37523559,
        8.5 to 0.387592406,
        9.0 to 0.39956728,
        9.5 to 0.411193551,
        10.0 to 0.42250001,
        10.5 to 0.432926419,
        11.0 to 0.44310755,
        11.5 to 0.453059958,
        12.0 to 0.46279839,
        12.5 to 0.472336083,
        13.0 to 0.48168495,
        13.5 to 0.490855807,
        14.0 to 0.49985844,
        14.5 to 0.508701765,
        15.0 to 0.51739395,
        15.5 to 0.525942511,
        16.0 to 0.53435433,
        16.5 to 0.542635767,
        17.0 to 0.55079269,
        17.5 to 0.558830576,
        18.0 to 0.56675452,
        18.5 to 0.574569153,
        19.0 to 0.58227891,
        19.5 to 0.589887917,
        20.0 to 0.59740001,
        20.5 to 0.604818814,
        21.0 to 0.61215729,
        21.5 to 0.619399365,
        22.0 to 0.62656713,
        22.5 to 0.633644533,
        23.0 to 0.64065295,
        23.5 to 0.647576426,
        24.0 to 0.65443563,
        24.5 to 0.661214806,
        25.0 to 0.667934,
        25.5 to 0.674577537,
        26.0 to 0.68116492,
        26.5 to 0.687680648,
        27.0 to 0.69414365,
        27.5 to 0.700538673,
        28.0 to 0.70688421,
        28.5 to 0.713164996,
        29.0 to 0.71939909,
        29.5 to 0.725571552,
        30.0 to 0.7317,
        30.5 to 0.734741009,
        31.0 to 0.73776948,
        31.5 to 0.740785574,
        32.0 to 0.74378943,
        32.5 to 0.746781211,
        33.0 to 0.74976104,
        33.5 to 0.752729087,
        34.0 to 0.75568551,
        34.5 to 0.758630378,
        35.0 to 0.76156384,
        35.5 to 0.764486065,
        36.0 to 0.76739717,
        36.5 to 0.770297266,
        37.0 to 0.7731865,
        37.5 to 0.776064962,
        38.0 to 0.77893275,
        38.5 to 0.781790055,
        39.0 to 0.78463697,
        39.5 to 0.787473578,
        40.0 to 0.79030001,
        40.5 to 0.792803968,
        41.0 to 0.79530001,
        41.5 to 0.797800015,
        42.0 to 0.80030001,
        42.5 to 0.802800014,
        43.0 to 0.80530001,
        43.5 to 0.807800018,
        44.0 to 0.81030001,
        44.5 to 0.812800014,
        45.0 to 0.81530001,
        45.5 to 0.817800011,
        46.0 to 0.82030001,
        46.5 to 0.822800008,
        47.0 to 0.82530001,
        47.5 to 0.827800005,
        48.0 to 0.83030001,
        48.5 to 0.832800011,
        49.0 to 0.83530001,
        49.5 to 0.837800008,
        50.0 to 0.84029999,
        50.5 to 0.842799995,
        51.0 to 0.84529999,
    )
}
