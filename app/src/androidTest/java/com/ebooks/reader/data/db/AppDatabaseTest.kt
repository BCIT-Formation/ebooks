package com.ebooks.reader.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.ReadingSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Instrumented tests for AppDatabase.
 * Verifies migrations, schema integrity, and basic CRUD operations.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun databaseCreatesAndInsertsBook() = runBlocking {
        val book = Book(
            id = UUID.randomUUID().toString(),
            title = "Test Book",
            author = "Test Author",
            filePath = "file:///test.epub",
            format = "EPUB",
            coverImage = null,
            lastReadTime = System.currentTimeMillis(),
            screenOrientationLock = "UNSPECIFIED",
            tiltScrollEnabled = false
        )

        db.bookDao().insertOrUpdateBook(book)
        val retrieved = db.bookDao().getBookById(book.id)

        assert(retrieved != null) { "Book should be retrievable after insert" }
        assert(retrieved?.title == "Test Book") { "Book title should match" }
        assert(retrieved?.author == "Test Author") { "Book author should match" }
    }

    @Test
    fun migrationCreatesReadingSessionsTable() = runBlocking {
        // After migration, reading_sessions table should exist
        val book = Book(
            id = UUID.randomUUID().toString(),
            title = "Test Book",
            author = "Test Author",
            filePath = "file:///test.epub",
            format = "EPUB",
            coverImage = null,
            lastReadTime = System.currentTimeMillis(),
            screenOrientationLock = "UNSPECIFIED",
            tiltScrollEnabled = false
        )
        db.bookDao().insertOrUpdateBook(book)

        val session = ReadingSession(
            id = UUID.randomUUID().toString(),
            bookId = book.id,
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 3600000, // 1 hour later
            chaptersVisited = 1
        )

        db.bookDao().insertReadingSession(session)
        val retrieved = db.bookDao().getReadingSessionsForBook(book.id)

        assert(retrieved.isNotEmpty()) { "Reading session should be retrievable" }
        assert(retrieved[0].bookId == book.id) { "Session should reference correct book" }
    }

    @Test
    fun getBookByIdReturnsNullForNonExistentBook() = runBlocking {
        val result = db.bookDao().getBookById("non-existent-id")

        assert(result == null) { "Non-existent book should return null" }
    }

    @Test
    fun insertedBooksAreRetriever() = runBlocking {
        val book1 = Book(
            id = UUID.randomUUID().toString(),
            title = "Book One",
            author = "Author One",
            filePath = "file:///book1.epub",
            format = "EPUB",
            coverImage = null,
            lastReadTime = System.currentTimeMillis(),
            screenOrientationLock = "UNSPECIFIED",
            tiltScrollEnabled = false
        )
        val book2 = Book(
            id = UUID.randomUUID().toString(),
            title = "Book Two",
            author = "Author Two",
            filePath = "file:///book2.epub",
            format = "EPUB",
            coverImage = null,
            lastReadTime = System.currentTimeMillis() - 1000,
            screenOrientationLock = "UNSPECIFIED",
            tiltScrollEnabled = false
        )

        db.bookDao().insertOrUpdateBook(book1)
        db.bookDao().insertOrUpdateBook(book2)
        val allBooks = db.bookDao().getAllBooks()

        assert(allBooks.size >= 2) { "Should have at least 2 books" }
        assert(allBooks.any { it.title == "Book One" }) { "Should find Book One" }
        assert(allBooks.any { it.title == "Book Two" }) { "Should find Book Two" }
    }
}
