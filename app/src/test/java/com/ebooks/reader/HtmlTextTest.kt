package com.ebooks.reader

import com.ebooks.reader.util.htmlToPlainText
import com.ebooks.reader.util.stripTinyImages
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

    @Test
    fun `stripTinyImages removes images sized by width and height attributes`() {
        val html = """<p>Text</p><img src="icon.png" width="16" height="16">"""
        val result = stripTinyImages(html)
        assertFalse(result.contains("<img"))
        assertTrue(result.contains("<p>Text</p>"))
    }

    @Test
    fun `stripTinyImages keeps images with no dimensions`() {
        val html = """<img src="photo.jpg" alt="A landscape">"""
        assertEquals(html, stripTinyImages(html))
    }

    @Test
    fun `stripTinyImages keeps large content images`() {
        val html = """<img src="photo.jpg" width="600" height="400">"""
        assertEquals(html, stripTinyImages(html))
    }

    @Test
    fun `stripTinyImages removes images sized via inline style`() {
        val html = """<img src="badge.png" style="width:20px;height:20px;border:0">"""
        assertFalse(stripTinyImages(html).contains("<img"))
    }

    @Test
    fun `stripTinyImages removes images flagged by decorative markers`() {
        val html = """<img src="https://example.com/wp-includes/images/smilies/wink.png" class="wp-smiley">"""
        assertFalse(stripTinyImages(html).contains("<img"))
    }

    @Test
    fun `stripTinyImages removes tracking pixels`() {
        val html = """<img src="https://stats.example.com/pixel.gif" width="1" height="1">"""
        assertFalse(stripTinyImages(html).contains("<img"))
    }

    @Test
    fun `stripTinyImages removes emoji-only alt images without dimensions`() {
        val html = """<img src="emoji.svg" alt="😀">"""
        assertFalse(stripTinyImages(html).contains("<img"))
    }

    @Test
    fun `stripTinyImages only removes matching tags in mixed content`() {
        val html = """<p>Intro</p><img src="tracker.gif" width="1" height="1"><img src="photo.jpg" width="800" height="600"><p>Outro</p>"""
        val result = stripTinyImages(html)
        assertFalse(result.contains("tracker.gif"))
        assertTrue(result.contains("photo.jpg"))
    }

    @Test
    fun `stripTinyImages on blank input returns input unchanged`() {
        assertEquals("", stripTinyImages(""))
    }
}
