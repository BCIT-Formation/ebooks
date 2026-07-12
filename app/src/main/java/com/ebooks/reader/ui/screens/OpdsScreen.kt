package com.ebooks.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebooks.reader.R
import com.ebooks.reader.data.opds.OpdsEntry
import com.ebooks.reader.viewmodel.OpdsViewModel

/**
 * OPDS catalog browser: enter a catalog URL, drill into navigation feeds,
 * download publications straight into the library (ADR-006).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsScreen(
    onBack: () -> Unit,
    viewModel: OpdsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.feed?.title?.ifBlank { null } ?: stringResource(R.string.opds_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.goBack()) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.catalogUrl,
                    onValueChange = viewModel::setCatalogUrl,
                    placeholder = { Text(stringResource(R.string.opds_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { viewModel.openCatalog() }, enabled = !uiState.isLoading) {
                    Icon(Icons.Default.TravelExplore, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.opds_open))
                }
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                uiState.feed == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(
                            Icons.Default.TravelExplore, null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.opds_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    val entries = uiState.feed!!.entries
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(entries) { _, entry ->
                            OpdsEntryRow(
                                entry = entry,
                                isDownloading = uiState.downloadingHref == entry.acquisitionHref,
                                onOpen = { viewModel.openEntry(entry) },
                                onDownload = { viewModel.download(entry) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpdsEntryRow(
    entry: OpdsEntry,
    isDownloading: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit
) {
    ListItem(
        headlineContent = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
        supportingContent = {
            val secondary = entry.author ?: entry.summary
            if (!secondary.isNullOrBlank()) {
                Text(secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            Icon(
                if (entry.isDownloadable) Icons.Default.MenuBook else Icons.Default.Folder,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            when {
                isDownloading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                entry.isDownloadable -> IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, stringResource(R.string.opds_download))
                }
                entry.isNavigation -> Icon(Icons.AutoMirrored.Filled.NavigateNext, null)
            }
        },
        modifier = if (entry.isNavigation) Modifier.clickable(onClick = onOpen) else Modifier
    )
}
