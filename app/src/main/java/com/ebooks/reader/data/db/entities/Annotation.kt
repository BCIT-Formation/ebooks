package com.ebooks.reader.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A freehand/shape/text annotation. [bookId] holds the id of the annotated
 * source — a book, or an RSS article id (prefixed `rss:`). There is deliberately
 * no foreign key, so the same table serves both; owners clean up their own
 * annotations on delete (see `BookRepository.deleteBook` / RSS article removal).
 */
@Entity(
    tableName = "annotations",
    indices = [
        Index("bookId"),
        Index("bookId", "pageIdentifier"),
        Index("createdAt")
    ]
)
data class Annotation(
    @PrimaryKey val id: String,
    val bookId: String,
    val pageIdentifier: String,
    val pageIndex: Int,
    val annotationType: String,
    val color: Int,
    val strokeWidth: Float = 3f,
    val opacity: Float = 1f,
    val points: String = "",
    val boundingBox: String = "",
    val textContent: String = "",
    val metadata: String = "",
    val createdAt: Long,
    val modifiedAt: Long,
    val isDeleted: Boolean = false
)
