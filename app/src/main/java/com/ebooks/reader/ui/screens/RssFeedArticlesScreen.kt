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
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Filter
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
enum class ArticleViewMode { LIST, GRID, SHELF_3D }
enum class ArticleFilter { ALL, UNREAD, FAVORITES }
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
    var showFilterMenu by remember { mutableStateOf(false) }
    var showViewModeMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(ArticleSortOrder.NEWEST) }
    var viewMode by remember { mutableStateOf(ArticleViewMode.LIST) }
    var filter by remember { mutableStateOf(ArticleFilter.ALL) }
    var lastUndoAction by remember { mutableStateOf<UndoAction?>(null) }
    var showSearchBar by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        val msg = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeMessage()
    }

    val feed = uiState.feeds.firstOrNull { it.id == feedId }
    val allArticles = uiState.articles.filter { it.feedId == feedId }

    // Apply filter by status (all, unread, favorites)
    val filteredByStatus = when (filter) {
        ArticleFilter.ALL -> allArticles
        ArticleFilter.UNREAD -> allArticles.filter { !it.isRead }
        ArticleFilter.FAVORITES -> allArticles.filter { it.isFavorite }
    }

    // Filter articles by search query
    val filteredArticles = if (searchQuery.isBlank()) {
        filteredByStatus
    } else {
        filteredByStatus.filter { article ->
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
                            TooltipIconButton(Icons.Default.Filter, stringResource(R.string.filter), { showFilterMenu = true })
                            DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("All") },
                                    onClick = { showFilterMenu = false; filter = ArticleFilter.ALL }
                                )
                                DropdownMenuItem(
                                    text = { Text("Unread") },
                                    onClick = { showFilterMenu = false; filter = ArticleFilter.UNREAD }
                                )
                                DropdownMenuItem(
                                    text = { Text("Favorites") },
                                    onClick = { showFilterMenu = false; filter = ArticleFilter.FAVORITES }
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showViewModeMenu = true }) {
                                Icon(when(viewMode) {
                                    ArticleViewMode.LIST -> Icons.Default.ViewList
                                    ArticleViewMode.GRID -> Icons.Default.GridView
                                    ArticleViewMode.SHELF_3D -> Icons.Default.ViewAgenda
                                }, "View mode")
                            }
                            DropdownMenu(expanded = showViewModeMenu, onDismissRequest = { showViewModeMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("List") },
                                    leadingIcon = { Icon(Icons.Default.ViewList, null) },
                                    onClick = { showViewModeMenu = false; viewMode = ArticleViewMode.LIST }
                                )
                                DropdownMenuItem(
                                    text = { Text("Grid") },
                                    leadingIcon = { Icon(Icons.Default.GridView, null) },
                                    onClick = { showViewModeMenu = false; viewMode = ArticleViewMode.GRID }
                                )
                                DropdownMenuItem(
                                    text = { Text("Shelf") },
                                    leadingIcon = { Icon(Icons.Default.ViewAgenda, null) },
                                    onClick = { showViewModeMenu = false; viewMode = ArticleViewMode.SHELF_3D }
                                )
                            }
                        }
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
                else -> when (viewMode) {
                    ArticleViewMode.LIST -> LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                    ArticleViewMode.GRID -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedArticles, key = { it.id }) { article ->
                            ArticleCard(
                                article = article,
                                isSelectionMode = uiState.isSelectionMode,
                                isSelected = article.id in uiState.selectedArticleIds,
                                onToggleSelection = { viewModel.toggleArticleSelection(article.id) },
                                onOpenArticle = { if (!uiState.isSelectionMode) onOpenArticle(article.id) else viewModel.toggleArticleSelection(article.id) }
                            )
                        }
                    }
                    ArticleViewMode.SHELF_3D -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sortedArticles, key = { it.id }) { article ->
                            ArticleShelfItem(
                                article = article,
                                isSelectionMode = uiState.isSelectionMode,
                                isSelected = article.id in uiState.selectedArticleIds,
                                onToggleSelection = { viewModel.toggleArticleSelection(article.id) },
                                onOpenArticle = { if (!uiState.isSelectionMode) onOpenArticle(article.id) else viewModel.toggleArticleSelection(article.id) }
                            )
                        }
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
fun ArticleCard(
    article: RssArticle,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onOpenArticle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenArticle)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onToggleSelection() }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        article.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelection() },
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (article.isFavorite) {
                        Icon(
                            Icons.Filled.Star,
                            null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                val dateFormatter = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
                val date = if (article.publishedAt > 0) dateFormatter.format(Date(article.publishedAt)) else ""
                Text(
                    date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ArticleShelfItem(
    article: RssArticle,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onOpenArticle: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(180.dp)
            .clickable(onClick = onOpenArticle)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onToggleSelection() }
                )
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = -8f + (if (isSelected) 0f else 0f)
                    shadowElevation = 8f
                },
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        article.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (article.isFavorite) {
                        Icon(
                            Icons.Filled.Star,
                            null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        if (isSelectionMode && isSelected) {
            Icon(
                Icons.Default.Check,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
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
