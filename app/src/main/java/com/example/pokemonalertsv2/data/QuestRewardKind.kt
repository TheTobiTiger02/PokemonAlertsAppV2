package com.example.pokemonalertsv2.data

/** Coarse reward buckets, used to pick an icon for a quest reward in the filter pickers. */
enum class QuestRewardKind { POKEMON, STARDUST, MEGA_ENERGY, CANDY, ITEM }

/**
 * Classifies a quest reward string such as "500 Stardust", "Charizard Mega Energy x10" or "Spinda".
 *
 * [knownSpecies] holds normalized species tokens from the catalog; a reward that names one of them
 * is treated as a Pokemon so the picker can show its artwork. Order matters: "Blaziken Mega Energy"
 * names a species but is energy, so the energy and candy checks run first.
 */
fun classifyQuestReward(reward: String?, knownSpecies: Set<String> = emptySet()): QuestRewardKind {
    val token = normalizeFilterTokenOrNull(reward) ?: return QuestRewardKind.ITEM
    return when {
        token.contains("mega energy") -> QuestRewardKind.MEGA_ENERGY
        token.contains("stardust") -> QuestRewardKind.STARDUST
        token.contains("candy") -> QuestRewardKind.CANDY
        token in knownSpecies -> QuestRewardKind.POKEMON
        // "Spinda" with no qualifier still reads as a species even when the catalog is cold.
        knownSpecies.isEmpty() && token.none(Char::isDigit) && token.count { it == ' ' } == 0 -> QuestRewardKind.POKEMON
        else -> QuestRewardKind.ITEM
    }
}

/** The artwork key for a reward, or null when the reward is not a Pokemon. */
fun questRewardSpeciesKey(reward: String?, knownSpecies: Set<String> = emptySet()): String? =
    if (classifyQuestReward(reward, knownSpecies) == QuestRewardKind.POKEMON) normalizeFilterTokenOrNull(reward) else null

/**
 * Reward key -> the thumbnail the alert itself carries, so the quest pickers show exactly the
 * artwork the feed and map already use for that reward (UICONS item/stardust/pokemon icons).
 * Rewards not currently live fall back to a generic icon by [classifyQuestReward].
 */
fun questRewardThumbnails(alerts: List<PokemonAlert>): Map<String, String> =
    alerts.asSequence()
        .filter { FilterAlertType.QUEST in it.filterAlertTypes() }
        .mapNotNull { alert ->
            val key = normalizeFilterTokenOrNull(alert.questReward) ?: return@mapNotNull null
            val url = alert.thumbnailUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            key to url
        }
        .toMap()

/**
 * Everything a per-species distance limit can name: spawns, raid bosses, and the Pokemon that
 * appear as quest rewards. The last group matters — a species like Spinda may never spawn in
 * the area and only ever show up as a reward, which is precisely when a wider limit is wanted.
 *
 * [knownSpecies] should be the full local Pokedex (normalized), which is what tells a reward
 * like "Spinda" apart from "Rare Candy".
 */
fun FilterCatalog.speciesLimitCandidates(knownSpecies: Set<String>): List<String> {
    val questSpecies = quests.mapNotNull { quest ->
        quest.reward.takeIf { questRewardSpeciesKey(it, knownSpecies) != null }
    }
    return (spawnSpecies + raidSpecies + questSpecies).distinctBy(::normalizeFilterToken).sorted()
}
