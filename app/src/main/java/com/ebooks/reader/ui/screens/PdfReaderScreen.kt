package com.ebooks.reader.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ebooks.reader.data.db.AppDatabase
import com.ebooks.reader.data.db.entities.ReadingProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A screen that renders PDF files page-by-page using [android.graphics.pdf.PdfRenderer].
 * Each page is rendered as a Bitmap at screen width and displayed in a scrollable column.
 * Supports reading progress tracking and auto-scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(bookId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("PDF") }
    var filePath by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var autoScrollSpeed by remember { mutableStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    // Load book metadata and restore scroll position
    LaunchedEffect(bookId) {
        val savedPage = withContext(Dispatchers.IO) {
            val book = AppDatabase.getInstance(context).bookDao().getBookById(bookId)
            if (book == null) {
                error = "Book not found."
                return@withContext null
            }
            title = book.title
            filePath = book.filePath
            try {
                val uri = Uri.parse(book.filePath)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                    }
                }
                AppDatabase.getInstance(context).bookDao().getReadingProgress(bookId)?.scrollPosition
            } catch (e: Exception) {
                error = "Could not open PDF: ${e.localizedMessage}"
                null
            }
        }
        // Scroll on the main thread — LazyListState must not be driven from IO.
        if (savedPage != null && savedPage > 0 && pageCount > 0) {
            listState.scrollToItem(savedPage.coerceIn(0, pageCount - 1))
        }
    }

    // Auto-scroll: continuously scroll down at configurable speed
    LaunchedEffect(autoScrollSpeed) {
        if (autoScrollSpeed <= 0) return@LaunchedEffect
        while (autoScrollSpeed > 0) {
            delay(50L)
            val currentPage = listState.firstVisibleItemIndex
            if (currentPage < pageCount - 1) {
                listState.animateScrollToItem(currentPage + 1)
                delay((100 * (11 - autoScrollSpeed)).toLong())  // Speed inversely affects delay
            }
        }
    }

    // Save scroll position periodically
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (pageCount > 0 && listState.firstVisibleItemIndex < pageCount) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(context).bookDao().saveReadingProgress(
                        ReadingProgress(
                            bookId = bookId,
                            chapterIndex = listState.firstVisibleItemIndex,
                            chapterHref = "page-${listState.firstVisibleItemIndex}",
                            scrollPosition = listState.firstVisibleItemIndex
                        )
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }
    ) { innerPadding ->
        when {
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            pageCount == 0 -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF444444)),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(pageCount) { pageIndex ->
                            PdfPageItem(
                                filePath = filePath ?: "",
                                pageIndex = pageIndex
                            )
                        }
                    }

                    // Top bar
                    AnimatedVisibility(
                        visible = showControls,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        TopAppBar(
                            title = { Text(title, maxLines = 1) },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                }
                            }
                        )
                    }

                    // Bottom bar with auto-scroll and page info
                    AnimatedVisibility(
                        visible = showControls,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        PdfBottomBar(
                            currentPage = listState.firstVisibleItemIndex + 1,
                            totalPages = pageCount,
                            autoScrollSpeed = autoScrollSpeed,
                            onAutoScrollSpeedChange = { autoScrollSpeed = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(filePath: String, pageIndex: Int) {
    val context = LocalContext.current
    var bitmap by remember(filePath, pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(filePath, pageIndex) {
        bitmap = withContext(Dispatchers.IO) {
            renderPdfPage(context, filePath, pageIndex)
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Page ${pageIndex + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }
}

private fun renderPdfPage(context: android.content.Context, filePath: String, pageIndex: Int): Bitmap? {
    return try {
        val uri = Uri.parse(filePath)
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex >= renderer.pageCount) return@use null
                renderer.openPage(pageIndex).use { page ->
                    val displayMetrics = context.resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val scale = screenWidth.toFloat() / page.width.toFloat()
                    val bitmapHeight = (page.height * scale).toInt()
                    val bmp = Bitmap.createBitmap(screenWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun PdfBottomBar(
    currentPage: Int,
    totalPages: Int,
    autoScrollSpeed: Int,
    onAutoScrollSpeedChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column {
            if (totalPages > 0) {
                LinearProgressIndicator(
                    progress = { currentPage.toFloat() / totalPages },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$currentPage / $totalPages",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(60.dp)
                )
                Icon(Icons.Default.SlowMotionVideo, null, modifier = Modifier.size(20.dp))
                Slider(
                    value = autoScrollSpeed.toFloat(),
                    onValueChange = { onAutoScrollSpeedChange(it.toInt()) },
                    valueRange = 0f..10f,
                    steps = 9,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (autoScrollSpeed == 0) "Off" else "$autoScrollSpeed",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(32.dp)
                )
            }
        }
    }
}
