package com.ebooks.reader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebooks.reader.R
import com.ebooks.reader.data.db.entities.RssArticle
import com.ebooks.reader.data.db.entities.RssFeed
import com.ebooks.reader.data.repository.RssRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RssUiState(
    val feeds: List<RssFeed> = emptyList(),
    val articles: List<RssArticle> = emptyList(),
    val isBusy: Boolean = false,
    val message: String? = null,
    val selectedArticleIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false
)

class RssViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RssRepository(application)

    private val _busy = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _selectedArticleIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)

    val uiState: StateFlow<RssUiState> = combine(
        repository.getFeeds(),
        repository.getAllArticles(),
        _busy,
        _message,
        _selectedArticleIds,
        _isSelectionMode
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        RssUiState(
            feeds = values[0] as List<RssFeed>,
            articles = values[1] as List<RssArticle>,
            isBusy = values[2] as Boolean,
            message = values[3] as String?,
            selectedArticleIds = values[4] as Set<String>,
            isSelectionMode = values[5] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RssUiState())

    fun addFeed(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        runBusy {
            when (val result = repository.addFeed(trimmed)) {
                is RssRepository.AddResult.Success ->
                    message(R.string.rss_feed_added, result.feed.title)
                is RssRepository.AddResult.AlreadyExists ->
                    message(R.string.rss_feed_exists)
                is RssRepository.AddResult.Failed ->
                    message(R.string.rss_feed_failed, result.message)
            }
        }
    }

    fun refresh() = runBusy {
        val count = repository.refreshAll()
        message(R.string.rss_refreshed, count.toString())
    }

    fun deleteFeed(feed: RssFeed) = runBusy { repository.deleteFeed(feed) }

    fun importOpml(uri: Uri) = runBusy {
        val added = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
            repository.importOpml(it)
        } ?: 0
        message(R.string.rss_opml_imported, added.toString())
    }

    fun exportOpml(uri: Uri) = runBusy {
        val opml = repository.exportOpml()
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
            it.write(opml.toByteArray())
        }
        message(R.string.rss_opml_exported)
    }

    fun consumeMessage() { _message.value = null }

    // ── Selection & bulk operations ────────────────────────────────────────────

    fun setSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) _selectedArticleIds.value = emptySet()
    }

    fun toggleArticleSelection(articleId: String) {
        _selectedArticleIds.value = if (articleId in _selectedArticleIds.value) {
            _selectedArticleIds.value - articleId
        } else {
            _selectedArticleIds.value + articleId
        }
    }

    fun selectAll(articleIds: List<String>) {
        _selectedArticleIds.value = articleIds.toSet()
        _isSelectionMode.value = true
    }

    fun clearSelection() {
        _selectedArticleIds.value = emptySet()
    }

    fun markSelectedAsRead() = runBusy {
        repository.markArticlesAsRead(_selectedArticleIds.value.toList())
        clearSelection()
        setSelectionMode(false)
    }

    fun markSelectedAsUnread() = runBusy {
        repository.markArticlesAsUnread(_selectedArticleIds.value.toList())
        clearSelection()
        setSelectionMode(false)
    }

    fun toggleFavorite(articleId: String) = runBusy {
        repository.toggleFavorite(articleId)
    }

    fun toggleRead(articleId: String) = runBusy {
        repository.toggleRead(articleId)
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) { runCatching { block() }.onFailure {
                _message.value = getApplication<Application>().getString(R.string.rss_feed_failed, it.message ?: "")
            } }
            _busy.value = false
        }
    }

    private fun message(resId: Int, vararg args: String) {
        _message.value = getApplication<Application>().getString(resId, *args)
    }
}
