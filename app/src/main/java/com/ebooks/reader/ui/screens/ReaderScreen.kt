package com.ebooks.reader.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ebooks.reader.BuildConfig
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebooks.reader.R
import com.ebooks.reader.ui.components.ChapterPanel
import com.ebooks.reader.ui.components.DrawingCanvas
import com.ebooks.reader.ui.components.DrawingToolbar
import com.ebooks.reader.data.dict.DictionaryClient
import com.ebooks.reader.data.dict.WordDefinition
import com.ebooks.reader.ui.components.ReaderSettingsSheet
import com.ebooks.reader.ui.components.TooltipIconButton
import com.ebooks.reader.ui.components.rememberTtsSpeaker
import com.ebooks.reader.util.htmlToPlainText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import com.ebooks.reader.viewmodel.OrientationLock
import com.ebooks.reader.viewmodel.ReaderThemeOption
import com.ebooks.reader.viewmodel.ReaderViewModel

@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModelFactory(LocalContext.current, bookId)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val ttsSpeaker = rememberTtsSpeaker()

    // One-time overlay teaching the reader's tap zones (shown on first book open).
    val readerPrefs = remember(context) {
        context.getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE)
    }
    var showGestureHint by remember { mutableStateOf(!readerPrefs.getBoolean("gesture_hint_shown", false)) }

    // Dictionary / translate on the current text selection.
    val dictClient = remember { DictionaryClient() }
    val dictScope = rememberCoroutineScope()
    var dictWord by remember { mutableStateOf<String?>(null) }
    var dictLoading by remember { mutableStateOf(false) }
    var dictResult by remember { mutableStateOf<WordDefinition?>(null) }
    var showDictSheet by remember { mutableStateOf(false) }

    LaunchedEffect(dictWord) {
        val w = dictWord ?: return@LaunchedEffect
        dictLoading = true
        dictResult = null
        dictResult = withContext(Dispatchers.IO) { dictClient.lookup(w) }
        dictLoading = false
    }

    val defineSelection: () -> Unit = defineSelection@{
        val webView = webViewRef.value ?: return@defineSelection
        webView.evaluateJavascript("window.getSelection().toString()") { value ->
            val selection = runCatching { JSONTokener(value).nextValue() as? String }.getOrNull().orEmpty()
            val word = selection.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
                .replace(Regex("[^\\p{L}'’-]"), "")
            if (word.isNotBlank()) {
                dictWord = word
                showDictSheet = true
            } else {
                dictScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.dict_select_word)) }
            }
        }
    }

    // The queued speech text is stale once the chapter changes
    LaunchedEffect(uiState.currentChapterIndex) { ttsSpeaker.stop() }

    // Share the actual book file (plus rendered annotation images) via the system sheet.
    val shareBook: () -> Unit = {
        viewModel.prepareShare { bundle ->
            if (bundle == null) {
                android.widget.Toast.makeText(context, context.getString(R.string.share_failed), android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val authority = "${context.packageName}.fileprovider"
                val bookUri = androidx.core.content.FileProvider.getUriForFile(context, authority, bundle.bookFile)
                val imageUris = bundle.annotationImages.map {
                    androidx.core.content.FileProvider.getUriForFile(context, authority, it)
                }
                val title = uiState.book?.title ?: ""
                val intent = if (imageUris.isEmpty()) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = bundle.mimeType
                        putExtra(Intent.EXTRA_STREAM, bookUri)
                        putExtra(Intent.EXTRA_SUBJECT, title)
                    }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(listOf(bookUri) + imageUris))
                        putExtra(Intent.EXTRA_SUBJECT, title)
                    }
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_book)))
            }
        }
    }

    // Auto-scroll: collect ticks from ViewModel and drive WebView scrolling
    LaunchedEffect(Unit) {
        viewModel.autoScrollTick.collect { speed ->
            webViewRef.value?.evaluateJavascript("window.scrollBy(0, ${speed * 2})", null)
        }
    }

    // In-page search: sync query to WebView's native find API
    LaunchedEffect(uiState.searchQuery, uiState.isSearchVisible) {
        val webView = webViewRef.value ?: return@LaunchedEffect
        if (uiState.isSearchVisible && uiState.searchQuery.isNotBlank()) {
            webView.findAllAsync(uiState.searchQuery)
        } else {
            webView.clearMatches()
        }
    }

    // Apply per-book screen orientation lock
    DisposableEffect(uiState.settings.orientationLock) {
        activity?.requestedOrientation = when (uiState.settings.orientationLock) {
            OrientationLock.PORTRAIT   -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationLock.LANDSCAPE  -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationLock.UNSPECIFIED -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Tilt-to-scroll: register accelerometer when enabled
    DisposableEffect(uiState.settings.tiltScrollEnabled) {
        if (!uiState.settings.tiltScrollEnabled) return@DisposableEffect onDispose {}
        val sensorManager = context.getSystemService<SensorManager>() ?: return@DisposableEffect onDispose {}
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return@DisposableEffect onDispose {}

        val listener = object : SensorEventListener {
            private var baseline: Float? = null

            override fun onSensorChanged(event: SensorEvent) {
                val tilt = -event.values[1]  // Y axis: forward tilt = positive
                if (baseline == null) { baseline = tilt; return }
                val delta = tilt - (baseline ?: tilt)
                if (delta > 1.5f) {   // tilted forward → scroll down
                    val pixels = ((delta - 1.5f) * 4).toInt().coerceIn(1, 20)
                    webViewRef.value?.evaluateJavascript("window.scrollBy(0, $pixels)", null)
                } else if (delta < -1.5f) {  // tilted back → scroll up
                    val pixels = ((-delta - 1.5f) * 4).toInt().coerceIn(1, 20)
                    webViewRef.value?.evaluateJavascript("window.scrollBy(0, -$pixels)", null)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // Show chapter-load failures as a dismissable snackbar (non-fatal)
    LaunchedEffect(uiState.chapterError) {
        val msg = uiState.chapterError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Long)
        viewModel.dismissChapterError()
    }

    val bgColor = remember(uiState.settings.themeOption) {
        when (uiState.settings.themeOption) {
            ReaderThemeOption.LIGHT -> Color.White
            ReaderThemeOption.DARK -> Color(0xFF1a1a2e)
            ReaderThemeOption.SEPIA -> Color(0xFFF3EAD3)
            ReaderThemeOption.NIGHT -> Color(0xFF0d0d0d)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().background(bgColor).padding(innerPadding)) {
        when {
            uiState.error != null -> ErrorScreen(message = uiState.error!!, onBack = onBack)
            uiState.book == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    EpubWebView(
                        html = uiState.currentChapterHtml,
                        bgColorHex = when (uiState.settings.themeOption) {
                            ReaderThemeOption.LIGHT -> "#FFFFFF"
                            ReaderThemeOption.DARK -> "#1a1a2e"
                            ReaderThemeOption.SEPIA -> "#F3EAD3"
                            ReaderThemeOption.NIGHT -> "#0d0d0d"
                        },
                        onScrollChanged = viewModel::saveProgress,
                        onCenterTap = { viewModel.toggleControls() },
                        onSwipeLeft = { viewModel.nextChapter() },
                        onSwipeRight = { viewModel.previousChapter() },
                        webViewRef = webViewRef,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Drawing canvas overlay
                    if (uiState.annotations.isNotEmpty() || uiState.isDrawingMode) {
                        DrawingCanvas(
                            modifier = Modifier.fillMaxSize(),
                            annotations = uiState.annotations,
                            isEnabled = uiState.isDrawingMode,
                            settings = uiState.drawingSettings,
                            onStrokeCompleted = { viewModel.saveAnnotation(it) },
                            onAnnotationDeleted = { viewModel.deleteAnnotation(it.id) }
                        )
                    }

                    // Night-light warm overlay — above WebView content, below all controls
                    if (uiState.settings.nightLightAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFFF8C00).copy(alpha = uiState.settings.nightLightAlpha))
                        )
                    }

                    if (uiState.isChapterLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopStart))
                    }

                    AnimatedVisibility(
                        visible = uiState.showControls,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        if (uiState.isSearchVisible) {
                            SearchTopBar(
                                query = uiState.searchQuery,
                                onQueryChange = viewModel::setSearchQuery,
                                onSearchPrev = { webViewRef.value?.findNext(false) },
                                onSearchNext = { webViewRef.value?.findNext(true) },
                                onClose = { viewModel.toggleSearch() }
                            )
                        } else {
                            ReaderTopBar(
                                title = uiState.book?.title ?: "",
                                chapterTitle = uiState.chapters.getOrNull(uiState.currentChapterIndex)?.title ?: "",
                                onBack = onBack,
                                onChapters = { viewModel.toggleChapterPanel() },
                                onBookmark = { viewModel.addBookmark() },
                                onSettings = { viewModel.toggleSettingsPanel() },
                                onSearch = { viewModel.toggleSearch() },
                                onDraw = { viewModel.toggleDrawingMode() },
                                onShare = shareBook,
                                onDefine = defineSelection
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = uiState.showControls && !uiState.isDrawingMode,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        ReaderBottomBar(
                            currentChapter = uiState.currentChapterIndex,
                            totalChapters = uiState.chapters.size,
                            onPrevChapter = { viewModel.previousChapter() },
                            onNextChapter = { viewModel.nextChapter() },
                            onFontDecrease = { viewModel.decreaseFontSize() },
                            onFontIncrease = { viewModel.increaseFontSize() },
                            onAutoScroll = { viewModel.toggleAutoScroll() },
                            isAutoScrolling = uiState.settings.autoScrollSpeed > 0,
                            onTts = {
                                ttsSpeaker.toggle(
                                    htmlToPlainText(uiState.currentChapterHtml.orEmpty()),
                                    uiState.book?.language
                                )
                            },
                            isTtsSpeaking = ttsSpeaker.isSpeaking
                        )
                    }

                    AnimatedVisibility(
                        visible = uiState.showControls && uiState.isDrawingMode,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        DrawingToolbar(
                            settings = uiState.drawingSettings,
                            onSettingsChanged = { viewModel.updateDrawingSettings(it) },
                            onClearPage = { viewModel.clearPageAnnotations() },
                            onClearAll = { viewModel.clearAllAnnotations() },
                            isDrawingEnabled = uiState.isDrawingMode
                        )
                    }

                    AnimatedVisibility(
                        visible = uiState.showChapterPanel,
                        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        ChapterPanel(
                            chapters = uiState.chapters,
                            currentChapterIndex = uiState.currentChapterIndex,
                            bookmarks = uiState.bookmarks,
                            onChapterSelected = { viewModel.loadChapter(it) },
                            onBookmarkSelected = { viewModel.navigateToBookmark(it) },
                            onBookmarkDeleted = { viewModel.deleteBookmark(it) },
                            onClose = { viewModel.closeAllPanels() }
                        )
                    }

                    // First-run tap-zone guide, above everything else
                    GestureHintOverlay(visible = showGestureHint) {
                        readerPrefs.edit().putBoolean("gesture_hint_shown", true).apply()
                        showGestureHint = false
                    }
                }

                if (uiState.showSettingsPanel) {
                    ReaderSettingsSheet(
                        settings = uiState.settings,
                        onSettingsChanged = { viewModel.updateSettings(it) },
                        onDismiss = { viewModel.closeAllPanels() },
                        customFonts = uiState.customFonts,
                        onImportFont = { viewModel.importFont(it) }
                    )
                }

                if (showDictSheet && dictWord != null) {
                    DictionarySheet(
                        word = dictWord!!,
                        loading = dictLoading,
                        result = dictResult,
                        onTranslate = { translateWord(context, dictWord!!) },
                        onSearch = { webSearch(context, dictWord!!) },
                        onDismiss = { showDictSheet = false }
                    )
                }
            }
        }
    } // end Box
    } // end Scaffold content lambda
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictionarySheet(
    word: String,
    loading: Boolean,
    result: WordDefinition?,
    onTranslate: () -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result?.word ?: word, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                result?.phonetic?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.dict_looking_up))
                }
                result == null -> Text(stringResource(R.string.dict_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> result.meanings.take(3).forEach { meaning ->
                    if (meaning.partOfSpeech.isNotBlank()) {
                        Text(meaning.partOfSpeech, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    meaning.definitions.forEach { def ->
                        Text("• $def", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTranslate, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Translate, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.dict_translate))
                }
                OutlinedButton(onClick = onSearch, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.dict_search))
                }
            }
        }
    }
}

