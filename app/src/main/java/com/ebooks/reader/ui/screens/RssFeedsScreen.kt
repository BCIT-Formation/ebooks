package com.ebooks.reader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebooks.reader.R
import com.ebooks.reader.data.db.entities.RssFeed
import com.ebooks.reader.ui.components.TooltipIconButton
import com.ebooks.reader.viewmodel.RssViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedsScreen(
    onOpenFeed: (String) -> Unit,
    viewModel: RssViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showViewModeMenu by remember { mutableStateOf(false) }
    var feedToDelete by remember { mutableStateOf<RssFeed?>(null) }
    var viewMode by remember { mutableStateOf(ArticleViewMode.LIST) }
    var sortByUnread by remember { mutableStateOf(false) }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rss_title), fontWeight = FontWeight.Bold) },
                actions = {
                    TooltipIconButton(Icons.Default.Refresh, stringResource(R.string.rss_refresh), { viewModel.refresh() })
                    TooltipIconButton(Icons.Default.Add, stringResource(R.string.rss_add_feed), { showAddDialog = true })
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
                                text = { Text("Sort by unread count") },
                                onClick = { showMenu = false; sortByUnread = !sortByUnread }
                            )
                            Divider()
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.rss_add_feed)) }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val sortedFeeds = if (sortByUnread) {
                uiState.feeds.sortedByDescending { feed ->
                    uiState.articles.count { it.feedId == feed.id && !it.isRead }
                }
            } else {
                uiState.feeds
            }

            when {
                sortedFeeds.isEmpty() -> EmptyRss(onAdd = { showAddDialog = true })
                else -> when (viewMode) {
                    ArticleViewMode.LIST -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sortedFeeds, key = { it.id }) { feed ->
                            FeedListItem(
                                feed = feed,
                                articleCount = uiState.articles.count { it.feedId == feed.id },
                                unreadCount = uiState.articles.count { it.feedId == feed.id && !it.isRead },
                                onClick = { onOpenFeed(feed.id) },
                                onDeleteRequest = { feedToDelete = it }
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
                        items(sortedFeeds, key = { it.id }) { feed ->
                            FeedCard(
                                feed = feed,
                                unreadCount = uiState.articles.count { it.feedId == feed.id && !it.isRead },
                                onClick = { onOpenFeed(feed.id) },
                                onDeleteRequest = { feedToDelete = it }
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
                        items(sortedFeeds, key = { it.id }) { feed ->
                            FeedShelfItem(
                                feed = feed,
                                unreadCount = uiState.articles.count { it.feedId == feed.id && !it.isRead },
                                onClick = { onOpenFeed(feed.id) }
                            )
                        }
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
                    viewModel.deleteFeed(feed); feedToDelete = null
                }) { Text(stringResource(R.string.delete_book), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { feedToDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun FeedListItem(
    feed: RssFeed,
    articleCount: Int,
    unreadCount: Int,
    onClick: () -> Unit,
    onDeleteRequest: (RssFeed) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(feed.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                "$articleCount ${stringResource(R.string.rss_articles)} • ${unreadCount} ${stringResource(R.string.rss_unread)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Badge {
                Text(unreadCount.toString())
            }
        },
        trailingContent = {
            IconButton(onClick = { onDeleteRequest(feed) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Delete, stringResource(R.string.rss_delete_feed), modifier = Modifier.size(20.dp))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 0.dp, vertical = 8.dp)
    )
}

@Composable
private fun FeedCard(
    feed: RssFeed,
    unreadCount: Int,
    onClick: () -> Unit,
    onDeleteRequest: (RssFeed) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    feed.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onDeleteRequest(feed) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, stringResource(R.string.rss_delete_feed), modifier = Modifier.size(18.dp))
                }
            }
            if (unreadCount > 0) {
                Badge(modifier = Modifier.align(Alignment.Start)) {
                    Text("$unreadCount unread")
                }
            }
        }
    }
}

@Composable
private fun FeedShelfItem(
    feed: RssFeed,
    unreadCount: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(160.dp)
            .clickable(onClick = onClick)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = -12f
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
                        feed.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (unreadCount > 0) {
                        Badge {
                            Text(unreadCount.toString())
                        }
                    }
                }
            }
        }
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
