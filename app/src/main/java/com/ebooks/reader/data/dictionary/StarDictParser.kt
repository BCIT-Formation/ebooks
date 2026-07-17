package com.ebooks.reader.data.dictionary

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.min

/**
 * StarDict dictionary format parser (.ifo, .idx, .dict)
 * Supports reading offline dictionaries without network access
 */
class StarDictParser(private val context: Context) {

    /**
     * Load a StarDict dictionary from a URI (typically from file picker)
     * Returns null if the dictionary is invalid or cannot be loaded
     */
    suspend fun loadDictionary(baseUri: Uri): StarDictDictionary? {
        return try {
            val baseName = baseUri.path?.substringBeforeLast(".") ?: return null
            val ifoUri = Uri.parse("$baseUri.ifo")
            val idxUri = Uri.parse("$baseUri.idx")
            val dictUri = Uri.parse("$baseUri.dict")

            val ifoData = readFileAsString(ifoUri) ?: return null
            val indexEntries = parseIndexFile(idxUri) ?: return null

            StarDictDictionary(
                name = parseIfoField(ifoData, "BookName") ?: "Dictionary",
                author = parseIfoField(ifoData, "Author") ?: "Unknown",
                version = parseIfoField(ifoData, "Version") ?: "1.0",
                indexEntries = indexEntries,
                dictUri = dictUri,
                context = context
            )
        } catch (e: Exception) {
            android.util.Log.e("StarDictParser", "Error loading dictionary", e)
            null
        }
    }

    /**
     * Search for a word in the dictionary
     * Returns the definition or null if not found
     */
    suspend fun searchWord(
        dictionary: StarDictDictionary,
        word: String
    ): String? {
        val normalized = word.lowercase().trim()

        // Binary search in index
        val entry = dictionary.indexEntries.binarySearch { indexEntry ->
            indexEntry.word.compareTo(normalized)
        }

        if (entry < 0) return null

        val indexEntry = dictionary.indexEntries[entry]
        return readDefinition(dictionary.dictUri, indexEntry.offset, indexEntry.size)
    }

    private fun parseIndexFile(uri: Uri): List<IndexEntry>? {
        return try {
            val entries = mutableListOf<IndexEntry>()
            val content = readFileAsBytes(uri) ?: return null

            var offset = 0
            while (offset < content.size) {
                // Read null-terminated word
                var endIdx = offset
                while (endIdx < content.size && content[endIdx] != 0.toByte()) {
                    endIdx++
                }

                if (endIdx >= content.size) break

                val word = String(content, offset, endIdx - offset).lowercase()
                offset = endIdx + 1

                // Read offset (4 bytes, big-endian)
                if (offset + 4 > content.size) break
                val dictOffset = content.sliceArray(offset until offset + 4).toUInt()
                offset += 4

                // Read size (4 bytes, big-endian)
                if (offset + 4 > content.size) break
                val size = content.sliceArray(offset until offset + 4).toUInt()
                offset += 4

                entries.add(IndexEntry(word, dictOffset.toInt(), size.toInt()))
            }

            entries
        } catch (e: Exception) {
            android.util.Log.e("StarDictParser", "Error parsing index", e)
            null
        }
    }

    private fun readDefinition(uri: Uri, offset: Int, size: Int): String? {
        return try {
            val data = readFileAsBytes(uri) ?: return null

            if (offset + size > data.size) return null

            val definition = data.sliceArray(offset until offset + size)

            // StarDict definitions can be gzip-compressed
            if (isGzipped(definition)) {
                val input = GZIPInputStream(definition.inputStream())
                input.readBytes().decodeToString()
            } else {
                definition.decodeToString()
            }
        } catch (e: Exception) {
            android.util.Log.e("StarDictParser", "Error reading definition", e)
            null
        }
    }

    private fun readFileAsString(uri: Uri): String? {
        return readFileAsBytes(uri)?.decodeToString()
    }

    private fun readFileAsBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedInputStream(stream).readBytes()
            }
        } catch (e: Exception) {
            android.util.Log.e("StarDictParser", "Error reading file", e)
            null
        }
    }

    private fun parseIfoField(content: String, field: String): String? {
        val regex = Regex("$field=([^\n]+)")
        return regex.find(content)?.groupValues?.get(1)?.trim()
    }

    private fun isGzipped(data: ByteArray): Boolean {
        return data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()
    }
}

data class IndexEntry(
    val word: String,
    val offset: Int,
    val size: Int
)

data class StarDictDictionary(
    val name: String,
    val author: String,
    val version: String,
    val indexEntries: List<IndexEntry>,
    val dictUri: Uri,
    val context: Context
) {
    suspend fun lookup(word: String): String? {
        val normalized = word.lowercase().trim()
        val entry = indexEntries.binarySearch { it.word.compareTo(normalized) }

        if (entry < 0) return null

        val indexEntry = indexEntries[entry]
        return try {
            context.contentResolver.openInputStream(dictUri)?.use { stream ->
                val data = stream.readBytes()
                if (indexEntry.offset + indexEntry.size > data.size) return@use null

                val definition = data.sliceArray(
                    indexEntry.offset until indexEntry.offset + indexEntry.size
                )

                if (isGzipped(definition)) {
                    GZIPInputStream(definition.inputStream()).readBytes().decodeToString()
                } else {
                    definition.decodeToString()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StarDict", "Lookup error", e)
            null
        }
    }

    private fun isGzipped(data: ByteArray): Boolean {
        return data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()
    }
}

// Extension for ByteArray to big-endian conversion
private fun ByteArray.toUInt(): UInt {
    return ((this[0].toInt() and 0xFF) shl 24 or
            (this[1].toInt() and 0xFF) shl 16 or
            (this[2].toInt() and 0xFF) shl 8 or
            (this[3].toInt() and 0xFF)).toUInt()
}