/** Sends the word to a translator: prefers the system text-processor (Google Translate, …). */
private fun translateWord(context: android.content.Context, word: String) {
    val process = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, word)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }
    if (process.resolveActivity(context.packageManager) != null) {
        context.startActivity(Intent.createChooser(process, word))
    } else {
        val uri = android.net.Uri.parse("https://translate.google.com/?sl=auto&tl=&op=translate&text=" +
            java.net.URLEncoder.encode(word, "UTF-8"))
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

private fun webSearch(context: android.content.Context, word: String) {
    val search = Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra(android.app.SearchManager.QUERY, word) }
    if (search.resolveActivity(context.packageManager) != null) {
        context.startActivity(search)
    } else {
        val uri = android.net.Uri.parse("https://www.google.com/search?q=" + java.net.URLEncoder.encode(word, "UTF-8"))
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EpubWebView(
    html: String?,
    bgColorHex: String,
    onScrollChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    webViewRef: MutableState<WebView?>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    textZoom = 100
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    // Book content is fully self-contained (images inlined as data
                    // URIs, external CSS stripped). Deny the WebView every avenue a
                    // malicious book could use to reach the filesystem or the network.
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    blockNetworkLoads = true
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                isVerticalScrollBarEnabled = false

                webViewClient = object : WebViewClient() {
                    // Block the book from navigating the reader to any external page;
                    // in-page anchors (footnotes) don't trigger this callback.
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: android.webkit.WebResourceRequest
                    ): Boolean = true

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript("""
                            (function() {
                                var last = 0;
                                window.addEventListener('scroll', function() {
                                    var y = Math.round(window.scrollY);
                                    if (Math.abs(y - last) > 50) { last = y; Android.onScroll(y); }
                                }, { passive: true });
                            })();
                        """.trimIndent(), null)
                    }
                }

                addJavascriptInterface(object {
                    @JavascriptInterface fun onScroll(position: Int) { onScrollChanged(position) }
                }, "Android")

                webViewRef.value = this
            }
        },
        update = { webView ->
            if (html != null) {
                try {
                    webView.setBackgroundColor(android.graphics.Color.parseColor(bgColorHex))
                } catch (_: Exception) {}
                webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { offset ->
                    val w = size.width
                    when {
                        offset.x < w * 0.25f -> onSwipeRight()
                        offset.x > w * 0.75f -> onSwipeLeft()
                        else -> onCenterTap()
                    }
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    chapterTitle: String,
    onBack: () -> Unit,
    onChapters: () -> Unit,
    onBookmark: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onDraw: () -> Unit = {},
    onShare: () -> Unit = {},
    onDefine: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (chapterTitle.isNotBlank()) {
                    Text(chapterTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        actions = {
            TooltipIconButton(Icons.Default.Translate, stringResource(R.string.dict_define), onDefine)
            TooltipIconButton(Icons.Default.Search, stringResource(R.string.search_in_book), onSearch)
            TooltipIconButton(Icons.Default.Share, stringResource(R.string.share_book), onShare)
            TooltipIconButton(Icons.Default.Edit, stringResource(R.string.draw_annotations), onDraw)
            TooltipIconButton(Icons.Default.List, stringResource(R.string.chapters), onChapters)
            TooltipIconButton(Icons.Default.BookmarkAdd, stringResource(R.string.add_bookmark), onBookmark)
            TooltipIconButton(Icons.Default.TextFormat, stringResource(R.string.settings), onSettings)
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onSearchPrev: () -> Unit, onSearchNext: () -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_in_book_hint)) },
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
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.close_search)) }
        },
        actions = {
            IconButton(onClick = onSearchPrev) { Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.previous_match)) }
            IconButton(onClick = onSearchNext) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.next_match)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    )
}

