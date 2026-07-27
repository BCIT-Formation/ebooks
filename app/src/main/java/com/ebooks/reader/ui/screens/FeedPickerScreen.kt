package com.ebooks.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebooks.reader.R
import com.ebooks.reader.data.rss.OpmlEntry
import com.ebooks.reader.viewmodel.FeedGroup
import com.ebooks.reader.viewmodel.FeedPickerViewModel

/**
 * First-launch checklist of the feeds bundled in `res/raw/default_feeds.opml`,
 * grouped by their OPML folder. Everything is ticked up front; the user unticks
 * what they don't want and confirms — nothing is subscribed behind their back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedPickerScreen(
    onFinished: (addedFeeds: Int) -> Unit,
    viewModel: FeedPickerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.addedCount) {
        uiState.addedCount?.let(onFinished)
    }

    val pendingSelection = uiState.selectedUrls.count { it !in uiState.subscribedUrls }
    val allSelected = pendingSelection > 0 && pendingSelection == uiState.selectableCount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_picker_title), fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(
                        onClick = { viewModel.setAllSelected(!allSelected) },
                        enabled = !uiState.isImporting
                    ) {
                        Text(
                            stringResource(
                                if (allSelected) R.string.feed_picker_deselect_all
                                else R.string.feed_picker_select_all
                            )
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    uiState.progress?.let { progress ->
                        Text(
                            stringResource(R.string.feed_picker_importing, progress.done, progress.total),
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = viewModel::skip, enabled = !uiState.isImporting) {
                            Text(stringResource(R.string.feed_picker_skip))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = viewModel::importSelection,
                            enabled = !uiState.isImporting && pendingSelection > 0
                        ) {
                            Text(pluralStringResource(R.plurals.feed_picker_add, pendingSelection, pendingSelection))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.feed_picker_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            uiState.groups.forEach { group ->
                item(key = "group:${group.title}") {
                    FeedGroupHeader(
                        group = group,
                        selectedUrls = uiState.selectedUrls,
                        enabled = !uiState.isImporting,
                        onToggle = { selected -> viewModel.setGroupSelected(group, selected) }
                    )
                }
                items(group.entries, key = { it.xmlUrl }) { entry ->
                    val alreadySubscribed = entry.xmlUrl in uiState.subscribedUrls
                    FeedPickerRow(
                        entry = entry,
                        checked = alreadySubscribed || entry.xmlUrl in uiState.selectedUrls,
                        alreadySubscribed = alreadySubscribed,
                        enabled = !uiState.isImporting && !alreadySubscribed,
                        onToggle = { viewModel.toggleFeed(entry.xmlUrl) }
                    )
                }
                item(key = "divider:${group.title}") {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun FeedGroupHeader(
    group: FeedGroup,
    selectedUrls: Set<String>,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val allSelected = group.entries.all { it.xmlUrl in selectedUrls }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle(!allSelected) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = allSelected, onCheckedChange = onToggle, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(
            group.title ?: stringResource(R.string.feed_picker_other_feeds),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            group.entries.size.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FeedPickerRow(
    entry: OpmlEntry,
    checked: Boolean,
    alreadySubscribed: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle() }
            .padding(start = 32.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (alreadySubscribed) stringResource(R.string.feed_picker_already_subscribed)
                else entry.siteUrl ?: entry.xmlUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
