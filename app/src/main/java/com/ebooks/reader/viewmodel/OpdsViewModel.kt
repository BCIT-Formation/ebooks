package com.ebooks.reader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebooks.reader.R
import com.ebooks.reader.data.opds.OpdsClient
import com.ebooks.reader.data.opds.OpdsEntry
import com.ebooks.reader.data.opds.OpdsFeed
import com.ebooks.reader.data.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class OpdsUiState(
    val catalogUrl: String = "",
    val feed: OpdsFeed? = null,
    /** URL of the currently displayed feed — base for resolving relative links. */
    val currentFeedUrl: String? = null,
    val isLoading: Boolean = false,
    val downloadingHref: String? = null,
    val error: String? = null,
    /** One-shot snackbar message. */
    val message: String? = null
)

class OpdsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(application)
    private val client = OpdsClient()

    private val _uiState = MutableStateFlow(OpdsUiState())
    val uiState: StateFlow<OpdsUiState> = _uiState.asStateFlow()

    /** Feed URLs behind the current one, for in-catalog back navigation. */
    private val backStack = ArrayDeque<String>()

    fun setCatalogUrl(url: String) {
        _uiState.update { it.copy(catalogUrl = url) }
    }

    fun openCatalog() {
        val url = _uiState.value.catalogUrl.trim()
        if (url.isBlank()) return
        backStack.clear()
        loadFeed(url)
    }

    fun openEntry(entry: OpdsEntry) {
        val base = _uiState.value.currentFeedUrl ?: return
        val href = entry.navigationHref ?: return
        backStack.addLast(base)
        loadFeed(client.resolve(base, href))
    }

    /** Returns false when there is no in-catalog history (caller should close the screen). */
    fun goBack(): Boolean {
        val previous = backStack.removeLastOrNull() ?: return false
        loadFeed(previous)
        return true
    }

    private fun loadFeed(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) { runCatching { client.fetchFeed(url) } }
            result.fold(
                onSuccess = { feed ->
                    _uiState.update {
                        it.copy(feed = feed, currentFeedUrl = url, isLoading = false)
                    }
                },
                onFailure = { failure ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = context().getString(R.string.opds_load_failed, failure.message.orEmpty())
                        )
                    }
                }
            )
        }
    }

    fun download(entry: OpdsEntry) {
        val base = _uiState.value.currentFeedUrl ?: return
        val href = entry.acquisitionHref ?: return
        if (_uiState.value.downloadingHref != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingHref = href) }
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val destDir = File(context().filesDir, "downloads")
                    val file = client.download(client.resolve(base, href), destDir, entry.title)
                    when (val result = repository.importBook(Uri.fromFile(file))) {
                        is BookRepository.ImportResult.Success ->
                            context().getString(R.string.opds_download_success, result.book.title)
                        is BookRepository.ImportResult.AlreadyExists ->
                            context().getString(R.string.already_in_library, result.book.title)
                        else -> context().getString(R.string.opds_import_failed, entry.title)
                    }
                }.getOrElse { failure ->
                    context().getString(R.string.opds_download_failed, failure.message.orEmpty())
                }
            }
            _uiState.update { it.copy(downloadingHref = null, message = message) }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun context(): Application = getApplication()
}
