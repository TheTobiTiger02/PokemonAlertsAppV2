package com.example.pokemonalertsv2.data.pokegenie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class PokeGenieCsvParserTest {

    /** The header from a real Poke Genie export, trimmed of the PvP-rank block. */
    private val realHeader =
        "Index,Name,Form,Pokemon,Gender,CP,HP,Atk IV,Def IV,Sta IV,IV Avg,Level Min,Level Max," +
            "Quick Move,Charge Move,Charge Move 2,Scan Date,Catch Date,Weight,Height,Lucky," +
            "Shadow/Purified,Favorite,Dust,Marked for PvP use"

    private fun parse(csv: String) = PokeGenieCsvParser.parse(StringReader(csv))

    private fun success(csv: String): PokeGenieParseResult.Success {
        val result = parse(csv)
        assertTrue("expected success but got $result", result is PokeGenieParseResult.Success)
        return result as PokeGenieParseResult.Success
    }

    @Test
    fun `reads a row in the real export layout`() {
        val csv = realHeader + "\n" +
            "2,Hippopotas,,449,♀,1126,139,4,11,15,66.7,34.0,34.0,Tackle,Body Slam,," +
            "\"2026-08-06 19:42\",8/3/2026,77.35kg,0.94m,0,0,0,7000,\n"

        val row = success(csv).rows.single()
        assertEquals(2, row.scanIndex)
        assertEquals("Hippopotas", row.name)
        assertNull(row.form)
        assertEquals(449, row.pokedexNumber)
        assertEquals("♀", row.gender)
        assertEquals(1126, row.cp)
        assertEquals(139, row.hp)
        assertEquals(4, row.atkIv)
        assertEquals(11, row.defIv)
        assertEquals(15, row.staIv)
        assertEquals(66.7, row.ivAvg!!, 1e-9)
        assertEquals(34.0, row.level!!, 1e-9)
        assertEquals("Tackle", row.quickMove)
        assertEquals("Body Slam", row.chargeMove)
        assertNull(row.chargeMove2)
        assertEquals(ShadowState.NORMAL, row.shadowState)
    }

    @Test
    fun `the Pokemon column is the dex number not the name`() {
        // A naive alias table maps "Pokemon" onto the name and silently ruins every row.
        val csv = realHeader + "\n" +
            "1,Diancie,Normal,719,,3091,127,15,15,15,100.0,50.0,50.0,Rock Throw,Rock Slide,,,,,,0,0,0,15000,\n"
        val row = success(csv).rows.single()
        assertEquals("Diancie", row.name)
        assertEquals(719, row.pokedexNumber)
    }

    @Test
    fun `reads the numeric shadow and purified flags`() {
        fun stateFor(flag: String): ShadowState {
            val csv = realHeader + "\n" +
                "1,Mewtwo,Normal,150,,4709,,14,15,15,97.8,50.0,50.0,,,,,,,,0,$flag,0,,\n"
            return success(csv).rows.single().shadowState
        }
        assertEquals(ShadowState.NORMAL, stateFor("0"))
        assertEquals(ShadowState.SHADOW, stateFor("1"))
        assertEquals(ShadowState.PURIFIED, stateFor("2"))
        // Other exporters write words.
        assertEquals(ShadowState.SHADOW, PokeGenieCsvParser.shadowState("Shadow"))
        assertEquals(ShadowState.PURIFIED, PokeGenieCsvParser.shadowState("purified"))
        assertEquals(ShadowState.NORMAL, PokeGenieCsvParser.shadowState(null))
    }

    @Test
    fun `tolerates missing moves and unappraised IVs`() {
        val csv = realHeader + "\n" +
            "3,Mareep,,179,♂,91,41,,,,,4.0,4.0,,,,,,,,0,0,0,400,\n"
        val row = success(csv).rows.single()
        assertEquals("Mareep", row.name)
        assertEquals(91, row.cp)
        assertNull(row.atkIv)
        assertNull(row.quickMove)
        assertNull(row.chargeMove)
        assertEquals(4.0, row.level!!, 1e-9)
    }

    @Test
    fun `matches headers regardless of case spacing and punctuation`() {
        val csv = "NAME,atk_iv,DEF-IV,sta iv,Level Min\n" +
            "Machamp,15,12,15,50.0\n"
        val row = success(csv).rows.single()
        assertEquals("Machamp", row.name)
        assertEquals(15, row.atkIv)
        assertEquals(12, row.defIv)
        assertEquals(15, row.staIv)
        assertEquals(50.0, row.level!!, 1e-9)
    }

    @Test
    fun `column order does not matter`() {
        val csv = "CP,Form,Name,Level Min\n4724,Normal,Mewtwo,50.0\n"
        val row = success(csv).rows.single()
        assertEquals("Mewtwo", row.name)
        assertEquals(4724, row.cp)
        assertEquals("Normal", row.form)
    }

    @Test
    fun `handles semicolon files with comma decimals`() {
        val csv = "Name;CP;IV Avg;Level Min\nMachamp;3425;93,3;50,0\n"
        val row = success(csv).rows.single()
        assertEquals("Machamp", row.name)
        assertEquals(3425, row.cp)
        assertEquals(93.3, row.ivAvg!!, 1e-9)
        assertEquals(50.0, row.level!!, 1e-9)
    }

    @Test
    fun `handles quoted fields BOM and CRLF`() {
        val csv = "﻿Name,Quick Move,Scan Date\r\n" +
            "Mewtwo,\"Confusion, Psycho Cut\",\"2026-08-24 22:44\"\r\n"
        val row = success(csv).rows.single()
        assertEquals("Mewtwo", row.name)
        assertEquals("Confusion, Psycho Cut", row.quickMove)
    }

    @Test
    fun `handles escaped quotes inside a field`() {
        val csv = "Name,Form\n" + "Mewtwo,\"the \"\"best\"\" one\"\n"
        assertEquals("the \"best\" one", success(csv).rows.single().form)
    }

    @Test
    fun `strips unit suffixes from numeric columns`() {
        val csv = "Name,IV Avg,CP,Level Min,Level Max\nMewtwo,97.8%,4724,50.0,50.0\n"
        val row = success(csv).rows.single()
        assertEquals(97.8, row.ivAvg!!, 1e-9)
        assertEquals(4724, row.cp)
    }

    @Test
    fun `level uses the midpoint of the range rounded to a half`() {
        assertEquals(34.0, PokeGenieCsvParser.midpointLevel(34.0, 34.0)!!, 1e-9)
        assertEquals(20.5, PokeGenieCsvParser.midpointLevel(20.0, 21.0)!!, 1e-9)
        assertEquals(20.5, PokeGenieCsvParser.midpointLevel(20.0, 20.5)!!, 1e-9)
        assertEquals(15.0, PokeGenieCsvParser.midpointLevel(15.0, null)!!, 1e-9)
        assertEquals(18.0, PokeGenieCsvParser.midpointLevel(null, 18.0)!!, 1e-9)
        assertNull(PokeGenieCsvParser.midpointLevel(null, null))
    }

    @Test
    fun `pads short rows and ignores blank and footer lines`() {
        val csv = realHeader + "\n" +
            "1,Machamp,,68\n" +
            "\n" +
            ",,,\n" +
            "2,Mewtwo,,150,,4724\n"
        val result = success(csv)
        assertEquals(listOf("Machamp", "Mewtwo"), result.rows.map { it.name })
        assertEquals(4724, result.rows[1].cp)
    }

    @Test
    fun `reports the unmapped columns it ignored`() {
        val csv = "Name,Rank % (G),Stat Product (U)\nMewtwo,76.43%,91.76%\n"
        val result = success(csv)
        assertEquals(2, result.unmappedHeaders.size)
        assertTrue(result.unmappedHeaders.contains("Rank % (G)"))
    }

    @Test
    fun `fails clearly when there is no name column`() {
        val result = parse("CP,Level Min\n4724,50.0\n")
        assertTrue(result is PokeGenieParseResult.Failure)
        assertEquals(
            PokeGenieParseResult.Reason.NO_NAME_COLUMN,
            (result as PokeGenieParseResult.Failure).reason
        )
    }

    @Test
    fun `fails on an empty file`() {
        assertEquals(
            PokeGenieParseResult.Reason.EMPTY_FILE,
            (parse("   ") as PokeGenieParseResult.Failure).reason
        )
    }

    @Test
    fun `sniffs the delimiter from the header`() {
        assertEquals(',', PokeGenieCsvParser.sniffDelimiter("a,b,c\n1,2,3"))
        assertEquals(';', PokeGenieCsvParser.sniffDelimiter("a;b;c\n1;2;3"))
        assertEquals('\t', PokeGenieCsvParser.sniffDelimiter("a\tb\tc\n1\t2\t3"))
    }
}
