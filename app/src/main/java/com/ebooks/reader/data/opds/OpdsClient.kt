package com.ebooks.reader.data.opds

import com.ebooks.reader.data.net.MAX_BOOK_BYTES
import com.ebooks.reader.data.net.copyWithLimit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private const val USER_AGENT = "EbookReader"
private const val ACCEPT_OPDS =
    "application/atom+xml;profile=opds-catalog, application/atom+xml, application/xml, */*"

/** Supported book extensions, and the MIME types that map to each. */
private val MIME_TO_EXTENSION = mapOf(
    "application/epub+zip" to "epub",
    "application/pdf" to "pdf",
    "text/plain" to "txt",
    "application/x-fictionbook+xml" to "fb2",
    "application/x-fictionbook" to "fb2",
    "application/x-cbz" to "cbz",
    "application/vnd.comicbook+zip" to "cbz",
)
private val SUPPORTED_EXTENSIONS = setOf("epub", "pdf", "txt", "fb2", "cbz")

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

    /**
     * Downloads a publication to [destDir]; returns the written file.
     * [mimeType] is the acquisition type advertised by the OPDS entry — the
     * most reliable source of the real format (download URLs like
     * `.../2701.epub.images` have no usable file extension of their own).
     */
    fun download(url: String, destDir: File, fallbackName: String, mimeType: String? = null): File {
        val connection = open(url)
        try {
            val name = resolveFileName(connection, fallbackName, mimeType)
            val dest = File(destDir.also { it.mkdirs() }, name)
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(dest).use { output -> copyWithLimit(input, output, MAX_BOOK_BYTES) }
                }
            } catch (e: IOException) {
                dest.delete() // don't leave a partial/oversized file behind
                throw e
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
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", ACCEPT_OPDS)
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP $code")
        }
        return connection
    }

    private fun extensionForMime(mime: String?): String? =
        MIME_TO_EXTENSION[mime?.substringBefore(";")?.trim()?.lowercase()]

    /**
     * Chooses a filename whose extension reflects the real format. The extension
     * is taken from the advertised MIME type (or the response's Content-Type),
     * NOT from the URL path — OPDS acquisition URLs frequently carry pseudo
     * suffixes like `.epub.images` that would otherwise be read as the extension.
     */
    private fun resolveFileName(connection: HttpURLConnection, fallbackName: String, mimeType: String?): String {
        val ext = extensionForMime(mimeType) ?: extensionForMime(connection.contentType)
        val disposition = connection.getHeaderField("Content-Disposition").orEmpty()
        val dispositionName = Regex("filename\\*?=\"?([^\";]+)\"?")
            .find(disposition)?.groupValues?.get(1)
            ?.let { sanitize(it) }

        // Trust a server-provided filename only when it already ends in a format we support.
        if (dispositionName != null && dispositionName.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS) {
            return dispositionName
        }

        val base = (dispositionName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: sanitize(fallbackName).ifBlank { "book" })
        // Default to epub when the type is unknown — the common OPDS case.
        return "$base.${ext ?: "epub"}"
    }

    private fun sanitize(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim()
}
