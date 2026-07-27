package com.ebooks.reader.data.rss

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for OPML parsing (XmlPullParser needs a real Android
 * runtime), including the folder structure the first-launch feed picker groups
 * its checklist by.
 */
@RunWith(AndroidJUnit4::class)
class OpmlTest {

    private val opml = """
        <?xml version="1.0" encoding="utf-8"?>
        <opml version="2.0">
          <head><title>Veille</title></head>
          <body>
            <outline type="rss" text="Korben" title="Korben" xmlUrl="https://korben.info/feedfull/" htmlUrl="https://korben.info"/>
            <outline text="Sécurité &amp; Compliance (FR)" title="Sécurité &amp; Compliance (FR)">
              <outline type="rss" text="ANSSI" title="ANSSI" xmlUrl="https://ssi.gouv.fr/feed/" htmlUrl="https://www.ssi.gouv.fr"/>
              <outline type="rss" text="CERT-FR" title="CERT-FR" xmlUrl="https://www.cert.ssi.gouv.fr/feed/"/>
            </outline>
            <outline text="Cyber (Global)" title="Cyber (Global)">
              <outline type="rss" text="Krebs" title="Krebs" xmlUrl="https://krebsonsecurity.com/feed/"/>
            </outline>
          </body>
        </opml>
    """.trimIndent()

    private fun parse(xml: String) = Opml.parse(xml.byteInputStream())

    @Test
    fun `parses every feed outline`() {
        assertEquals(4, parse(opml).size)
    }

    @Test
    fun `a top-level feed has no category`() {
        val korben = parse(opml).first()
        assertEquals("Korben", korben.title)
        assertEquals("https://korben.info/feedfull/", korben.xmlUrl)
        assertEquals("https://korben.info", korben.siteUrl)
        assertNull(korben.category)
    }

    @Test
    fun `nested feeds carry their enclosing folder`() {
        val byTitle = parse(opml).associateBy { it.title }
        assertEquals("Sécurité & Compliance (FR)", byTitle.getValue("ANSSI").category)
        assertEquals("Sécurité & Compliance (FR)", byTitle.getValue("CERT-FR").category)
        assertEquals("Cyber (Global)", byTitle.getValue("Krebs").category)
    }

    @Test
    fun `a folder does not leak into the feeds that follow it`() {
        // The trailing feed sits after a closed folder, so it must be uncategorised.
        val trailing = parse(
            """
            <opml version="2.0"><body>
              <outline text="Dossier" title="Dossier">
                <outline type="rss" title="Dedans" xmlUrl="https://a.example/feed"/>
              </outline>
              <outline type="rss" title="Dehors" xmlUrl="https://b.example/feed"/>
            </body></opml>
            """.trimIndent()
        ).last()
        assertEquals("Dehors", trailing.title)
        assertNull(trailing.category)
    }

    @Test
    fun `folder outlines are not mistaken for feeds`() {
        assertTrue(parse(opml).none { it.title == "Cyber (Global)" })
    }

    @Test
    fun `malformed documents yield no entries`() {
        assertTrue(parse("<opml><body><outline").isEmpty())
    }
}
