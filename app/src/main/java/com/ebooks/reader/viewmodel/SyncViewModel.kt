package com.ebooks.reader.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebooks.reader.R
import com.ebooks.reader.data.repository.BookRepository
import com.ebooks.reader.data.sync.PROGRESS_SNAPSHOT_FILE_NAME
import com.ebooks.reader.data.sync.SyncCredentialStore
import com.ebooks.reader.data.sync.WebDavClient
import com.ebooks.reader.data.sync.WebDavCredentials
import com.ebooks.reader.data.sync.WebDavFile
import com.ebooks.reader.data.sync.parseProgressSnapshot
import com.ebooks.reader.data.sync.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** File extensions the WebDAV browser offers to download. */
private val BOOK_EXTENSIONS = setOf("epub", "pdf", "txt", "fb2", "cbz")

data class SyncUiState(
    // Cloud folder (Google Drive / OneDrive via SAF)
    val cloudFolderUri: String? = null,
    // WebDAV
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPassword: String = "",
    val webdavFiles: List<WebDavFile> = emptyList(),
    val isConnected: Boolean = false,
    val isBusy: Boolean = false,
    val downloadingHref: String? = null,
    /** One-shot snackbar message. */
    val message: String? = null
)

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(application)
    private val credentialStore = SyncCredentialStore(application)

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        val saved = credentialStore.load()
        _uiState.update {
            it.copy(
                cloudFolderUri = credentialStore.loadCloudFolder(),
                webdavUrl = saved?.url.orEmpty(),
                webdavUser = saved?.username.orEmpty(),
                webdavPassword = saved?.password.orEmpty()
            )
        }
    }

    fun setWebdavUrl(value: String) = _uiState.update { it.copy(webdavUrl = value) }
    fun setWebdavUser(value: String) = _uiState.update { it.copy(webdavUser = value) }
    fun setWebdavPassword(value: String) = _uiState.update { it.copy(webdavPassword = value) }
    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    // ── Cloud folder sync (SAF — works with Google Drive / OneDrive providers) ──

    fun setCloudFolder(uri: Uri) {
        runCatching {
            context().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        credentialStore.saveCloudFolder(uri.toString())
        _uiState.update { it.copy(cloudFolderUri = uri.toString()) }
    }

    fun exportToCloudFolder() = runBusy {
        val treeUri = _uiState.value.cloudFolderUri?.let(Uri::parse)
            ?: return@runBusy message(R.string.sync_no_folder)
        val json = repository.buildProgressSnapshot().toJson()
        val target = findSnapshotInTree(treeUri)
            ?: createSnapshotInTree(treeUri)
            ?: return@runBusy message(R.string.sync_folder_unavailable)
        context().contentResolver.openOutputStream(target, "wt")?.use { output ->
            output.write(json.toByteArray())
        } ?: return@runBusy message(R.string.sync_folder_unavailable)
        message(R.string.sync_export_done)
    }

    fun importFromCloudFolder() = runBusy {
        val treeUri = _uiState.value.cloudFolderUri?.let(Uri::parse)
            ?: return@runBusy message(R.string.sync_no_folder)
        val file = findSnapshotInTree(treeUri)
            ?: return@runBusy message(R.string.sync_no_snapshot)
        val json = context().contentResolver.openInputStream(file)?.use { input ->
            input.bufferedReader().readText()
        } ?: return@runBusy message(R.string.sync_folder_unavailable)
        applySnapshotJson(json)
    }

    /** Looks up [PROGRESS_SNAPSHOT_FILE_NAME] among the picked tree's children. */
    private fun findSnapshotInTree(treeUri: Uri): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        context().contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == PROGRESS_SNAPSHOT_FILE_NAME) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                }
            }
        }
        return null
    }

    private fun createSnapshotInTree(treeUri: Uri): Uri? = runCatching {
        DocumentsContract.createDocument(
            context().contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri)
            ),
            "application/json",
            PROGRESS_SNAPSHOT_FILE_NAME
        )
    }.getOrNull()

    // ── WebDAV ────────────────────────────────────────────────────────────────

    fun connectWebdav() = runBusy {
        val client = webdavClient() ?: return@runBusy
        credentialStore.save(
            WebDavCredentials(
                url = _uiState.value.webdavUrl.trim(),
                username = _uiState.value.webdavUser.trim(),
                password = _uiState.value.webdavPassword
            )
        )
        val files = client.listFiles()
            .filter { !it.isDirectory && it.name.substringAfterLast(".").lowercase() in BOOK_EXTENSIONS }
        _uiState.update { it.copy(webdavFiles = files, isConnected = true) }
        message(R.string.sync_connected, files.size.toString())
    }

    fun downloadWebdavBook(file: WebDavFile) {
        if (_uiState.value.downloadingHref != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingHref = file.href) }
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val client = requireNotNull(webdavClient())
                    val downloaded = client.download(file.href, File(context().filesDir, "downloads"))
                    when (val result = repository.importBook(Uri.fromFile(downloaded))) {
                        is BookRepository.ImportResult.Success ->
                            context().getString(R.string.opds_download_success, result.book.title)
                        is BookRepository.ImportResult.AlreadyExists ->
                            context().getString(R.string.already_in_library, result.book.title)
                        else -> context().getString(R.string.opds_import_failed, file.name)
                    }
                }.getOrElse { failure ->
                    context().getString(R.string.sync_failed, failure.message.orEmpty())
                }
            }
            _uiState.update { it.copy(downloadingHref = null, message = text) }
        }
    }

    fun uploadProgressToWebdav() = runBusy {
        val client = webdavClient() ?: return@runBusy
        client.uploadText(PROGRESS_SNAPSHOT_FILE_NAME, repository.buildProgressSnapshot().toJson())
        message(R.string.sync_export_done)
    }

    fun downloadProgressFromWebdav() = runBusy {
        val client = webdavClient() ?: return@runBusy
        val json = client.downloadText(PROGRESS_SNAPSHOT_FILE_NAME)
            ?: return@runBusy message(R.string.sync_no_snapshot)
        applySnapshotJson(json)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun applySnapshotJson(json: String) {
        val snapshot = parseProgressSnapshot(json) ?: return message(R.string.sync_invalid_snapshot)
        val applied = repository.applyProgressSnapshot(snapshot)
        message(R.string.sync_import_done, applied.toString())
    }

    /** Builds a client from the current form fields; posts an error message when invalid. */
    private fun webdavClient(): WebDavClient? {
        val state = _uiState.value
        val url = state.webdavUrl.trim()
        if (!url.startsWith("https://", ignoreCase = true)) {
            message(R.string.sync_https_required)
            return null
        }
        return WebDavClient(url, state.webdavUser.trim(), state.webdavPassword)
    }

    /** Runs [block] on IO with the busy flag set; surfaces IOExceptions as messages. */
    private fun runBusy(block: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            withContext(Dispatchers.IO) {
                try {
                    block()
                } catch (failure: IOException) {
                    message(R.string.sync_failed, failure.message.orEmpty())
                } catch (failure: SecurityException) {
                    message(R.string.sync_failed, failure.message.orEmpty())
                }
            }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    private fun message(resId: Int, vararg args: String) {
        _uiState.update { it.copy(message = context().getString(resId, *args)) }
    }

    private fun context(): Application = getApplication()
}
