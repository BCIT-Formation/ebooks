package com.ebooks.reader.data.dict

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

/**
 * Stores and queries user-imported StarDict dictionaries (fully offline —
 * files are picked through SAF, so no network or permissions are involved).
 *
 * Dictionaries live flat in `filesDir/dictionaries/`; a dictionary is usable
 * once its `.ifo`, `.idx` and `.dict` files (matched by base name) are all
 * present. Compressed pickings (`.idx.gz`, `.dict.dz` — dictzip is plain
 * gzip) are inflated during import so lookups always hit plain files.
 */
class StarDictManager(private val context: Context) {

    /** A successful offline lookup: which dictionary answered, and its definition text. */
    data class OfflineDefinition(val dictionaryName: String, val definition: String)

    private fun dictsDir(): File = File(context.filesDir, "dictionaries").also { it.mkdirs() }

    /** Book names of the installed (complete) dictionaries, sorted. */
    fun listDictionaries(): List<String> = loadAll().map { it.info.bookName }.sorted()

    /**
     * Copies the picked StarDict files into app storage, inflating `.gz`/`.dz`
     * payloads on the way. Returns the book name of the first dictionary that
     * became complete and readable, or null when the pick doesn't add up to a
     * usable dictionary (the partial files are kept so the user can complete
     * the trio with a follow-up pick).
     */
    fun importDictionary(uris: List<Uri>): String? {
        val dir = dictsDir()
        val touchedBases = mutableSetOf<String>()
        for (uri in uris) {
            val rawName = displayName(uri) ?: continue
            var name = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val inflate = name.endsWith(".gz", true) || name.endsWith(".dz", true)
            if (inflate) name = name.dropLast(3)
            val extension = name.substringAfterLast('.', "").lowercase()
            if (extension !in setOf("ifo", "idx", "dict", "syn")) continue
            val imported = runCatching {
                context.contentResolver.openInputStream(uri)?.use { raw ->
                    val input = if (inflate) GZIPInputStream(raw) else raw
                    FileOutputStream(File(dir, name)).use { out -> input.copyTo(out) }
                }
            }.getOrNull() != null
            if (imported) touchedBases += name.removeSuffix(".$extension")
        }
        if (touchedBases.isEmpty()) return null

        synchronized(cacheLock) { cache = null } // new files — reload on next lookup
        return touchedBases.firstNotNullOfOrNull { base ->
            openDictionary(dir, base)?.info?.bookName
        }
    }

    /** Looks the word up in every installed dictionary; first hit wins. */
    fun lookup(word: String): OfflineDefinition? {
        val clean = word.trim()
        if (clean.isBlank()) return null
        for (dictionary in loadAll()) {
            val definition = dictionary.lookup(clean) ?: dictionary.lookup(clean.lowercase())
            if (definition != null) return OfflineDefinition(dictionary.info.bookName, definition)
        }
        return null
    }

    private fun openDictionary(dir: File, base: String): StarDictDictionary? {
        val ifo = File(dir, "$base.ifo")
        val idx = File(dir, "$base.idx")
        val dict = File(dir, "$base.dict")
        if (!ifo.isFile || !idx.isFile || !dict.isFile) return null
        return StarDictDictionary.open(ifo, idx, dict)
    }

    private fun loadAll(): List<StarDictDictionary> {
        synchronized(cacheLock) {
            cache?.let { return it }
            val dir = dictsDir()
            val bases = dir.listFiles { file -> file.isFile && file.extension.equals("ifo", true) }
                ?.map { it.name.removeSuffix(".ifo") }
                ?.sorted()
                ?: emptyList()
            val loaded = bases.mapNotNull { openDictionary(dir, it) }
            cache = loaded
            return loaded
        }
    }

    private fun displayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
        }
        return uri.lastPathSegment
    }

    companion object {
        // Parsed indexes are shared process-wide so reopening the reader does
        // not re-read multi-megabyte .idx files on every screen.
        private val cacheLock = Any()

        @Volatile
        private var cache: List<StarDictDictionary>? = null
    }
}
