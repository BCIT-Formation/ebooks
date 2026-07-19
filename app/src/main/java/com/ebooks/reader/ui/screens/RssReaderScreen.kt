package com.ebooks.reader.ui.screens

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebooks.reader.R
import com.ebooks.reader.data.db.entities.Annotation
import com.ebooks.reader.data.db.entities.RssArticle
import com.ebooks.reader.data.dict.DictionaryClient
import com.ebooks.reader.data.repository.RssRepository
import com.ebooks.reader.ui.components.DrawingCanvas
import com.ebooks.reader.ui.components.DrawingSettings
import com.ebooks.reader.ui.components.DrawingToolbar
import com.ebooks.reader.ui.components.TooltipIconButton
import com.ebooks.reader.ui.components.rememberTtsSpeaker
import com.ebooks.reader.util.htmlToPlainText
import com.ebooks.reader.util.renderAnnotationsToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream

/**
 * Reads a downloaded RSS article fully offline, with the same drawing/annotation
 * tools and sharing as the book readers. The article HTML is rendered in a
 * locked-down WebView (see [ReaderScreen]) with a [DrawingCanvas] overlay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssReaderScreen(
    articleId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { RssRepository(context) }

    var article by remember { mutableStateOf<RssArticle?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var html by remember { mutableStateOf<String?>(null) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var isDrawingMode by remember { mutableStateOf(false) }
    var drawingSettings by remember { mutableStateOf(DrawingSettings()) }
    var annotations by remember { mutableStateOf<List<Annotation>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }
    var dictWord by remember { mutableStateOf<String?>(null) }
    var dictDefinition by remember { mutableStateOf<String?>(null) }
    var isDictLoading by remember { mutableStateOf(false) }
    val ttsSpeaker = rememberTtsSpeaker()

    LaunchedEffect(articleId) {
        withContext(Dispatchers.IO) {
            val loaded = repository.getArticle(articleId)
            article = loaded
            if (loaded != null) {
                html = buildArticleHtml(loaded)
                annotations = repository.getArticleAnnotations(articleId)
                repository.markRead(articleId)
            }
            isLoading = false
        }
    }

    val defineSelection: () -> Unit = defineSelection@{
        val webView = webViewRef.value ?: return@defineSelection
        webView.evaluateJavascript("window.getSelection().toString()") { value ->
            val selection = runCatching { JSONTokener(value).nextValue() as? String }.getOrNull().orEmpty()
            val word = selection.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
                .replace(Regex("[^\\p{L}''-]"), "")
            if (word.isNotBlank()) {
                dictWord = word
                isDictLoading = true
                scope.launch {
                    val def = withContext(Dispatchers.IO) {
                        runCatching {
                            DictionaryClient().lookup(word)
                        }.getOrNull()
                    }
                    dictDefinition = def
                    isDictLoading = false
                }
            } else {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.dict_select_word)) }
            }
        }
    }

    // Share the article (link + title) plus any annotation images.
    val shareArticle: () -> Unit = shareArticle@{
        val a = article ?: return@shareArticle
        scope.launch {
            val images = withContext(Dispatchers.IO) {
                if (annotations.isEmpty()) emptyList() else runCatching {
                    val dir = File(context.cacheDir, "share").also { it.mkdirs(); it.listFiles()?.forEach { f -> f.delete() } }
                    val bmp = renderAnnotationsToBitmap(annotations)
                    val file = File(dir, "article_notes.png")
                    FileOutputStream(file).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
                    bmp.recycle()
                    listOf(file)
                }.getOrDefault(emptyList())
            }
            val text = listOfNotNull(a.title, a.link).joinToString("\n")
            val authority = "${context.packageName}.fileprovider"
            val intent = if (images.isEmpty()) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, a.title)
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_SUBJECT, a.title)
                    putExtra(Intent.EXTRA_TEXT, text)
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        ArrayList(images.map { FileProvider.getUriForFile(context, authority, it) })
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_book)))
        }
    }

    val openOriginal: () -> Unit = openOriginal@{
        val link = article?.link ?: return@openOriginal
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link))) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(article?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    TooltipIconButton(Icons.Default.Translate, stringResource(R.string.dict_define), defineSelection)
                    TooltipIconButton(
                        Icons.Default.VolumeUp,
                        stringResource(R.string.read_aloud),
                        {
                            if (article != null) {
                                val plainText = htmlToPlainText(article.contentHtml)
                                ttsSpeaker.toggle(plainText)
                            }
                        }
                    )
                    TooltipIconButton(Icons.Default.Share, stringResource(R.string.share_book), shareArticle)
                    TooltipIconButton(Icons.Default.Edit, stringResource(R.string.draw_annotations), { isDrawingMode = !isDrawingMode })
                    if (article?.link != null) {
                        TooltipIconButton(Icons.Default.OpenInBrowser, stringResource(R.string.rss_open_original), openOriginal)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                article == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.cannot_open_book), color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.apply {
                                    javaScriptEnabled = false
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    allowFileAccess = false
                                    allowContentAccess = false
                                }
                                webViewClient = object : WebViewClient() {
                                    // Article text is offline; let taps on links open externally.
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                                        return true
                                    }
                                }
                                webViewRef.value = this
                            }
                        },
                        update = { webView ->
                            val content = html
                            if (content != null && webView.tag != content.hashCode()) {
                                webView.tag = content.hashCode()
                                webView.loadDataWithBaseURL(article?.link, content, "text/html", "UTF-8", null)
                            }
                        }
                    )

                    if (annotations.isNotEmpty() || isDrawingMode) {
                        DrawingCanvas(
                            modifier = Modifier.fillMaxSize(),
                            annotations = annotations,
                            isEnabled = isDrawingMode,
                            settings = drawingSettings,
                            onStrokeCompleted = { annotation ->
                                scope.launch {
                                    repository.addArticleAnnotation(articleId, annotation)
                                    annotations = repository.getArticleAnnotations(articleId)
                                }
                            },
                            onAnnotationDeleted = { annotation ->
                                scope.launch {
                                    repository.deleteAnnotation(annotation.id)
                                    annotations = repository.getArticleAnnotations(articleId)
                                }
                            }
                        )
                    }

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
                                scope.launch {
                                    repository.clearArticleAnnotations(articleId)
                                    annotations = emptyList()
                                }
                            },
                            onClearAll = {
                                scope.launch {
                                    repository.clearArticleAnnotations(articleId)
                                    annotations = emptyList()
                                }
                            },
                            isDrawingEnabled = true
                        )
                    }
                }
            }
        }
    }

    if (dictWord != null) {
        AlertDialog(
            onDismissRequest = { dictWord = null; dictDefinition = null },
            title = { Text(dictWord ?: "") },
            text = {
                if (isDictLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (dictDefinition != null) {
                    Text(dictDefinition ?: "", modifier = Modifier.fillMaxWidth())
                } else {
                    Text(stringResource(R.string.dict_not_found), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { dictWord = null; dictDefinition = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

/** Wraps an article's stored HTML fragment in a readable, self-contained page. */
private fun buildArticleHtml(article: RssArticle): String {
    val meta = listOfNotNull(article.author, article.link?.let { java.net.URI(it).host }).joinToString(" · ")
    return """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          body { margin: 0; padding: 20px 16px 48px; font-family: Georgia, serif; font-size: 18px;
                 line-height: 1.6; word-wrap: break-word; }
          h1 { font-size: 1.4em; line-height: 1.25; margin: 0 0 4px; }
          .meta { color: #888; font-size: 0.8em; margin-bottom: 16px; }
          img, figure, video, iframe { max-width: 100%; height: auto; }
          pre { overflow-x: auto; }
          a { color: #1565C0; }
          @media (prefers-color-scheme: dark) {
            body { background: #121212; color: #E6E1E5; } a { color: #82B4FF; }
          }
        </style></head><body>
        <h1>${escapeHtml(article.title)}</h1>
        ${if (meta.isNotBlank()) "<div class=\"meta\">${escapeHtml(meta)}</div>" else ""}
        ${article.contentHtml.ifBlank { escapeHtml(article.summary ?: "") }}
        </body></html>
    """.trimIndent()
}

private fun escapeHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
