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

@Database(
    entities = [Book::class, ReadingProgress::class, Bookmark::class, ReadingSession::class, Annotation::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** Adds the reading_sessions table introduced in schema version 2. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
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
        val MIGRATION_2_3 = object : Migration(2, 3) {
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ebook_reader.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // Safety net: if a future schema change has no migration, wipe rather than crash.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
