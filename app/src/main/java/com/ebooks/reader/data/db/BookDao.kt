package com.ebooks.reader.data.db

import androidx.room.*
import com.ebooks.reader.data.db.entities.Annotation
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.Bookmark
import com.ebooks.reader.data.db.entities.ReadingProgress
import com.ebooks.reader.data.db.entities.ReadingSession
import com.ebooks.reader.data.db.entities.ReadingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    // ── Books ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM books ORDER BY title ASC")
    fun getAllBooksByTitle(): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY author ASC, title ASC")
    fun getAllBooksByAuthor(): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun getAllBooksByDate(): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY CASE WHEN lastReadAt IS NULL THEN 1 ELSE 0 END ASC, lastReadAt DESC, title ASC")
    fun getAllBooksByRecent(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE readingStatus = :status ORDER BY title ASC")
    fun getBooksByStatus(status: ReadingStatus): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE fileType = :fileType ORDER BY title ASC")
    fun getBooksByType(fileType: String): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: String): Book?

    @Query("SELECT * FROM books WHERE filePath = :path")
    suspend fun getBookByPath(path: String): Book?

    /** One-shot snapshot of all books — for background operations like cover rebuild. */
    @Query("SELECT * FROM books")
    suspend fun getAllBooksSnapshot(): List<Book>

    /** The book most recently opened for reading — shown in the home screen widget. */
    @Query("SELECT * FROM books WHERE lastReadAt IS NOT NULL ORDER BY lastReadAt DESC LIMIT 1")
    suspend fun getMostRecentlyReadBook(): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book)

    @Update
    suspend fun updateBook(book: Book)

    @Delete
    suspend fun deleteBook(book: Book)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: String)

    @Query("UPDATE books SET readingStatus = :status WHERE id = :bookId")
    suspend fun updateReadingStatus(bookId: String, status: ReadingStatus)

    @Query("""UPDATE books SET lastReadAt = :time,
              readingStatus = CASE WHEN readingStatus = 'UNREAD' THEN 'READING' ELSE readingStatus END
              WHERE id = :bookId""")
    suspend fun updateLastRead(bookId: String, time: Long)

    // ── Reading Progress ──────────────────────────────────────────────────────

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getReadingProgress(bookId: String): ReadingProgress?

    /** One-shot snapshot of all reading progress — for progress sync export. */
    @Query("SELECT * FROM reading_progress")
    suspend fun getAllReadingProgress(): List<ReadingProgress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReadingProgress(progress: ReadingProgress)

    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun deleteReadingProgress(bookId: String)

    // ── Bookmarks ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY chapterIndex ASC, position ASC")
    fun getBookmarks(bookId: String): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND chapterIndex = :chapterIndex ORDER BY position ASC")
    suspend fun getBookmarksForChapter(bookId: String, chapterIndex: Int): List<Bookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteAllBookmarks(bookId: String)

    // ── Reading Sessions ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingSession(session: ReadingSession)

    /** Total milliseconds spent reading this book across all sessions. */
    @Query("SELECT COALESCE(SUM(endTime - startTime), 0) FROM reading_sessions WHERE bookId = :bookId")
    suspend fun getTotalReadingTimeMs(bookId: String): Long

    /** Number of completed reading sessions for this book. */
    @Query("SELECT COUNT(*) FROM reading_sessions WHERE bookId = :bookId")
    suspend fun getSessionCount(bookId: String): Int

    /** The five most recent sessions for a book, newest first. */
    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startTime DESC LIMIT 5")
    suspend fun getRecentSessions(bookId: String): List<ReadingSession>

    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId")
    suspend fun deleteReadingSessions(bookId: String)

    // ── Annotations ───────────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM annotations
        WHERE bookId = :bookId AND pageIdentifier = :pageIdentifier
        AND isDeleted = 0
        ORDER BY createdAt ASC
    """)
    suspend fun getAnnotationsForPage(bookId: String, pageIdentifier: String): List<Annotation>

    @Query("""
        SELECT * FROM annotations
        WHERE bookId = :bookId AND isDeleted = 0
        ORDER BY pageIdentifier ASC, createdAt ASC
    """)
    fun getAnnotationsByBook(bookId: String): Flow<List<Annotation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: Annotation)

    @Update
    suspend fun updateAnnotation(annotation: Annotation)

    @Query("UPDATE annotations SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteAnnotation(id: String)

    @Query("DELETE FROM annotations WHERE bookId = :bookId AND pageIdentifier = :pageIdentifier")
    suspend fun deletePageAnnotations(bookId: String, pageIdentifier: String)

    @Query("DELETE FROM annotations WHERE bookId = :bookId")
    suspend fun deleteAllAnnotations(bookId: String)

    @Query("SELECT COUNT(*) FROM annotations WHERE bookId = :bookId AND isDeleted = 0")
    suspend fun getAnnotationCount(bookId: String): Int
}
