package com.ebooks.reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ebooks.reader.data.db.entities.Annotation
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.Bookmark
import com.ebooks.reader.data.db.entities.ReadingProgress
import com.ebooks.reader.data.db.entities.ReadingSession
import com.ebooks.reader.data.db.entities.RssArticle
import com.ebooks.reader.data.db.entities.RssFeed

@Database(
    entities = [
        Book::class, ReadingProgress::class, Bookmark::class, ReadingSession::class,
        Annotation::class, RssFeed::class, RssArticle::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun rssDao(): RssDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** Adds the reading_sessions table introduced in schema version 2. */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS reading_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        bookId TEXT NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER NOT NULL,
                        chaptersVisited INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_reading_sessions_bookId ON reading_sessions(bookId)"
                )
            }
        }

        /** Adds the annotations table introduced in schema version 3. */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS annotations (
                        id TEXT NOT NULL PRIMARY KEY,
                        bookId TEXT NOT NULL,
                        pageIdentifier TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        annotationType TEXT NOT NULL,
                        color INTEGER NOT NULL,
                        strokeWidth REAL NOT NULL DEFAULT 3.0,
                        opacity REAL NOT NULL DEFAULT 1.0,
                        points TEXT NOT NULL DEFAULT '',
                        boundingBox TEXT NOT NULL DEFAULT '',
                        textContent TEXT NOT NULL DEFAULT '',
                        metadata TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        modifiedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_annotations_bookId ON annotations(bookId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_annotations_bookId_pageIdentifier ON annotations(bookId, pageIdentifier)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_annotations_createdAt ON annotations(createdAt)"
                )
            }
        }

        /**
         * Adds the RSS tables (`rss_feeds`, `rss_articles`) and relaxes the
         * `annotations` foreign key to `books` so RSS articles can be annotated
         * with the same drawing tools. The annotations table is rebuilt without
         * the FK, preserving existing rows.
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS rss_feeds (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        url TEXT NOT NULL,
                        siteUrl TEXT,
                        addedAt INTEGER NOT NULL,
                        lastFetchedAt INTEGER
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rss_feeds_url ON rss_feeds(url)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS rss_articles (
                        id TEXT NOT NULL PRIMARY KEY,
                        feedId TEXT NOT NULL,
                        guid TEXT NOT NULL,
                        title TEXT NOT NULL,
                        link TEXT,
                        author TEXT,
                        contentHtml TEXT NOT NULL DEFAULT '',
                        summary TEXT,
                        publishedAt INTEGER NOT NULL DEFAULT 0,
                        fetchedAt INTEGER NOT NULL,
                        isRead INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(feedId) REFERENCES rss_feeds(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rss_articles_feedId ON rss_articles(feedId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rss_articles_feedId_guid ON rss_articles(feedId, guid)")

                // Rebuild annotations without the books foreign key.
                db.execSQL("""
                    CREATE TABLE annotations_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        bookId TEXT NOT NULL,
                        pageIdentifier TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        annotationType TEXT NOT NULL,
                        color INTEGER NOT NULL,
                        strokeWidth REAL NOT NULL DEFAULT 3.0,
                        opacity REAL NOT NULL DEFAULT 1.0,
                        points TEXT NOT NULL DEFAULT '',
                        boundingBox TEXT NOT NULL DEFAULT '',
                        textContent TEXT NOT NULL DEFAULT '',
                        metadata TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        modifiedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO annotations_new SELECT * FROM annotations")
                db.execSQL("DROP TABLE annotations")
                db.execSQL("ALTER TABLE annotations_new RENAME TO annotations")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_bookId ON annotations(bookId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_bookId_pageIdentifier ON annotations(bookId, pageIdentifier)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_createdAt ON annotations(createdAt)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ebook_reader.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    // Safety net: if a future schema change has no migration, wipe rather than crash.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
