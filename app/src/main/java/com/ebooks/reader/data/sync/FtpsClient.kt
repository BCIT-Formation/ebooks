package com.ebooks.reader.data.sync

import com.ebooks.reader.data.net.MAX_BOOK_BYTES
import com.ebooks.reader.data.net.MAX_SNAPSHOT_BYTES
import com.ebooks.reader.data.net.copyWithLimit
import com.ebooks.reader.data.net.readTextLimited
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPReply
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000

/** Default port of explicit FTPS (FTP with AUTH TLS upgrade). */
const val DEFAULT_FTPS_PORT = 21

data class FtpsFile(
    val name: String,
    val isDirectory: Boolean,
    val size: Long
)

/** Host/port/path parsed from an `ftps://` URL. */
data class FtpsEndpoint(val host: String, val port: Int, val path: String)

/**
 * Parses an `ftps://host[:port][/path]` URL. Returns null for any other
 * scheme, in particular plain `ftp://`, which stays banned (ADR-006:
 * encrypted transports only), or for a missing host / invalid port.
 */
fun parseFtpsUrl(url: String): FtpsEndpoint? {
    val trimmed = url.trim()
    if (!trimmed.startsWith("ftps://", ignoreCase = true)) return null
    val rest = trimmed.substring("ftps://".length)
    val hostPort = rest.substringBefore('/')
    val host = hostPort.substringBefore(':')
    if (host.isBlank()) return null
    val port = if (':' in hostPort) {
        hostPort.substringAfter(':').toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
    } else {
        DEFAULT_FTPS_PORT
    }
    val path = "/" + rest.substringAfter('/', "").trim('/')
    return FtpsEndpoint(host, port, path.trimEnd('/').ifEmpty { "/" })
}

/**
 * Minimal FTPS client (ADR-006 / ADR-008). Explicit TLS (`AUTH TLS`) with the
 * data channel forced to encrypted (`PROT P`), so neither credentials nor file
 * contents ever travel in cleartext. Mirrors [WebDavClient]'s contract:
 * directory listing, book download, and progress-snapshot text up/download.
 * Each operation uses a fresh, short-lived connection, user-initiated only.
 */
class FtpsClient(
    url: String,
    private val username: String,
    private val password: String
) {
    private val endpoint = requireNotNull(parseFtpsUrl(url)) {
        "Only ftps:// URLs are allowed (ADR-006)"
    }

    /** Lists the configured directory. Throws [IOException] on failure. */
    fun listFiles(): List<FtpsFile> = withConnection { ftp ->
        ftp.listFiles(endpoint.path)
            .filterNotNull()
            .map { FtpsFile(it.name, it.isDirectory, it.size) }
    }

    /** Downloads [name] from the configured directory into [destDir]. */
    fun download(name: String, destDir: File): File = withConnection { ftp ->
        val dest = File(destDir.also { it.mkdirs() }, safeName(name))
        try {
            val input = ftp.retrieveFileStream(remotePath(name))
                ?: throw IOException("FTPS download failed (${ftp.replyCode})")
            input.use { stream ->
                FileOutputStream(dest).use { output -> copyWithLimit(stream, output, MAX_BOOK_BYTES) }
            }
            if (!ftp.completePendingCommand()) {
                throw IOException("FTPS download failed (${ftp.replyCode})")
            }
        } catch (e: IOException) {
            dest.delete()
            throw e
        }
        dest
    }

    /** Reads a text file in the configured directory; null when it does not exist. */
    fun downloadText(fileName: String): String? = withConnection { ftp ->
        val input = ftp.retrieveFileStream(remotePath(fileName))
            ?: return@withConnection null
        val text = input.use { it.readTextLimited(MAX_SNAPSHOT_BYTES) }
        if (!ftp.completePendingCommand()) return@withConnection null
        text
    }

    /** Writes a file into the configured directory (creates or overwrites). */
    fun uploadText(fileName: String, content: String): Unit = withConnection { ftp ->
        val stored = ByteArrayInputStream(content.toByteArray()).use { stream ->
            ftp.storeFile(remotePath(fileName), stream)
        }
        if (!stored) throw IOException("FTPS upload failed (${ftp.replyCode})")
    }

    private fun <T> withConnection(block: (org.apache.commons.net.ftp.FTPSClient) -> T): T {
        // Explicit mode: connect on the control port, then upgrade via AUTH TLS.
        val ftp = org.apache.commons.net.ftp.FTPSClient(false)
        ftp.connectTimeout = CONNECT_TIMEOUT_MS
        ftp.defaultTimeout = READ_TIMEOUT_MS
        try {
            ftp.connect(endpoint.host, endpoint.port)
            ftp.soTimeout = READ_TIMEOUT_MS
            if (!FTPReply.isPositiveCompletion(ftp.replyCode)) {
                throw IOException("FTPS connection refused (${ftp.replyCode})")
            }
            if (!ftp.login(username, password)) {
                throw IOException("FTPS login failed (${ftp.replyCode})")
            }
            // Encrypt the data channel as well as the control channel.
            ftp.execPBSZ(0)
            ftp.execPROT("P")
            ftp.setFileType(FTP.BINARY_FILE_TYPE)
            ftp.enterLocalPassiveMode()
            return block(ftp)
        } finally {
            runCatching {
                if (ftp.isConnected) {
                    ftp.logout()
                    ftp.disconnect()
                }
            }
        }
    }

    private fun remotePath(name: String): String =
        endpoint.path.trimEnd('/') + "/" + name.trimStart('/')

    private fun safeName(name: String): String =
        name.substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .ifBlank { "file" }
}
