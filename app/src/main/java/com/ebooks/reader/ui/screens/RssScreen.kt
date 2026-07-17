package com.ebooks.reader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssScreen(
    onOpenArticle: (String) -> Unit,
    viewModel: RssViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var selectedFeedId by remember { mutableStateOf<String?>(null) }
    var feedToDelete by remember { mutableStateOf<RssFeed?>(null) }

    val opmlImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::importOpml)
    }
    val opmlExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/xml")) { uri: Uri? ->
        uri?.let(viewModel::exportOpml)
    }

    LaunchedEffect(uiState.message) {
        val msg = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeMessage()
    }

    val articles = remember(uiState.articles, selectedFeedId) {
        selectedFeedId?.let { id -> uiState.articles.filter { it.feedId == id } } ?: uiState.articles
    }
    val feedTitles = remember(uiState.feeds) { uiState.feeds.associate { it.id to it.title } }

    Scaffold(
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
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.rss_title), fontWeight = FontWeight.Bold) },
                    actions = {
                        TooltipIconButton(Icons.Default.Refresh, stringResource(R.string.rss_refresh), { viewModel.refresh() })
                        TooltipIconButton(Icons.Default.Add, stringResource(R.string.rss_add_feed), { showAddDialog = true })
                        Box {
                            TooltipIconButton(Icons.Default.MoreVert, stringResource(R.string.settings), { showMenu = true })
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rss_import_opml)) },
                                    onClick = { showMenu = false; opmlImport.launch(arrayOf("*/*")) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rss_export_opml)) },
                                    onClick = { showMenu = false; opmlExport.launch("subscriptions.opml") }
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text(stringResource(R.string.rss_add_feed)) }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (uiState.feeds.isNotEmpty()) {
                FeedFilterRow(
                    feeds = uiState.feeds,
                    selectedFeedId = selectedFeedId,
                    onSelect = { selectedFeedId = it },
                    onDeleteRequest = { feedToDelete = it }
                )
            }

            when {
                uiState.feeds.isEmpty() -> EmptyRss(onAdd = { showAddDialog = true })
                articles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.rss_no_articles), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(articles, key = { it.id }) { article ->
                        ArticleRow(
                            article = article,
                            feedTitle = feedTitles[article.feedId].orEmpty(),
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

    if (showAddDialog) {
        AddFeedDialog(
            onAdd = { url -> viewModel.addFeed(url); showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }

    feedToDelete?.let { feed ->
        AlertDialog(
            onDismissRequest = { feedToDelete = null },
            title = { Text(stringResource(R.string.rss_delete_feed)) },
            text = { Text(stringResource(R.string.rss_delete_feed_message, feed.title)) },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedFeedId == feed.id) selectedFeedId = null
                    viewModel.deleteFeed(feed); feedToDelete = null
                }) { Text(stringResource(R.string.delete_book), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { feedToDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedFilterRow(
    feeds: List<RssFeed>,
    selectedFeedId: String?,
    onSelect: (String?) -> Unit,
    onDeleteRequest: (RssFeed) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = selectedFeedId == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.rss_all_feeds)) })
            feeds.forEach { feed ->
                FilterChip(
                    selected = selectedFeedId == feed.id,
                    onClick = { onSelect(feed.id) },
                    label = { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
        // Unsubscribe affordance for the currently-filtered feed.
        feeds.firstOrNull { it.id == selectedFeedId }?.let { feed ->
            TextButton(
                onClick = { onDeleteRequest(feed) },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.rss_delete_feed))
            }
        }
    }
}

@Composable
private fun ArticleRow(
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
                val date = if (article.publishedAt > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(article.publishedAt)) else ""
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

@Composable
private fun AddFeedDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rss_add_feed)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.rss_feed_url_hint)) }
            )
        },
        confirmButton = { TextButton(onClick = { if (url.isNotBlank()) onAdd(url) }) { Text(stringResource(R.string.rss_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun EmptyRss(onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.RssFeed, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.rss_empty_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.rss_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.rss_add_feed))
        }
    }
}
