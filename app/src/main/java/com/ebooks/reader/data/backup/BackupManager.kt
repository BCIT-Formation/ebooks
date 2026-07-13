package com.ebooks.reader.data.backup

import android.content.Context
import com.ebooks.reader.data.db.AppDatabase
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full backup and restore of the library: the Room database (books, progress,
 * bookmarks, sessions, annotations, RSS feeds/articles) plus cover images,
 * packed into a single `.zip` the user picks via the Storage Access Framework.
 */
class BackupManager(private val context: Context) {

    private fun coversDir() = File(context.filesDir, "covers")
    private fun dbFile() = context.getDatabasePath(AppDatabase.DB_NAME)

    /** Writes a backup archive to [out]. */
    fun export(out: OutputStream) {
        // Flush the write-ahead log into the main DB file so a single-file copy is complete.
        runCatching {
            AppDatabase.getInstance(context).openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        }
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.txt"))
            zip.write("ebookreader-backup;v1".toByteArray())
            zip.closeEntry()

            dbFile().takeIf { it.exists() }?.let { putFile(zip, "database/${AppDatabase.DB_NAME}", it) }
            coversDir().listFiles()?.forEach { putFile(zip, "covers/${it.name}", it) }
        }
    }

    /**
     * Restores from a backup archive. Replaces the database and covers, then
     * resets the Room singleton. The caller MUST restart the process afterwards
     * so no stale references to the old database survive.
     */
    fun restore(input: InputStream) {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val safe = sanitize(entry.name)
                        if (safe != null) {
                            val dest = File(staging, safe).also { it.parentFile?.mkdirs() }
                            dest.outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val restoredDb = File(staging, "database/${AppDatabase.DB_NAME}")
            if (!restoredDb.exists()) throw IOException("Not a valid EbookReader backup")

            AppDatabase.resetInstance()

            val target = dbFile().also { it.parentFile?.mkdirs() }
            restoredDb.copyTo(target, overwrite = true)
            // Drop stale WAL/SHM so SQLite rebuilds them for the restored DB.
            File(target.parentFile, "${AppDatabase.DB_NAME}-wal").delete()
            File(target.parentFile, "${AppDatabase.DB_NAME}-shm").delete()

            val covers = coversDir().apply { mkdirs() }
            File(staging, "covers").listFiles()?.forEach { it.copyTo(File(covers, it.name), overwrite = true) }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun putFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /** Reject path-traversal entries; only keep the two known subtrees. */
    private fun sanitize(name: String): String? {
        if (name.contains("..")) return null
        return when {
            name.startsWith("database/") || name.startsWith("covers/") -> name
            else -> null
        }
    }
}
