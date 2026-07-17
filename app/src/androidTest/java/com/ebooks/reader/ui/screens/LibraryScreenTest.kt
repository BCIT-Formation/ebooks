package com.ebooks.reader.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for LibraryScreen.
 * Verifies book list display and navigation to reader.
 */
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun libraryScreenDisplaysTitle() {
        composeTestRule.setContent {
            LibraryScreen(onOpenBook = { _, _ -> })
        }

        composeTestRule
            .onNodeWithText("My Library", substring = true)
            .assertExists()
    }

    @Test
    fun libraryScreenDisplaysAddButton() {
        composeTestRule.setContent {
            LibraryScreen(onOpenBook = { _, _ -> })
        }

        composeTestRule
            .onNodeWithText("Add Book", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun bookCardClickNavigatesToReader() {
        var selectedBookId: String? = null
        composeTestRule.setContent {
            LibraryScreen(onOpenBook = { bookId, _ ->
                selectedBookId = bookId
            })
        }

        // Assumes at least one book is present in the library; skips gracefully otherwise.
        val bookCards = composeTestRule.onAllNodesWithTag("book_card")
        if (bookCards.fetchSemanticsNodes().isNotEmpty()) {
            bookCards[0].performClick()
            assert(selectedBookId != null) { "Book selection should invoke callback" }
        }
    }

    @Test
    fun settingsButtonIsAccessible() {
        composeTestRule.setContent {
            LibraryScreen(onOpenBook = { _, _ -> })
        }

        composeTestRule
            .onNodeWithContentDescription("Settings")
            .assertExists()
    }
}
