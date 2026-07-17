package com.ebooks.reader.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single article belonging to an [RssFeed]. The article HTML is stored on
 * device at fetch time so it can be read (and annotated) fully offline.
 */
@Entity(
    tableName = "rss_articles",
    foreignKeys = [
        ForeignKey(
            entity = RssFeed::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("feedId"), Index(value = ["feedId", "guid"], unique = true)]
)
data class RssArticle(
    @PrimaryKey val id: String,
    val feedId: String,
    /** Stable per-feed identifier (RSS <guid> / Atom <id>, falls back to the link). */
    val guid: String,
    val title: String,
    val link: String? = null,
    val author: String? = null,
    /** Full article HTML captured for offline reading. */
    val contentHtml: String = "",
    val summary: String? = null,
    val publishedAt: Long = 0L,
    val fetchedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isFavorite: Boolean = false
)
