package com.example.pokemonalertsv2.data.gamemaster

/**
 * Resolves Pokebattler form names onto the icon set numeric ids.
 *
 * Pokebattler names forms (`NECROZMA_DAWN_WINGS`); the icon set numbers them
 * (`800_f2719.png`). The masterfile is the bridge, but the two spell names differently --
 * "Altered" versus "Altered Forme", "Wash" versus "Wash Rotom", "Plant" versus "Plant
 * Cloak" -- so matching is done on a word set with the decorative words and the species
 * name removed. That lifts coverage from 389 to 467 of 505 forms; the remainder are
 * cosmetic-only species (Spinda patterns, Flabebe colours) that fall back to the base
 * sprite and look fine.
 */
class SpriteVariantIndex(
    private val formIds: Map<Pair<Int, List<String>>, Int>,
    private val megaIds: Map<Pair<Int, Int>, Int>
) {

    /** Icon-set form number for a species, or null to use the plain dex sprite. */
    fun formId(dexNumber: Int?, formName: String?, speciesId: String?): Int? {
        if (dexNumber == null || formName.isNullOrBlank()) return null
        val short = formName.removePrefix(speciesId.orEmpty()).trimStart('_')
        return formIds[dexNumber to tokens(short, speciesId)]
    }

    /** Icon-set temporary-evolution number, matched on base attack so order cannot drift. */
    fun megaEvoId(dexNumber: Int?, baseAttack: Int?): Int? {
        if (dexNumber == null || baseAttack == null) return null
        return megaIds[dexNumber to baseAttack]
    }

    companion object {
        const val MASTERFILE_URL =
            "https://raw.githubusercontent.com/WatWowMap/Masterfile-Generator/master/" +
                "master-latest-poracle.json"

        /** Words that mark a form without naming it. */
        private val NOISE = setOf("FORM", "FORME", "CLOAK", "STYLE", "SIZE", "SEA", "PLUMAGE", "MODE")

        fun from(response: MasterfileResponse): SpriteVariantIndex {
            val forms = HashMap<Pair<Int, List<String>>, Int>()
            val megas = HashMap<Pair<Int, Int>, Int>()
            response.monsters.values.forEach { monster ->
                val dex = monster.id.takeIf { it > 0 } ?: return@forEach
                monster.form?.takeIf { it.id > 0 }?.let { form ->
                    forms.putIfAbsent(dex to tokens(form.name, monster.name), form.id)
                }
                monster.tempEvolutions.forEach { temp ->
                    val attack = temp.stats?.baseAttack ?: return@forEach
                    if (temp.tempEvoId > 0) megas.putIfAbsent(dex to attack, temp.tempEvoId)
                }
            }
            return SpriteVariantIndex(forms, megas)
        }

        private fun tokens(value: String?, species: String?): List<String> {
            val speciesWords = split(species).toSet()
            return split(value).filter { it !in NOISE && it !in speciesWords }
        }

        private fun split(value: String?): List<String> = value.orEmpty()
            .uppercase()
            .split(Regex("[^A-Z0-9]+"))
            .filter { it.isNotEmpty() }
    }
}
