package com.ebooks.reader.data.parser

import com.github.junrar.Archive
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Page extraction for comic book archives. CBZ is a ZIP of images and is read
 * with the built-in [ZipInputStream] (ADR-001); CBR is a RAR of images and is
 * read with junrar (ADR-007: RAR has no open format spec, so a decoder library is
 * the only practical option; RAR5 archives are not supported by junrar).
 *
 * Pages are written to [destDir] as `page_00000.ext`, `page_00001.ext`, … in
 * reading order (archive entry names sorted case-insensitively, the de-facto
 * comic page order).
 */
object ComicArchive {

    /** Image extensions that count as comic pages. */
    val PAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    /**
     * True when the archive entry named [entryName] is a displayable page.
     * macOS AppleDouble sidecars (`__MACOSX/._page.jpg`) and other hidden
     * dotfiles share image extensions but are not real pages; excluding them
     * avoids the duplicate/broken pages seen in archives zipped on macOS.
     */
    fun isComicPage(entryName: String): Boolean {
        val normalized = entryName.replace('\\', '/')
        if (normalized.split('/').any { it == "__MACOSX" }) return false
        val baseName = normalized.substringAfterLast('/')
        if (baseName.startsWith(".")) return false
        return baseName.substringAfterLast('.', "").lowercase() in PAGE_EXTENSIONS
    }

    /** Extracts the pages of a CBZ (ZIP) archive read from [input] into [destDir]. */
    fun extractCbzPages(input: InputStream, destDir: File): List<File> {
        destDir.mkdirs()
        val extracted = mutableListOf<Pair<String, File>>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isComicPage(entry.name)) {
                    val tmp = tempPageFile(destDir, extracted.size, entry.name)
                    FileOutputStream(tmp).use { out -> zip.copyTo(out) }
                    extracted += entry.name to tmp
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return renameIntoReadingOrder(destDir, extracted)
    }

    /**
     * Extracts the pages of a CBR (RAR) archive read from [input] into [destDir].
     * junrar failures (corrupt archive, unsupported RAR5, encryption) surface
     * as [IOException] so callers handle both formats identically.
     */
    fun extractCbrPages(input: InputStream, destDir: File): List<File> {
        destDir.mkdirs()
        val extracted = mutableListOf<Pair<String, File>>()
        try {
            Archive(input).use { archive ->
                for (header in archive.fileHeaders) {
                    // RAR stores paths with backslashes; normalise for sorting.
                    val name = header.fileName.replace('\\', '/')
                    if (header.isDirectory || !isComicPage(name)) continue
                    val tmp = tempPageFile(destDir, extracted.size, name)
                    FileOutputStream(tmp).use { out -> archive.extractFile(header, out) }
                    extracted += name to tmp
                }
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message ?: "Could not read RAR archive", e)
        }
        return renameIntoReadingOrder(destDir, extracted)
    }

    /** Indexed temp name: avoids archive path traversal and name clashes. */
    private fun tempPageFile(dir: File, index: Int, entryName: String): File {
        val ext = entryName.substringAfterLast('.', "").lowercase()
        return File(dir, "tmp_%05d.%s".format(index, ext))
    }

    /**
     * Renames temp files into reading order so a cached listing stays consistent.
     * Entry names are compared with a case-insensitive *natural* order so an
     * un-padded "page2" sorts before "page10"; plain lexicographic order put
     * "page10" first and scrambled the reading order of many real comics.
     */
    private fun renameIntoReadingOrder(dir: File, extracted: List<Pair<String, File>>): List<File> =
        extracted
            .sortedWith { a, b -> naturalCompare(a.first, b.first) }
            .mapIndexed { index, (_, tmp) ->
                val target = File(dir, "page_%05d.%s".format(index, tmp.extension))
                if (tmp.renameTo(target)) target else tmp
            }

    /**
     * Case-insensitive natural-order comparison: runs of digits are compared by
     * numeric value (ignoring leading zeros) so "page2" < "page10". Falls back
     * to lower-cased character comparison outside digit runs.
     */
    private fun naturalCompare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var sa = i
                var sb = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                // Skip leading zeros so "007" and "7" compare as equal.
                while (sa < i - 1 && a[sa] == '0') sa++
                while (sb < j - 1 && b[sb] == '0') sb++
                val lenA = i - sa
                val lenB = j - sb
                if (lenA != lenB) return lenA - lenB
                for (k in 0 until lenA) {
                    val d = a[sa + k].code - b[sb + k].code
                    if (d != 0) return d
                }
            } else {
                val d = ca.lowercaseChar().code - cb.lowercaseChar().code
                if (d != 0) return d
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
