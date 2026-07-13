package com.ebooks.reader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebooks.reader.R
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.ReadingStatus
import com.ebooks.reader.ui.components.BookGridCard
import com.ebooks.reader.ui.components.BookListItem
import com.ebooks.reader.ui.components.BookshelfView
import com.ebooks.reader.ui.components.TooltipIconButton
import com.ebooks.reader.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (bookId: String, fileType: String) -> Unit,
    onOpenOpds: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    displayMode: com.ebooks.reader.ui.theme.DisplayMode = com.ebooks.reader.ui.theme.DisplayMode.LCD,
    onDisplayModeChange: (com.ebooks.reader.ui.theme.DisplayMode) -> Unit = {},
    viewModel: LibraryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showSortSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showMenuFor by remember { mutableStateOf<Book?>(null) }
    var showStatsFor by remember { mutableStateOf<Book?>(null) }
    var showEditFor by remember { mutableStateOf<Book?>(null) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var isRebuildingCovers by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBook(it) }
    }

    val backupExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let { dest ->
            viewModel.exportBackup(dest) { ok ->
                scope.launch { snackbarHostState.showSnackbar(context.getString(if (ok) R.string.backup_exported else R.string.backup_failed)) }
            }
        }
    }
    val backupRestore = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { src ->
            viewModel.restoreBackup(src) { ok ->
                if (ok) restartApp(context)
                else scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.backup_restore_failed)) }
            }
        }
    }

    // Surface import outcomes (success / duplicate / error) and reset the one-shot state.
    LaunchedEffect(uiState.importProgress) {
        val message = when (val state = uiState.importProgress) {
            is ImportState.Success -> context.getString(R.string.imported_book, state.book.title)
            is ImportState.AlreadyExists -> context.getString(R.string.already_in_library, state.book.title)
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
                onViewModeSelected = { viewModel.setViewMode(it) },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                // Open with "*/*": document providers report no reliable MIME type
                // for .fb2/.cbz, so filtering by MIME greys those files out. The
                // import path validates the extension and rejects unsupported files.
                onClick = { filePicker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_book)) }
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
                    EmptyLibrary(onAddBook = { filePicker.launch(arrayOf("*/*")) })
                }
                else -> {
                    when (uiState.viewMode) {
                        ViewMode.BOOKSHELF -> {
                            BookshelfView(
                                books = uiState.books,
                                onBookClick = { book -> onOpenBook(book.id, book.fileType) },
                                onBookLongClick = { book -> showMenuFor = book }
                            )
                        }
                        ViewMode.GRID -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
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
                            Text(stringResource(R.string.importing_book))
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
            currentTag = uiState.filterTag,
            allTags = uiState.allTags,
            onStatusSelected = { viewModel.setFilterStatus(it) },
            onFileTypeSelected = { viewModel.setFilterFileType(it) },
            onTagSelected = { viewModel.setFilterTag(it) },
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
            onEdit = { showEditFor = book; showMenuFor = null },
            onDelete = { viewModel.deleteBook(book); showMenuFor = null }
        )
    }

    showEditFor?.let { book ->
        EditDetailsDialog(
            book = book,
            onSave = { viewModel.updateBookDetails(it); showEditFor = null },
            onDismiss = { showEditFor = null }
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
                viewModel.rebuildCovers { isRebuildingCovers = false }
            },
            onOpenOpds = { showSettingsMenu = false; onOpenOpds() },
            onOpenSync = { showSettingsMenu = false; onOpenSync() },
            displayMode = displayMode,
            onDisplayModeChange = onDisplayModeChange,
            onBackup = { showSettingsMenu = false; backupExport.launch("ebookreader-backup.zip") },
            onRestore = { showSettingsMenu = false; showRestoreConfirm = true },
            onDismiss = { showSettingsMenu = false }
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            icon = { Icon(Icons.Default.Restore, null) },
            title = { Text(stringResource(R.string.backup_restore)) },
            text = { Text(stringResource(R.string.backup_restore_warning)) },
            confirmButton = {
                TextButton(onClick = { showRestoreConfirm = false; backupRestore.launch(arrayOf("application/zip", "*/*")) }) {
                    Text(stringResource(R.string.backup_restore))
                }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** Cold-restarts the app so no stale database references survive a restore. */
private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
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
    onViewModeSelected: (ViewMode) -> Unit,
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
                    placeholder = { Text(stringResource(R.string.search_books_hint)) },
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close_search))
                }
            }
        )
    } else {
        var showViewMenu by remember { mutableStateOf(false) }
        TopAppBar(
            title = { Text(stringResource(R.string.my_library), fontWeight = FontWeight.Bold) },
            actions = {
                TooltipIconButton(Icons.Default.Search, stringResource(R.string.search), onSearchToggle)
                TooltipIconButton(Icons.Default.Sort, stringResource(R.string.sort), onSort)
                TooltipIconButton(Icons.Default.FilterList, stringResource(R.string.filter), onFilter)
                Box {
                    TooltipIconButton(
                        icon = when (viewMode) {
                            ViewMode.LIST -> Icons.Default.ViewList
                            ViewMode.GRID -> Icons.Default.GridView
                            ViewMode.BOOKSHELF -> Icons.Default.ViewModule
                        },
                        label = stringResource(R.string.change_view),
                        onClick = { showViewMenu = true }
                    )
                    DropdownMenu(expanded = showViewMenu, onDismissRequest = { showViewMenu = false }) {
                        ViewModeItem(Icons.Default.ViewList, stringResource(R.string.view_list), viewMode == ViewMode.LIST) {
                            onViewModeSelected(ViewMode.LIST); showViewMenu = false
                        }
                        ViewModeItem(Icons.Default.GridView, stringResource(R.string.view_grid), viewMode == ViewMode.GRID) {
                            onViewModeSelected(ViewMode.GRID); showViewMenu = false
                        }
                        ViewModeItem(Icons.Default.ViewModule, stringResource(R.string.view_bookshelf), viewMode == ViewMode.BOOKSHELF) {
                            onViewModeSelected(ViewMode.BOOKSHELF); showViewMenu = false
                        }
                    }
                }
                TooltipIconButton(Icons.Default.Settings, stringResource(R.string.settings), onSettings)
            },
            scrollBehavior = scrollBehavior
        )
    }
}

