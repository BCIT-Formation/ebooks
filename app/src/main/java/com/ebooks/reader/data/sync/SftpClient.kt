package com.ebooks.reader.data.sync

import com.ebooks.reader.data.net.MAX_BOOK_BYTES
import com.ebooks.reader.data.net.MAX_SNAPSHOT_BYTES
import com.ebooks.reader.data.net.copyWithLimit
import com.ebooks.reader.data.net.readTextLimited
import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SSHRuntimeException
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.PublicKey
import java.util.EnumSet

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000

/** Default SSH port. */
const val DEFAULT_SFTP_PORT = 22

data class SftpFile(
    val name: String,
    val isDirectory: Boolean,
    val size: Long
)

/** Host/port/path parsed from an `sftp://` URL. */
data class SftpEndpoint(val host: String, val port: Int, val path: String)

/**
 * Parses an `sftp://host[:port][/path]` URL. Returns null for any other
 * scheme, a missing host, or an invalid port.
 */
fun parseSftpUrl(url: String): SftpEndpoint? {
    val trimmed = url.trim()
    if (!trimmed.startsWith("sftp://", ignoreCase = true)) return null
    val rest = trimmed.substring("sftp://".length)
    val hostPort = rest.substringBefore('/')
    val host = hostPort.substringBefore(':')
    if (host.isBlank()) return null
    val port = if (':' in hostPort) {
        hostPort.substringAfter(':').toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
    } else {
        DEFAULT_SFTP_PORT
    }
    val path = "/" + rest.substringAfter('/', "").trim('/')
    return SftpEndpoint(host, port, path.trimEnd('/').ifEmpty { "/" })
}

/** Persists one host-key fingerprint per host:port for TOFU pinning. */
interface SftpHostKeyStore {
    fun knownFingerprint(host: String, port: Int): String?
    fun rememberFingerprint(host: String, port: Int, fingerprint: String)
}

/**
 * Trust-on-first-use host-key verification: the first key a server presents
 * is remembered, and every later connection must present the same key. A
 * changed key (potential MITM) fails verification, which surfaces as the
 * sync-failed message.
 */
class TofuHostKeyVerifier(private val store: SftpHostKeyStore) : HostKeyVerifier {

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val fingerprint = SecurityUtils.getFingerprint(key)
        val known = store.knownFingerprint(hostname, port)
        return if (known == null) {
            store.rememberFingerprint(hostname, port, fingerprint)
            true
        } else {
            known == fingerprint
        }
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
}

/**
 * Minimal SFTP client (ADR-006 / ADR-009, sshj). Password auth with
 * trust-on-first-use host-key pinning. Mirrors [WebDavClient] / [FtpsClient]:
 * directory listing, book download, and progress-snapshot text up/download.
 * Each operation uses a fresh, short-lived connection, user-initiated only.
 */
class SftpClient(
    url: String,
    private val username: String,
    private val password: String,
    private val hostKeyStore: SftpHostKeyStore
) {
    private val endpoint = requireNotNull(parseSftpUrl(url)) {
        "Only sftp:// URLs are allowed (ADR-006)"
    }

    /** Lists the configured directory. Throws [IOException] on failure. */
    fun listFiles(): List<SftpFile> = withConnection { sftp ->
        sftp.ls(endpoint.path).map { SftpFile(it.name, it.isDirectory, it.attributes.size) }
    }

    /** Downloads [name] from the configured directory into [destDir]. */
    fun download(name: String, destDir: File): File = withConnection { sftp ->
        val dest = File(destDir.also { it.mkdirs() }, safeName(name))
        try {
            sftp.open(remotePath(name)).use { remote ->
                remote.RemoteFileInputStream().use { input ->
                    FileOutputStream(dest).use { output ->
                        copyWithLimit(input, output, MAX_BOOK_BYTES)
                    }
                }
            }
        } catch (e: IOException) {
            dest.delete()
            throw e
        }
        dest
    }

    /** Reads a text file in the configured directory; null when it does not exist. */
    fun downloadText(fileName: String): String? = withConnection { sftp ->
        try {
            sftp.open(remotePath(fileName)).use { remote ->
                remote.RemoteFileInputStream().use { it.readTextLimited(MAX_SNAPSHOT_BYTES) }
            }
        } catch (e: SFTPException) {
            if (e.statusCode == Response.StatusCode.NO_SUCH_FILE) null else throw e
        }
    }

    /** Writes a file into the configured directory (creates or overwrites). */
    fun uploadText(fileName: String, content: String): Unit = withConnection { sftp ->
        val bytes = content.toByteArray()
        sftp.open(
            remotePath(fileName),
            EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
        ).use { remote ->
            remote.write(0, bytes, 0, bytes.size)
        }
    }

    private fun <T> withConnection(block: (SFTPClient) -> T): T {
        val ssh = SSHClient(AndroidConfig())
        ssh.addHostKeyVerifier(TofuHostKeyVerifier(hostKeyStore))
        ssh.connectTimeout = CONNECT_TIMEOUT_MS
        ssh.timeout = READ_TIMEOUT_MS
        try {
            ssh.connect(endpoint.host, endpoint.port)
            ssh.authPassword(username, password)
            ssh.newSFTPClient().use { sftp ->
                return block(sftp)
            }
        } catch (e: SSHRuntimeException) {
            // Surface sshj's crypto/runtime failures like any other IO error.
            throw IOException(e.message ?: "SFTP failure", e)
        } finally {
            runCatching { ssh.disconnect() }
        }
    }

    private fun remotePath(name: String): String =
        endpoint.path.trimEnd('/') + "/" + name.trimStart('/')

    private fun safeName(name: String): String =
        name.substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .ifBlank { "file" }
}
