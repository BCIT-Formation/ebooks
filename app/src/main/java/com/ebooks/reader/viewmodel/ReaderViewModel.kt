package com.ebooks.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ebooks.reader.R
import com.ebooks.reader.data.db.entities.Annotation
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.Bookmark
import com.ebooks.reader.data.db.entities.ReadingProgress
import com.ebooks.reader.data.db.entities.ReadingSession
import com.ebooks.reader.data.db.entities.ReadingStatus
import com.ebooks.reader.data.parser.EpubBook
import com.ebooks.reader.data.parser.EpubChapter
import com.ebooks.reader.data.parser.ReaderTheme
import com.ebooks.reader.data.repository.BookRepository
import com.ebooks.reader.ui.components.DrawingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class ReaderThemeOption { LIGHT, DARK, SEPIA, NIGHT }
enum class OrientationLock { UNSPECIFIED, PORTRAIT, LANDSCAPE }
enum class FontFamily(val css: String, val displayName: String) {
    SERIF("Georgia, serif", "Georgia"),
    SANS_SERIF("'Roboto', sans-serif", "Roboto"),
    MONO("'Courier New', monospace", "Mono"),
    OPENTYPE("'OpenDyslexic', serif", "Dyslexic");
}

data class ReaderSettings(
    val themeOption: ReaderThemeOption = ReaderThemeOption.LIGHT,
    val fontSize: Int = 18,
    val lineHeight: Float = 1.6f,
    val fontFamily: FontFamily = FontFamily.SERIF,
    /** Absolute path of a user-imported TTF/OTF; overrides [fontFamily] when set. */
    val customFontPath: String? = null,
    val paragraphIndent: Boolean = false,
    val brightness: Float = -1f,  // -1 = system
    val autoScrollSpeed: Int = 0, // 0 = off, 1-10 speed
    val keepScreenOn: Boolean = false,
    val isFullscreen: Boolean = false,
    val orientationLock: OrientationLock = OrientationLock.UNSPECIFIED,
    val tiltScrollEnabled: Boolean = false,
    /** Minutes before auto-scroll is automatically stopped. 0 = disabled. */
    val sleepTimerMinutes: Int = 0,
    /** Warm amber overlay intensity [0f = off … 0.5f = full]. */
    val nightLightAlpha: Float = 0f
)

data class ReaderUiState(
    val book: Book? = null,
    val epubBook: EpubBook? = null,
    val chapters: List<EpubChapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val currentChapterHtml: String? = null,
    val isChapterLoading: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
    val isDrawingMode: Boolean = false,
    val drawingSettings: DrawingSettings = DrawingSettings(),
    val annotationSaveInProgress: Boolean = false,
    val showControls: Boolean = true,
    val showChapterPanel: Boolean = false,
    val showSettingsPanel: Boolean = false,
    val showBookmarksPanel: Boolean = false,
    val showHighlightsPanel: Boolean = false,
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    val settings: ReaderSettings = ReaderSettings(),
    /** Absolute paths of user-imported TTF/OTF font files. */
    val customFonts: List<String> = emptyList(),
    /** Fatal error shown when the book cannot be loaded at all. */
    val error: String? = null,
    /** Non-fatal error shown as a snackbar when a single chapter fails to load. */
    val chapterError: String? = null
)

class ReaderViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = BookRepository(application)
    private val bookId: String = savedStateHandle["bookId"] ?: ""

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /** Wall-clock time when this ViewModel was created (= session start). */
    private val sessionStartMs = System.currentTimeMillis()
    /** Distinct chapter indices visited during this session. */
    private val visitedChapters = mutableSetOf<Int>()

    private var autoScrollJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val _autoScrollTick = MutableSharedFlow<Int>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val autoScrollTick: SharedFlow<Int> = _autoScrollTick.asSharedFlow()

    // Flow-based debounce for scroll progress saving — avoids creating a new Job
    // object on every scroll event (which can fire hundreds of times per second).
    private val scrollEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        if (bookId.isNotBlank()) {
            loadBook()
        }
        viewModelScope.launch {
            val fonts = repository.listCustomFonts().map { it.absolutePath }
            _uiState.update { it.copy(customFonts = fonts) }
        }
        viewModelScope.launch {
            scrollEvents
                .debounce(1_000L)
                .collect { position -> persistProgress(position) }
        }
    }

    private fun loadBook() {
        // Single coroutine for sequential loading — avoids the race where the
        // second launch reads an empty chapters list before the first has populated it.
        viewModelScope.launch {
            val book = repository.getBookById(bookId) ?: run {
                _uiState.update { it.copy(error = context().getString(R.string.reader_book_not_found)) }
                return@launch
            }
            val epubBook = try {
                repository.parseEpubBook(book)
            } catch (_: java.io.IOException) {
                _uiState.update { it.copy(error = context().getString(R.string.reader_could_not_open_book)) }
                return@launch
            } catch (_: Exception) {
                _uiState.update { it.copy(error = context().getString(R.string.reader_parse_failed)) }
                return@launch
            }
            val progress = repository.getReadingProgress(bookId)
            val chapters = epubBook?.chapters ?: emptyList()
            val startIndex = progress?.chapterIndex?.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)) ?: 0

            _uiState.update { state ->
                state.copy(
                    book = book,
                    epubBook = epubBook,
                    chapters = chapters,
                    currentChapterIndex = startIndex
                )
            }

            repository.updateLastRead(bookId)

            // Load the starting chapter now that state is populated
            if (chapters.isNotEmpty()) {
                loadChapter(startIndex)
            }
        }

        // Observe bookmarks in a separate coroutine (infinite flow — must be isolated)
        viewModelScope.launch {
            repository.getBookmarks(bookId).collect { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
        }
    }

    fun loadChapter(index: Int) {
        val chapters = _uiState.value.chapters
        if (index < 0 || index >= chapters.size) return

        viewModelScope.launch {
            visitedChapters.add(index)
            _uiState.update { it.copy(isChapterLoading = true, currentChapterIndex = index, chapterError = null, isDrawingMode = false) }
            val chapter = chapters[index]
            val theme = buildReaderTheme()
            val html = repository.getChapterHtml(bookId, chapter.href, theme)
            if (html == null) {
                _uiState.update { it.copy(
                    isChapterLoading = false,
                    chapterError = context().getString(R.string.reader_chapter_load_failed)
                )}
            } else {
                _uiState.update { it.copy(
                    currentChapterHtml = html,
                    isChapterLoading = false,
                    showChapterPanel = false,
                    chapterError = null
                )}
                // Load annotations for this chapter
                loadPageAnnotations()
            }
        }
    }

    fun nextChapter() {
        val current = _uiState.value.currentChapterIndex
        val total = _uiState.value.chapters.size
        if (current < total - 1) loadChapter(current + 1)
    }

    fun previousChapter() {
        val current = _uiState.value.currentChapterIndex
        if (current > 0) loadChapter(current - 1)
    }

    // ── Controls Visibility ───────────────────────────────────────────────────

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun toggleChapterPanel() {
        _uiState.update { it.copy(
            showChapterPanel = !it.showChapterPanel,
            showSettingsPanel = false,
            showBookmarksPanel = false,
            showHighlightsPanel = false
        )}
    }

    fun toggleSettingsPanel() {
        _uiState.update { it.copy(
            showSettingsPanel = !it.showSettingsPanel,
            showChapterPanel = false,
            showBookmarksPanel = false,
            showHighlightsPanel = false
        )}
    }

    fun toggleBookmarksPanel() {
        _uiState.update { it.copy(
            showBookmarksPanel = !it.showBookmarksPanel,
            showChapterPanel = false,
            showSettingsPanel = false,
            showHighlightsPanel = false
        )}
    }

    fun closeAllPanels() {
        _uiState.update { it.copy(
            showChapterPanel = false,
            showSettingsPanel = false,
            showBookmarksPanel = false,
            showHighlightsPanel = false
        )}
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun updateSettings(settings: ReaderSettings) {
        val old = _uiState.value.settings
        val visualChanged = settings.themeOption != old.themeOption ||
            settings.fontSize != old.fontSize ||
            settings.lineHeight != old.lineHeight ||
            settings.fontFamily != old.fontFamily ||
            settings.customFontPath != old.customFontPath ||
            settings.paragraphIndent != old.paragraphIndent
        val speedChanged = settings.autoScrollSpeed != old.autoScrollSpeed
        val timerChanged = settings.sleepTimerMinutes != old.sleepTimerMinutes

        _uiState.update { it.copy(settings = settings) }

        if (visualChanged) loadChapter(_uiState.value.currentChapterIndex)
        if (speedChanged) {
            if (settings.autoScrollSpeed > 0) startAutoScroll() else stopAutoScroll()
        }
        if (timerChanged) {
            sleepTimerJob?.cancel()
            if (settings.sleepTimerMinutes > 0 && settings.autoScrollSpeed > 0) {
                startSleepTimer(settings.sleepTimerMinutes)
            }
        }
    }

    fun setTheme(theme: ReaderThemeOption) {
        updateSettings(_uiState.value.settings.copy(themeOption = theme))
    }

    fun setFontSize(size: Int) {
        updateSettings(_uiState.value.settings.copy(fontSize = size.coerceIn(12, 32)))
    }

    fun increaseFontSize() = setFontSize(_uiState.value.settings.fontSize + 2)
    fun decreaseFontSize() = setFontSize(_uiState.value.settings.fontSize - 2)

    fun setFontFamily(family: FontFamily) {
        updateSettings(_uiState.value.settings.copy(fontFamily = family))
    }

    /** Imports a TTF/OTF picked by the user and selects it immediately. */
    fun importFont(uri: android.net.Uri) {
        viewModelScope.launch {
            val imported = repository.importFont(uri) ?: run {
                _uiState.update { it.copy(chapterError = context().getString(R.string.reader_import_font_failed)) }
                return@launch
            }
            val fonts = repository.listCustomFonts().map { it.absolutePath }
            _uiState.update { it.copy(customFonts = fonts) }
            updateSettings(_uiState.value.settings.copy(customFontPath = imported.absolutePath))
        }
    }

    fun setLineHeight(height: Float) {
        updateSettings(_uiState.value.settings.copy(lineHeight = height.coerceIn(1.0f, 3.0f)))
    }

    fun toggleAutoScroll() {
        val current = _uiState.value.settings.autoScrollSpeed
        setAutoScrollSpeed(if (current > 0) 0 else 3)
    }

    fun setAutoScrollSpeed(speed: Int) {
        val coerced = speed.coerceIn(0, 10)
        _uiState.update { it.copy(settings = it.settings.copy(autoScrollSpeed = coerced)) }
        if (coerced > 0) startAutoScroll() else stopAutoScroll()
    }

    private fun startAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = viewModelScope.launch {
            while (true) {
                delay(50L)
                val speed = _uiState.value.settings.autoScrollSpeed
                if (speed > 0) _autoScrollTick.emit(speed)
            }
        }
        // (Re-)arm the sleep timer if one is already configured
        val timerMins = _uiState.value.settings.sleepTimerMinutes
        if (timerMins > 0) startSleepTimer(timerMins)
    }

    private fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
    }

    private fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            // When the timer fires, turn off scroll and reset both settings in state
            stopAutoScroll()
            _uiState.update { it.copy(settings = it.settings.copy(autoScrollSpeed = 0, sleepTimerMinutes = 0)) }
        }
    }

    fun dismissChapterError() {
        _uiState.update { it.copy(chapterError = null) }
    }

    /** Prepares the book file (+ annotation images) for the system share sheet. */
    fun prepareShare(onReady: (BookRepository.ShareBundle?) -> Unit) {
        viewModelScope.launch { onReady(repository.prepareShare(bookId)) }
    }

    fun toggleSearch() {
        _uiState.update { it.copy(isSearchVisible = !it.isSearchVisible, searchQuery = "") }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // ── Bookmarks ─────────────────────────────────────────────────────────────

    fun addBookmark(scrollPosition: Int = 0, selectedText: String? = null) {
        val state = _uiState.value
        val chapter = state.chapters.getOrNull(state.currentChapterIndex) ?: return
        viewModelScope.launch {
            val bookmark = Bookmark(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                chapterIndex = state.currentChapterIndex,
                chapterHref = chapter.href,
                position = scrollPosition,
                selectedText = selectedText
            )
            repository.addBookmark(bookmark)
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.deleteBookmark(bookmark) }
    }

    fun navigateToBookmark(bookmark: Bookmark) {
        if (bookmark.chapterIndex != _uiState.value.currentChapterIndex) {
            loadChapter(bookmark.chapterIndex)
        }
        // Scroll position handled by WebView
    }

    // ── Highlights (a bookmark that carries selected text + colour + note) ──────

    fun toggleHighlightsPanel() {
        _uiState.update { it.copy(
            showHighlightsPanel = !it.showHighlightsPanel,
            showChapterPanel = false, showSettingsPanel = false, showBookmarksPanel = false
        )}
    }

    /** Highlights of the currently-open chapter — re-applied to the WebView. */
    fun currentChapterHighlights(): List<Bookmark> =
        _uiState.value.bookmarks.filter {
            !it.selectedText.isNullOrBlank() && it.chapterIndex == _uiState.value.currentChapterIndex
        }

    fun addHighlight(text: String, color: Int, note: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return
        val state = _uiState.value
        val chapter = state.chapters.getOrNull(state.currentChapterIndex) ?: return
        viewModelScope.launch {
            repository.addBookmark(
                Bookmark(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    chapterIndex = state.currentChapterIndex,
                    chapterHref = chapter.href,
                    position = 0,
                    selectedText = cleaned,
                    note = note.trim().ifBlank { null },
                    color = color
                )
            )
        }
    }

    fun updateHighlightNote(bookmark: Bookmark, note: String) {
        viewModelScope.launch { repository.updateBookmark(bookmark.copy(note = note.trim().ifBlank { null })) }
    }

    /** Builds a Markdown document of every highlight in the book, grouped by chapter. */
    fun buildHighlightsMarkdown(): String {
        val state = _uiState.value
        val book = state.book
        val highlights = state.bookmarks
            .filter { !it.selectedText.isNullOrBlank() }
            .sortedWith(compareBy({ it.chapterIndex }, { it.position }))
        val sb = StringBuilder()
        sb.append("# ").append(book?.title ?: context().getString(R.string.hl_export_default_title)).append("\n")
        if (!book?.author.isNullOrBlank() && book?.author != "Unknown") sb.append("*").append(book?.author).append("*\n")
        sb.append("\n")
        var lastChapter = -1
        for (h in highlights) {
            if (h.chapterIndex != lastChapter) {
                val title = state.chapters.getOrNull(h.chapterIndex)?.title?.takeIf { it.isNotBlank() }
                    ?: context().getString(R.string.chapter_number, h.chapterIndex + 1)
                sb.append("\n## ").append(title).append("\n\n")
                lastChapter = h.chapterIndex
            }
            sb.append("> ").append(h.selectedText?.replace("\n", "\n> ")).append("\n")
            h.note?.takeIf { it.isNotBlank() }?.let { sb.append("\n").append(it).append("\n") }
            sb.append("\n")
        }
        return sb.toString()
    }

    // ── Annotations ───────────────────────────────────────────────────────────

    fun toggleDrawingMode() {
        _uiState.update { it.copy(isDrawingMode = !it.isDrawingMode) }
    }

    fun updateDrawingSettings(settings: DrawingSettings) {
        _uiState.update { it.copy(drawingSettings = settings) }
    }

    fun saveAnnotation(annotation: Annotation) {
        val state = _uiState.value
        val chapter = state.chapters.getOrNull(state.currentChapterIndex) ?: return
        val pageId = "chapter-${state.currentChapterIndex}"

        viewModelScope.launch {
            _uiState.update { it.copy(annotationSaveInProgress = true) }
            val toSave = annotation.copy(
                bookId = bookId,
                pageIdentifier = pageId,
                pageIndex = state.currentChapterIndex
            )
            repository.addAnnotation(toSave)
            loadPageAnnotations()
            _uiState.update { it.copy(annotationSaveInProgress = false) }
        }
    }

    private fun loadPageAnnotations() {
        val state = _uiState.value
        val pageId = "chapter-${state.currentChapterIndex}"

        viewModelScope.launch {
            val annots = repository.getPageAnnotations(bookId, pageId)
            _uiState.update { it.copy(annotations = annots) }
        }
    }

    fun deleteAnnotation(annotationId: String) {
        viewModelScope.launch {
            repository.deleteAnnotation(annotationId)
            loadPageAnnotations()
        }
    }

    fun clearPageAnnotations() {
        val state = _uiState.value
        val pageId = "chapter-${state.currentChapterIndex}"
        viewModelScope.launch {
            repository.clearPageAnnotations(bookId, pageId)
            _uiState.update { it.copy(annotations = emptyList()) }
        }
    }

    fun clearAllAnnotations() {
        viewModelScope.launch {
            repository.clearAllAnnotations(bookId)
            _uiState.update { it.copy(annotations = emptyList()) }
        }
    }

    // ── Progress Saving ───────────────────────────────────────────────────────

    fun saveProgress(scrollPosition: Int) {
        scrollEvents.tryEmit(scrollPosition)
    }

    private suspend fun persistProgress(scrollPosition: Int) {
        val state = _uiState.value
        repository.saveReadingProgress(
            ReadingProgress(
                bookId = bookId,
                chapterIndex = state.currentChapterIndex,
                chapterHref = state.chapters.getOrNull(state.currentChapterIndex)?.href ?: "",
                scrollPosition = scrollPosition
            )
        )
        if (state.currentChapterIndex == state.chapters.size - 1) {
            repository.updateReadingStatus(bookId, ReadingStatus.READ)
        }
    }

    // ── Theme Building ────────────────────────────────────────────────────────

    private fun buildReaderTheme(): ReaderTheme {
        val settings = _uiState.value.settings
        val base = when (settings.themeOption) {
            ReaderThemeOption.LIGHT -> ReaderTheme.LIGHT
            ReaderThemeOption.DARK -> ReaderTheme.DARK
            ReaderThemeOption.SEPIA -> ReaderTheme.SEPIA
            ReaderThemeOption.NIGHT -> ReaderTheme.NIGHT
        }
        return base.copy(
            fontSize = settings.fontSize,
            lineHeight = settings.lineHeight,
            fontFamily = if (settings.customFontPath != null) {
                "'CustomReaderFont', ${settings.fontFamily.css}"
            } else {
                settings.fontFamily.css
            },
            paragraphIndent = settings.paragraphIndent,
            customFontPath = settings.customFontPath
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoScroll()
        // Free the cached EPUB ZIP now that this book is closed.
        repository.releaseParserCache()
        // viewModelScope is already cancelled when onCleared runs, so launching
        // there would silently drop the write. Use an independent scope that
        // outlives the ViewModel just long enough to persist the session.
        if (bookId.isNotBlank()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                repository.saveReadingSession(
                    ReadingSession(
                        id = UUID.randomUUID().toString(),
                        bookId = bookId,
                        startTime = sessionStartMs,
                        endTime = System.currentTimeMillis(),
                        chaptersVisited = visitedChapters.size.coerceAtLeast(1)
                    )
                )
            }
        }
    }

    private fun context(): Application = getApplication()
}
