package com.ebooks.reader.data.rss

import com.ebooks.reader.data.db.entities.RssFeed
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * A feed subscription read from an OPML document. [category] is the enclosing
 * folder outline (OPML nests feeds inside title-only outlines); it is null for
 * feeds sitting directly at the top level.
 */
data class OpmlEntry(
    val title: String,
    val xmlUrl: String,
    val siteUrl: String?,
    val category: String? = null
)

/** Pure-Kotlin OPML (feed list) import and export — the standard interchange format. */
object Opml {

    /** Parses an OPML document into feed subscriptions. Returns empty on error. */
    fun parse(stream: InputStream): List<OpmlEntry> = runCatching {
        val parser = XmlPullParserFactory.newInstance()
            .also { it.isNamespaceAware = false }
            .newPullParser()
            .also { it.setInput(stream, null) }

        val entries = mutableListOf<OpmlEntry>()
        // Folder outlines currently open, innermost last. A feed outline pushes
        // null so the depth stays in step with the END_TAG events.
        val folders = ArrayDeque<String?>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val isOutline = (event == XmlPullParser.START_TAG || event == XmlPullParser.END_TAG) &&
                parser.name.equals("outline", ignoreCase = true)
            if (isOutline) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                            ?: parser.getAttributeValue(null, "xmlurl")
                        val label = (parser.getAttributeValue(null, "title")
                            ?: parser.getAttributeValue(null, "text"))?.trim()
                        if (!xmlUrl.isNullOrBlank()) {
                            val site = parser.getAttributeValue(null, "htmlUrl")
                            entries.add(
                                OpmlEntry(
                                    title = label?.ifBlank { null } ?: xmlUrl.trim(),
                                    xmlUrl = xmlUrl.trim(),
                                    siteUrl = site,
                                    category = folders.lastOrNull { it != null }
                                )
                            )
                            folders.addLast(null)
                        } else {
                            folders.addLast(label?.ifBlank { null })
                        }
                    }
                    XmlPullParser.END_TAG -> folders.removeLastOrNull()
                }
            }
            event = parser.next()
        }
        entries
    }.getOrDefault(emptyList())

    /** Serializes subscribed feeds to an OPML document. */
    fun export(feeds: List<RssFeed>): String {
        val body = feeds.joinToString("\n") { feed ->
            "    <outline type=\"rss\" text=\"${escape(feed.title)}\" title=\"${escape(feed.title)}\" " +
                "xmlUrl=\"${escape(feed.url)}\"${feed.siteUrl?.let { " htmlUrl=\"${escape(it)}\"" } ?: ""}/>"
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>ReadIt subscriptions</title></head>
              <body>
            $body
              </body>
            </opml>
        """.trimIndent()
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("\"", "&quot;")
        .replace("<", "&lt;").replace(">", "&gt;")
}
