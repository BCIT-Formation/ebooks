package com.ebooks.reader.data.repository

import android.content.Context
import android.util.Log
import com.ebooks.reader.R
import com.ebooks.reader.data.db.AppDatabase
import com.ebooks.reader.data.db.entities.Annotation
import com.ebooks.reader.data.db.entities.RssArticle
import com.ebooks.reader.data.db.entities.RssFeed
import com.ebooks.reader.data.rss.Opml
import com.ebooks.reader.data.rss.RssClient
import com.ebooks.reader.util.AnnotationMarkdownBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RssRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val rssDao = db.rssDao()
    private val bookDao = db.bookDao() // annotations live in the shared annotations table
    private val client = RssClient()

    companion object {
        private val REGEX_H_OPEN = Regex("<h[1-6][^>]*>")
        private val REGEX_H_CLOSE = Regex("</h[1-6]>")
        private val REGEX_P_OPEN = Regex("<p[^>]*>")
        private val REGEX_P_CLOSE = Regex("</p>")
        private val REGEX_BR = Regex("<br\\s*/?\\s*>")
        private val REGEX_STRONG_OPEN = Regex("<strong[^>]*>")
        private val REGEX_STRONG_CLOSE = Regex("</strong>")
        private val REGEX_EM_OPEN = Regex("<em[^>]*>")
        private val REGEX_EM_CLOSE = Regex("</em>")
        private val REGEX_B_OPEN = Regex("<b[^>]*>")
        private val REGEX_B_CLOSE = Regex("</b>")
        private val REGEX_I_OPEN = Regex("<i[^>]*>")
        private val REGEX_I_CLOSE = Regex("</i>")
        private val REGEX_LINK = Regex("<a[^>]*href=\"([^\"]+)\"[^>]*>([^<]*)</a>")
        private val REGEX_TAG = Regex("<[^>]+>")
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    fun getFeeds(): Flow<List<RssFeed>> = rssDao.getFeeds()
    fun getAllArticles(): Flow<List<RssArticle>> = rssDao.getAllArticles()
    fun getArticlesForFeed(feedId: String): Flow<List<RssArticle>> = rssDao.getArticlesForFeed(feedId)
    suspend fun getArticle(id: String): RssArticle? = withContext(Dispatchers.IO) { rssDao.getArticle(id) }
    suspend fun markRead(id: String) = withContext(Dispatchers.IO) { rssDao.markArticleRead(id) }

    // ── Article operations ─────────────────────────────────────────────────────────

    suspend fun markArticlesAsRead(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) rssDao.markArticlesRead(ids)
    }

    suspend fun markArticlesAsUnread(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) rssDao.markArticlesUnread(ids)
    }

    suspend fun toggleFavorite(articleId: String) = withContext(Dispatchers.IO) {
        rssDao.toggleArticleFavorite(articleId)
    }

    suspend fun toggleRead(articleId: String) = withContext(Dispatchers.IO) {
        rssDao.toggleArticleRead(articleId)
    }

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
            val inserted = storeArticles(feedId, parsed.articles)
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

    /** Imports the bundled default feeds from res/raw/default_feeds.opml on first app install. */
    suspend fun importDefaultFeeds(context: Context): Int = withContext(Dispatchers.IO) {
        val input = context.resources.openRawResource(R.raw.default_feeds)
        importOpml(input)
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

    suspend fun deleteAnnotation(id: String) =
        withContext(Dispatchers.IO) { bookDao.softDeleteAnnotation(id) }

    // ── Sharing RSS Articles ────────────────────────────────────────────────────

    data class ShareArticleBundle(
        val articleMarkdown: File,
        val articleAnnotations: File?
    )

    /**
     * Prepares an RSS article for sharing. Creates a markdown file with:
     * - Article title, author, publish date
     * - Full article content
     * - All annotations (highlights + notes)
     */
    suspend fun prepareShareArticle(articleId: String, context: Context): ShareArticleBundle? =
        withContext(Dispatchers.IO) {
            val article = getArticle(articleId) ?: return@withContext null
            val shareDir = File(context.cacheDir, "share").also { it.mkdirs() }

            val safeTitle = article.title.replace(Regex("[^A-Za-z0-9._ -]"), "_")
                .trim().ifBlank { "article" }.take(50)

            // Create markdown file with article content
            val markdown = buildString {
                append("# ").append(article.title).append("\n\n")

                if (!article.author.isNullOrBlank()) {
                    append("**Auteur:** ").append(article.author).append("\n\n")
                }

                if (article.publishedAt > 0) {
                    val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                    append("**Date:** ").append(dateFormat.format(Date(article.publishedAt))).append("\n\n")
                }

                if (!article.link.isNullOrBlank()) {
                    append("[Lien original](").append(article.link).append(")\n\n")
                }

                append("---\n\n")

                // Article content (HTML to Markdown conversion)
                val content = article.contentHtml?.let { htmlToMarkdown(it) } ?: article.summary
                if (!content.isNullOrBlank()) {
                    append(content).append("\n\n")
                }
            }

            val articleMarkdownFile = try {
                File(shareDir, "${safeTitle}_article.md").apply {
                    writeText(markdown)
                }
            } catch (e: Exception) {
                Log.e("RssRepository", "Error creating article markdown", e)
                return@withContext null
            }

            // Add annotations if any exist
            val annotations = getArticleAnnotations(articleId)
            val annotationsFile = if (annotations.isNotEmpty()) {
                try {
                    val annotationsMarkdown = buildAnnotationsMarkdown(annotations, safeTitle)
                    File(shareDir, "${safeTitle}_annotations.md").apply {
                        writeText(annotationsMarkdown)
                    }
                } catch (e: Exception) {
                    Log.e("RssRepository", "Error creating annotations markdown", e)
                    null
                }
            } else {
                null
            }

            ShareArticleBundle(articleMarkdownFile, annotationsFile)
        }

    private fun htmlToMarkdown(html: String): String {
        return html
            .replace(REGEX_H_OPEN, "## ")
            .replace(REGEX_H_CLOSE, "\n\n")
            .replace(REGEX_P_OPEN, "")
            .replace(REGEX_P_CLOSE, "\n\n")
            .replace(REGEX_BR, "\n")
            .replace(REGEX_STRONG_OPEN, "**")
            .replace(REGEX_STRONG_CLOSE, "**")
            .replace(REGEX_EM_OPEN, "*")
            .replace(REGEX_EM_CLOSE, "*")
            .replace(REGEX_B_OPEN, "**")
            .replace(REGEX_B_CLOSE, "**")
            .replace(REGEX_I_OPEN, "*")
            .replace(REGEX_I_CLOSE, "*")
            .replace(REGEX_LINK, "[$2]($1)")
            .replace(REGEX_TAG, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }

    private fun buildAnnotationsMarkdown(annotations: List<Annotation>, safeTitle: String): String {
        return buildString {
            append("# Annotations - ").append(safeTitle).append("\n\n")

            annotations.forEach { annotation ->
                if (!annotation.textContent.isNullOrBlank()) {
                    append("> ").append(annotation.textContent.replace("\n", "\n> ")).append("\n\n")
                }
                if (!annotation.metadata.isNullOrBlank()) {
                    append("**Note:** ").append(annotation.metadata).append("\n\n")
                }
                append("---\n\n")
            }
        }
    }

    private fun deterministicId(seed: String): String =
        UUID.nameUUIDFromBytes(seed.toByteArray()).toString()
}
