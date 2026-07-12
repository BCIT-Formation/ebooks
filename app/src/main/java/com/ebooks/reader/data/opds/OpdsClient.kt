package com.ebooks.reader.data.opds

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private const val USER_AGENT = "EbookReader"

/**
 * Minimal HTTPS client for OPDS catalogs (ADR-006: user-initiated only,
 * encrypted transport only). Uses [HttpURLConnection] — no HTTP library.
 */
class OpdsClient {

    private val parser = OpdsParser()

    /** Resolves a possibly relative OPDS link against the feed it came from. */
    fun resolve(baseUrl: String, href: String): String = URL(URL(baseUrl), href).toString()

    /** Fetches and parses a catalog feed. Throws [IOException] on network/parse failure. */
    fun fetchFeed(url: String): OpdsFeed {
        val connection = open(url)
        try {
            connection.inputStream.use { stream ->
                return parser.parse(stream) ?: throw IOException("Not a valid OPDS feed")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads a publication to [destDir]; returns the written file. */
    fun download(url: String, destDir: File, fallbackName: String): File {
        val connection = open(url)
        try {
            val name = fileNameFrom(connection, url, fallbackName)
            val dest = File(destDir.also { it.mkdirs() }, name)
            connection.inputStream.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            return dest
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://", ignoreCase = true)) {
            "Only https:// URLs are allowed (ADR-006)"
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP $code")
        }
        return connection
    }

    /** Picks a file name from Content-Disposition, then the URL path, then the entry title. */
    private fun fileNameFrom(connection: HttpURLConnection, url: String, fallbackName: String): String {
        val disposition = connection.getHeaderField("Content-Disposition").orEmpty()
        val fromHeader = Regex("filename=\"?([^\";]+)\"?").find(disposition)?.groupValues?.get(1)
        val fromUrl = URL(url).path.substringAfterLast("/").takeIf { it.contains(".") }
        val raw = fromHeader ?: fromUrl ?: fallbackName
        var name = raw.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifBlank { "book" }
        if (!name.contains(".")) {
            // No extension anywhere — infer from the response MIME type
            val mime = connection.contentType.orEmpty().substringBefore(";")
            name += when (mime) {
                "application/pdf" -> ".pdf"
                "text/plain" -> ".txt"
                "application/x-fictionbook+xml" -> ".fb2"
                "application/x-cbz" -> ".cbz"
                else -> ".epub"
            }
        }
        return name
    }
}
