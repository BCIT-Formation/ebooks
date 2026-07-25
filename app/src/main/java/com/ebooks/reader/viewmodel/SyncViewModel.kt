package com.ebooks.reader.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebooks.reader.R
import com.ebooks.reader.data.repository.BookRepository
import com.ebooks.reader.data.sync.FtpsClient
import com.ebooks.reader.data.sync.FtpsFile
import com.ebooks.reader.data.sync.PROGRESS_SNAPSHOT_FILE_NAME
import com.ebooks.reader.data.sync.SftpClient
import com.ebooks.reader.data.sync.SftpFile
import com.ebooks.reader.data.sync.SftpHostKeyStore
import com.ebooks.reader.data.sync.ShareCredentials
import com.ebooks.reader.data.sync.SmbClient
import com.ebooks.reader.data.sync.SmbEntry
import com.ebooks.reader.data.sync.SyncCredentialStore
import com.ebooks.reader.data.sync.WebDavClient
import com.ebooks.reader.data.sync.WebDavFile
import com.ebooks.reader.data.sync.parseFtpsUrl
import com.ebooks.reader.data.sync.parseProgressSnapshot
import com.ebooks.reader.data.sync.parseSftpUrl
import com.ebooks.reader.data.sync.parseSmbUrl
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

/** File extensions the WebDAV / FTPS / SFTP browsers offer to download. */
private val BOOK_EXTENSIONS = setOf("epub", "pdf", "txt", "fb2", "cbz", "cbr")

