package com.ebooks.reader.data.sync

import com.ebooks.reader.data.net.MAX_BOOK_BYTES
import com.ebooks.reader.data.net.MAX_SNAPSHOT_BYTES
import com.ebooks.reader.data.net.copyWithLimit
import com.ebooks.reader.data.net.readTextLimited
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000

/** Default SMB-over-TCP port. */
const val DEFAULT_SMB_PORT = 445

data class SmbEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long
)

/** Host/port/path parsed from an `smb://` URL. The path starts with the share name. */
data class SmbEndpoint(val host: String, val port: Int, val path: String)

/**
 * Parses an `smb://host[:port]/share[/path]` URL. Returns null for any other
 * scheme, a missing host, an invalid port, or a missing share segment (the
 * SMB protocol addresses files inside a named share, so `smb://host/` alone
 * cannot be browsed for books).
 */
fun parseSmbUrl(url: String): SmbEndpoint? {
    val trimmed = url.trim()
    if (!trimmed.startsWith("smb://", ignoreCase = true)) return null
    val rest = trimmed.substring("smb://".length)
    val hostPort = rest.substringBefore('/')
    val host = hostPort.substringBefore(':')
    if (host.isBlank()) return null
    val port = if (':' in hostPort) {
        hostPort.substringAfter(':').toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
    } else {
        DEFAULT_SMB_PORT
    }
    val path = rest.substringAfter('/', "").trim('/')
    if (path.isBlank()) return null // share name required
    return SmbEndpoint(host, port, "/$path")
}

/**
 * Minimal SMB2/3 client for Windows / NAS / macOS network shares
 * (ADR-006 / ADR-010, jcifs-ng). SMB1 is disabled (`minVersion=SMB202`), so
 * connections are refused rather than silently downgraded to the legacy
 * protocol; message signing and encryption follow the SMB dialect negotiation.
 * Mirrors [WebDavClient] / [FtpsClient]'s contract: directory listing, book
 * download, and progress-snapshot text up/download. Each operation uses a
 * fresh, short-lived connection, user-initiated only.
 *
 * The username may carry a Windows domain as `DOMAIN\user`; a blank username
 * connects anonymously (guest shares).
 */
class SmbClient(
    url: String,
    username: String,
    private val password: String
) {
    private val endpoint = requireNotNull(parseSmbUrl(url)) {
        "Only smb://host/share URLs are allowed (ADR-006)"
    }
    private val domain: String?
    private val user: String

    init {
        val parts = username.trim().split('\\', limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank()) {
            domain = parts[0]
            user = parts[1]
        } else {
            domain = null
            user = username.trim()
        }
    }

    /** jcifs URL of the configured directory — jcifs requires the trailing slash. */
    private val directoryUrl: String =
        "smb://${endpoint.host}:${endpoint.port}${endpoint.path}/"

    /** Lists the configured directory. Throws [IOException] on failure. */
    fun listFiles(): List<SmbEntry> = withContext { ctx ->
        SmbFile(directoryUrl, ctx).use { dir ->
            dir.listFiles().map { entry ->
                entry.use {
                    SmbEntry(
                        name = it.name.trimEnd('/'),
                        isDirectory = it.isDirectory,
                        size = if (it.isDirectory) 0L else it.length()
                    )
                }
            }
        }
    }

    /** Downloads [name] from the configured directory into [destDir]. */
    fun download(name: String, destDir: File): File = withContext { ctx ->
        val dest = File(destDir.also { it.mkdirs() }, safeName(name))
        try {
            SmbFile(remoteUrl(name), ctx).use { remote ->
                remote.inputStream.use { input ->
                    FileOutputStream(dest).use { output -> copyWithLimit(input, output, MAX_BOOK_BYTES) }
                }
            }
        } catch (e: IOException) {
            dest.delete()
            throw e
        }
        dest
    }

    /** Reads a text file in the configured directory; null when it does not exist. */
    fun downloadText(fileName: String): String? = withContext { ctx ->
        SmbFile(remoteUrl(fileName), ctx).use { remote ->
            if (!remote.exists()) return@withContext null
            remote.inputStream.use { it.readTextLimited(MAX_SNAPSHOT_BYTES) }
        }
    }

    /** Writes a file into the configured directory (creates or overwrites). */
    fun uploadText(fileName: String, content: String): Unit = withContext { ctx ->
        SmbFile(remoteUrl(fileName), ctx).use { remote ->
            remote.outputStream.use { it.write(content.toByteArray()) }
        }
    }

    private fun <T> withContext(block: (CIFSContext) -> T): T {
        val properties = Properties().apply {
            // SMB2 or newer only — never negotiate down to the insecure SMB1.
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.connTimeout", CONNECT_TIMEOUT_MS.toString())
            setProperty("jcifs.smb.client.responseTimeout", READ_TIMEOUT_MS.toString())
        }
        val base = BaseContext(PropertyConfiguration(properties))
        val ctx = if (user.isBlank()) {
            base.withAnonymousCredentials()
        } else {
            base.withCredentials(NtlmPasswordAuthenticator(domain, user, password))
        }
        try {
            return block(ctx)
        } finally {
            // Releases the transport/session pools held by this context.
            runCatching { ctx.close() }
        }
    }

    private fun remoteUrl(name: String): String = directoryUrl + name.trimStart('/')

    private fun safeName(name: String): String =
        name.substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .ifBlank { "file" }
}