@Composable
private fun ReaderBottomBar(currentChapter: Int, totalChapters: Int, onPrevChapter: () -> Unit, onNextChapter: () -> Unit, onFontDecrease: () -> Unit, onFontIncrease: () -> Unit, onAutoScroll: () -> Unit, isAutoScrolling: Boolean, onTts: () -> Unit = {}, isTtsSpeaking: Boolean = false) {
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
        Column {
            if (totalChapters > 0) {
                LinearProgressIndicator(progress = { (currentChapter + 1).toFloat() / totalChapters }, modifier = Modifier.fillMaxWidth())
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TooltipIconButton(Icons.Default.NavigateBefore, stringResource(R.string.previous_chapter), onPrevChapter, enabled = currentChapter > 0)
                Text(if (totalChapters > 0) "${currentChapter + 1} / $totalChapters" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TooltipIconButton(Icons.Default.Remove, stringResource(R.string.smaller_font), onFontDecrease)
                TooltipIconButton(
                    icon = if (isAutoScrolling) Icons.Default.PauseCircleOutline else Icons.Default.PlayCircleOutline,
                    label = stringResource(if (isAutoScrolling) R.string.stop_auto_scroll else R.string.auto_scroll),
                    onClick = onAutoScroll,
                    tint = if (isAutoScrolling) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                TooltipIconButton(Icons.Default.Add, stringResource(R.string.larger_font), onFontIncrease)
                TooltipIconButton(
                    icon = if (isTtsSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    label = stringResource(if (isTtsSpeaking) R.string.stop_read_aloud else R.string.read_aloud),
                    onClick = onTts,
                    tint = if (isTtsSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                TooltipIconButton(Icons.Default.NavigateNext, stringResource(R.string.next_chapter), onNextChapter, enabled = totalChapters == 0 || currentChapter < totalChapters - 1)
            }
        }
    }
}

/** First-run overlay that explains the reader's three tap zones. */
@Composable
private fun GestureHintOverlay(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.reader_hint_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GestureZone(Icons.Default.ChevronLeft, stringResource(R.string.reader_hint_prev))
                    GestureZone(Icons.Default.TouchApp, stringResource(R.string.reader_hint_menu))
                    GestureZone(Icons.Default.ChevronRight, stringResource(R.string.reader_hint_next))
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onDismiss) { Text(stringResource(R.string.reader_hint_got_it)) }
            }
        }
    }
}

@Composable
private fun GestureZone(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ErrorScreen(message: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.cannot_open_book), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack) { Text(stringResource(R.string.go_back)) }
    }
}

/** Unwrap the hosting [android.app.Activity] from a (possibly wrapped) Context. */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

class ReaderViewModelFactory(private val context: android.content.Context, private val bookId: String) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
        val handle = SavedStateHandle(mapOf("bookId" to bookId))
        @Suppress("UNCHECKED_CAST")
        return ReaderViewModel(context.applicationContext as android.app.Application, handle) as T
    }
}
