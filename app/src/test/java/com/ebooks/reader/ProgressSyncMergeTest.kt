package com.ebooks.reader

import com.ebooks.reader.data.sync.ProgressEntry
import com.ebooks.reader.data.sync.selectNewerEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressSyncMergeTest {

    private fun entry(
        title: String = "Alice in Wonderland",
        author: String = "Lewis Carroll",
        chapterIndex: Int = 0,
        lastReadAt: Long = 1_000L
    ) = ProgressEntry(
        title = title,
        author = author,
        chapterIndex = chapterIndex,
        scrollPosition = 0,
        lastReadAt = lastReadAt,
        readingStatus = "READING"
    )

    @Test
    fun `newer remote entry is selected`() {
        val local = listOf(entry(chapterIndex = 2, lastReadAt = 1_000L))
        val remote = listOf(entry(chapterIndex = 5, lastReadAt = 2_000L))
        val result = selectNewerEntries(local, remote)
        assertEquals(1, result.size)
        assertEquals(5, result[0].chapterIndex)
    }

    @Test
    fun `older remote entry is ignored`() {
        val local = listOf(entry(lastReadAt = 2_000L))
        val remote = listOf(entry(lastReadAt = 1_000L))
        assertTrue(selectNewerEntries(local, remote).isEmpty())
    }

    @Test
    fun `equal timestamps are not applied`() {
        val local = listOf(entry(lastReadAt = 1_000L))
        val remote = listOf(entry(lastReadAt = 1_000L))
        assertTrue(selectNewerEntries(local, remote).isEmpty())
    }

    @Test
    fun `remote entry with no matching local book is skipped`() {
        val local = listOf(entry(title = "Moby Dick", author = "Melville"))
        val remote = listOf(entry(lastReadAt = 9_000L))
        assertTrue(selectNewerEntries(local, remote).isEmpty())
    }

    @Test
    fun `matching is case-insensitive on title and author`() {
        val local = listOf(entry(title = "alice in wonderland", author = "LEWIS CARROLL", lastReadAt = 1_000L))
        val remote = listOf(entry(lastReadAt = 2_000L))
        assertEquals(1, selectNewerEntries(local, remote).size)
    }

    @Test
    fun `only newer entries survive a mixed snapshot`() {
        val local = listOf(
            entry(title = "A", lastReadAt = 1_000L),
            entry(title = "B", lastReadAt = 5_000L)
        )
        val remote = listOf(
            entry(title = "A", lastReadAt = 2_000L),
            entry(title = "B", lastReadAt = 4_000L),
            entry(title = "C", lastReadAt = 9_000L)
        )
        val result = selectNewerEntries(local, remote)
        assertEquals(1, result.size)
        assertEquals("A", result[0].title)
    }
}
