package com.ebooks.reader.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ebooks.reader.data.db.AppDatabase
import com.ebooks.reader.data.db.entities.Annotation
import com.ebooks.reader.data.parser.Fb2Parser
import com.ebooks.reader.ui.components.DrawingCanvas
import com.ebooks.reader.ui.components.DrawingSettings
import com.ebooks.reader.ui.components.DrawingToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A screen that renders FB2 (FictionBook) ebook files.
 * FB2 is an XML-based format popular in Russian-speaking countries.
 * Content is rendered as HTML in a WebView with search and auto-scroll support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Fb2ReaderScreen(bookId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("FB2 Book") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Parsed HTML is held in state and pushed to the WebView from AndroidView's
    // update block: the WebView does not exist yet while the loading spinner is
    // shown, and WebView methods must be called on the main thread anyway.
    var htmlContent by remember { mutableStateOf<String?>(null) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var autoScrollSpeed by remember { mutableStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var isDrawingMode by remember { mutableStateOf(false) }
    var drawingSettings by remember { mutableStateOf(DrawingSettings()) }
    var annotations by remember { mutableStateOf<List<Annotation>>(emptyList()) }

    LaunchedEffect(bookId) {
        withContext(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getInstance(context).bookDao()
                val book = dao.getBookById(bookId)
                if (book == null) {
                    error = "Book not found."
                    isLoading = false
                    return@withContext
                }
                title = book.title

                val parser = Fb2Parser(context)
                val uri = Uri.parse(book.filePath)
                val fb2Book = parser.parse(uri)

                if (fb2Book == null) {
                    error = "Failed to parse FB2 file."
                    isLoading = false
                    return@withContext
                }

                htmlContent = fb2Book.htmlContent
                // Load annotations for this page
                annotations = dao.getAnnotationsByBook(bookId).first()
                    .filter { it.pageIdentifier == "chapter-0" && !it.isDeleted }
                isLoading = false
            } catch (e: Exception) {
                error = "Error loading book: ${e.localizedMessage}"
                isLoading = false
            }
        }
    }

    // Search: sync query to WebView's native find API
    LaunchedEffect(searchQuery, searchVisible) {
        val webView = webViewRef.value ?: return@LaunchedEffect
        if (searchVisible && searchQuery.isNotBlank()) {
            webView.findAllAsync(searchQuery)
        } else {
            webView.clearMatches()
        }
    }

    // Auto-scroll: send scroll commands at configurable speed
    LaunchedEffect(autoScrollSpeed) {
        if (autoScrollSpeed <= 0) return@LaunchedEffect
        while (autoScrollSpeed > 0) {
            delay(50L)
            webViewRef.value?.evaluateJavascript("window.scrollBy(0, ${autoScrollSpeed * 2})", null)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }
    ) { innerPadding ->
        when {
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.apply {
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    javaScriptEnabled = true
                                    // FB2 HTML is self-contained (cover inlined as a
                                    // data URI). Deny filesystem and network access so a
                                    // crafted book can't reach out.
                                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    @Suppress("DEPRECATION")
                                    allowFileAccessFromFileURLs = false
                                    @Suppress("DEPRECATION")
                                    allowUniversalAccessFromFileURLs = false
                                    blockNetworkLoads = true
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: android.webkit.WebResourceRequest
                                    ): Boolean = true
                                }
                                webViewRef.value = this
                            }
                        },
                        update = { webView ->
                            val html = htmlContent
                            // Guard against reloading (and losing scroll position) on
                            // every recomposition — only load when the content changes.
                            if (html != null && webView.tag != html.hashCode()) {
                                webView.tag = html.hashCode()
                                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                            }
                        }
                    )

                    // Drawing canvas overlay
                    if (annotations.isNotEmpty() || isDrawingMode) {
                        DrawingCanvas(
                            modifier = Modifier.fillMaxSize(),
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

                    // Top bar with search or title
                    AnimatedVisibility(
                        visible = showControls,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        if (searchVisible) {
                            Fb2SearchBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearchPrev = { webViewRef.value?.findNext(false) },
                                onSearchNext = { webViewRef.value?.findNext(true) },
                                onClose = { searchVisible = false; searchQuery = "" }
                            )
                        } else {
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
                                            tint = if (isDrawingMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(onClick = { searchVisible = true }) {
                                        Icon(Icons.Default.Search, "Search in book")
                                    }
                                }
                            )
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

                    // Bottom bar with auto-scroll and other controls (when not drawing)
                    AnimatedVisibility(
                        visible = showControls && !isDrawingMode,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Fb2BottomBar(
                            autoScrollSpeed = autoScrollSpeed,
                            onAutoScrollSpeedChange = { autoScrollSpeed = it }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Fb2SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchPrev: () -> Unit,
    onSearchNext: () -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search in book…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchNext() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search") }
        },
        actions = {
            IconButton(onClick = onSearchPrev) { Icon(Icons.Default.KeyboardArrowUp, "Previous match") }
            IconButton(onClick = onSearchNext) { Icon(Icons.Default.KeyboardArrowDown, "Next match") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    )
}

@Composable
private fun Fb2BottomBar(
    autoScrollSpeed: Int,
    onAutoScrollSpeedChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
