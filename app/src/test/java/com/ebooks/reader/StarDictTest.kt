package com.ebooks.reader

import com.ebooks.reader.data.dict.StarDictIndex
import com.ebooks.reader.data.dict.StarDictIndexEntry
import com.ebooks.reader.data.dict.StarDictInfo
import com.ebooks.reader.data.dict.extractStarDictDefinition
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StarDictTest {

    // ── .ifo parsing ──────────────────────────────────────────────────────────

    @Test
    fun `parses a valid ifo file`() {
        val info = StarDictInfo.parse(
            """
            StarDict's dict ifo file
            version=2.4.2
            bookname=Test Dictionary
            wordcount=3
            idxfilesize=42
            sametypesequence=m
            """.trimIndent()
        )
        assertEquals("Test Dictionary", info?.bookName)
        assertEquals(3, info?.wordCount)
        assertEquals("m", info?.sameTypeSequence)
    }

    @Test
    fun `rejects ifo without magic line or with 64-bit offsets`() {
        assertNull(StarDictInfo.parse("bookname=X\nwordcount=1"))
        assertNull(
            StarDictInfo.parse(
                "StarDict's dict ifo file\nbookname=X\nwordcount=1\nidxoffsetbits=64"
            )
        )
    }

    // ── .idx parsing + lookup ─────────────────────────────────────────────────

    private fun buildIdx(vararg entries: Triple<String, Int, Int>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((word, offset, size) in entries) {
            out.write(word.toByteArray(Charsets.UTF_8))
            out.write(0)
            for (shift in intArrayOf(24, 16, 8, 0)) out.write((offset shr shift) and 0xFF)
            for (shift in intArrayOf(24, 16, 8, 0)) out.write((size shr shift) and 0xFF)
        }
        return out.toByteArray()
    }

    @Test
    fun `parses idx records`() {
        val entries = StarDictIndex.parse(
            buildIdx(
                Triple("apple", 0, 10),
                Triple("Banana", 10, 20),
                Triple("cherry", 30, 5)
            )
        )
        assertEquals(
            listOf(
                StarDictIndexEntry("apple", 0L, 10),
                StarDictIndexEntry("Banana", 10L, 20),
                StarDictIndexEntry("cherry", 30L, 5)
            ),
            entries
        )
    }

    @Test
    fun `rejects truncated idx data`() {
        val valid = buildIdx(Triple("apple", 0, 10))
        assertNull(StarDictIndex.parse(valid.copyOfRange(0, valid.size - 3)))
    }

    @Test
    fun `find is case-insensitive and prefers exact case`() {
        val entries = StarDictIndex.parse(
            buildIdx(
                Triple("apple", 0, 1),
                Triple("Banana", 1, 2),
                Triple("cherry", 3, 4)
            )
        )!!
        assertEquals("Banana", StarDictIndex.find(entries, "banana")?.word)
        assertEquals("apple", StarDictIndex.find(entries, "APPLE")?.word)
        assertEquals("cherry", StarDictIndex.find(entries, "cherry")?.word)
        assertNull(StarDictIndex.find(entries, "durian"))
    }

    // ── .dict record rendering ────────────────────────────────────────────────

    @Test
    fun `plain text record with sametypesequence m`() {
        val data = "a round fruit".toByteArray(Charsets.UTF_8)
        assertEquals("a round fruit", extractStarDictDefinition(data, "m"))
    }

    @Test
    fun `html record with sametypesequence h is flattened`() {
        val data = "<b>fruit</b>: a <i>round</i> thing".toByteArray(Charsets.UTF_8)
        assertEquals("fruit : a round thing", extractStarDictDefinition(data, "h"))
    }

    @Test
    fun `typed record without sametypesequence`() {
        // 'm' type byte + nul-terminated text, then a second 'm' field to the end.
        val out = ByteArrayOutputStream()
        out.write('m'.code)
        out.write("first".toByteArray(Charsets.UTF_8))
        out.write(0)
        out.write('m'.code)
        out.write("second".toByteArray(Charsets.UTF_8))
        out.write(0)
        assertEquals("first\nsecond", extractStarDictDefinition(out.toByteArray(), null))
    }
}
