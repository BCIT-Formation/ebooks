package com.ebooks.reader.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.ebooks.reader.data.db.AppDatabase
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.Bookmark
import com.ebooks.reader.data.db.entities.FileType
import com.ebooks.reader.data.db.entities.ReadingProgress
import com.ebooks.reader.data.db.entities.ReadingSession
import com.ebooks.reader.data.db.entities.ReadingStatus
import com.ebooks.reader.data.parser.EpubBook
import com.ebooks.reader.data.parser.EpubChapter
import com.ebooks.reader.data.parser.EpubParser
import com.ebooks.reader.data.parser.ReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val BUNDLED_ALICE_ASSET = "alice_wonderland.epub"
private const val BUNDLED_ALICE_SENTINEL = "bundled:alice_wonderland"

class BookRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.bookDao()
    private val epubParser = EpubParser(context)

    // ── Book Queries ──────────────────────────────────────────────────────────

    fun getBooksByTitle(): Flow<List<Book>> = dao.getAllBooksByTitle()
    fun getBooksByAuthor(): Flow<List<Book>> = dao.getAllBooksByAuthor()
    fun getBooksByDate(): Flow<List<Book>> = dao.getAllBooksByDate()
    fun getBooksByRecent(): Flow<List<Book>> = dao.getAllBooksByRecent()
    fun getBooksByStatus(status: ReadingStatus): Flow<List<Book>> = dao.getBooksByStatus(status)
    fun getBooksByType(fileType: String): Flow<List<Book>> = dao.getBooksByType(fileType)

    suspend fun getBookById(id: String): Book? = dao.getBookById(id)

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Outcome of an [importBook] call. Distinguishes the failure modes so callers
     * can surface a precise message rather than a single generic error.
     */
    sealed class ImportResult {
        data class Success(val book: Book) : ImportResult()
        /** The file was already in the library; [book] is the existing entry. */
        data class AlreadyExists(val book: Book) : ImportResult()
        /** The extension maps to no supported [FileType]. */
        data class UnsupportedFormat(val extension: String) : ImportResult()
        /** The file could not be opened/read (moved, deleted, empty, or no permission). */
        data class Unreadable(val fileName: String?) : ImportResult()
        /** The file was readable but could not be parsed (e.g. corrupt EPUB). */
        data class ParseFailed(val fileName: String) : ImportResult()
    }

    suspend fun importBook(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {}

        val fileName = getFileName(uri) ?: return@withContext ImportResult.Unreadable(null)
        val extension = fileName.substringAfterLast(".", "").lowercase()
        val fileType = FileType.fromExtension(extension)
            ?: return@withContext ImportResult.UnsupportedFormat(extension)

        // Deduplicate by source URI before doing any work.
        dao.getBookByPath(uri.toString())?.let { return@withContext ImportResult.AlreadyExists(it) }

        // Fail fast if the file cannot actually be read (avoids inserting a dead row).
        if (!isReadable(uri)) return@withContext ImportResult.Unreadable(fileName)

        val fileSize = getFileSize(uri)
        val bookId = UUID.randomUUID().toString()

        val book = when (fileType) {
            FileType.EPUB -> importEpub(uri, bookId, fileSize)
            FileType.PDF -> importPdf(uri, bookId, fileSize, fileName)
            FileType.TXT, FileType.FB2 -> importTextBook(uri, bookId, fileSize, fileName, fileType)
            FileType.CBZ -> importCbz(uri, bookId, fileSize, fileName)
        } ?: return@withContext ImportResult.ParseFailed(fileName)

        ImportResult.Success(book)
    }

    /** True if the URI can be opened and contains at least one byte. */
    private fun isReadable(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.read() != -1 } ?: false
    }.getOrDefault(false)

    private suspend fun importEpub(uri: Uri, bookId: String, fileSize: Long): Book? {
        val epubBook = epubParser.parse(uri) ?: return null
        val coverPath = epubBook.coverBytes?.let { saveCover(bookId, it) }

        val book = Book(
            id = bookId,
            title = epubBook.title,
            author = epubBook.author,
            filePath = uri.toString(),
            fileType = FileType.EPUB.extension,
            coverPath = coverPath,
            fileSize = fileSize,
            description = epubBook.description,
            publisher = epubBook.publisher,
            language = epubBook.language,
            totalChapters = epubBook.chapters.size
        )
        dao.insertBook(book)
        return book
    }

    private suspend fun importPdf(uri: Uri, bookId: String, fileSize: Long, fileName: String): Book {
        val title = fileName.removeSuffix(".pdf")
        val book = Book(
            id = bookId,
            title = title,
            author = "Unknown",
            filePath = uri.toString(),
            fileType = FileType.PDF.extension,
            fileSize = fileSize
        )
        dao.insertBook(book)
        return book
    }

    private suspend fun importTextBook(uri: Uri, bookId: String, fileSize: Long, fileName: String, fileType: FileType): Book {
        val title = fileName.removeSuffix(".${fileType.extension}")
        val book = Book(
            id = bookId,
            title = title,
            author = "Unknown",
            filePath = uri.toString(),
            fileType = fileType.extension,
            fileSize = fileSize
        )
        dao.insertBook(book)
        return book
    }

    private suspend fun importCbz(uri: Uri, bookId: String, fileSize: Long, fileName: String): Book {
        val title = fileName.removeSuffix(".cbz")
        val book = Book(
            id = bookId,
            title = title,
            author = "Unknown",
            filePath = uri.toString(),
            fileType = FileType.CBZ.extension,
            fileSize = fileSize
        )
        dao.insertBook(book)
        return book
    }

    // ── Cover Management ──────────────────────────────────────────────────────

    private fun saveCover(bookId: String, bytes: ByteArray): String? = runCatching {
        val coversDir = File(context.filesDir, "covers").also { it.mkdirs() }
        val coverFile = File(coversDir, "$bookId.jpg")
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return@runCatching null
        FileOutputStream(coverFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        coverFile.absolutePath
    }.getOrNull()

    /**
     * Re-parses the source file of every EPUB book and regenerates its cover image.
     * Useful after the covers directory is cleared or migrated.
     * Runs on the IO dispatcher; call from a coroutine scope.
     */
    suspend fun rebuildCovers() = withContext(Dispatchers.IO) {
        val allBooks = dao.getAllBooksSnapshot()
        for (book in allBooks) {
            if (book.fileType != FileType.EPUB.extension) continue
            try {
                val uri = resolveUri(book.filePath)
                val epubBook = epubParser.parse(uri) ?: continue
                val coverBytes = epubBook.coverBytes ?: continue
                val newCoverPath = saveCover(book.id, coverBytes) ?: continue
                dao.updateBook(book.copy(coverPath = newCoverPath))
            } catch (_: Exception) {
                // Skip books whose file is no longer accessible
            }
        }
    }

    // ── Book Updates ──────────────────────────────────────────────────────────

    suspend fun updateBook(book: Book) = dao.updateBook(book)

    suspend fun deleteBook(book: Book, deleteFile: Boolean = false) {
        dao.deleteBook(book)
        dao.deleteReadingProgress(book.id)
        dao.deleteAllBookmarks(book.id)
        // Delete cover
        book.coverPath?.let { File(it).delete() }
        if (deleteFile) {
            // Only delete if the file is in app internal storage
            val uri = Uri.parse(book.filePath)
            if (uri.scheme == "file" && book.filePath.startsWith(context.filesDir.absolutePath)) {
                File(book.filePath).delete()
            }
        }
    }

    suspend fun updateReadingStatus(bookId: String, status: ReadingStatus) =
        dao.updateReadingStatus(bookId, status)

    suspend fun updateLastRead(bookId: String) =
        dao.updateLastRead(bookId, System.currentTimeMillis())

    // ── Reading Progress ──────────────────────────────────────────────────────

    suspend fun getReadingProgress(bookId: String): ReadingProgress? =
        dao.getReadingProgress(bookId)

    suspend fun saveReadingProgress(progress: ReadingProgress) =
        dao.saveReadingProgress(progress)

    // ── Reading Sessions ──────────────────────────────────────────────────────

    /**
     * Saves a completed reading session.  Sessions shorter than 10 seconds are
     * silently discarded (accidental opens, orientation changes, etc.).
     */
    suspend fun saveReadingSession(session: ReadingSession) = withContext(Dispatchers.IO) {
        val durationMs = session.endTime - session.startTime
        if (durationMs < 10_000L) return@withContext
        dao.insertReadingSession(session)
    }

    data class ReadingStats(
        val totalReadingTimeMs: Long,
        val sessionCount: Int,
        val averageSessionMs: Long,
        val lastSessionMs: Long?   // null if no sessions yet
    )

    suspend fun getReadingStats(bookId: String): ReadingStats = withContext(Dispatchers.IO) {
        val totalMs    = dao.getTotalReadingTimeMs(bookId)
        val count      = dao.getSessionCount(bookId)
        val avgMs      = if (count > 0) totalMs / count else 0L
        val recentSess = dao.getRecentSessions(bookId)
        val lastMs     = recentSess.firstOrNull()?.let { it.endTime - it.startTime }
        ReadingStats(totalMs, count, avgMs, lastMs)
    }

    // ── Bookmarks ─────────────────────────────────────────────────────────────

    fun getBookmarks(bookId: String): Flow<List<Bookmark>> = dao.getBookmarks(bookId)

    suspend fun addBookmark(bookmark: Bookmark) = dao.insertBookmark(bookmark)

    suspend fun deleteBookmark(bookmark: Bookmark) = dao.deleteBookmark(bookmark)

    // ── EPUB Content ──────────────────────────────────────────────────────────

    private fun resolveUri(filePath: String): Uri = when (filePath) {
        BUNDLED_ALICE_SENTINEL -> Uri.fromFile(File(context.filesDir, "bundled/$BUNDLED_ALICE_ASSET"))
        else -> Uri.parse(filePath)
    }

    suspend fun getChapterHtml(bookId: String, chapterHref: String, theme: ReaderTheme): String? =
        withContext(Dispatchers.IO) {
            val book = dao.getBookById(bookId) ?: return@withContext null
            when (book.fileType) {
                FileType.TXT.extension, FileType.FB2.extension -> {
                    val uri = resolveUri(book.filePath)
                    val text = try {
                        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    } catch (_: Exception) { null } ?: return@withContext null
                    epubParser.buildHtmlFromText(text, theme)
                }
                else -> epubParser.getChapterHtml(resolveUri(book.filePath), chapterHref, theme)
            }
        }

    // Accept the already-fetched Book to avoid an extra DB round-trip
    suspend fun parseEpubBook(book: Book): EpubBook? =
        withContext(Dispatchers.IO) {
            when (book.fileType) {
                FileType.TXT.extension, FileType.FB2.extension -> EpubBook(
                    title = book.title,
                    author = book.author,
                    description = null,
                    publisher = null,
                    language = null,
                    coverBytes = null,
                    chapters = listOf(EpubChapter(0, book.title, "text://content"))
                )
                else -> epubParser.parse(resolveUri(book.filePath))
            }
        }

    // ── Bundled Books ─────────────────────────────────────────────────────────

    suspend fun seedBundledBooks() = withContext(Dispatchers.IO) {
        // Only seed once — sentinel path prevents duplicate entries
        if (dao.getBookByPath(BUNDLED_ALICE_SENTINEL) != null) return@withContext

        val booksDir = File(context.filesDir, "bundled").also { it.mkdirs() }
        val dest = File(booksDir, BUNDLED_ALICE_ASSET)

        // Copy asset to internal storage so we have a stable file:// URI
        if (!dest.exists()) {
            context.assets.open(BUNDLED_ALICE_ASSET).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        }

        val fileUri = Uri.fromFile(dest)
        val epubBook = epubParser.parse(fileUri) ?: return@withContext
        val bookId = UUID.randomUUID().toString()
        val coverPath = epubBook.coverBytes?.let { saveCover(bookId, it) }

        val book = Book(
            id = bookId,
            title = epubBook.title,
            author = epubBook.author,
            filePath = BUNDLED_ALICE_SENTINEL,
            fileType = FileType.EPUB.extension,
            coverPath = coverPath,
            fileSize = dest.length(),
            description = epubBook.description,
            publisher = epubBook.publisher,
            language = epubBook.language,
            totalChapters = epubBook.chapters.size
        )
        dao.insertBook(book)
    }

    // ── File Utilities ────────────────────────────────────────────────────────

    private fun getFileName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
        }
        return uri.lastPathSegment
    }

    private fun getFileSize(uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst()) return cursor.getLong(sizeIndex)
        }
        return 0L
    }
}
