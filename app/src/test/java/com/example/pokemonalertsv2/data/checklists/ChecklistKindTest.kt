package com.example.pokemonalertsv2.data.checklists

import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistKindTest {
    /** The collection images of real goDex collections (checked 2026-11-23). */
    @Test
    fun goDexCollectionImagesMapToKinds() {
        val base = "https://godex.site/images/collections/"
        assertEquals(ChecklistKind.HUNDO, ChecklistKind.fromGoDexImage("${base}normal-hundo.svg"))
        assertEquals(ChecklistKind.HUNDO, ChecklistKind.fromGoDexImage("/images/collections/hundo.png"))
        assertEquals(ChecklistKind.SHINY, ChecklistKind.fromGoDexImage("${base}normal-shiny.svg"))
        assertEquals(ChecklistKind.COSTUME, ChecklistKind.fromGoDexImage("${base}event-shiny.svg"))
        assertEquals(ChecklistKind.LUCKY, ChecklistKind.fromGoDexImage("${base}lucky.svg"))
        assertEquals(ChecklistKind.SHADOW, ChecklistKind.fromGoDexImage("${base}shadow.svg"))
        assertEquals(ChecklistKind.DYNAMAX, ChecklistKind.fromGoDexImage("${base}dmax.svg"))
        assertEquals("XXL and a plain living dex share this image", ChecklistKind.ANY, ChecklistKind.fromGoDexImage("${base}normal.svg"))
        assertEquals(ChecklistKind.HUNDO, ChecklistKind.fromGoDexImage("${base}mega-hundo.svg"))
        assertEquals(ChecklistKind.COSTUME, ChecklistKind.fromGoDexImage("${base}event.svg"))
        assertEquals("needs a shadow first", ChecklistKind.SHADOW, ChecklistKind.fromGoDexImage("${base}purified.svg"))
        assertEquals("only shadow raids give one", ChecklistKind.SHADOW, ChecklistKind.fromGoDexImage("${base}shadow-shiny.svg"))
        assertEquals(ChecklistKind.DYNAMAX, ChecklistKind.fromGoDexImage("${base}gmax.svg"))
        assertEquals("only max battles give one", ChecklistKind.DYNAMAX, ChecklistKind.fromGoDexImage("${base}gmax-shiny.svg"))
        assertEquals(true, ChecklistKind.isGoDexHundoDex("${base}normal-hundo.svg"))
        assertEquals(false, ChecklistKind.isGoDexHundoDex("${base}mega-hundo.svg"))
        assertEquals(ChecklistKind.ANY, ChecklistKind.fromName("nonsense"))
    }
}
