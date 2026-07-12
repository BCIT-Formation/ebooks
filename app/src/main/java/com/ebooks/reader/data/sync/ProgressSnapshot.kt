package com.ebooks.reader.data.sync

/** File name used for progress snapshots on WebDAV servers and cloud folders. */
const val PROGRESS_SNAPSHOT_FILE_NAME = "ebook-reader-progress.json"

/**
 * A device-independent snapshot of reading progress, exchanged between
 * devices via a cloud folder (Google Drive / OneDrive through SAF) or a
 * WebDAV server. Books are matched across devices by title + author because
 * book IDs and file URIs are device-specific.
 *
 * Pure Kotlin (no Android imports) so the merge logic is unit-testable.
 */
data class ProgressSnapshot(
    val exportedAt: Long,
    val entries: List<ProgressEntry>
) {
    companion object {
        const val VERSION = 1
    }
}

data class ProgressEntry(
    val title: String,
    val author: String,
    val chapterIndex: Int,
    val scrollPosition: Int,
    val lastReadAt: Long,
    val readingStatus: String
)

/**
 * Newer-wins merge: returns the remote entries that match a local book
 * (same title + author, case-insensitive) and are strictly more recent
 * than the local reading state. Only those should be applied locally.
 */
fun selectNewerEntries(local: List<ProgressEntry>, remote: List<ProgressEntry>): List<ProgressEntry> =
    remote.filter { remoteEntry ->
        val localEntry = local.find {
            it.title.equals(remoteEntry.title, ignoreCase = true) &&
                it.author.equals(remoteEntry.author, ignoreCase = true)
        }
        localEntry != null && remoteEntry.lastReadAt > localEntry.lastReadAt
    }
