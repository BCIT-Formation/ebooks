package com.ebooks.reader.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
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
