package com.ebooks.reader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebooks.reader.R
import com.ebooks.reader.data.backup.BackupManager
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.ReadingStatus
import com.ebooks.reader.data.db.entities.tagList
import com.ebooks.reader.data.repository.BookRepository
import com.ebooks.reader.data.repository.BookRepository.ReadingStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder { TITLE, AUTHOR, DATE, RECENT, SERIES }
enum class ViewMode { LIST, GRID, BOOKSHELF }

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sortOrder: SortOrder = SortOrder.TITLE,
    val viewMode: ViewMode = ViewMode.GRID,
    val filterStatus: ReadingStatus? = null,
    val filterFileType: String? = null,
    val filterTag: String? = null,
    val searchQuery: String = "",
    /** Distinct tags across the whole library, for the filter UI. */
    val allTags: List<String> = emptyList(),
    val importProgress: ImportState = ImportState.Idle
)

sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    data class Success(val book: Book) : ImportState()
    /** The book was already in the library — informational, not an error. */
    data class AlreadyExists(val book: Book) : ImportState()
    data class Error(val message: String) : ImportState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(application)
    private val backupManager = BackupManager(application)

    init {
        viewModelScope.launch { repository.seedBundledBooks() }
    }

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    private val _filterStatus = MutableStateFlow<ReadingStatus?>(null)
    private val _filterFileType = MutableStateFlow<String?>(null)
    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    private val _searchQuery = MutableStateFlow("")
    private val _filterTag = MutableStateFlow<String?>(null)
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)

    private data class FilterState(
        val status: ReadingStatus?,
        val fileType: String?,
        val viewMode: ViewMode,
        val query: String,
        val tag: String?
    )

    // Group flows to stay within the 5-parameter typed combine() overload
    private val booksWithSort = _sortOrder.flatMapLatest { sort ->
        getBooksFlow(sort).map { books -> Pair(sort, books) }
    }
    private val filterState = combine(_filterStatus, _filterFileType, _viewMode, _searchQuery, _filterTag) {
        status, fileType, viewMode, query, tag -> FilterState(status, fileType, viewMode, query, tag)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        booksWithSort,
        filterState,
        _importState
    ) { (sort, books), filter, importState ->
        val filtered = books.filter { book ->
            (filter.status == null || book.readingStatus == filter.status) &&
            (filter.fileType == null || book.fileType == filter.fileType) &&
            (filter.tag == null || book.tagList().any { it.equals(filter.tag, ignoreCase = true) }) &&
            (filter.query.isBlank() || book.title.contains(filter.query, ignoreCase = true) ||
             book.author.contains(filter.query, ignoreCase = true))
        }
        LibraryUiState(
            books = filtered,
            sortOrder = sort,
            viewMode = filter.viewMode,
            filterStatus = filter.status,
            filterFileType = filter.fileType,
            filterTag = filter.tag,
            searchQuery = filter.query,
            allTags = books.flatMap { it.tagList() }.distinct().sorted(),
            importProgress = importState
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LibraryUiState(isLoading = true)
    )

    private fun getBooksFlow(sort: SortOrder): Flow<List<Book>> = when (sort) {
        SortOrder.TITLE -> repository.getBooksByTitle()
        SortOrder.AUTHOR -> repository.getBooksByAuthor()
        SortOrder.DATE -> repository.getBooksByDate()
        SortOrder.RECENT -> repository.getBooksByRecent()
        SortOrder.SERIES -> repository.getBooksBySeries()
    }

    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }
    fun setViewMode(mode: ViewMode) { _viewMode.value = mode }
    fun setFilterStatus(status: ReadingStatus?) { _filterStatus.value = status }
    fun setFilterFileType(fileType: String?) { _filterFileType.value = fileType }
    fun setFilterTag(tag: String?) { _filterTag.value = tag }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    /** Saves user-edited metadata (title/author/series/tags) for a book. */
    fun updateBookDetails(book: Book) {
        viewModelScope.launch { repository.updateBook(book) }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportState.Loading
            _importState.value = when (val result = repository.importBook(uri)) {
                is BookRepository.ImportResult.Success ->
                    ImportState.Success(result.book)
                is BookRepository.ImportResult.AlreadyExists ->
                    ImportState.AlreadyExists(result.book)
                is BookRepository.ImportResult.UnsupportedFormat ->
                    ImportState.Error(
                        if (result.extension.isBlank()) context().getString(R.string.import_error_no_extension)
                        else context().getString(R.string.import_error_unsupported_format, result.extension)
                    )
                is BookRepository.ImportResult.Unreadable ->
                    ImportState.Error(context().getString(R.string.import_error_unreadable))
                is BookRepository.ImportResult.ParseFailed ->
                    ImportState.Error(context().getString(R.string.import_error_parse_failed, result.fileName))
            }
        }
    }

    fun resetImportState() { _importState.value = ImportState.Idle }

    fun deleteBook(book: Book, deleteFile: Boolean = false) {
        viewModelScope.launch { repository.deleteBook(book, deleteFile) }
    }

    fun updateReadingStatus(bookId: String, status: ReadingStatus) {
        viewModelScope.launch { repository.updateReadingStatus(bookId, status) }
    }

    /** [onDone] is invoked on the main thread once the rebuild has finished. */
    fun rebuildCovers(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.rebuildCovers()
            onDone()
        }
    }

    /** Returns reading statistics for a book; suitable for one-shot UI display. */
    suspend fun getReadingStats(bookId: String): ReadingStats = repository.getReadingStats(bookId)

    /** Writes a full library backup (.zip) to [uri]; [onResult] runs on the main thread. */
    fun exportBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<android.app.Application>().contentResolver.openOutputStream(uri)?.use {
                        backupManager.export(it)
                    } ?: throw IllegalStateException("Cannot open destination")
                }.isSuccess
            }
            onResult(ok)
        }
    }

    /**
     * Restores a backup from [uri]. On success [onResult] is called with true;
     * the caller must restart the app so no stale DB references remain.
     */
    fun restoreBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<android.app.Application>().contentResolver.openInputStream(uri)?.use {
                        backupManager.restore(it)
                    } ?: throw IllegalStateException("Cannot open backup")
                }.isSuccess
            }
            onResult(ok)
        }
    }

    private fun context(): Application = getApplication()
}