data class SyncUiState(
    // Cloud folder (Google Drive / OneDrive via SAF)
    val cloudFolderUri: String? = null,
    // WebDAV
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPassword: String = "",
    val webdavFiles: List<WebDavFile> = emptyList(),
    val isConnected: Boolean = false,
    // FTPS (ADR-008)
    val ftpsUrl: String = "",
    val ftpsUser: String = "",
    val ftpsPassword: String = "",
    val ftpsFiles: List<FtpsFile> = emptyList(),
    val isFtpsConnected: Boolean = false,
    // SFTP (ADR-009)
    val sftpUrl: String = "",
    val sftpUser: String = "",
    val sftpPassword: String = "",
    val sftpFiles: List<SftpFile> = emptyList(),
    val isSftpConnected: Boolean = false,
    // SMB (ADR-010)
    val smbUrl: String = "",
    val smbUser: String = "",
    val smbPassword: String = "",
    val smbFiles: List<SmbEntry> = emptyList(),
    val isSmbConnected: Boolean = false,
    val isBusy: Boolean = false,
    val downloadingHref: String? = null,
    val downloadingFtpsName: String? = null,
    val downloadingSftpName: String? = null,
    val downloadingSmbName: String? = null,
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
        val savedFtps = credentialStore.loadFtps()
        val savedSftp = credentialStore.loadSftp()
        val savedSmb = credentialStore.loadSmb()
        _uiState.update {
            it.copy(
                cloudFolderUri = credentialStore.loadCloudFolder(),
                webdavUrl = saved?.url.orEmpty(),
                webdavUser = saved?.username.orEmpty(),
                webdavPassword = saved?.password.orEmpty(),
                ftpsUrl = savedFtps?.url.orEmpty(),
                ftpsUser = savedFtps?.username.orEmpty(),
                ftpsPassword = savedFtps?.password.orEmpty(),
                sftpUrl = savedSftp?.url.orEmpty(),
                sftpUser = savedSftp?.username.orEmpty(),
                sftpPassword = savedSftp?.password.orEmpty(),
                smbUrl = savedSmb?.url.orEmpty(),
                smbUser = savedSmb?.username.orEmpty(),
                smbPassword = savedSmb?.password.orEmpty()
            )
        }
    }

    fun setWebdavUrl(value: String) = _uiState.update { it.copy(webdavUrl = value) }
    fun setWebdavUser(value: String) = _uiState.update { it.copy(webdavUser = value) }
    fun setWebdavPassword(value: String) = _uiState.update { it.copy(webdavPassword = value) }
    fun setFtpsUrl(value: String) = _uiState.update { it.copy(ftpsUrl = value) }
    fun setFtpsUser(value: String) = _uiState.update { it.copy(ftpsUser = value) }
    fun setFtpsPassword(value: String) = _uiState.update { it.copy(ftpsPassword = value) }
    fun setSftpUrl(value: String) = _uiState.update { it.copy(sftpUrl = value) }
    fun setSftpUser(value: String) = _uiState.update { it.copy(sftpUser = value) }
    fun setSftpPassword(value: String) = _uiState.update { it.copy(sftpPassword = value) }
    fun setSmbUrl(value: String) = _uiState.update { it.copy(smbUrl = value) }
    fun setSmbUser(value: String) = _uiState.update { it.copy(smbUser = value) }
    fun setSmbPassword(value: String) = _uiState.update { it.copy(smbPassword = value) }
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
            ShareCredentials(
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

    // ── FTPS (ADR-008) ────────────────────────────────────────────────────────

    fun connectFtps() = runBusy {
        val client = ftpsClient() ?: return@runBusy
        credentialStore.saveFtps(
            ShareCredentials(
                url = _uiState.value.ftpsUrl.trim(),
                username = _uiState.value.ftpsUser.trim(),
                password = _uiState.value.ftpsPassword
            )
        )
        val files = client.listFiles()
            .filter { !it.isDirectory && it.name.substringAfterLast(".").lowercase() in BOOK_EXTENSIONS }
        _uiState.update { it.copy(ftpsFiles = files, isFtpsConnected = true) }
        message(R.string.sync_connected, files.size.toString())
    }

    fun downloadFtpsBook(file: FtpsFile) {
        if (_uiState.value.downloadingFtpsName != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingFtpsName = file.name) }
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val client = requireNotNull(ftpsClient())
                    val downloaded = client.download(file.name, File(context().filesDir, "downloads"))
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
            _uiState.update { it.copy(downloadingFtpsName = null, message = text) }
        }
    }

    fun uploadProgressToFtps() = runBusy {
        val client = ftpsClient() ?: return@runBusy
        client.uploadText(PROGRESS_SNAPSHOT_FILE_NAME, repository.buildProgressSnapshot().toJson())
        message(R.string.sync_export_done)
    }

    fun downloadProgressFromFtps() = runBusy {
        val client = ftpsClient() ?: return@runBusy
        val json = client.downloadText(PROGRESS_SNAPSHOT_FILE_NAME)
            ?: return@runBusy message(R.string.sync_no_snapshot)
        applySnapshotJson(json)
    }

    // ── SFTP (ADR-009) ────────────────────────────────────────────────────────

    fun connectSftp() = runBusy {
        val client = sftpClient() ?: return@runBusy
        credentialStore.saveSftp(
            ShareCredentials(
                url = _uiState.value.sftpUrl.trim(),
                username = _uiState.value.sftpUser.trim(),
                password = _uiState.value.sftpPassword
            )
        )
        val files = client.listFiles()
            .filter { !it.isDirectory && it.name.substringAfterLast(".").lowercase() in BOOK_EXTENSIONS }
        _uiState.update { it.copy(sftpFiles = files, isSftpConnected = true) }
        message(R.string.sync_connected, files.size.toString())
    }

    fun downloadSftpBook(file: SftpFile) {
        if (_uiState.value.downloadingSftpName != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingSftpName = file.name) }
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val client = requireNotNull(sftpClient())
                    val downloaded = client.download(file.name, File(context().filesDir, "downloads"))
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
            _uiState.update { it.copy(downloadingSftpName = null, message = text) }
        }
    }

    fun uploadProgressToSftp() = runBusy {
        val client = sftpClient() ?: return@runBusy
        client.uploadText(PROGRESS_SNAPSHOT_FILE_NAME, repository.buildProgressSnapshot().toJson())
        message(R.string.sync_export_done)
    }

    fun downloadProgressFromSftp() = runBusy {
        val client = sftpClient() ?: return@runBusy
        val json = client.downloadText(PROGRESS_SNAPSHOT_FILE_NAME)
            ?: return@runBusy message(R.string.sync_no_snapshot)
        applySnapshotJson(json)
    }

    // ── SMB (ADR-010) ─────────────────────────────────────────────────────────

    fun connectSmb() = runBusy {
        val client = smbClient() ?: return@runBusy
        credentialStore.saveSmb(
            ShareCredentials(
                url = _uiState.value.smbUrl.trim(),
                username = _uiState.value.smbUser.trim(),
                password = _uiState.value.smbPassword
            )
        )
        val files = client.listFiles()
            .filter { !it.isDirectory && it.name.substringAfterLast(".").lowercase() in BOOK_EXTENSIONS }
        _uiState.update { it.copy(smbFiles = files, isSmbConnected = true) }
        message(R.string.sync_connected, files.size.toString())
    }

    fun downloadSmbBook(file: SmbEntry) {
        if (_uiState.value.downloadingSmbName != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingSmbName = file.name) }
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val client = requireNotNull(smbClient())
                    val downloaded = client.download(file.name, File(context().filesDir, "downloads"))
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
            _uiState.update { it.copy(downloadingSmbName = null, message = text) }
        }
    }

    fun uploadProgressToSmb() = runBusy {
        val client = smbClient() ?: return@runBusy
        client.uploadText(PROGRESS_SNAPSHOT_FILE_NAME, repository.buildProgressSnapshot().toJson())
        message(R.string.sync_export_done)
    }

    fun downloadProgressFromSmb() = runBusy {
        val client = smbClient() ?: return@runBusy
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

    /** Builds an FTPS client from the current form fields; posts an error message when invalid. */
    private fun ftpsClient(): FtpsClient? {
        val state = _uiState.value
        val url = state.ftpsUrl.trim()
        if (parseFtpsUrl(url) == null) {
            message(R.string.sync_ftps_required)
            return null
        }
        return FtpsClient(url, state.ftpsUser.trim(), state.ftpsPassword)
    }

    /** Host-key fingerprints are pinned in the credential store (TOFU, ADR-009). */
    private val sftpHostKeyStore = object : SftpHostKeyStore {
        override fun knownFingerprint(host: String, port: Int): String? =
            credentialStore.knownHostFingerprint(host, port)

        override fun rememberFingerprint(host: String, port: Int, fingerprint: String) =
            credentialStore.rememberHostFingerprint(host, port, fingerprint)
    }

    /** Builds an SFTP client from the current form fields; posts an error message when invalid. */
    private fun sftpClient(): SftpClient? {
        val state = _uiState.value
        val url = state.sftpUrl.trim()
        if (parseSftpUrl(url) == null) {
            message(R.string.sync_sftp_required)
            return null
        }
        return SftpClient(url, state.sftpUser.trim(), state.sftpPassword, sftpHostKeyStore)
    }

    /** Builds an SMB client from the current form fields; posts an error message when invalid. */
    private fun smbClient(): SmbClient? {
        val state = _uiState.value
        val url = state.smbUrl.trim()
        if (parseSmbUrl(url) == null) {
            message(R.string.sync_smb_required)
            return null
        }
        return SmbClient(url, state.smbUser.trim(), state.smbPassword)
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
