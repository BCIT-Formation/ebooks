package com.ebooks.reader.ui.screens

import android.annotation.SuppressLint
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
import androidx.compose.material.icons.outlined.CreateNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebooks.reader.ui.components.ChapterPanel
import com.ebooks.reader.ui.components.DrawingCanvas
import com.ebooks.reader.ui.components.DrawingToolbar
import com.ebooks.reader.ui.components.ReaderSettingsSheet
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
                                onDraw = { viewModel.toggleDrawingMode() }
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
                            isAutoScrolling = uiState.settings.autoScrollSpeed > 0
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
                }

                if (uiState.showSettingsPanel) {
                    ReaderSettingsSheet(
                        settings = uiState.settings,
                        onSettingsChanged = { viewModel.updateSettings(it) },
                        onDismiss = { viewModel.closeAllPanels() }
                    )
                }
            }
        }
    } // end Box
    } // end Scaffold content lambda
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
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                isVerticalScrollBarEnabled = false

                webViewClient = object : WebViewClient() {
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
    onDraw: () -> Unit = {}
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
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search in book") }
            IconButton(onClick = onDraw) { Icon(Icons.Outlined.CreateNote, "Draw annotations") }
            IconButton(onClick = onChapters) { Icon(Icons.Default.List, "Chapters") }
            IconButton(onClick = onBookmark) { Icon(Icons.Default.BookmarkAdd, "Add bookmark") }
            IconButton(onClick = onSettings) { Icon(Icons.Default.TextFormat, "Settings") }
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
private fun ReaderBottomBar(currentChapter: Int, totalChapters: Int, onPrevChapter: () -> Unit, onNextChapter: () -> Unit, onFontDecrease: () -> Unit, onFontIncrease: () -> Unit, onAutoScroll: () -> Unit, isAutoScrolling: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
        Column {
            if (totalChapters > 0) {
                LinearProgressIndicator(progress = { (currentChapter + 1).toFloat() / totalChapters }, modifier = Modifier.fillMaxWidth())
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevChapter, enabled = currentChapter > 0) { Icon(Icons.Default.NavigateBefore, "Previous") }
                Text(if (totalChapters > 0) "${currentChapter + 1} / $totalChapters" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onFontDecrease) { Icon(Icons.Default.Remove, "Smaller font") }
                IconButton(onClick = onAutoScroll) {
                    Icon(
                        if (isAutoScrolling) Icons.Default.PauseCircleOutline else Icons.Default.PlayCircleOutline,
                        if (isAutoScrolling) "Stop auto-scroll" else "Auto-scroll",
                        tint = if (isAutoScrolling) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onFontIncrease) { Icon(Icons.Default.Add, "Larger font") }
                IconButton(onClick = onNextChapter, enabled = totalChapters == 0 || currentChapter < totalChapters - 1) { Icon(Icons.Default.NavigateNext, "Next") }
            }
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Cannot open book", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Go back") }
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
