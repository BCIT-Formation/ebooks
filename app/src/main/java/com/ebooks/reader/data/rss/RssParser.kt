package com.ebooks.reader.data.rss

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedFeed(
    val title: String,
    val siteUrl: String?,
    val articles: List<ParsedArticle>
)

data class ParsedArticle(
    val guid: String,
    val title: String,
    val link: String?,
    val author: String?,
    val contentHtml: String,
    val summary: String?,
    val publishedAt: Long
)

/**
 * Pure-Kotlin parser for RSS 2.0 and Atom feeds (ADR-001: no external parsing
 * libraries). Returns null on any error, matching the other parsers' contract.
 */
class RssParser {

    fun parse(stream: InputStream): ParsedFeed? = runCatching {
        val parser = XmlPullParserFactory.newInstance()
            .also { it.isNamespaceAware = false }
            .newPullParser()
            .also { it.setInput(stream, null) }

        var feedTitle = ""
        var siteUrl: String? = null
        var isAtom = false
        val articles = mutableListOf<ParsedArticle>()

        var inItem = false
        // Per-item accumulators
        var title = ""; var link: String? = null; var guid: String? = null
        var author: String? = null; var content = ""; var summary: String? = null
        var date = 0L

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfterLast(":")?.lowercase()
            when (event) {
                XmlPullParser.START_TAG -> when (tag) {
                    "feed" -> isAtom = true
                    "item", "entry" -> {
                        inItem = true
                        title = ""; link = null; guid = null; author = null
                        content = ""; summary = null; date = 0L
                    }
                    "title" -> {
                        val t = parser.nextTextSafe()
                        if (inItem) title = t else if (feedTitle.isEmpty()) feedTitle = t
                    }
                    "link" -> {
                        // Atom uses <link href="...">; RSS uses <link>text</link>
                        val href = parser.getAttributeValue(null, "href")
                        if (href != null) {
                            val rel = parser.getAttributeValue(null, "rel")
                            if (inItem) { if (rel == null || rel == "alternate") link = href }
                            else if (siteUrl == null && (rel == null || rel == "alternate")) siteUrl = href
                        } else {
                            val text = parser.nextTextSafe()
                            if (inItem) link = text else if (siteUrl == null) siteUrl = text
                        }
                    }
                    "guid", "id" -> if (inItem && guid == null) guid = parser.nextTextSafe()
                    "creator", "author" -> if (inItem) {
                        val a = parser.nextTextSafe()
                        if (a.isNotBlank()) author = a
                    }
                    "encoded", "content" -> if (inItem) content = parser.nextTextSafe()
                    "description", "summary" -> if (inItem) {
                        val s = parser.nextTextSafe()
                        summary = s
                        if (content.isBlank()) content = s
                    }
                    "pubdate", "published", "updated", "date" -> if (inItem && date == 0L) {
                        date = parseDate(parser.nextTextSafe())
                    }
                }
                XmlPullParser.END_TAG -> if (tag == "item" || tag == "entry") {
                    inItem = false
                    val id = guid?.takeIf { it.isNotBlank() } ?: link ?: title
                    if (title.isNotBlank() && id.isNotBlank()) {
                        articles.add(
                            ParsedArticle(
                                guid = id,
                                title = title.trim(),
                                link = link?.trim(),
                                author = author?.trim(),
                                contentHtml = content,
                                summary = summary?.let { stripTags(it).take(300) },
                                publishedAt = date
                            )
                        )
                    }
                }
            }
            event = parser.next()
        }

        ParsedFeed(
            title = feedTitle.ifBlank { siteUrl ?: "Feed" },
            siteUrl = siteUrl,
            articles = articles
        )
    }.getOrNull()

    private fun XmlPullParser.nextTextSafe(): String =
        runCatching { nextText().trim() }.getOrDefault("")

    private fun stripTags(html: String): String =
        html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

    private val dateFormats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",   // RFC 822 (RSS)
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "yyyy-MM-dd'T'HH:mm:ssXXX",       // RFC 3339 (Atom)
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd"
    )

    private fun parseDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        for (pattern in dateFormats) {
            runCatching {
                return SimpleDateFormat(pattern, Locale.ENGLISH).parse(raw)?.time ?: 0L
            }
        }
        return 0L
    }
}
