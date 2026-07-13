package com.ebooks.reader.data.net

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Hard cap on a downloaded book/comic file — guards against disk-fill (ADR-006). */
const val MAX_BOOK_BYTES: Long = 512L * 1024 * 1024 // 512 MB

/** Hard cap on small control payloads (progress snapshots, catalog feeds). */
const val MAX_SNAPSHOT_BYTES: Long = 8L * 1024 * 1024 // 8 MB

/**
 * Copies [input] to [output], aborting with an [IOException] once more than
 * [maxBytes] have been read. Returns the number of bytes copied. Prevents a
 * malicious or misconfigured server from exhausting device storage.
 */
fun copyWithLimit(input: InputStream, output: OutputStream, maxBytes: Long): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        total += read
        if (total > maxBytes) {
            throw IOException("Download exceeds the ${maxBytes / (1024 * 1024)} MB limit")
        }
        output.write(buffer, 0, read)
    }
    return total
}

/** Reads a capped text payload; throws [IOException] beyond [maxBytes]. */
fun InputStream.readTextLimited(maxBytes: Long): String {
    val output = java.io.ByteArrayOutputStream()
    copyWithLimit(this, output, maxBytes)
    return output.toString(Charsets.UTF_8.name())
}
