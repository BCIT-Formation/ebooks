package com.ebooks.reader.ui.screens

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebooks.reader.R
import com.ebooks.reader.data.db.entities.RssArticle
import com.ebooks.reader.data.db.entities.RssFeed
import com.ebooks.reader.ui.components.TooltipIconButton
import com.ebooks.reader.viewmodel.RssViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedArticlesScreen(
    feedId: String,
    onOpenArticle: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RssViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        val msg = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeMessage()
    }

    val feed = uiState.feeds.firstOrNull { it.id == feedId }
    val articles = uiState.articles.filter { it.feedId == feedId }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.rss_articles_selected, uiState.selectedArticleIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.setSelectionMode(false) }) {
                            Icon(Icons.Default.Close, stringResource(R.string.close))
                        }
                    },
                    actions = {
                        TooltipIconButton(
                            Icons.Default.Check,
                            stringResource(R.string.rss_mark_as_read),
                            { viewModel.markSelectedAsRead() }
                        )
                        Box {
                            TooltipIconButton(Icons.Default.MoreVert, stringResource(R.string.settings), { showMenu = true })
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rss_mark_as_unread)) },
                                    onClick = { showMenu = false; viewModel.markSelectedAsUnread() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rss_select_all)) },
                                    onClick = { showMenu = false; viewModel.selectAll(articles.map { it.id }) }
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(feed?.title ?: stringResource(R.string.rss_title), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${articles.size} ${stringResource(R.string.rss_articles)}", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        TooltipIconButton(Icons.Default.Refresh, stringResource(R.string.rss_refresh), { viewModel.refresh() })
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            when {
                articles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.rss_no_articles), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(articles, key = { it.id }) { article ->
                        ArticleRow(
                            article = article,
                            feedTitle = feed?.title.orEmpty(),
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = article.id in uiState.selectedArticleIds,
                            onToggleSelection = { viewModel.toggleArticleSelection(article.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(article.id) },
                            onToggleRead = { viewModel.toggleRead(article.id) },
                            onLongPress = { viewModel.setSelectionMode(true); viewModel.toggleArticleSelection(article.id) },
                            onOpenArticle = { if (!uiState.isSelectionMode) onOpenArticle(article.id) else viewModel.toggleArticleSelection(article.id) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ArticleRow(
    article: RssArticle,
    feedTitle: String,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRead: () -> Unit,
    onLongPress: () -> Unit,
    onOpenArticle: () -> Unit
) {
    var swipeOffset by remember { mutableStateOf(0f) }
    val swipeThreshold = 80f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    swipeOffset = (swipeOffset + dragAmount).coerceIn(-200f, 200f)
                    if (swipeOffset < -swipeThreshold) {
                        onToggleFavorite()
                        swipeOffset = 0f
                    } else if (swipeOffset > swipeThreshold) {
                        onToggleRead()
                        swipeOffset = 0f
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { onOpenArticle() }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.CenterStart),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (swipeOffset > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.CenterEnd)
                        .fillMaxWidth(swipeOffset / swipeThreshold),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.rss_mark_as_read),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.CenterStart),
            contentAlignment = Alignment.CenterStart
        ) {
            if (swipeOffset < 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.CenterStart)
                        .fillMaxWidth((-swipeOffset) / swipeThreshold),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = stringResource(R.string.rss_favorite),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        ListItem(
            headlineContent = {
                Text(
                    article.title,
                    fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
                val date = if (article.publishedAt > 0) dateFormatter.format(Date(article.publishedAt)) else ""
                Text(listOf(feedTitle, date).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingContent = {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        if (article.isFavorite) {
                            Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        if (!article.isRead) {
                            Box(Modifier.size(10.dp).padding(top = 4.dp)) {
                                Icon(Icons.Default.RssFeed, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Icon(Icons.Default.RssFeed, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = (swipeOffset / 10).dp)
        )
    }
}
