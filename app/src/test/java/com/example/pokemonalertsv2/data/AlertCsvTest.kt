package com.example.pokemonalertsv2.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertCsvTest {
    @Test
    fun writesAHeaderAndOneRowPerAlert() {
        val csv = AlertCsv.toCsv(
            listOf(
                PokemonAlert(
                    id = 7, name = "100% Dratini", pokemon = "Dratini", type = listOf("Hundo", "PvP"),
                    area = "Darmstadt", ivAttack = 15, ivDefense = 15, ivStamina = 15, cp = 545,
                    latitude = 49.87, longitude = 8.65, endTime = "2026-11-10T19:00:00Z"
                )
            )
        )
        val lines = csv.split("\r\n")
        assertEquals("id,name,pokemon,form,types,area,location,iv_attack,iv_defense,iv_stamina,cp,level,shiny,latitude,longitude,ends", lines[0])
        assertEquals("7,100% Dratini,Dratini,,Hundo|PvP,Darmstadt,,15,15,15,545,,,49.87,8.65,2026-11-10T19:00:00Z", lines[1])
        assertEquals("", lines[2])
    }

    @Test
    fun quotesCommasQuotesAndLineBreaks() {
        assertEquals("plain", AlertCsv.escape("plain"))
        assertEquals("\"a,b\"", AlertCsv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", AlertCsv.escape("say \"hi\""))
        assertEquals("\"two\nlines\"", AlertCsv.escape("two\nlines"))
    }
}
