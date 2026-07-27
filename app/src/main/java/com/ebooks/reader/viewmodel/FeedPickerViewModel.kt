package com.ebooks.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebooks.reader.data.repository.RssRepository
import com.ebooks.reader.data.rss.OpmlEntry
import com.ebooks.reader.data.settings.FirstRunManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One OPML folder and the feeds it holds; [title] is null for top-level feeds. */
data class FeedGroup(val title: String?, val entries: List<OpmlEntry>)

/** Progress of the subscribe pass — one step per feed handled. */
data class FeedImportProgress(val done: Int, val total: Int)

data class FeedPickerUiState(
    val groups: List<FeedGroup> = emptyList(),
    val selectedUrls: Set<String> = emptySet(),
    /** Feeds already in the library: shown ticked and locked, never re-imported. */
    val subscribedUrls: Set<String> = emptySet(),
    val progress: FeedImportProgress? = null,
    val addedCount: Int? = null
) {
    val isImporting: Boolean get() = progress != null
    val selectableCount: Int get() = groups.sumOf { group ->
        group.entries.count { it.xmlUrl !in subscribedUrls }
    }
}

/**
 * Drives the first-launch feed checklist: reads the bundled OPML catalogue,
 * tracks which feeds the user ticked, and subscribes to the selection.
 */
class FeedPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RssRepository(application)
    private val firstRunManager = FirstRunManager.getInstance(application)

    private val _catalogue = MutableStateFlow<List<OpmlEntry>>(emptyList())
    private val _selectedUrls = MutableStateFlow<Set<String>>(emptySet())
    private val _progress = MutableStateFlow<FeedImportProgress?>(null)
    private val _addedCount = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<FeedPickerUiState> = combine(
        _catalogue,
        _selectedUrls,
        repository.getFeeds(),
        _progress,
        _addedCount
    ) { catalogue, selected, feeds, progress, added ->
        FeedPickerUiState(
            groups = catalogue.groupBy { it.category }.map { (title, entries) -> FeedGroup(title, entries) },
            selectedUrls = selected,
            subscribedUrls = feeds.map { it.url }.toSet(),
            progress = progress,
            addedCount = added
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedPickerUiState())

    init {
        viewModelScope.launch {
            val catalogue = withContext(Dispatchers.IO) {
                repository.readDefaultFeeds(getApplication<Application>())
            }
            _catalogue.value = catalogue
            // Everything ticked by default — the list is a curated selection, so
            // "confirm" is the common path and unticking is the exception.
            _selectedUrls.value = catalogue.map { it.xmlUrl }.toSet()
        }
    }

    fun toggleFeed(xmlUrl: String) {
        _selectedUrls.value = if (xmlUrl in _selectedUrls.value) {
            _selectedUrls.value - xmlUrl
        } else {
            _selectedUrls.value + xmlUrl
        }
    }

    fun setGroupSelected(group: FeedGroup, selected: Boolean) {
        val urls = group.entries.map { it.xmlUrl }.toSet()
        _selectedUrls.value = if (selected) _selectedUrls.value + urls else _selectedUrls.value - urls
    }

    fun setAllSelected(selected: Boolean) {
        _selectedUrls.value = if (selected) _catalogue.value.map { it.xmlUrl }.toSet() else emptySet()
    }

    /** Subscribes to the ticked feeds, then settles the first-launch flag. */
    fun importSelection() {
        if (_progress.value != null) return
        val subscribed = uiState.value.subscribedUrls
        val selection = _catalogue.value.filter { it.xmlUrl in _selectedUrls.value && it.xmlUrl !in subscribed }
        if (selection.isEmpty()) {
            skip()
            return
        }
        viewModelScope.launch {
            _progress.value = FeedImportProgress(0, selection.size)
            val added = runCatching {
                repository.subscribeFromOpml(selection) { done, total ->
                    // Fetches run in parallel, so callbacks can arrive out of order.
                    _progress.update { shown ->
                        if (done > (shown?.done ?: 0)) FeedImportProgress(done, total) else shown
                    }
                }
            }.getOrDefault(0)
            firstRunManager.markFeedSetupComplete()
            _progress.value = null
            _addedCount.value = added
        }
    }

    /** Dismisses the picker without subscribing to anything. */
    fun skip() {
        firstRunManager.markFeedSetupComplete()
        _addedCount.value = 0
    }
}
