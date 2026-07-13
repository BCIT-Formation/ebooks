package com.ebooks.reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebooks.reader.data.db.entities.RssArticle
import com.ebooks.reader.data.db.entities.RssFeed
import kotlinx.coroutines.flow.Flow

@Dao
interface RssDao {

    // ── Feeds ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM rss_feeds ORDER BY title ASC")
    fun getFeeds(): Flow<List<RssFeed>>

    @Query("SELECT * FROM rss_feeds")
    suspend fun getFeedsSnapshot(): List<RssFeed>

    @Query("SELECT * FROM rss_feeds WHERE url = :url")
    suspend fun getFeedByUrl(url: String): RssFeed?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFeed(feed: RssFeed)

    @Query("UPDATE rss_feeds SET lastFetchedAt = :time WHERE id = :feedId")
    suspend fun markFeedFetched(feedId: String, time: Long)

    @Query("DELETE FROM rss_feeds WHERE id = :feedId")
    suspend fun deleteFeed(feedId: String)

    // ── Articles ─────────────────────────────────────────────────────────────

    /** All articles across all feeds, newest first — the unified RSS timeline. */
    @Query("SELECT * FROM rss_articles ORDER BY publishedAt DESC, fetchedAt DESC")
    fun getAllArticles(): Flow<List<RssArticle>>

    @Query("SELECT * FROM rss_articles WHERE feedId = :feedId ORDER BY publishedAt DESC, fetchedAt DESC")
    fun getArticlesForFeed(feedId: String): Flow<List<RssArticle>>

    @Query("SELECT * FROM rss_articles WHERE feedId = :feedId")
    suspend fun getArticlesForFeedSnapshot(feedId: String): List<RssArticle>

    @Query("SELECT * FROM rss_articles WHERE id = :id")
    suspend fun getArticle(id: String): RssArticle?

    /** Insert new articles without clobbering an already-stored one (keeps read state). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<RssArticle>)

    @Query("UPDATE rss_articles SET isRead = 1 WHERE id = :id")
    suspend fun markArticleRead(id: String)

    @Query("SELECT COUNT(*) FROM rss_articles WHERE feedId = :feedId AND isRead = 0")
    suspend fun unreadCount(feedId: String): Int
}
