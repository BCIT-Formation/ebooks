package com.ebooks.reader

import com.ebooks.reader.data.parser.ComicArchive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComicArchiveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun buildZip(vararg entries: Pair<String, ByteArray>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    // ── isComicPage ───────────────────────────────────────────────────────────

    @Test
    fun `image extensions are recognised case-insensitively`() {
        assertTrue(ComicArchive.isComicPage("page01.jpg"))
        assertTrue(ComicArchive.isComicPage("Page01.PNG"))
        assertTrue(ComicArchive.isComicPage("folder/page01.webp"))
        assertTrue(ComicArchive.isComicPage("page.JPEG"))
    }

    @Test
    fun `non-image entries are rejected`() {
        assertFalse(ComicArchive.isComicPage("ComicInfo.xml"))
        assertFalse(ComicArchive.isComicPage("readme.txt"))
        assertFalse(ComicArchive.isComicPage("Thumbs.db"))
        assertFalse(ComicArchive.isComicPage("noextension"))
    }

    // ── CBZ extraction ────────────────────────────────────────────────────────

    @Test
    fun `pages are extracted in case-insensitive name order`() {
        val zip = buildZip(
            "B_page.png" to byteArrayOf(2),
            "a_page.jpg" to byteArrayOf(1),
            "c_page.gif" to byteArrayOf(3)
        )
        val pages = ComicArchive.extractCbzPages(zip, tempFolder.newFolder())
        assertEquals(3, pages.size)
        assertEquals("page_00000.jpg", pages[0].name)
        assertEquals("page_00001.png", pages[1].name)
        assertEquals("page_00002.gif", pages[2].name)
        assertEquals(1, pages[0].readBytes().single().toInt())
        assertEquals(2, pages[1].readBytes().single().toInt())
        assertEquals(3, pages[2].readBytes().single().toInt())
    }

    @Test
    fun `non-image and directory entries are skipped`() {
        val zip = buildZip(
            "ComicInfo.xml" to "<ComicInfo/>".toByteArray(),
            "pages/" to ByteArray(0),
            "pages/001.jpg" to byteArrayOf(7)
        )
        val pages = ComicArchive.extractCbzPages(zip, tempFolder.newFolder())
        assertEquals(1, pages.size)
        assertEquals("page_00000.jpg", pages[0].name)
    }

    @Test
    fun `archive with no images yields an empty list`() {
        val zip = buildZip("readme.txt" to "hi".toByteArray())
        assertTrue(ComicArchive.extractCbzPages(zip, tempFolder.newFolder()).isEmpty())
    }

    @Test
    fun `entry names with traversal segments cannot escape the destination`() {
        val dir = tempFolder.newFolder()
        val zip = buildZip("../../evil.jpg" to byteArrayOf(9))
        val pages = ComicArchive.extractCbzPages(zip, dir)
        assertEquals(1, pages.size)
        // Written under an indexed name inside destDir, not at the entry path.
        assertEquals(dir, pages[0].parentFile)
        assertEquals("page_00000.jpg", pages[0].name)
    }
}
