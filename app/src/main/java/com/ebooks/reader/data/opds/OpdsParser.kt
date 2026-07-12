package com.ebooks.reader.data.opds

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

private const val REL_ACQUISITION_PREFIX = "http://opds-spec.org/acquisition"
private val DOWNLOADABLE_TYPES = listOf(
    "application/epub+zip",
    "application/pdf",
    "text/plain",
    "application/x-fictionbook+xml",
    "application/x-cbz"
)

/**
 * Pure-Kotlin OPDS 1.x Atom feed parser using [XmlPullParser], in keeping
 * with ADR-001 (no external parsing libraries). Returns null on any error —
 * same boundary contract as the EPUB/FB2 parsers.
 */
class OpdsParser {

    fun parse(stream: InputStream): OpdsFeed? = runCatching {
        val parser = XmlPullParserFactory.newInstance()
            .also { it.isNamespaceAware = false }
            .newPullParser()
            .also { it.setInput(stream, null) }

        var feedTitle = ""
        val entries = mutableListOf<OpdsEntry>()

        var inEntry = false
        var entryTitle = ""
        var entryAuthor: String? = null
        var entrySummary: String? = null
        var navigationHref: String? = null
        var acquisitionHref: String? = null
        var acquisitionType: String? = null
        var inAuthor = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfterLast(":")) {
                    "entry" -> {
                        inEntry = true
                        entryTitle = ""
                        entryAuthor = null
                        entrySummary = null
                        navigationHref = null
                        acquisitionHref = null
                        acquisitionType = null
                    }
                    "title" -> {
                        val text = parser.nextTextSafe()
                        if (inEntry) entryTitle = text else if (feedTitle.isEmpty()) feedTitle = text
                    }
                    "author" -> if (inEntry) inAuthor = true
                    "name" -> if (inEntry && inAuthor) entryAuthor = parser.nextTextSafe()
                    "summary", "content" -> if (inEntry && entrySummary.isNullOrBlank()) {
                        entrySummary = parser.nextTextSafe().takeIf { it.isNotBlank() }
                    }
                    "link" -> if (inEntry) {
                        val rel = parser.getAttributeValue(null, "rel").orEmpty()
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        val href = parser.getAttributeValue(null, "href").orEmpty()
                        if (href.isNotBlank()) {
                            val isAcquisition = rel.startsWith(REL_ACQUISITION_PREFIX) ||
                                DOWNLOADABLE_TYPES.any { type.startsWith(it) }
                            val isNavigation = type.contains("application/atom+xml")
                            when {
                                // Prefer EPUB over other formats when several links exist
                                isAcquisition && (acquisitionHref == null || type.startsWith("application/epub+zip")) -> {
                                    acquisitionHref = href
                                    acquisitionType = type.substringBefore(";").ifBlank { null }
                                }
                                isNavigation && navigationHref == null -> navigationHref = href
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name.substringAfterLast(":")) {
                    "author" -> inAuthor = false
                    "entry" -> {
                        inEntry = false
                        if (entryTitle.isNotBlank()) {
                            entries.add(
                                OpdsEntry(
                                    title = entryTitle,
                                    author = entryAuthor,
                                    summary = entrySummary,
                                    navigationHref = navigationHref,
                                    acquisitionHref = acquisitionHref,
                                    acquisitionType = acquisitionType
                                )
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }
        OpdsFeed(title = feedTitle, entries = entries)
    }.getOrNull()

    /** nextText() throws on nested markup (e.g. XHTML summaries); degrade to empty. */
    private fun XmlPullParser.nextTextSafe(): String = runCatching { nextText().trim() }.getOrDefault("")
}
