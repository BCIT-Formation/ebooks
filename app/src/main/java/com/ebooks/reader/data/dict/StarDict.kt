package com.ebooks.reader.data.dict

import com.ebooks.reader.util.htmlToPlainText
import java.io.File
import java.io.RandomAccessFile

/**
 * Pure-Kotlin StarDict dictionary support (ADR-001 style: no external parsing
 * libraries, unit-testable on the JVM). A StarDict dictionary is three files
 * sharing a base name:
 *
 *  - `.ifo`  — plain-text metadata (bookname, wordcount, sametypesequence, …)
 *  - `.idx`  — sorted index: UTF-8 word + '\0' + 32-bit BE offset + 32-bit BE size
 *  - `.dict` — definition data addressed by the index entries
 *
 * Compressed variants (`.idx.gz`, `.dict.dz` — dictzip is gzip-compatible)
 * are inflated at import time by [StarDictManager], so this layer only ever
 * sees the plain files. 64-bit offset indexes (`idxoffsetbits=64`) are rare
 * and rejected at parse time.
 */

private const val IFO_MAGIC = "StarDict's dict ifo file"

/** Parsed `.ifo` metadata. */
data class StarDictInfo(
    val bookName: String,
    val wordCount: Int,
    val sameTypeSequence: String?
) {
    companion object {
        fun parse(text: String): StarDictInfo? {
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.firstOrNull() != IFO_MAGIC) return null
            val values = lines.drop(1).mapNotNull { line ->
                val eq = line.indexOf('=')
                if (eq <= 0) null else line.substring(0, eq).trim() to line.substring(eq + 1).trim()
            }.toMap()
            if ((values["idxoffsetbits"] ?: "32") != "32") return null
            return StarDictInfo(
                bookName = values["bookname"]?.takeIf { it.isNotBlank() } ?: return null,
                wordCount = values["wordcount"]?.toIntOrNull() ?: return null,
                sameTypeSequence = values["sametypesequence"]?.takeIf { it.isNotBlank() }
            )
        }
    }
}

/** One `.idx` record: where the word's definition lives inside the `.dict` file. */
data class StarDictIndexEntry(val word: String, val offset: Long, val size: Int)

object StarDictIndex {

    /** Parses a plain (already inflated) `.idx` file. Returns null on malformed data. */
    fun parse(bytes: ByteArray): List<StarDictIndexEntry>? {
        val entries = mutableListOf<StarDictIndexEntry>()
        var i = 0
        while (i < bytes.size) {
            val start = i
            while (i < bytes.size && bytes[i] != 0.toByte()) i++
            if (i >= bytes.size) return null // word missing its terminator
            val word = String(bytes, start, i - start, Charsets.UTF_8)
            i++ // skip '\0'
            if (i + 8 > bytes.size) return null // truncated offset/size
            val offset = readUInt32(bytes, i)
            val size = readUInt32(bytes, i + 4)
            i += 8
            if (word.isNotEmpty()) entries += StarDictIndexEntry(word, offset, size.toInt())
        }
        return entries
    }

    private fun readUInt32(bytes: ByteArray, at: Int): Long =
        ((bytes[at].toLong() and 0xFF) shl 24) or
            ((bytes[at + 1].toLong() and 0xFF) shl 16) or
            ((bytes[at + 2].toLong() and 0xFF) shl 8) or
            (bytes[at + 3].toLong() and 0xFF)

    private fun asciiCaseCompare(a: String, b: String): Int {
        val n = minOf(a.length, b.length)
        for (i in 0 until n) {
            val ca = asciiLower(a[i])
            val cb = asciiLower(b[i])
            if (ca != cb) return ca - cb
        }
        return a.length - b.length
    }

    private fun asciiLower(c: Char): Int = if (c in 'A'..'Z') c.code + 32 else c.code

    /**
     * Case-insensitive binary search. StarDict indexes are sorted with
     * `g_ascii_strcasecmp` (only A-Z folded, ties broken by exact compare),
     * so an ASCII-folded probe lands inside the run of case variants; an
     * exact-case match among those neighbours is preferred when present.
     */
    fun find(entries: List<StarDictIndexEntry>, word: String): StarDictIndexEntry? {
        var lo = 0
        var hi = entries.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = asciiCaseCompare(entries[mid].word, word)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> {
                    var first = mid
                    while (first > 0 && asciiCaseCompare(entries[first - 1].word, word) == 0) first--
                    var i = first
                    while (i < entries.size && asciiCaseCompare(entries[i].word, word) == 0) {
                        if (entries[i].word == word) return entries[i]
                        i++
                    }
                    return entries[first]
                }
            }
        }
        return null
    }
}

