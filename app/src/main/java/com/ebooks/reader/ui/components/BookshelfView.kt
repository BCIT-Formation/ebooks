package com.ebooks.reader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ebooks.reader.data.db.entities.Book

private const val BOOKS_PER_SHELF = 3

// Wood tones for the shelf boards — warm enough to read as wood on both themes.
private val ShelfFront = Color(0xFF8B5A2B)
private val ShelfEdge = Color(0xFF5C3A14)
private val ShelfBack = Color(0xFF3E2A12)

/**
 * A bookshelf rendering of the library: rows of standing covers on wooden
 * boards, with a subtle 3D perspective (outer books angled toward the center).
 */
@Composable
fun BookshelfView(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    val shelves = androidx.compose.runtime.remember(books) { books.chunked(BOOKS_PER_SHELF) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(shelves.size) { rowIndex ->
            ShelfRow(
                books = shelves[rowIndex],
                onBookClick = onBookClick,
                onBookLongClick = onBookLongClick
            )
        }
    }
}

@Composable
private fun ShelfRow(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.6f to ShelfBack.copy(alpha = 0.08f),
                        1f to ShelfBack.copy(alpha = 0.25f)
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                books.forEachIndexed { index, book ->
                    ShelfBookCover(
                        book = book,
                        // Angle outer books toward the shelf center for depth
                        rotationYDegrees = when {
                            books.size < 2 -> 0f
                            index == 0 -> 8f
                            index == books.size - 1 -> -8f
                            else -> 0f
                        },
                        onClick = { onBookClick(book) },
                        onLongClick = { onBookLongClick(book) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keep cover width constant on a partially filled shelf
                repeat(BOOKS_PER_SHELF - books.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        ShelfBoard()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookCover(
    book: Book,
    rotationYDegrees: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    Box(
        modifier = modifier
            .aspectRatio(0.65f)
            .graphicsLayer {
                rotationY = rotationYDegrees
                cameraDistance = 12f * density
            }
            .shadow(6.dp, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (book.coverPath != null) {
            AsyncImage(
                model = book.coverPath,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DefaultCover(title = book.title, author = book.author)
        }
        // Spine highlight on the lit side so the tilt reads as 3D
        if (rotationYDegrees != 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            if (rotationYDegrees > 0f) {
                                listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Black.copy(alpha = 0.18f))
                            } else {
                                listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent, Color.White.copy(alpha = 0.18f))
                            }
                        )
                    )
            )
        }
    }
}

@Composable
private fun ShelfBoard() {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Board top surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Brush.verticalGradient(listOf(ShelfFront, ShelfEdge)))
        )
        // Front edge / drop shadow under the board
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(ShelfEdge, ShelfBack.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )
    }
}
