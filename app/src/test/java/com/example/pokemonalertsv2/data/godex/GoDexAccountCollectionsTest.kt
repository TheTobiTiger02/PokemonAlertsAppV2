package com.example.pokemonalertsv2.data.godex

import com.example.pokemonalertsv2.data.checklists.ChecklistKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shaped like the signed-in goDex home page (read 2026-11-24), with made-up ids and links. */
class GoDexAccountCollectionsTest {
    private fun card(id: String, image: String, title: String, counts: String, publicId: String?) = """
        <div class="collection-row">
          <a href="https://godex.site/collection/$id" dusk="collection-$id" class="flex grow items-center py-3 pl-4">
            <div class="flex items-center">
              <div class="inline-flex items-center"><div class="w-[46px]">
                <img src="https://godex.site/images/collections/$image.svg" alt="$title">
              </div></div>
              <div class="ml-4 flex-col items-start justify-start gap-1">
                <div class="text-left text-xl font-bold">$title</div>
                <div class="text-left text-sm leading-tight">
                  <span class="font-semibold">$counts</span> <span class="font-normal">(99.04%)</span>
                </div>
              </div>
            </div>
          </a>
          <div dusk="collection-tags-$id">Multivariante</div>
          <a dusk="configure-collection-$id" href="#">…</a>
          ${publicId?.let { """<label dusk="public-link-$id" value="https://godex.site/public-collection/$it"><input dusk="public-link-$id" value="https://godex.site/public-collection/$it" readonly type="text"></label>""" }.orEmpty()}
        </div>
    """

    private val signedIn = """
        <html><body>
        <nav><a dusk="desktop-profile" href="https://godex.site/profile">Profile</a></nav>
        <div dusk="collections-container">
          ${card("101", "normal-hundo", "HundoLivingDex", "932 / 941", "PUBHUNDO")}
          ${card("102", "lucky", "Lucky Dex", "12 / 941", null)}
          ${card("103", "normal", "XXL Dex", "0 / 1025", "PUBXXL")}
          ${card("104", "mega-hundo", "Hundo Mega Dex", "3 / 50", null)}
        </div>
        <button dusk="show-create-collection">New</button>
        </body></html>
    """

    @Test
    fun readsEveryCollectionOfTheAccount() {
        val collections = GoDexAccountCollections.parse(signedIn)!!
        assertEquals(listOf("101", "102", "103", "104"), collections.map { it.collectionId })

        val hundo = collections[0]
        assertEquals("https://godex.site/collection/101", hundo.url)
        assertEquals("HundoLivingDex", hundo.title)
        assertEquals(ChecklistKind.HUNDO, hundo.kind)
        assertTrue(hundo.isHundoDex)
        assertEquals(932 to 941, hundo.caught to hundo.total)
        assertEquals("https://godex.site/public-collection/PUBHUNDO", hundo.publicUrl)

        assertEquals(ChecklistKind.LUCKY, collections[1].kind)
        assertNull("not public", collections[1].publicUrl)
        assertEquals("XXL by its name", ChecklistKind.XXL, collections[2].kind)
        assertEquals(ChecklistKind.HUNDO, collections[3].kind)
        assertFalse("megas only", collections[3].isHundoDex)
    }

    @Test
    fun theLandingPageMeansSignedOut() {
        val guest = """<html><body><a dusk="google-login" href="https://godex.site/auth/google">Google</a></body></html>"""
        assertNull(GoDexAccountCollections.parse(guest))
        assertEquals(emptyList<GoDexAccountCollection>(), GoDexAccountCollections.parse("""<div dusk="collections-container"></div>"""))
    }

    @Test
    fun ownerLinksHaveACollectionNumber() {
        assertEquals("766", GoDexAccountCollections.collectionIdOf("https://godex.site/collection/766"))
        assertNull(GoDexAccountCollections.collectionIdOf("https://godex.site/public-collection/263f962ec3aec640f5145bb1755823a6"))
        assertNull(GoDexAccountCollections.collectionIdOf(null))
    }
}