/**
 * Renders one `.dict` record to display text, honouring `sametypesequence`.
 * Text-ish field types are kept ('m', 'l', 't', 'y', 'k', 'w', 'n');
 * markup fields ('h' HTML, 'x' XDXF, 'g' Pango) are flattened with
 * [htmlToPlainText]; media fields ('W', 'P', 'X') are skipped.
 */
fun extractStarDictDefinition(data: ByteArray, sameTypeSequence: String?): String {
    val parts = mutableListOf<String>()

    fun renderField(type: Char, start: Int, end: Int) {
        if (end <= start) return
        val text = String(data, start, end - start, Charsets.UTF_8)
        when (type) {
            'h', 'x', 'g' -> parts += htmlToPlainText(text)
            'W', 'P', 'X' -> Unit // media/resource fields — nothing to show
            else -> parts += text
        }
    }

    var i = 0
    if (sameTypeSequence != null) {
        // Type bytes are omitted from the record; the last field also omits
        // its terminator/size and simply runs to the end of the record.
        for ((fieldIndex, type) in sameTypeSequence.withIndex()) {
            if (i >= data.size) break
            val isLast = fieldIndex == sameTypeSequence.length - 1
            if (isLast) {
                renderField(type, i, data.size)
                i = data.size
            } else if (type.isUpperCase()) {
                if (i + 4 > data.size) break
                val size = readSize(data, i)
                val start = i + 4
                val end = (start + size).coerceAtMost(data.size)
                renderField(type, start, end)
                i = end
            } else {
                var z = i
                while (z < data.size && data[z] != 0.toByte()) z++
                renderField(type, i, z)
                i = z + 1
            }
        }
    } else {
        // Each field carries its own leading type byte.
        while (i < data.size) {
            val type = data[i].toInt().toChar()
            i++
            if (type.isUpperCase()) {
                if (i + 4 > data.size) break
                val size = readSize(data, i)
                i += 4
                val end = (i + size).coerceAtMost(data.size)
                renderField(type, i, end)
                i = end
            } else {
                var z = i
                while (z < data.size && data[z] != 0.toByte()) z++
                renderField(type, i, z)
                i = z + 1
            }
        }
    }
    return parts.filter { it.isNotBlank() }.joinToString("\n").trim()
}

private fun readSize(bytes: ByteArray, at: Int): Int =
    ((bytes[at].toInt() and 0xFF) shl 24) or
        ((bytes[at + 1].toInt() and 0xFF) shl 16) or
        ((bytes[at + 2].toInt() and 0xFF) shl 8) or
        (bytes[at + 3].toInt() and 0xFF)

/** An opened dictionary: parsed metadata + index, definitions read on demand. */
class StarDictDictionary(
    val info: StarDictInfo,
    private val entries: List<StarDictIndexEntry>,
    private val dictFile: File
) {
    fun lookup(word: String): String? {
        val entry = StarDictIndex.find(entries, word) ?: return null
        if (entry.size <= 0 || entry.size > MAX_DEFINITION_BYTES) return null
        val data = runCatching {
            RandomAccessFile(dictFile, "r").use { raf ->
                raf.seek(entry.offset)
                ByteArray(entry.size).also { raf.readFully(it) }
            }
        }.getOrNull() ?: return null
        return extractStarDictDefinition(data, info.sameTypeSequence).takeIf { it.isNotBlank() }
    }

    companion object {
        private const val MAX_DEFINITION_BYTES = 1024 * 1024

        /** Opens the trio of plain files; returns null when any of them is invalid. */
        fun open(ifoFile: File, idxFile: File, dictFile: File): StarDictDictionary? = runCatching {
            val info = StarDictInfo.parse(ifoFile.readText(Charsets.UTF_8)) ?: return null
            val entries = StarDictIndex.parse(idxFile.readBytes()) ?: return null
            if (entries.isEmpty() || !dictFile.isFile) return null
            StarDictDictionary(info, entries, dictFile)
        }.getOrNull()
    }
}
