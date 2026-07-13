package com.ebooks.reader.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A subscribed RSS/Atom feed. Articles are downloaded and cached for offline reading. */
@Entity(tableName = "rss_feeds", indices = [Index(value = ["url"], unique = true)])
data class RssFeed(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val siteUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastFetchedAt: Long? = null
)
