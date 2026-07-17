package com.ebooks.reader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.ReadingStatus

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGridCard(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag("book_card")
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.65f)) {
                if (book.coverPath != null) {
                    AsyncImage(model = book.coverPath, contentDescription = book.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    DefaultCover(title = book.title, author = book.author)
                }

                if (book.readingStatus != ReadingStatus.UNREAD) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                        when (book.readingStatus) {
                            ReadingStatus.READING -> Icon(Icons.Default.MenuBook, "Reading", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(10.dp)).padding(2.dp))
                            ReadingStatus.READ -> Icon(Icons.Default.CheckCircle, "Read", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp).background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(10.dp)).padding(2.dp))
                            else -> {}
                        }
                    }
                }

                if (book.readingStatus == ReadingStatus.READING && book.totalChapters > 0) {
                    LinearProgressIndicator(
                        progress = { book.currentChapter.toFloat() / book.totalChapters },
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(book.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                if (book.author.isNotBlank() && book.author != "Unknown") {
                    Text(book.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListItem(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text("${book.author} • ${book.fileType.uppercase()}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            Box(modifier = Modifier.size(48.dp, 64.dp).clip(RoundedCornerShape(4.dp))) {
                if (book.coverPath != null) {
                    AsyncImage(model = book.coverPath, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    DefaultCover(title = book.title, author = book.author, modifier = Modifier.fillMaxSize())
                }
            }
        },
        trailingContent = {
            when (book.readingStatus) {
                ReadingStatus.READING -> Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                ReadingStatus.READ -> Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                else -> {}
            }
        },
        modifier = modifier.testTag("book_card").combinedClickable(onClick = onClick, onLongClick = onLongClick)
    )
}

@Composable
fun DefaultCover(title: String, author: String, modifier: Modifier = Modifier) {
    val gradientColors = remember(title) {
        val seed = title.hashCode()
        val hue = ((seed and 0xFF) / 255f) * 360f
        listOf(Color.hsl(hue, 0.5f, 0.35f), Color.hsl((hue + 30f) % 360f, 0.4f, 0.25f))
    }
    Box(modifier = modifier.fillMaxSize().background(Brush.verticalGradient(gradientColors)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Icon(Icons.Default.Book, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
            if (author.isNotBlank() && author != "Unknown") {
                Text(author, color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
