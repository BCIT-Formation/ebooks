package com.ebooks.reader

import com.ebooks.reader.util.htmlToPlainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextTest {

    @Test
    fun `strips tags and keeps text`() {
        assertEquals("Hello world", htmlToPlainText("<p>Hello <b>world</b></p>"))
    }

    @Test
    fun `removes style blocks entirely`() {
        val html = "<style>body { color: red; }</style><p>Content</p>"
        val result = htmlToPlainText(html)
        assertEquals("Content", result)
        assertFalse(result.contains("color"))
    }

    @Test
    fun `removes script blocks entirely`() {
        val result = htmlToPlainText("<script>window.scrollBy(0, 10);</script><p>Text</p>")
        assertEquals("Text", result)
    }

    @Test
    fun `block boundaries become line breaks`() {
        val result = htmlToPlainText("<p>One.</p><p>Two.</p>")
        assertTrue(result.contains("One.\n"))
        assertTrue(result.contains("Two."))
    }

    @Test
    fun `decodes named entities`() {
        assertEquals("Fish & \"chips\" <cheap>", htmlToPlainText("Fish &amp; &quot;chips&quot; &lt;cheap&gt;"))
    }

    @Test
    fun `decodes decimal and hex numeric entities`() {
        assertEquals("It’s fine", htmlToPlainText("It&#8217;s fine"))
        assertEquals("A", htmlToPlainText("&#x41;"))
    }

    @Test
    fun `nbsp becomes plain space`() {
        assertEquals("a b", htmlToPlainText("a&nbsp;b"))
    }

    @Test
    fun `collapses whitespace runs`() {
        assertEquals("one two", htmlToPlainText("one \t   two"))
    }

    @Test
    fun `collapses excess blank lines`() {
        val result = htmlToPlainText("<p>a</p><br><br><br><p>b</p>")
        assertFalse(result.contains("\n\n\n"))
    }

    @Test
    fun `full chapter document extracts readable text`() {
        val html = """
            <html><head><title>ch1</title><style>p{margin:0}</style></head>
            <body><h1>Chapter 1</h1><p>It was a bright cold day.</p></body></html>
        """.trimIndent()
        val result = htmlToPlainText(html)
        assertTrue(result.startsWith("Chapter 1"))
        assertTrue(result.contains("It was a bright cold day."))
        assertFalse(result.contains("margin"))
        assertFalse(result.contains("ch1"))
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals("", htmlToPlainText(""))
    }
}
