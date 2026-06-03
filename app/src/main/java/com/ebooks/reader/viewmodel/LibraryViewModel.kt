package com.ebooks.reader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.ReadingStatus
import com.ebooks.reader.data.repository.BookRepository
import com.ebooks.reader.data.repository.BookRepository.ReadingStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder { TITLE, AUTHOR, DATE, RECENT }
enum class ViewMode { LIST, GRID, BOOKSHELF }

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sortOrder: SortOrder = SortOrder.TITLE,
    val viewMode: ViewMode = ViewMode.GRID,
    val filterStatus: ReadingStatus? = null,
    val filterFileType: String? = null,
    val searchQuery: String = "",
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

    init {
        viewModelScope.launch { repository.seedBundledBooks() }
    }

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    private val _filterStatus = MutableStateFlow<ReadingStatus?>(null)
    private val _filterFileType = MutableStateFlow<String?>(null)
    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    private val _searchQuery = MutableStateFlow("")
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)

    private data class FilterState(
        val status: ReadingStatus?,
        val fileType: String?,
        val viewMode: ViewMode,
        val query: String
    )

    // Group flows to stay within the 5-parameter typed combine() overload
    private val booksWithSort = _sortOrder.flatMapLatest { sort ->
        getBooksFlow(sort).map { books -> Pair(sort, books) }
    }
    private val filterState = combine(_filterStatus, _filterFileType, _viewMode, _searchQuery) {
        status, fileType, viewMode, query -> FilterState(status, fileType, viewMode, query)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        booksWithSort,
        filterState,
        _importState
    ) { (sort, books), filter, importState ->
        val filtered = books.filter { book ->
            (filter.status == null || book.readingStatus == filter.status) &&
            (filter.fileType == null || book.fileType == filter.fileType) &&
            (filter.query.isBlank() || book.title.contains(filter.query, ignoreCase = true) ||
             book.author.contains(filter.query, ignoreCase = true))
        }
        LibraryUiState(
            books = filtered,
            sortOrder = sort,
            viewMode = filter.viewMode,
            filterStatus = filter.status,
            filterFileType = filter.fileType,
            searchQuery = filter.query,
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
    }

    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }
    fun setViewMode(mode: ViewMode) { _viewMode.value = mode }
    fun setFilterStatus(status: ReadingStatus?) { _filterStatus.value = status }
    fun setFilterFileType(fileType: String?) { _filterFileType.value = fileType }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

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
                        if (result.extension.isBlank()) "Unsupported file (no extension)."
                        else "Unsupported format: .${result.extension}"
                    )
                is BookRepository.ImportResult.Unreadable ->
                    ImportState.Error("Could not read the file. It may have been moved or deleted.")
                is BookRepository.ImportResult.ParseFailed ->
                    ImportState.Error("Could not open \"${result.fileName}\". The file may be corrupted.")
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

    fun rebuildCovers() {
        viewModelScope.launch { repository.rebuildCovers() }
    }

    /** Returns reading statistics for a book; suitable for one-shot UI display. */
    suspend fun getReadingStats(bookId: String): ReadingStats = repository.getReadingStats(bookId)
}
