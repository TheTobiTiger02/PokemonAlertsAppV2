package com.example.pokemonalertsv2.data.godex

import org.junit.Assert.assertEquals
import org.junit.Test

class GoDexWriteTest {

    private val methodRegex = Regex("(?:wire:click|x-on:click|@click)?\\s*(?:\\\$wire\\.)?([a-zA-Z0-9_]+)\\(")

    @Test
    fun testMethodNameParsing() {
        val click1 = "\$wire.toggle('0001-none')"
        val click2 = "toggleCaught('0002-male')"
        val click3 = "x-on:click=\"\$wire.toggleCheck('0003-female')\""
        val click4 = "catch('0004-none')"

        assertEquals("toggle", methodRegex.find(click1)?.groupValues?.get(1))
        assertEquals("toggleCaught", methodRegex.find(click2)?.groupValues?.get(1))
        assertEquals("toggleCheck", methodRegex.find(click3)?.groupValues?.get(1))
        assertEquals("catch", methodRegex.find(click4)?.groupValues?.get(1))
    }
}
