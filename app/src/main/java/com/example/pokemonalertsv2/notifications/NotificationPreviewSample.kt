package com.example.pokemonalertsv2.notifications

import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.HundoCP
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonMoves
import com.example.pokemonalertsv2.data.PvpRanking
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus

private const val PREVIEW_NOW = 1_800_000_000_000L

/** Fixed data makes a selection's effect visible without posting an alert or using live location. */
internal fun notificationPreviewInput(
    category: FilterAlertType,
    journey: Boolean = false,
    raidWatch: Boolean = false,
    inRange: Boolean = false,
    now: Long = PREVIEW_NOW,
    /** A recent real alert of [category]: its picture and facts make the preview authentic. */
    base: PokemonAlert? = null,
    latitude: Double? = null,
    longitude: Double? = null
): NotificationContentInput {
    val encounter = category in setOf(FilterAlertType.SPAWN, FilterAlertType.HUNDO, FilterAlertType.NUNDO, FilterAlertType.PVP, FilterAlertType.COMMON)
    val alert = PokemonAlert(
        name = if (encounter) "Rookidee" else category.label,
        pokemon = when {
            category == FilterAlertType.RAID -> "Mewtwo"
            category == FilterAlertType.MAX_BATTLE -> "Charizard"
            encounter || category == FilterAlertType.WEATHER -> "Rookidee"
            else -> null
        },
        type = listOf(when (category) {
            FilterAlertType.OTHER -> "Other"
            FilterAlertType.WEATHER -> "WeatherChange"
            FilterAlertType.MAX_BATTLE -> "MaxBattle"
            else -> category.name
        }) + listOfNotNull("5".takeIf { category == FilterAlertType.RAID }),
        cp = if (encounter) 419 else null,
        iv = when {
            category == FilterAlertType.NUNDO -> "0/0/0"
            category == FilterAlertType.HUNDO -> "15/15/15"
            encounter -> "10/12/14"
            else -> null
        },
        pokemonForm = if (encounter) "Normal" else null,
        currentWeather = "Windy",
        isWeatherBoosted = category == FilterAlertType.RAID,
        gym = when (category) {
            FilterAlertType.RAID -> "Central Gym"
            FilterAlertType.MAX_BATTLE -> "Fountain Power Spot"
            else -> null
        },
        pokestop = if (category in setOf(FilterAlertType.QUEST, FilterAlertType.ROCKET, FilterAlertType.KECLEON)) "Library Stop" else null,
        area = "Alsbach",
        moves = when (category) {
            FilterAlertType.RAID -> PokemonMoves("Confusion", "Psystrike")
            FilterAlertType.MAX_BATTLE -> PokemonMoves("Fire Spin", "Blast Burn")
            else -> null
        },
        hundoCP = when (category) {
            FilterAlertType.RAID -> HundoCP(2387, 2984)
            FilterAlertType.MAX_BATTLE -> HundoCP(1635, 2044)
            else -> null
        },
        raidLevel = if (category == FilterAlertType.MAX_BATTLE) 6 else null,
        breadMode = if (category == FilterAlertType.MAX_BATTLE) 2 else null,
        pvpRankings = if (category == FilterAlertType.PVP) listOf(PvpRanking(league = "Great League", rank = 1)) else null,
        questTask = if (category == FilterAlertType.QUEST) "Make 3 Great Throws" else null,
        questReward = if (category in setOf(FilterAlertType.QUEST, FilterAlertType.TAPPABLE)) "Rare Candy ×3" else null,
        requiresAR = category == FilterAlertType.QUEST,
        gruntType = if (category == FilterAlertType.ROCKET) "Dragon" else null,
        weatherFrom = if (category == FilterAlertType.WEATHER) "Cloudy" else null,
        weatherTo = if (category == FilterAlertType.WEATHER) "Windy" else null,
        newCp = if (category == FilterAlertType.WEATHER) 593 else null,
        newIv = if (category == FilterAlertType.WEATHER) "10/12/14" else null,
        description = com.example.pokemonalertsv2.util.AppText.get(com.example.pokemonalertsv2.R.string.i18n_preview_additional_details),
        latitude = latitude,
        longitude = longitude,
        endTime = (now + 20 * 60_000L).toString()
    ).let { sample -> base?.copy(endTime = sample.endTime) ?: sample }
    return NotificationContentInput(
        alert = alert,
        distanceText = if (journey) "320 m walk" else "320 m",
        walkingText = com.example.pokemonalertsv2.util.AppText.get(com.example.pokemonalertsv2.R.string.walk_minutes, 4),
        inRange = inRange && !raidWatch,
        nowMillis = now,
        goDexText = "Needed in GoDex".takeIf { category == FilterAlertType.HUNDO },
        goDexStatus = GoDexMatchStatus.NEEDED.takeIf { category == FilterAlertType.HUNDO },
        raidWatch = raidWatch,
        teamText = "1. Tyranitar ×2 · Bite › Brutal Swing\n2. Hydreigon ×2 · Bite › Brutal Swing\n3. Gengar ×2 · Shadow Claw › Shadow Ball".takeIf { raidWatch }
    )
}
