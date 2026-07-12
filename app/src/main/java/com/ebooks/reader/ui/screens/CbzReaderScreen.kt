package com.ebooks.reader.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ebooks.reader.data.db.AppDatabase
import com.ebooks.reader.data.db.entities.Annotation
import com.ebooks.reader.data.db.entities.ReadingProgress
import com.ebooks.reader.ui.components.DrawingCanvas
import com.ebooks.reader.ui.components.DrawingSettings
import com.ebooks.reader.ui.components.DrawingToolbar
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A screen that renders CBZ (comic book archive) files. A CBZ is a ZIP of
 * images; pages are extracted once to the cache directory (pure-Kotlin
 * [ZipInputStream], per ADR-001) and displayed full-width in reading order.
 * Reading progress (last visible page) is persisted like the PDF reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CbzReaderScreen(bookId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("Comic") }
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
                error = "Book not found."
                isLoading = false
                return@withContext null
            }
            title = book.title
            try {
                val extracted = extractCbzPages(context, bookId, Uri.parse(book.filePath))
                if (extracted.isEmpty()) {
                    error = "No images found in this comic archive."
                } else {
                    pages = extracted
                }
                // Load annotations and group by page
                val allAnnotations = dao.getAnnotationsForPage(bookId, "")
                    .filter { !it.isDeleted }
                    .groupBy { it.pageIndex }
                annotationsByPage = allAnnotations
                isLoading = false
                dao.getReadingProgress(bookId)?.scrollPosition
            } catch (e: Exception) {
                error = "Could not open comic: ${e.localizedMessage}"
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isDrawingMode = !isDrawingMode }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Draw",
                            tint = if (isDrawingMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    if (pages.isNotEmpty()) {
                        Text(
                            "${listState.firstVisibleItemIndex + 1} / ${pages.size}",
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
                            Box(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth()
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

private val CBZ_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

/**
 * Extracts all image entries of the CBZ at [uri] into `cache/cbz/<bookId>/`,
 * named `page_00000.ext` … in reading order (zip entry names sorted
 * case-insensitively, the de-facto CBZ page order). Returns the cached files
 * directly when the book was already extracted.
 */
private fun extractCbzPages(context: Context, bookId: String, uri: Uri): List<File> {
    val dir = File(context.cacheDir, "cbz/$bookId")
    dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.let {
        if (it.isNotEmpty()) return it
    }
    dir.mkdirs()

    // Single pass: extract to temp names (zip order), remember the entry name.
    val extracted = mutableListOf<Pair<String, File>>()
    context.contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val ext = entry.name.substringAfterLast('.', "").lowercase()
                if (!entry.isDirectory && ext in CBZ_IMAGE_EXTENSIONS) {
                    // Indexed file name: avoids zip path traversal and name clashes.
                    val tmp = File(dir, "tmp_%05d.%s".format(extracted.size, ext))
                    FileOutputStream(tmp).use { out -> zip.copyTo(out) }
                    extracted += entry.name to tmp
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    // Rename into reading order so the cached listing above stays consistent.
    return extracted
        .sortedBy { (name, _) -> name.lowercase() }
        .mapIndexed { index, (_, tmp) ->
            val target = File(dir, "page_%05d.%s".format(index, tmp.extension))
            if (tmp.renameTo(target)) target else tmp
        }
}
