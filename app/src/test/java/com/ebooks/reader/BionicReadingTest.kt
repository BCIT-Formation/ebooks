package com.ebooks.reader

import com.ebooks.reader.util.bionicHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BionicReadingTest {

    @Test
    fun `bolds leading 40 percent of long words`() {
        // "reading" (7 letters) → ceil(2.8) = 3 bold
        assertEquals("<b>rea</b>ding", bionicHtml("reading"))
        // "bionic" (6 letters) → ceil(2.4) = 3 bold
        assertEquals("<b>bio</b>nic", bionicHtml("bionic"))
    }

    @Test
    fun `short words bold their first letter only`() {
        assertEquals("<b>a</b>", bionicHtml("a"))
        assertEquals("<b>t</b>he <b>c</b>at", bionicHtml("the cat"))
    }

    @Test
    fun `tags are preserved untouched`() {
        assertEquals("<p class=\"x\"><b>he</b>llo</p>", bionicHtml("<p class=\"x\">hello</p>"))
    }

    @Test
    fun `style and head contents are not transformed`() {
        val html = "<head><style>body { color: red; }</style></head><body>words here</body>"
        val out = bionicHtml(html)
        assertTrue(out.contains("<style>body { color: red; }</style>"))
        assertTrue(out.contains("<b>wo</b>rds"))
    }

    @Test
    fun `entities are kept intact and act as boundaries`() {
        val out = bionicHtml("fish &amp; chips")
        assertTrue(out.contains("&amp;"))
        assertFalse(out.contains("<b>&"))
        assertEquals("<b>fi</b>sh &amp; <b>ch</b>ips", out)
    }

    @Test
    fun `apostrophes stay inside their word`() {
        // "don't" is one 5-char word → 2 bold
        assertEquals("<b>do</b>n't", bionicHtml("don't"))
    }

    @Test
    fun `punctuation and digits are left alone`() {
        assertEquals("42, <b>ti</b>mes!", bionicHtml("42, times!"))
    }
}
