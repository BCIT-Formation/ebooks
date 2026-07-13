package com.ebooks.reader.data.repository

import android.content.Context
import com.ebooks.reader.data.db.AppDatabase
import com.ebooks.reader.data.db.entities.Annotation
import com.ebooks.reader.data.db.entities.RssArticle
import com.ebooks.reader.data.db.entities.RssFeed
import com.ebooks.reader.data.rss.Opml
import com.ebooks.reader.data.rss.RssClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

class RssRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val rssDao = db.rssDao()
    private val bookDao = db.bookDao() // annotations live in the shared annotations table
    private val client = RssClient()

    // ── Reads ─────────────────────────────────────────────────────────────────

    fun getFeeds(): Flow<List<RssFeed>> = rssDao.getFeeds()
    fun getAllArticles(): Flow<List<RssArticle>> = rssDao.getAllArticles()
    fun getArticlesForFeed(feedId: String): Flow<List<RssArticle>> = rssDao.getArticlesForFeed(feedId)
    suspend fun getArticle(id: String): RssArticle? = withContext(Dispatchers.IO) { rssDao.getArticle(id) }
    suspend fun markRead(id: String) = withContext(Dispatchers.IO) { rssDao.markArticleRead(id) }

    // ── Feed management ─────────────────────────────────────────────────────────

    sealed class AddResult {
        data class Success(val feed: RssFeed, val newArticles: Int) : AddResult()
        object AlreadyExists : AddResult()
        data class Failed(val message: String) : AddResult()
    }

    /** Adds (or refreshes) a feed by URL: fetches, parses, and stores its articles offline. */
    suspend fun addFeed(url: String): AddResult = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = client.fetch(url)
            val normalizedUrl = url.trim()
            val existing = rssDao.getFeedByUrl(normalizedUrl)
            val feedId = existing?.id ?: deterministicId(normalizedUrl)
            val feed = RssFeed(
                id = feedId,
                title = parsed.title,
                url = normalizedUrl,
                siteUrl = parsed.siteUrl,
                addedAt = existing?.addedAt ?: System.currentTimeMillis(),
                lastFetchedAt = System.currentTimeMillis()
            )
            rssDao.upsertFeed(feed)
            val inserted = storeArticles(feedId, parsed.articles.map { it })
            AddResult.Success(feed, inserted)
        }.getOrElse { AddResult.Failed(it.message ?: "Unknown error") }
    }

    /** Re-fetches every subscribed feed, adding any new articles. Returns total new articles. */
    suspend fun refreshAll(): Int = withContext(Dispatchers.IO) {
        var total = 0
        for (feed in rssDao.getFeedsSnapshot()) {
            runCatching {
                val parsed = client.fetch(feed.url)
                total += storeArticles(feed.id, parsed.articles)
                rssDao.markFeedFetched(feed.id, System.currentTimeMillis())
            }
        }
        total
    }

    suspend fun deleteFeed(feed: RssFeed) = withContext(Dispatchers.IO) {
        // Remove annotations attached to this feed's articles (no FK does it for us).
        rssDao.getArticlesForFeedSnapshot(feed.id).forEach { article ->
            bookDao.deleteAllAnnotations(annotationKey(article.id))
        }
        rssDao.deleteFeed(feed.id) // articles cascade
    }

    private suspend fun storeArticles(feedId: String, articles: List<com.ebooks.reader.data.rss.ParsedArticle>): Int {
        val rows = articles.map { a ->
            RssArticle(
                id = deterministicId("$feedId|${a.guid}"),
                feedId = feedId,
                guid = a.guid,
                title = a.title,
                link = a.link,
                author = a.author,
                contentHtml = a.contentHtml,
                summary = a.summary,
                publishedAt = a.publishedAt
            )
        }
        rssDao.insertArticles(rows) // IGNORE keeps existing (read state preserved)
        return rows.size
    }

    // ── OPML ────────────────────────────────────────────────────────────────────

    /** Imports feeds from an OPML document; returns how many new feeds were added. */
    suspend fun importOpml(input: InputStream): Int = withContext(Dispatchers.IO) {
        val entries = Opml.parse(input)
        var added = 0
        for (entry in entries) {
            if (rssDao.getFeedByUrl(entry.xmlUrl.trim()) != null) continue
            if (addFeed(entry.xmlUrl) is AddResult.Success) added++
        }
        added
    }

    suspend fun exportOpml(): String = withContext(Dispatchers.IO) {
        Opml.export(rssDao.getFeedsSnapshot())
    }

    // ── Article annotations (shared drawing tools) ──────────────────────────────

    /** Key used to store an article's annotations in the shared annotations table. */
    fun annotationKey(articleId: String): String = "rss:$articleId"
    private val pageId = "article"

    suspend fun getArticleAnnotations(articleId: String): List<Annotation> =
        withContext(Dispatchers.IO) { bookDao.getAnnotationsForPage(annotationKey(articleId), pageId) }

    suspend fun addArticleAnnotation(articleId: String, annotation: Annotation) =
        withContext(Dispatchers.IO) {
            bookDao.insertAnnotation(annotation.copy(bookId = annotationKey(articleId), pageIdentifier = pageId, pageIndex = 0))
        }

    suspend fun clearArticleAnnotations(articleId: String) =
        withContext(Dispatchers.IO) { bookDao.deletePageAnnotations(annotationKey(articleId), pageId) }

    private fun deterministicId(seed: String): String =
        UUID.nameUUIDFromBytes(seed.toByteArray()).toString()
}
