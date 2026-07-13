package com.ebooks.reader.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ReadingStatus { UNREAD, READING, READ }

enum class FileType(val extension: String, val mimeType: String) {
    EPUB("epub", "application/epub+zip"),
    PDF("pdf", "application/pdf"),
    TXT("txt", "text/plain"),
    FB2("fb2", "application/x-fictionbook+xml"),
    CBZ("cbz", "application/x-cbz");

    companion object {
        fun fromExtension(ext: String): FileType? =
            entries.find { it.extension.equals(ext, ignoreCase = true) }
    }
}

@Entity(tableName = "books", indices = [Index(value = ["filePath"], unique = true)])
data class Book(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val fileType: String,
    val coverPath: String? = null,
    val fileSize: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    val totalChapters: Int = 0,
    val currentChapter: Int = 0,
    val currentPage: Int = 0,
    val description: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val series: String? = null,
    val seriesIndex: Int? = null,
    /** Comma-separated user tags / collections (e.g. "Favourites, To read"). */
    val tags: String = "",
)

/** The book's tags as a trimmed, non-empty list. */
fun Book.tagList(): List<String> = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
