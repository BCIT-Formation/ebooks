package com.ebooks.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ebooks.reader.R
import com.ebooks.reader.data.db.entities.Bookmark
import com.ebooks.reader.data.parser.EpubChapter

enum class ChapterPanelTab { CHAPTERS, BOOKMARKS }

@Composable
fun ChapterPanel(
    chapters: List<EpubChapter>,
    currentChapterIndex: Int,
    bookmarks: List<Bookmark>,
    onChapterSelected: (Int) -> Unit,
    onBookmarkSelected: (Bookmark) -> Unit,
    onBookmarkDeleted: (Bookmark) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(ChapterPanelTab.CHAPTERS) }
    val listState = rememberLazyListState()

    LaunchedEffect(currentChapterIndex) {
        if (chapters.isNotEmpty()) {
            listState.animateScrollToItem(currentChapterIndex.coerceAtMost(chapters.size - 1))
        }
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chapter_panel_contents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
            }

            // Tab row
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == ChapterPanelTab.CHAPTERS,
                    onClick = { selectedTab = ChapterPanelTab.CHAPTERS },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    text = { Text(stringResource(R.string.chapters)) }
                )
                Tab(
                    selected = selectedTab == ChapterPanelTab.BOOKMARKS,
                    onClick = { selectedTab = ChapterPanelTab.BOOKMARKS },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                    text = { Text(stringResource(R.string.bookmarks)) }
                )
            }

            when (selectedTab) {
                ChapterPanelTab.CHAPTERS -> {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(chapters) { idx, chapter ->
                            ChapterItem(
                                chapter = chapter,
                                isCurrentChapter = idx == currentChapterIndex,
                                onClick = { onChapterSelected(idx) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
                ChapterPanelTab.BOOKMARKS -> {
                    if (bookmarks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.no_bookmarks_yet),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(bookmarks) { _, bookmark ->
                                BookmarkItem(
                                    bookmark = bookmark,
                                    onClick = { onBookmarkSelected(bookmark) },
                                    onDelete = { onBookmarkDeleted(bookmark) }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: EpubChapter,
    isCurrentChapter: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isCurrentChapter)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else
        MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = chapter.title.ifBlank { stringResource(R.string.chapter_number, chapter.index + 1) },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isCurrentChapter) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrentChapter) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (isCurrentChapter) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.current_chapter_desc),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun BookmarkItem(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                bookmark.selectedText ?: stringResource(R.string.chapter_number, bookmark.chapterIndex + 1),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(stringResource(R.string.chapter_number, bookmark.chapterIndex + 1), style = MaterialTheme.typography.labelSmall)
        },
        leadingContent = {
            Icon(Icons.Default.Bookmark, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete_bookmark),
                    modifier = Modifier.size(16.dp))
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
