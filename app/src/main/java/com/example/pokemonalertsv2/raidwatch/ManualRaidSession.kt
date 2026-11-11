package com.example.pokemonalertsv2.raidwatch

import com.example.pokemonalertsv2.data.HundoCP
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.RaidTier
import com.example.pokemonalertsv2.data.counters.AvailableRaidBoss
import com.example.pokemonalertsv2.data.counters.PokebattlerNameNormalizer
import com.example.pokemonalertsv2.data.counters.megaBaseSpeciesId
import com.example.pokemonalertsv2.data.sim.CpmTable
import com.example.pokemonalertsv2.data.sim.SimSpecies
import kotlin.math.floor
import kotlin.math.sqrt

const val MANUAL_RAID_DURATION_MILLIS = 45L * 60L * 1_000L

data class ManualRaidBoss(
    val catalogue: AvailableRaidBoss,
    val tierLabel: String,
    val hundoCP: HundoCP?,
    val pokedexId: Int?,
    val spriteUrls: List<String>
)

/** Catch CP for a perfect Pokemon at levels 20 and 25. */
internal fun perfectCatchCp(species: SimSpecies): HundoCP = HundoCP(
    level20 = perfectCpAtLevel(species, 20.0),
    level25 = perfectCpAtLevel(species, 25.0)
)

/**
 * The species the trainer actually catches for a boss id.
 *
 * A mega or primal raid awards the base form, so its catch CP comes from the base species'
 * stats — `CHARIZARD_MEGA_X` catches Charizard, not the mega.
 */
internal fun catchSpeciesId(pokemonId: String): String =
    PokebattlerNameNormalizer.baseSpeciesId(pokemonId).megaBaseSpeciesId()

private fun perfectCpAtLevel(species: SimSpecies, level: Double): Int {
    val cpm = CpmTable.forLevel(level)
    val raw = (species.baseAttack + 15) *
        sqrt((species.baseDefense + 15).toDouble()) *
        sqrt((species.baseStamina + 15).toDouble()) *
        cpm * cpm / 10.0
    return floor(raw).toInt().coerceAtLeast(10)
}

internal fun manualRaidTierLabel(boss: AvailableRaidBoss): String {
    if (boss.pokemonId.contains("_PRIMAL", ignoreCase = true)) return "Primal"
    return RaidTier.fromPokebattlerRaidLevel(boss.raidLevel)?.displayLabel
        ?: boss.raidLevel.removePrefix("RAID_LEVEL_").replace('_', ' ').lowercase()
            .replaceFirstChar { it.titlecase() }
}

internal fun createManualRaidAlert(
    boss: ManualRaidBoss,
    nowMillis: Long = System.currentTimeMillis()
): PokemonAlert = PokemonAlert(
    name = "Manual raid · ${boss.catalogue.displayName}",
    description = "Started manually for a ${boss.tierLabel} raid",
    imageUrl = boss.spriteUrls.firstOrNull(),
    thumbnailUrl = boss.spriteUrls.firstOrNull(),
    endTime = (nowMillis + MANUAL_RAID_DURATION_MILLIS).toString(),
    type = listOf("Raid", boss.tierLabel),
    pokemon = boss.catalogue.displayName,
    pokedexId = boss.pokedexId,
    isShiny = boss.catalogue.shiny,
    hundoCP = boss.hundoCP
)
