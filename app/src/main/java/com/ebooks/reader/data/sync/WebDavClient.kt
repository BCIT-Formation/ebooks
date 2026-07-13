package com.ebooks.reader.data.sync

import android.util.Base64
import com.ebooks.reader.data.net.MAX_BOOK_BYTES
import com.ebooks.reader.data.net.MAX_SNAPSHOT_BYTES
import com.ebooks.reader.data.net.copyWithLimit
import com.ebooks.reader.data.net.readTextLimited
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000

data class WebDavFile(
    val href: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long
)

/**
 * Minimal WebDAV client over HTTPS with Basic auth (ADR-006). Supports the
 * three operations the sync feature needs: directory listing (PROPFIND),
 * download (GET) and upload (PUT). Pure [HttpURLConnection] + [XmlPullParser].
 */
class WebDavClient(
    baseUrl: String,
    private val username: String,
    private val password: String
) {
    private val baseUrl = baseUrl.trimEnd('/') + "/"

    init {
        require(this.baseUrl.startsWith("https://", ignoreCase = true)) {
            "Only https:// URLs are allowed (ADR-006)"
        }
    }

    /** Lists the base directory (Depth: 1). Throws [IOException] on failure. */
    fun listFiles(): List<WebDavFile> {
        val connection = open(baseUrl, "PROPFIND")
        connection.setRequestProperty("Depth", "1")
        connection.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
        connection.doOutput = true
        connection.outputStream.use {
            it.write(
                """<?xml version="1.0" encoding="utf-8"?>
                   <d:propfind xmlns:d="DAV:">
                     <d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/></d:prop>
                   </d:propfind>""".trimIndent().toByteArray()
            )
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            connection.inputStream.use { stream ->
                return parseMultistatus(stream)
                    // The first response is the directory itself — drop it
                    .filterNot { URL(URL(baseUrl), it.href).path.trimEnd('/') == URL(baseUrl).path.trimEnd('/') }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads [href] (absolute or relative to the base URL) into [destDir]. */
    fun download(href: String, destDir: File): File {
        val url = URL(URL(baseUrl), href).toString()
        val connection = open(url, "GET")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val name = decodedName(href)
            val dest = File(destDir.also { it.mkdirs() }, name)
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(dest).use { output -> copyWithLimit(input, output, MAX_BOOK_BYTES) }
                }
            } catch (e: IOException) {
                dest.delete()
                throw e
            }
            return dest
        } finally {
            connection.disconnect()
        }
    }

    /** Reads a text file in the base directory; null when it does not exist (404). */
    fun downloadText(fileName: String): String? {
        val connection = open(baseUrl + fileName, "GET")
        try {
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> null
                in 200..299 -> connection.inputStream.use { it.readTextLimited(MAX_SNAPSHOT_BYTES) }
                else -> throw IOException("HTTP $code")
            }
        } catch (_: FileNotFoundException) {
            return null
        } finally {
            connection.disconnect()
        }
    }

    /** Writes a file into the base directory (creates or overwrites). */
    fun uploadText(fileName: String, content: String) {
        val connection = open(baseUrl + fileName, "PUT")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.doOutput = true
        try {
            connection.outputStream.use { it.write(content.toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "EbookReader")
        if (username.isNotBlank()) {
            val token = Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
            connection.setRequestProperty("Authorization", "Basic $token")
        }
        return connection
    }

    private fun decodedName(href: String): String {
        val raw = href.trimEnd('/').substringAfterLast("/")
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        return decoded.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "file" }
    }

    private fun parseMultistatus(stream: InputStream): List<WebDavFile> {
        val parser = XmlPullParserFactory.newInstance()
            .also { it.isNamespaceAware = false }
            .newPullParser()
            .also { it.setInput(stream, null) }

        val files = mutableListOf<WebDavFile>()
        var href = ""
        var name = ""
        var isDirectory = false
        var size = 0L

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfterLast(":") ?: ""
            when (event) {
                XmlPullParser.START_TAG -> when (tag) {
                    "response" -> { href = ""; name = ""; isDirectory = false; size = 0L }
                    "href" -> href = runCatching { parser.nextText().trim() }.getOrDefault("")
                    "displayname" -> name = runCatching { parser.nextText().trim() }.getOrDefault("")
                    "collection" -> isDirectory = true
                    "getcontentlength" -> size = runCatching { parser.nextText().trim().toLong() }.getOrDefault(0L)
                }
                XmlPullParser.END_TAG -> if (tag == "response" && href.isNotBlank()) {
                    files.add(
                        WebDavFile(
                            href = href,
                            name = name.ifBlank { decodedName(href) },
                            isDirectory = isDirectory,
                            size = size
                        )
                    )
                }
            }
            event = parser.next()
        }
        return files
    }
}
