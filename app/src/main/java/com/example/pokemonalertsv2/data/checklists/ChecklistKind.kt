package com.example.pokemonalertsv2.data.checklists

/**
 * What a checklist collects. It decides which alerts a checklist can badge: a Hundo checklist
 * matches hundo alerts only, a Shadow one shadow raids, the rest any alert of the species.
 */
enum class ChecklistKind {
    HUNDO, SHINY, COSTUME, LUCKY, SHADOW, DYNAMAX, ANY;

    companion object {
        /**
         * The kind of a goDex collection, from its collection image
         * (`/images/collections/normal-shiny.svg`, `lucky.svg`, `dmax.svg`, ...). Checked
         * against real Shiny, Lucky, Dynamax, Shiny Costume, Shadow and XXL collections.
         */
        fun fromGoDexImage(src: String): ChecklistKind {
            val name = src.substringAfterLast('/').substringBefore('.').lowercase()
            return when {
                "hundo" in name -> HUNDO
                "event" in name -> COSTUME
                "shiny" in name -> SHINY
                "lucky" in name -> LUCKY
                "shadow" in name -> SHADOW
                "dmax" in name || "dynamax" in name || "gmax" in name -> DYNAMAX
                else -> ANY
            }
        }

        fun fromName(name: String?): ChecklistKind = entries.firstOrNull { it.name == name } ?: ANY
    }
}
