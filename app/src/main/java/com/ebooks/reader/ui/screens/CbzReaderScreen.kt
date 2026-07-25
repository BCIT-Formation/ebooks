package com.ebooks.reader.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ebooks.reader.R
import com.ebooks.reader.data.db.AppDatabase
import com.ebooks.reader.data.db.entities.Annotation
import com.ebooks.reader.data.db.entities.FileType
import com.ebooks.reader.data.db.entities.ReadingProgress
import com.ebooks.reader.data.parser.ComicArchive
import com.ebooks.reader.ui.components.DrawingCanvas
import com.ebooks.reader.ui.components.DrawingSettings
import com.ebooks.reader.ui.components.DrawingToolbar
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A screen that renders CBZ and CBR (comic book archive) files. Pages are
 * extracted once to the cache directory ([ComicArchive]: ZipInputStream for
 * CBZ per ADR-001, junrar for CBR per ADR-007) and displayed full-width in
 * reading order. Reading progress (last visible page) is persisted like the
 * PDF reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CbzReaderScreen(bookId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(context.getString(R.string.cbz_default_title)) }
    var pages by remember { mutableStateOf<List<File>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isDrawingMode by remember { mutableStateOf(false) }
    var drawingSettings by remember { mutableStateOf(DrawingSettings()) }
    var annotationsByPage by remember { mutableStateOf<Map<Int, List<Annotation>>>(emptyMap()) }
    val listState = rememberLazyListState()

    LaunchedEffect(bookId) {
        val savedPage = withContext(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(context).bookDao()
            val book = dao.getBookById(bookId)
            if (book == null) {
                error = context.getString(R.string.reader_book_not_found)
                isLoading = false
                return@withContext null
            }
            title = book.title
            try {
                val extracted = extractComicPages(context, bookId, Uri.parse(book.filePath), book.fileType)
                if (extracted.isEmpty()) {
                    error = context.getString(R.string.cbz_no_images)
                } else {
                    pages = extracted
                }
                // Load annotations and group by page
                val allAnnotations = dao.getAnnotationsByBook(bookId).first()
                annotationsByPage = allAnnotations.groupBy { it.pageIndex }
                isLoading = false
                dao.getReadingProgress(bookId)?.scrollPosition
            } catch (e: Exception) {
                error = context.getString(R.string.cbz_could_not_open, e.localizedMessage)
                isLoading = false
                null
            }
        }
        // Restore on the main thread — LazyListState must not be driven from IO.
        if (savedPage != null && savedPage > 0 && pages.isNotEmpty()) {
            listState.scrollToItem(savedPage.coerceIn(0, pages.size - 1))
        }
    }

    // Persist the last visible page whenever it changes.
    LaunchedEffect(listState.firstVisibleItemIndex, pages.size) {
        val page = listState.firstVisibleItemIndex
        if (pages.isNotEmpty() && page < pages.size) {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).bookDao().saveReadingProgress(
                    ReadingProgress(
                        bookId = bookId,
                        chapterIndex = page,
                        chapterHref = "page-$page",
                        scrollPosition = page
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { isDrawingMode = !isDrawingMode }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.draw_annotations),
                            tint = if (isDrawingMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (pages.isNotEmpty()) {
                        Text(
                            stringResource(R.string.page_counter, listState.firstVisibleItemIndex + 1, pages.size),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        }
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
            isLoading -> {
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
                            .padding(innerPadding)
                            .background(Color.Black),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(pages, key = { _, file -> file.name }) { index, file ->
                            var zoom by remember(file.name) { mutableFloatStateOf(1f) }
                            var pan by remember(file.name) { mutableStateOf(Offset.Zero) }
                            var boxSize by remember(file.name) { mutableStateOf(IntSize.Zero) }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { boxSize = it }
                                    // Only two-or-more-finger gestures are consumed here, so a
                                    // single-finger drag still reaches the LazyColumn's scroll.
                                    .pointerInput(file.name) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            do {
                                                val event = awaitPointerEvent()
                                                if (event.changes.size >= 2) {
                                                    val zoomChange = event.calculateZoom()
                                                    val panChange = event.calculatePan()
                                                    val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                                    val maxX = (boxSize.width * (newZoom - 1f) / 2f).coerceAtLeast(0f)
                                                    val maxY = (boxSize.height * (newZoom - 1f) / 2f).coerceAtLeast(0f)
                                                    pan = if (newZoom > 1f) {
                                                        Offset(
                                                            (pan.x + panChange.x).coerceIn(-maxX, maxX),
                                                            (pan.y + panChange.y).coerceIn(-maxY, maxY)
                                                        )
                                                    } else {
                                                        Offset.Zero
                                                    }
                                                    zoom = newZoom
                                                    event.changes.forEach { it.consume() }
                                                }
                                            } while (event.changes.any { it.pressed })
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = stringResource(R.string.page_number_desc, index + 1),
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer(
                                            scaleX = zoom,
                                            scaleY = zoom,
                                            translationX = pan.x,
                                            translationY = pan.y
                                        )
                                )

                                if (annotationsByPage[index]?.isNotEmpty() == true || isDrawingMode) {
                                    DrawingCanvas(
                                        modifier = Modifier.fillMaxWidth(),
                                        annotations = annotationsByPage[index] ?: emptyList(),
                                        isEnabled = isDrawingMode && listState.firstVisibleItemIndex == index,
                                        settings = drawingSettings,
                                        onStrokeCompleted = { annotation ->
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val annotationWithPage = annotation.copy(
                                                        bookId = bookId,
                                                        pageIdentifier = "page-$index",
                                                        pageIndex = index
                                                    )
                                                    AppDatabase.getInstance(context).bookDao().insertAnnotation(annotationWithPage)
                                                    annotationsByPage = annotationsByPage.toMutableMap().apply {
                                                        put(index, (this[index] ?: emptyList()) + annotationWithPage)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Drawing toolbar (when drawing mode active)
                    AnimatedVisibility(
                        visible = isDrawingMode,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        DrawingToolbar(
                            settings = drawingSettings,
                            onSettingsChanged = { drawingSettings = it },
                            onClearPage = {
                                val currentPage = listState.firstVisibleItemIndex
                                annotationsByPage = annotationsByPage.toMutableMap().apply {
                                    put(currentPage, emptyList())
                                }
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        AppDatabase.getInstance(context).bookDao()
                                            .deletePageAnnotations(bookId, "page-$currentPage")
                                    }
                                }
                            },
                            onClearAll = {
                                annotationsByPage = emptyMap()
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        AppDatabase.getInstance(context).bookDao()
                                            .deleteAllAnnotations(bookId)
                                    }
                                }
                            },
                            isDrawingEnabled = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * Extracts all image entries of the comic archive at [uri] into
 * `cache/cbz/<bookId>/` via [ComicArchive] (ZIP for CBZ, RAR for CBR).
 * Returns the cached files directly when the book was already extracted.
 */
private fun extractComicPages(context: Context, bookId: String, uri: Uri, fileType: String): List<File> {
    val dir = File(context.cacheDir, "cbz/$bookId")
    dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.let {
        if (it.isNotEmpty()) return it
    }

    context.contentResolver.openInputStream(uri)?.use { input ->
        return if (fileType == FileType.CBR.extension) {
            ComicArchive.extractCbrPages(input, dir)
        } else {
            ComicArchive.extractCbzPages(input, dir)
        }
    }
    return emptyList()
}
