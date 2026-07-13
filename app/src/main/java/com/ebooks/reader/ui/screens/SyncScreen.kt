package com.ebooks.reader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebooks.reader.R
import com.ebooks.reader.viewmodel.SyncViewModel

/**
 * Sync & backup screen (ADR-006):
 *  - Cloud folder sync — the user picks a folder via the system document picker,
 *    which includes Google Drive / OneDrive providers; reading progress is
 *    exported/imported as a JSON snapshot there.
 *  - WebDAV — browse and download books from a server, and exchange the same
 *    progress snapshot over HTTPS with Basic auth.
 */
/** Renders a short, pre-numbered list of instructions under a card title. */
@Composable
private fun NumberedSteps(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEach { step ->
            Text(
                step,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.setCloudFolder(it) }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Cloud folder (Google Drive / OneDrive via SAF) ────────────────
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.sync_cloud_folder_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    NumberedSteps(
                        listOf(
                            stringResource(R.string.sync_cloud_step1),
                            stringResource(R.string.sync_cloud_step2),
                            stringResource(R.string.sync_cloud_step3)
                        )
                    )
                    if (uiState.cloudFolderUri != null) {
                        Text(
                            stringResource(R.string.sync_folder_selected),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sync_choose_folder))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.exportToCloudFolder() },
                            enabled = uiState.cloudFolderUri != null && !uiState.isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.sync_export))
                        }
                        Button(
                            onClick = { viewModel.importFromCloudFolder() },
                            enabled = uiState.cloudFolderUri != null && !uiState.isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.sync_import))
                        }
                    }
                }
            }

            // ── WebDAV ────────────────────────────────────────────────────────
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.sync_webdav_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    NumberedSteps(
                        listOf(
                            stringResource(R.string.sync_webdav_step1),
                            stringResource(R.string.sync_webdav_step2),
                            stringResource(R.string.sync_webdav_step3)
                        )
                    )
                    OutlinedTextField(
                        value = uiState.webdavUrl,
                        onValueChange = viewModel::setWebdavUrl,
                        label = { Text(stringResource(R.string.sync_webdav_url)) },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.webdavUser,
                            onValueChange = viewModel::setWebdavUser,
                            label = { Text(stringResource(R.string.sync_webdav_user)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uiState.webdavPassword,
                            onValueChange = viewModel::setWebdavPassword,
                            label = { Text(stringResource(R.string.sync_webdav_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { viewModel.connectWebdav() },
                        enabled = !uiState.isBusy && uiState.webdavUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.sync_connect))
                    }
                    if (uiState.isConnected) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.uploadProgressToWebdav() },
                                enabled = !uiState.isBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.sync_export))
                            }
                            OutlinedButton(
                                onClick = { viewModel.downloadProgressFromWebdav() },
                                enabled = !uiState.isBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.sync_import))
                            }
                        }
                        if (uiState.webdavFiles.isNotEmpty()) {
                            Text(
                                stringResource(R.string.sync_books_on_server),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            uiState.webdavFiles.forEach { file ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        file.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (uiState.downloadingHref == file.href) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    } else {
                                        IconButton(onClick = { viewModel.downloadWebdavBook(file) }) {
                                            Icon(Icons.Default.Download, stringResource(R.string.opds_download))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
