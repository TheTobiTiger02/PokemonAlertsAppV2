package com.example.pokemonalertsv2.data

/** Alert history as CSV (RFC 4180): one header row, one row per alert. */
internal object AlertCsv {
    private val HEADER = listOf(
        "id", "name", "pokemon", "form", "types", "area", "location",
        "iv_attack", "iv_defense", "iv_stamina", "cp", "level", "shiny",
        "latitude", "longitude", "ends"
    )

    fun write(alerts: List<PokemonAlert>, out: Appendable) {
        out.appendRow(HEADER)
        alerts.forEach { alert ->
            out.appendRow(
                listOf(
                    alert.id?.toString(),
                    alert.name,
                    alert.pokemon,
                    alert.pokemonForm,
                    alert.type.orEmpty().joinToString("|"),
                    alert.area,
                    alert.pokemonLocation ?: alert.gym ?: alert.pokestop,
                    alert.ivAttack?.toString(),
                    alert.ivDefense?.toString(),
                    alert.ivStamina?.toString(),
                    alert.cp?.toString(),
                    alert.level?.toString(),
                    alert.isShiny?.toString(),
                    alert.latitude?.toString(),
                    alert.longitude?.toString(),
                    alert.endTime
                )
            )
        }
    }

    fun toCsv(alerts: List<PokemonAlert>): String = buildString { write(alerts, this) }

    private fun Appendable.appendRow(values: List<String?>) {
        values.forEachIndexed { index, value ->
            if (index > 0) append(',')
            append(escape(value.orEmpty()))
        }
        append("\r\n")
    }

    internal fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
