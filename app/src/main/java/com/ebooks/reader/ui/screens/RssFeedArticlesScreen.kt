package com.ebooks.reader.ui.screens

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

enum class ArticleSortOrder { NEWEST, OLDEST, UNREAD_FIRST }
data class UndoAction(val articleId: String, val actionType: String, val previousValue: Boolean)

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
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(ArticleSortOrder.NEWEST) }
    var lastUndoAction by remember { mutableStateOf<UndoAction?>(null) }
    var showSearchBar by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        val msg = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeMessage()
    }

    val feed = uiState.feeds.firstOrNull { it.id == feedId }
    val allArticles = uiState.articles.filter { it.feedId == feedId }

    // Filter articles by search query
    val filteredArticles = if (searchQuery.isBlank()) {
        allArticles
    } else {
        allArticles.filter { article ->
            article.title.contains(searchQuery, ignoreCase = true) ||
            article.summary?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    // Sort articles based on selected order
    val sortedArticles = when (sortOrder) {
        ArticleSortOrder.NEWEST -> filteredArticles.sortedByDescending { it.publishedAt }
        ArticleSortOrder.OLDEST -> filteredArticles.sortedBy { it.publishedAt }
        ArticleSortOrder.UNREAD_FIRST -> filteredArticles.sortedWith(compareBy({ it.isRead }, { -it.publishedAt }))
    }

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
                                    onClick = { showMenu = false; viewModel.selectAll(sortedArticles.map { it.id }) }
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            } else if (showSearchBar) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.rss_search_articles)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { showSearchBar = false; searchQuery = "" }) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(feed?.title ?: stringResource(R.string.rss_title), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${sortedArticles.size} ${stringResource(R.string.rss_articles)}", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        TooltipIconButton(Icons.Default.Search, stringResource(R.string.rss_search_articles), { showSearchBar = true })
                        Box {
                            TooltipIconButton(Icons.Default.MoreVert, stringResource(R.string.settings), { showMenu = true })
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rss_sort_newest)) },
                                    onClick = { showMenu = false; sortOrder = ArticleSortOrder.NEWEST }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rss_sort_oldest)) },
                                    onClick = { showMenu = false; sortOrder = ArticleSortOrder.OLDEST }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rss_sort_unread_first)) },
                                    onClick = { showMenu = false; sortOrder = ArticleSortOrder.UNREAD_FIRST }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rss_mark_as_read)) },
                                    leadingIcon = { Icon(Icons.Default.DoneAll, null) },
                                    onClick = { showMenu = false; viewModel.markArticlesAsRead(sortedArticles.map { it.id }) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rss_select_multiple)) },
                                    onClick = { showMenu = false; viewModel.setSelectionMode(true) }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            when {
                sortedArticles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val message = if (searchQuery.isNotBlank()) {
                        stringResource(R.string.rss_no_results)
                    } else if (allArticles.isEmpty()) {
                        stringResource(R.string.rss_no_articles)
                    } else {
                        "No articles match filters"
                    }
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sortedArticles, key = { it.id }) { article ->
                        ArticleRow(
                            article = article,
                            feedTitle = feed?.title.orEmpty(),
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = article.id in uiState.selectedArticleIds,
                            onToggleSelection = { viewModel.toggleArticleSelection(article.id) },
                            onToggleFavorite = {
                                lastUndoAction = UndoAction(article.id, "favorite", article.isFavorite)
                                viewModel.toggleFavorite(article.id)
                            },
                            onToggleRead = {
                                lastUndoAction = UndoAction(article.id, "read", article.isRead)
                                viewModel.toggleRead(article.id)
                            },
                            onLongPress = { viewModel.setSelectionMode(true); viewModel.toggleArticleSelection(article.id) },
                            onOpenArticle = { if (!uiState.isSelectionMode) onOpenArticle(article.id) else viewModel.toggleArticleSelection(article.id) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    // Undo action snackbar
    if (lastUndoAction != null) {
        LaunchedEffect(lastUndoAction) {
            val action = lastUndoAction ?: return@LaunchedEffect
            val result = snackbarHostState.showSnackbar(
                message = "Action performed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                // Undo the action
                when (action.actionType) {
                    "favorite" -> viewModel.toggleFavorite(action.articleId)
                    "read" -> viewModel.toggleRead(action.articleId)
                }
            }
            lastUndoAction = null
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
    val swipeThreshold = 60f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { swipeOffset = 0f }) { change, dragAmount ->
                    change.consume()
                    swipeOffset = (swipeOffset + dragAmount).coerceIn(-150f, 150f)
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
        if (swipeOffset > 0) {
            val progress = (swipeOffset / swipeThreshold).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(progress)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.rss_mark_as_read),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else if (swipeOffset < 0) {
            val progress = ((-swipeOffset) / swipeThreshold).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(progress)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = stringResource(R.string.rss_favorite),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
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
                        val iconColor = if (article.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        Icon(
                            if (article.isFavorite) Icons.Filled.Star else Icons.Default.RssFeed,
                            null,
                            tint = iconColor,
                            modifier = Modifier.size(if (article.isFavorite) 24.dp else 20.dp)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = (swipeOffset / 10).dp)
        )
    }
}
