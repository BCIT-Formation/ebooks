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

    /** True when the archive entry named [entryName] is a displayable page. */
    fun isComicPage(entryName: String): Boolean =
        entryName.substringAfterLast('.', "").lowercase() in PAGE_EXTENSIONS

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

    /** Renames temp files into reading order so a cached listing stays consistent. */
    private fun renameIntoReadingOrder(dir: File, extracted: List<Pair<String, File>>): List<File> =
        extracted
            .sortedBy { (name, _) -> name.lowercase() }
            .mapIndexed { index, (_, tmp) ->
                val target = File(dir, "page_%05d.%s".format(index, tmp.extension))
                if (tmp.renameTo(target)) target else tmp
            }
}
