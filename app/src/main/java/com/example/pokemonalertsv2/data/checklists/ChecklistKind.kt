package com.example.pokemonalertsv2.data.checklists

/**
 * What a checklist collects. It sets which alerts a checklist badges by default and what those
 * alerts must show ([ChecklistMatcher]): a Hundo checklist hundo alerts, XXL and XXS that size,
 * a Shadow one shadow raids, the rest any alert of the species.
 */
enum class ChecklistKind {
    HUNDO, SHINY, XXL, XXS, COSTUME, LUCKY, SHADOW, DYNAMAX, ANY;

    companion object {
        /**
         * The kind of a goDex collection, from its collection image
         * (`/images/collections/normal-shiny.svg`, `lucky.svg`, `dmax.svg`, ...). Checked
         * against every image a goDex account offers: normal, normal-hundo, mega-hundo, event,
         * event-shiny, normal-shiny, lucky, purified, shadow, shadow-shiny, dmax, gmax, gmax-shiny.
         * A kind that only some alerts can show wins over "shiny": a shiny shadow comes from a
         * shadow raid, a shiny Gigantamax from a max battle. Purified Pokémon are caught as
         * shadows first.
         */
        fun fromGoDexImage(src: String): ChecklistKind {
            val name = src.substringAfterLast('/').substringBefore('.').lowercase()
            return when {
                "hundo" in name -> HUNDO
                "shadow" in name || "purified" in name -> SHADOW
                "dmax" in name || "dynamax" in name || "gmax" in name -> DYNAMAX
                "event" in name -> COSTUME
                "shiny" in name -> SHINY
                "lucky" in name -> LUCKY
                else -> ANY
            }
        }

        /**
         * A goDex collection's kind from its image and title. An XXL or XXS dex has the plain
         * living-dex image, so only its name tells ("XXL Dex").
         */
        fun fromGoDexCollection(image: String, title: String): ChecklistKind {
            val kind = fromGoDexImage(image)
            if (kind != ANY) return kind
            val words = title.uppercase().split(Regex("[^A-Z0-9]+"))
            return when {
                "XXL" in words -> XXL
                "XXS" in words -> XXS
                else -> ANY
            }
        }

        /** The one image goDex gives a Hundo dex; mega-hundo is a Hundo kind too but holds megas only. */
        fun isGoDexHundoDex(src: String): Boolean =
            src.substringAfterLast('/').substringBefore('.').equals("normal-hundo", ignoreCase = true)

        fun fromName(name: String?): ChecklistKind = entries.firstOrNull { it.name == name } ?: ANY
    }
}
