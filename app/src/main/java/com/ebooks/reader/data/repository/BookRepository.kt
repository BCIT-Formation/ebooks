package com.ebooks.reader.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.ebooks.reader.data.db.AppDatabase
import com.ebooks.reader.data.db.entities.Annotation
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
import com.ebooks.reader.data.sync.ProgressEntry
import com.ebooks.reader.data.sync.ProgressSnapshot
import com.ebooks.reader.data.sync.selectNewerEntries
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
    fun getBooksBySeries(): Flow<List<Book>> = dao.getAllBooksBySeries()
    fun getBooksByStatus(status: ReadingStatus): Flow<List<Book>> = dao.getBooksByStatus(status)
    fun getBooksByType(fileType: String): Flow<List<Book>> = dao.getBooksByType(fileType)

    suspend fun getBookById(id: String): Book? = dao.getBookById(id)
    suspend fun getMostRecentlyReadBook(): Book? = dao.getMostRecentlyReadBook()

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

        // The freshly-imported book's ZIP is cached by the parser but won't be
        // read again here — release it so the library doesn't retain a book.
        epubParser.clearCache()
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
        // Don't retain the last book parsed during the rebuild loop.
        epubParser.clearCache()
    }

    /** Releases the parser's in-memory ZIP cache — call when a book is closed. */
    fun releaseParserCache() = epubParser.clearCache()

    // ── Custom Fonts ──────────────────────────────────────────────────────────

    private fun fontsDir(): File = File(context.filesDir, "fonts").also { it.mkdirs() }

    /** User-imported TTF/OTF files, sorted by name. */
    suspend fun listCustomFonts(): List<File> = withContext(Dispatchers.IO) {
        fontsDir()
            .listFiles { file -> file.extension.lowercase() in setOf("ttf", "otf") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * Copies a user-picked TTF/OTF file into app storage so the reader can use
     * it as a custom font. Returns the stored file, or null when the pick is
     * not a font file or cannot be read.
     */
    suspend fun importFont(uri: Uri): File? = withContext(Dispatchers.IO) {
        val name = getFileName(uri) ?: return@withContext null
        val extension = name.substringAfterLast(".", "").lowercase()
        if (extension != "ttf" && extension != "otf") return@withContext null
        val dest = File(fontsDir(), name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        runCatching {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            if (copied == null) null else dest
        }.getOrNull()
    }

    // ── Book Updates ──────────────────────────────────────────────────────────

    suspend fun updateBook(book: Book) = dao.updateBook(book)

    suspend fun deleteBook(book: Book, deleteFile: Boolean = false) {
        dao.deleteBook(book)
        dao.deleteReadingProgress(book.id)
        dao.deleteAllBookmarks(book.id)
        dao.deleteAllAnnotations(book.id)
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

    // ── Progress Sync (ADR-006) ───────────────────────────────────────────────

    /** Builds the device-independent progress snapshot exchanged during sync. */
    suspend fun buildProgressSnapshot(): ProgressSnapshot = withContext(Dispatchers.IO) {
        val progressByBook = dao.getAllReadingProgress().associateBy { it.bookId }
        val entries = dao.getAllBooksSnapshot().mapNotNull { book ->
            val lastRead = book.lastReadAt ?: return@mapNotNull null
            val progress = progressByBook[book.id]
            ProgressEntry(
                title = book.title,
                author = book.author,
                chapterIndex = progress?.chapterIndex ?: 0,
                scrollPosition = progress?.scrollPosition ?: 0,
                lastReadAt = lastRead,
                readingStatus = book.readingStatus.name
            )
        }
        ProgressSnapshot(exportedAt = System.currentTimeMillis(), entries = entries)
    }

    /**
     * Applies a remote snapshot with newer-wins semantics (books matched by
     * title + author). Returns the number of books whose progress advanced.
     */
    suspend fun applyProgressSnapshot(remote: ProgressSnapshot): Int = withContext(Dispatchers.IO) {
        val local = buildProgressSnapshot()
        val toApply = selectNewerEntries(local.entries, remote.entries)
        val books = dao.getAllBooksSnapshot()
        var applied = 0
        for (entry in toApply) {
            val book = books.find {
                it.title.equals(entry.title, ignoreCase = true) &&
                    it.author.equals(entry.author, ignoreCase = true)
            } ?: continue
            val existing = dao.getReadingProgress(book.id)
            dao.saveReadingProgress(
                ReadingProgress(
                    bookId = book.id,
                    chapterIndex = entry.chapterIndex,
                    chapterHref = existing?.chapterHref ?: "",
                    scrollPosition = entry.scrollPosition
                )
            )
            val status = runCatching { ReadingStatus.valueOf(entry.readingStatus) }
                .getOrDefault(book.readingStatus)
            dao.updateBook(book.copy(lastReadAt = entry.lastReadAt, readingStatus = status))
            applied++
        }
        applied
    }

    // ── Bookmarks ─────────────────────────────────────────────────────────────

    fun getBookmarks(bookId: String): Flow<List<Bookmark>> = dao.getBookmarks(bookId)

    suspend fun addBookmark(bookmark: Bookmark) = dao.insertBookmark(bookmark)

    /** Re-inserts (REPLACE by id) — used to edit a highlight's note. */
    suspend fun updateBookmark(bookmark: Bookmark) = dao.insertBookmark(bookmark)

    suspend fun deleteBookmark(bookmark: Bookmark) = dao.deleteBookmark(bookmark)

    suspend fun getHighlightsSnapshot(bookId: String): List<Bookmark> = dao.getHighlightsSnapshot(bookId)

    // ── Annotations ───────────────────────────────────────────────────────────

    suspend fun getPageAnnotations(bookId: String, pageIdentifier: String): List<Annotation> =
        dao.getAnnotationsForPage(bookId, pageIdentifier)

    fun getAnnotationsByBook(bookId: String): Flow<List<Annotation>> =
        dao.getAnnotationsByBook(bookId)

    suspend fun addAnnotation(annotation: Annotation) =
        dao.insertAnnotation(annotation)

    suspend fun updateAnnotation(annotation: Annotation) =
        dao.updateAnnotation(annotation)

    suspend fun deleteAnnotation(id: String) =
        dao.softDeleteAnnotation(id)

    suspend fun clearPageAnnotations(bookId: String, pageIdentifier: String) =
        dao.deletePageAnnotations(bookId, pageIdentifier)

    suspend fun clearAllAnnotations(bookId: String) =
        dao.deleteAllAnnotations(bookId)

    suspend fun hasAnnotations(bookId: String): Boolean =
        dao.getAnnotationCount(bookId) > 0

    // ── Sharing ───────────────────────────────────────────────────────────────

    /** Files to hand to the system share sheet: the book itself plus any annotation images/markdown. */
    data class ShareBundle(
        val bookFile: File,
        val mimeType: String,
        val annotationImages: List<File>,
        val annotationMarkdown: File? = null
    )

    /**
     * Prepares a book (and its annotations, if any) for sharing. Copies the book
     * into cache (so a `content://` SAF book becomes a shareable file), renders
     * each annotated page to a PNG, and creates a markdown file with all highlights + notes.
     * Returns null if the book file can't be read.
     */
    suspend fun prepareShare(bookId: String): ShareBundle? = withContext(Dispatchers.IO) {
        val book = dao.getBookById(bookId) ?: return@withContext null
        val shareDir = File(context.cacheDir, "share").also { it.mkdirs(); it.clearContents() }

        val safeTitle = book.title.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifBlank { "book" }
        val bookFile = File(shareDir, "$safeTitle.${book.fileType}")
        val copied = runCatching {
            context.contentResolver.openInputStream(resolveUri(book.filePath))?.use { input ->
                FileOutputStream(bookFile).use { output -> input.copyTo(output) }
            }
        }.getOrNull() ?: return@withContext null

        val annotations = dao.getAnnotationsSnapshot(bookId)
        val images = annotations.groupBy { it.pageIndex }.toSortedMap().mapNotNull { (page, pageAnnotations) ->
            runCatching {
                val bitmap = com.ebooks.reader.util.renderAnnotationsToBitmap(pageAnnotations)
                val imageFile = File(shareDir, "${safeTitle}_notes_p${page + 1}.png")
                FileOutputStream(imageFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                bitmap.recycle()
                imageFile
            }.getOrNull()
        }

        // Generate markdown with all highlights and notes
        val markdownFile = generateAnnotationsMarkdown(book, annotations, safeTitle, shareDir)

        ShareBundle(bookFile, mimeTypeFor(book.fileType), images, markdownFile)
    }

    /**
     * Generates a markdown file containing all highlights and notes from a book
     */
    private fun generateAnnotationsMarkdown(
        book: Book,
        annotations: List<Annotation>,
        safeTitle: String,
        shareDir: File
    ): File? {
        val highlights = annotations.filter { it.note != null || it.selectedText != null }
        if (highlights.isEmpty()) return null

        val markdown = buildString {
            append("# ").append(book.title).append("\n\n")
            if (book.author != "Unknown") {
                append("**Auteur:** ").append(book.author).append("\n\n")
            }
            append("## Annotations\n\n")

            highlights.forEach { annotation ->
                if (!annotation.selectedText.isNullOrBlank()) {
                    append("> ").append(annotation.selectedText?.replace("\n", "\n> ")).append("\n\n")
                }
                if (!annotation.note.isNullOrBlank()) {
                    append("**Note:** ").append(annotation.note).append("\n\n")
                }
                append("---\n\n")
            }
        }

        return try {
            File(shareDir, "${safeTitle}_annotations.md").apply {
                writeText(markdown)
            }
        } catch (e: Exception) {
            Log.e("BookRepository", "Error creating markdown file", e)
            null
        }
    }

    private fun mimeTypeFor(fileType: String): String = when (fileType.lowercase()) {
        "epub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "fb2" -> "application/x-fictionbook+xml"
        "cbz" -> "application/x-cbz"
        else -> "application/octet-stream"
    }

    private fun File.clearContents() {
        listFiles()?.forEach { it.delete() }
    }

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
