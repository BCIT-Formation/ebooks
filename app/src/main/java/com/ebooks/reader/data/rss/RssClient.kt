package com.ebooks.reader.data.rss

import com.ebooks.reader.data.net.readTextLimited
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
// Feeds can be larger than a progress snapshot but should still be bounded.
private const val MAX_FEED_BYTES = 16L * 1024 * 1024

/**
 * Fetches and parses RSS/Atom feeds over HTTPS (ADR-006: user-initiated,
 * encrypted transport only). An `http://` URL is transparently upgraded to
 * `https://` since cleartext is forbidden app-wide.
 */
class RssClient {

    private val parser = RssParser()

    fun fetch(url: String): ParsedFeed {
        val httpsUrl = normalizeToHttps(url)
        val connection = open(httpsUrl)
        try {
            val xml = connection.inputStream.use { it.readTextLimited(MAX_FEED_BYTES) }
            return parser.parse(ByteArrayInputStream(xml.toByteArray()))
                ?: throw IOException("Not a valid RSS or Atom feed")
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeToHttps(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
            else -> "https://$trimmed"
        }
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "EbookReader")
        connection.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP $code")
        }
        return connection
    }
}
