package com.ebooks.reader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.ReadingStatus
import com.ebooks.reader.ui.components.BookGridCard
import com.ebooks.reader.ui.components.BookListItem
import com.ebooks.reader.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (bookId: String, fileType: String) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showSortSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showMenuFor by remember { mutableStateOf<Book?>(null) }
    var showStatsFor by remember { mutableStateOf<Book?>(null) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var isRebuildingCovers by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBook(it) }
    }

    // Surface import outcomes (success / duplicate / error) and reset the one-shot state.
    LaunchedEffect(uiState.importProgress) {
        val message = when (val state = uiState.importProgress) {
            is ImportState.Success -> "Imported \"${state.book.title}\""
            is ImportState.AlreadyExists -> "\"${state.book.title}\" is already in your library"
            is ImportState.Error -> state.message
            else -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.resetImportState()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LibraryTopBar(
                searchActive = searchActive,
                searchQuery = uiState.searchQuery,
                viewMode = uiState.viewMode,
                onSearchToggle = { searchActive = !searchActive; if (!searchActive) viewModel.setSearchQuery("") },
                onSearchQuery = viewModel::setSearchQuery,
                onSearchClose = { searchActive = false; viewModel.setSearchQuery("") },
                onSort = { showSortSheet = true },
                onFilter = { showFilterSheet = true },
                onSettings = { showSettingsMenu = true },
                onViewModeToggle = {
                    viewModel.setViewMode(
                        when (uiState.viewMode) {
                            ViewMode.LIST -> ViewMode.GRID
                            ViewMode.GRID -> ViewMode.BOOKSHELF
                            ViewMode.BOOKSHELF -> ViewMode.LIST
                        }
                    )
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    filePicker.launch(
                        arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/x-fictionbook+xml")
                    )
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Book") }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.books.isEmpty() && !uiState.isLoading -> {
                    EmptyLibrary(onAddBook = {
                        filePicker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain"))
                    })
                }
                else -> {
                    when (uiState.viewMode) {
                        ViewMode.GRID, ViewMode.BOOKSHELF -> {
                            val columns = if (uiState.viewMode == ViewMode.BOOKSHELF) 3 else 2
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.books, key = { it.id }) { book ->
                                    BookGridCard(
                                        book = book,
                                        onClick = { onOpenBook(book.id, book.fileType) },
                                        onLongClick = { showMenuFor = book }
                                    )
                                }
                            }
                        }
                        ViewMode.LIST -> {
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.books, key = { it.id }) { book ->
                                    BookListItem(
                                        book = book,
                                        onClick = { onOpenBook(book.id, book.fileType) },
                                        onLongClick = { showMenuFor = book }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.importProgress is ImportState.Loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(elevation = CardDefaults.cardElevation(8.dp)) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Importing book…")
                        }
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        SortSheet(
            currentSort = uiState.sortOrder,
            onSortSelected = { viewModel.setSortOrder(it); showSortSheet = false },
            onDismiss = { showSortSheet = false }
        )
    }

    if (showFilterSheet) {
        FilterSheet(
            currentStatus = uiState.filterStatus,
            currentFileType = uiState.filterFileType,
            onStatusSelected = { viewModel.setFilterStatus(it) },
            onFileTypeSelected = { viewModel.setFilterFileType(it) },
            onDismiss = { showFilterSheet = false }
        )
    }

    showMenuFor?.let { book ->
        BookContextMenu(
            book = book,
            onDismiss = { showMenuFor = null },
            onOpen = { onOpenBook(book.id, book.fileType); showMenuFor = null },
            onMarkStatus = { status -> viewModel.updateReadingStatus(book.id, status); showMenuFor = null },
            onStats = { showStatsFor = book; showMenuFor = null },
            onDelete = { viewModel.deleteBook(book); showMenuFor = null }
        )
    }

    showStatsFor?.let { book ->
        ReadingStatsDialog(
            book = book,
            viewModel = viewModel,
            onDismiss = { showStatsFor = null }
        )
    }

    if (showSettingsMenu) {
        SettingsDialog(
            isRebuildingCovers = isRebuildingCovers,
            onRebuildCovers = {
                isRebuildingCovers = true
                viewModel.rebuildCovers()
                isRebuildingCovers = false
            },
            onDismiss = { showSettingsMenu = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    searchActive: Boolean,
    searchQuery: String,
    viewMode: ViewMode,
    onSearchToggle: () -> Unit,
    onSearchQuery: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSort: () -> Unit,
    onFilter: () -> Unit,
    onSettings: () -> Unit,
    onViewModeToggle: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    if (searchActive) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQuery,
                    placeholder = { Text("Search books…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = onSearchClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                }
            }
        )
    } else {
        TopAppBar(
            title = { Text("My Library", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = onSearchToggle) { Icon(Icons.Default.Search, "Search") }
                IconButton(onClick = onSort) { Icon(Icons.Default.Sort, "Sort") }
                IconButton(onClick = onFilter) { Icon(Icons.Default.FilterList, "Filter") }
                IconButton(onClick = onViewModeToggle) {
                    Icon(
                        when (viewMode) {
                            ViewMode.LIST -> Icons.Default.GridView
                            ViewMode.GRID -> Icons.Default.ViewModule
                            ViewMode.BOOKSHELF -> Icons.Default.ViewList
                        }, "Change view"
                    )
                }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
            },
            scrollBehavior = scrollBehavior
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(currentSort: SortOrder, onSortSelected: (SortOrder) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Sort by", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        listOf(SortOrder.TITLE to "Book name", SortOrder.AUTHOR to "Author", SortOrder.DATE to "Import date", SortOrder.RECENT to "Recent").forEach { (sort, label) ->
            ListItem(
                headlineContent = { Text(label) },
                trailingContent = { if (sort == currentSort) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onSortSelected(sort) }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    currentStatus: ReadingStatus?,
    currentFileType: String?,
    onStatusSelected: (ReadingStatus?) -> Unit,
    onFileTypeSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text("Library filter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            Text("Reading status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = currentStatus == null, onClick = { onStatusSelected(null) }, label = { Text("All") })
                ReadingStatus.entries.forEach { status ->
                    FilterChip(selected = currentStatus == status, onClick = { onStatusSelected(status) }, label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("File type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = currentFileType == null, onClick = { onFileTypeSelected(null) }, label = { Text("All") })
                listOf("epub", "pdf", "txt", "fb2").forEach { type ->
                    FilterChip(selected = currentFileType == type, onClick = { onFileTypeSelected(type) }, label = { Text(type.uppercase()) })
                }
            }
        }
    }
}

@Composable
private fun BookContextMenu(
    book: Book,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onMarkStatus: (ReadingStatus) -> Unit,
    onStats: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title, maxLines = 1) },
        text = {
            Column {
                ListItem(headlineContent = { Text("Open") }, leadingContent = { Icon(Icons.Default.MenuBook, null) }, modifier = Modifier.clickable(onClick = onOpen))
                ListItem(headlineContent = { Text("Reading Stats") }, leadingContent = { Icon(Icons.Default.Timer, null) }, modifier = Modifier.clickable(onClick = onStats))
                HorizontalDivider()
                Text("Mark as:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadingStatus.entries.forEach { status ->
                        FilterChip(selected = book.readingStatus == status, onClick = { onMarkStatus(status) }, label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ListItem(headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable { showDeleteDialog = true })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete book?") },
            text = { Text("Remove \"${book.title}\" from your library?") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ReadingStatsDialog(
    book: Book,
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit
) {
    var stats by remember { mutableStateOf<com.ebooks.reader.data.repository.BookRepository.ReadingStats?>(null) }

    LaunchedEffect(book.id) {
        stats = viewModel.getReadingStats(book.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Timer, null) },
        title = { Text(book.title, maxLines = 1) },
        text = {
            val s = stats
            if (s == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatsRow("Total reading time", formatDuration(s.totalReadingTimeMs))
                    StatsRow("Sessions", s.sessionCount.toString())
                    if (s.sessionCount > 0) {
                        StatsRow("Avg. session length", formatDuration(s.averageSessionMs))
                        s.lastSessionMs?.let { StatsRow("Last session", formatDuration(it)) }
                    }
                    if (s.sessionCount == 0) {
                        Text(
                            "No reading sessions recorded yet.\nOpen the book to start tracking!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Converts milliseconds to a human-readable string like "3h 24m" or "45m" or "< 1m". */
private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "< 1m"
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(
    isRebuildingCovers: Boolean,
    onRebuildCovers: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Library Settings") },
        text = {
            Column {
                Text("Rebuild Cover Images", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Re-import cover images from all EPUB books in your library.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRebuildCovers,
                    enabled = !isRebuildingCovers,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRebuildingCovers) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rebuilding...")
                    } else {
                        Text("Rebuild Covers")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun EmptyLibrary(onAddBook: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.LibraryBooks, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Your library is empty", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Add your first book by tapping the + button", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddBook) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Book")
        }
    }
}
