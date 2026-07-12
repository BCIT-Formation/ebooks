package com.ebooks.reader.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ebooks.reader.data.db.AppDatabase
import com.ebooks.reader.data.db.entities.Annotation
import com.ebooks.reader.ui.components.DrawingCanvas
import com.ebooks.reader.ui.components.DrawingSettings
import com.ebooks.reader.ui.components.DrawingToolbar
import com.ebooks.reader.ui.components.rememberTtsSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxtReaderScreen(bookId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var paragraphs by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isDrawingMode by remember { mutableStateOf(false) }
    var drawingSettings by remember { mutableStateOf(DrawingSettings()) }
    var annotations by remember { mutableStateOf<List<Annotation>>(emptyList()) }
    val ttsSpeaker = rememberTtsSpeaker()

    LaunchedEffect(bookId) {
        withContext(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(context).bookDao()
            val book = dao.getBookById(bookId)
            if (book == null) {
                error = "Book not found."
                isLoading = false
                return@withContext
            }
            title = book.title
            try {
                val uri = Uri.parse(book.filePath)
                val text = context.contentResolver.openInputStream(uri)
                    ?.use { it.bufferedReader().readText() }
                    ?: throw IllegalStateException("Could not open file")
                paragraphs = text.split(Regex("\\n{2,}"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { text.lines().filter { it.isNotBlank() } }
                // Load annotations for this page
                annotations = dao.getAnnotationsByBook(bookId).first()
                    .filter { it.pageIdentifier == "chapter-0" && !it.isDeleted }
            } catch (e: Exception) {
                error = "Could not read file: ${e.localizedMessage}"
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifEmpty { "Text" }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { ttsSpeaker.toggle(paragraphs.joinToString("\n\n")) }) {
                        Icon(
                            if (ttsSpeaker.isSpeaking) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = if (ttsSpeaker.isSpeaking) "Stop reading aloud" else "Read aloud",
                            tint = if (ttsSpeaker.isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { isDrawingMode = !isDrawingMode }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Draw",
                            tint = if (isDrawingMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
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
            else -> {
                val listState = rememberLazyListState()
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(paragraphs) { _, paragraph ->
                            Text(
                                text = paragraph,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Drawing canvas overlay
                    if (annotations.isNotEmpty() || isDrawingMode) {
                        DrawingCanvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            annotations = annotations,
                            isEnabled = isDrawingMode,
                            settings = drawingSettings,
                            onStrokeCompleted = { annotation ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val annotationWithPage = annotation.copy(
                                            bookId = bookId,
                                            pageIdentifier = "chapter-0",
                                            pageIndex = 0
                                        )
                                        AppDatabase.getInstance(context).bookDao().insertAnnotation(annotationWithPage)
                                        annotations = annotations + annotationWithPage
                                    }
                                }
                            }
                        )
                    }

                    // Drawing toolbar
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
                                annotations = emptyList()
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        AppDatabase.getInstance(context).bookDao()
                                            .deletePageAnnotations(bookId, "chapter-0")
                                    }
                                }
                            },
                            onClearAll = {
                                annotations = emptyList()
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