@Composable
private fun ViewModeItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { Icon(icon, null) },
        trailingIcon = { if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(currentSort: SortOrder, onSortSelected: (SortOrder) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        listOf(
            SortOrder.TITLE to stringResource(R.string.sort_book_name),
            SortOrder.AUTHOR to stringResource(R.string.sort_author),
            SortOrder.DATE to stringResource(R.string.sort_import_date),
            SortOrder.RECENT to stringResource(R.string.sort_recent),
            SortOrder.SERIES to stringResource(R.string.sort_series)
        ).forEach { (sort, label) ->
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
    currentTag: String?,
    allTags: List<String>,
    onStatusSelected: (ReadingStatus?) -> Unit,
    onFileTypeSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(stringResource(R.string.library_filter), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            Text(stringResource(R.string.reading_status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = currentStatus == null, onClick = { onStatusSelected(null) }, label = { Text(stringResource(R.string.all)) })
                ReadingStatus.entries.forEach { status ->
                    FilterChip(selected = currentStatus == status, onClick = { onStatusSelected(status) }, label = { Text(statusLabel(status)) })
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.file_type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = currentFileType == null, onClick = { onFileTypeSelected(null) }, label = { Text(stringResource(R.string.all)) })
                listOf("epub", "pdf", "txt", "fb2", "cbz").forEach { type ->
                    FilterChip(selected = currentFileType == type, onClick = { onFileTypeSelected(type) }, label = { Text(type.uppercase()) })
                }
            }
            if (allTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.tags), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = currentTag == null, onClick = { onTagSelected(null) }, label = { Text(stringResource(R.string.all)) })
                    allTags.forEach { tag ->
                        FilterChip(selected = currentTag == tag, onClick = { onTagSelected(tag) }, label = { Text(tag) })
                    }
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
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title, maxLines = 1) },
        text = {
            Column {
                ListItem(headlineContent = { Text(stringResource(R.string.open_book)) }, leadingContent = { Icon(Icons.Default.MenuBook, null) }, modifier = Modifier.clickable(onClick = onOpen))
                ListItem(headlineContent = { Text(stringResource(R.string.edit_details)) }, leadingContent = { Icon(Icons.Default.Edit, null) }, modifier = Modifier.clickable(onClick = onEdit))
                ListItem(headlineContent = { Text(stringResource(R.string.reading_stats)) }, leadingContent = { Icon(Icons.Default.Timer, null) }, modifier = Modifier.clickable(onClick = onStats))
                HorizontalDivider()
                Text(stringResource(R.string.mark_as), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadingStatus.entries.forEach { status ->
                        FilterChip(selected = book.readingStatus == status, onClick = { onMarkStatus(status) }, label = { Text(statusLabel(status)) })
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ListItem(headlineContent = { Text(stringResource(R.string.delete_book), color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable { showDeleteDialog = true })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_book_title)) },
            text = { Text(stringResource(R.string.delete_book_message, book.title)) },
            confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.delete_book), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun EditDetailsDialog(book: Book, onSave: (Book) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var series by remember { mutableStateOf(book.series.orEmpty()) }
    var seriesIndex by remember { mutableStateOf(book.seriesIndex?.toString().orEmpty()) }
    var tags by remember { mutableStateOf(book.tags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_details)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text(stringResource(R.string.field_title)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = author, onValueChange = { author = it }, singleLine = true, label = { Text(stringResource(R.string.field_author)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = series, onValueChange = { series = it }, singleLine = true, label = { Text(stringResource(R.string.field_series)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = seriesIndex,
                    onValueChange = { v -> seriesIndex = v.filter { it.isDigit() } },
                    singleLine = true,
                    label = { Text(stringResource(R.string.field_series_index)) },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text(stringResource(R.string.field_tags)) }, supportingText = { Text(stringResource(R.string.field_tags_hint)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    book.copy(
                        title = title.trim().ifBlank { book.title },
                        author = author.trim().ifBlank { "Unknown" },
                        series = series.trim().ifBlank { null },
                        seriesIndex = seriesIndex.toIntOrNull(),
                        tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
                    )
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
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
                    StatsRow(stringResource(R.string.total_reading_time), formatDuration(s.totalReadingTimeMs))
                    StatsRow(stringResource(R.string.sessions), s.sessionCount.toString())
                    if (s.sessionCount > 0) {
                        StatsRow(stringResource(R.string.avg_session_length), formatDuration(s.averageSessionMs))
                        s.lastSessionMs?.let { StatsRow(stringResource(R.string.last_session), formatDuration(it)) }
                    }
                    if (s.sessionCount == 0) {
                        Text(
                            stringResource(R.string.no_sessions_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun statusLabel(status: ReadingStatus): String = stringResource(
    when (status) {
        ReadingStatus.UNREAD -> R.string.unread
        ReadingStatus.READING -> R.string.reading
        ReadingStatus.READ -> R.string.read
    }
)

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
    onOpenOpds: () -> Unit,
    onOpenSync: () -> Unit,
    displayMode: com.ebooks.reader.ui.theme.DisplayMode,
    onDisplayModeChange: (com.ebooks.reader.ui.theme.DisplayMode) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_settings)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Display mode (screen type) — fixes contrast by picking a
                // predictable high-contrast scheme instead of wallpaper colours.
                Text(stringResource(R.string.display_mode), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.display_mode_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                val modes = listOf(
                    com.ebooks.reader.ui.theme.DisplayMode.LCD to R.string.display_lcd,
                    com.ebooks.reader.ui.theme.DisplayMode.AMOLED to R.string.display_amoled,
                    com.ebooks.reader.ui.theme.DisplayMode.EINK to R.string.display_eink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { (mode, labelRes) ->
                        FilterChip(
                            selected = displayMode == mode,
                            onClick = { onDisplayModeChange(mode) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.opds_title)) },
                    supportingContent = { Text(stringResource(R.string.opds_settings_hint), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.TravelExplore, null) },
                    modifier = Modifier.clickable(onClick = onOpenOpds)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sync_title)) },
                    supportingContent = { Text(stringResource(R.string.sync_settings_hint), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.CloudSync, null) },
                    modifier = Modifier.clickable(onClick = onOpenSync)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.backup_title)) },
                    supportingContent = { Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Archive, null) },
                    modifier = Modifier.clickable(onClick = onBackup)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.backup_restore)) },
                    supportingContent = { Text(stringResource(R.string.backup_restore_hint), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Restore, null) },
                    modifier = Modifier.clickable(onClick = onRestore)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.rebuild_covers_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.rebuild_covers_description), style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRebuildCovers,
                    enabled = !isRebuildingCovers,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRebuildingCovers) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.rebuilding))
                    } else {
                        Text(stringResource(R.string.rebuild_covers))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        }
    )
}

@Composable
private fun EmptyLibrary(onAddBook: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.LibraryBooks, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.empty_library_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.empty_library_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddBook) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_book))
        }
    }
}
