package com.ebooks.reader.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ebooks.reader.data.db.AppDatabase
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.FileType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_BOOK_ID = "reader-screen-test-book"

/**
 * Instrumented tests for ReaderScreen.
 * Verifies core reading functionality: navigation, search, and settings.
 *
 * The reader's top bar (Back/Search/Settings) only renders once the book has
 * loaded, so a real book row (fileType TXT — no file I/O needed to "parse" it,
 * see BookRepository.parseEpubBook) is seeded before each test and removed after.
 */
@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun seedBook() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppDatabase.getInstance(context).bookDao().insertBook(
            Book(
                id = TEST_BOOK_ID,
                title = "Test Book",
                author = "Test Author",
                filePath = "unused",
                fileType = FileType.TXT.extension
            )
        )
        // The reader shows a full-screen first-run gesture hint overlay that swallows the
        // first tap. Pre-dismiss it via its real SharedPreferences flag so top-bar clicks
        // land on the intended button instead of just dismissing the overlay.
        context.getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("gesture_hint_shown", true).apply()
    }

    @After
    fun removeBook() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppDatabase.getInstance(context).bookDao().deleteBookById(TEST_BOOK_ID)
    }

    private fun waitForTopBar() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun readerScreenDisplaysBackButton() {
        var backPressed = false
        composeTestRule.setContent {
            ReaderScreen(
                bookId = TEST_BOOK_ID,
                onBack = { backPressed = true }
            )
        }

        waitForTopBar()
        // The reader's WebView sits underneath the top bar and can intercept real touch
        // dispatch for overlapping screen coordinates, so invoke the click semantics
        // action directly instead of performClick()'s coordinate-based tap.
        composeTestRule
            .onNodeWithContentDescription("Back")
            .performSemanticsAction(SemanticsActions.OnClick)

        assert(backPressed) { "Back button should trigger onBack callback" }
    }

    @Test
    fun searchToggleShowsSearchBar() {
        composeTestRule.setContent {
            ReaderScreen(
                bookId = TEST_BOOK_ID,
                onBack = {}
            )
        }

        waitForTopBar()
        // Tap search button (should be in top bar)
        composeTestRule
            .onNodeWithContentDescription("Search in book")
            .performClick()

        // Verify search field appears
        composeTestRule
            .onNodeWithTag("SearchTextField", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun settingsButtonShowsBottomSheet() {
        composeTestRule.setContent {
            ReaderScreen(
                bookId = TEST_BOOK_ID,
                onBack = {}
            )
        }

        waitForTopBar()
        // Tap settings button
        composeTestRule
            .onNodeWithContentDescription("Settings")
            .performClick()

        // Verify settings sheet appears (ModalBottomSheet animates in over several frames)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Theme", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
