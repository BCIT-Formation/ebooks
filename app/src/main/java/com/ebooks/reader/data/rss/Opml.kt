package com.ebooks.reader.data.rss

import com.ebooks.reader.data.db.entities.RssFeed
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

data class OpmlEntry(val title: String, val xmlUrl: String, val siteUrl: String?)

/** Pure-Kotlin OPML (feed list) import and export — the standard interchange format. */
object Opml {

    /** Parses an OPML document into feed subscriptions. Returns empty on error. */
    fun parse(stream: InputStream): List<OpmlEntry> = runCatching {
        val parser = XmlPullParserFactory.newInstance()
            .also { it.isNamespaceAware = false }
            .newPullParser()
            .also { it.setInput(stream, null) }

        val entries = mutableListOf<OpmlEntry>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("outline", ignoreCase = true)) {
                val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                    ?: parser.getAttributeValue(null, "xmlurl")
                if (!xmlUrl.isNullOrBlank()) {
                    val title = parser.getAttributeValue(null, "title")
                        ?: parser.getAttributeValue(null, "text")
                        ?: xmlUrl
                    val site = parser.getAttributeValue(null, "htmlUrl")
                    entries.add(OpmlEntry(title.trim(), xmlUrl.trim(), site))
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
              <head><title>EbookReader subscriptions</title></head>
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
