package com.ebooks.reader.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "ebook_sync_credentials"
private const val PREFS = "sync_prefs"
private const val GCM_TAG_BITS = 128

data class ShareCredentials(val url: String, val username: String, val password: String)

/**
 * Stores network-share credentials (WebDAV, FTPS, SFTP, SMB) with the password
 * encrypted by an AES-GCM key held in the Android Keystore (ADR-006:
 * credentials encrypted at rest). No external crypto library needed.
 */
class SyncCredentialStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(credentials: ShareCredentials) = saveServer("webdav", credentials)
    fun load(): ShareCredentials? = loadServer("webdav")

    fun saveFtps(credentials: ShareCredentials) = saveServer("ftps", credentials)
    fun loadFtps(): ShareCredentials? = loadServer("ftps")

    fun saveSftp(credentials: ShareCredentials) = saveServer("sftp", credentials)
    fun loadSftp(): ShareCredentials? = loadServer("sftp")

    // ── SFTP key-based auth (ADR-009 amendment) ───────────────────────────────

    /** Stores the PEM key and its passphrase encrypted at rest (never plaintext). */
    fun saveSftpKey(key: SftpPrivateKey) {
        putEncrypted("sftp_key_pem", key.pem)
        putEncrypted("sftp_key_pass", key.passphrase)
    }

    /** The installed SFTP private key, or null when only password auth is set up. */
    fun loadSftpKey(): SftpPrivateKey? {
        val pem = getEncrypted("sftp_key_pem")?.takeIf { it.isNotBlank() } ?: return null
        return SftpPrivateKey(pem, getEncrypted("sftp_key_pass").orEmpty())
    }

    fun hasSftpKey(): Boolean = prefs.contains("sftp_key_pem_data")

    fun clearSftpKey() {
        prefs.edit()
            .remove("sftp_key_pem_data").remove("sftp_key_pem_iv")
            .remove("sftp_key_pass_data").remove("sftp_key_pass_iv")
            .apply()
    }

    // ── SFTP host-key pinning (TOFU, ADR-009) ─────────────────────────────────

    fun knownHostFingerprint(host: String, port: Int): String? =
        prefs.getString("sftp_hostkey_${host}_$port", null)

    fun rememberHostFingerprint(host: String, port: Int, fingerprint: String) {
        prefs.edit().putString("sftp_hostkey_${host}_$port", fingerprint).apply()
    }

    fun saveSmb(credentials: ShareCredentials) = saveServer("smb", credentials)
    fun loadSmb(): ShareCredentials? = loadServer("smb")

    private fun saveServer(prefix: String, credentials: ShareCredentials) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val encrypted = cipher.doFinal(credentials.password.toByteArray())
        prefs.edit()
            .putString("${prefix}_url", credentials.url)
            .putString("${prefix}_user", credentials.username)
            .putString("${prefix}_pass", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("${prefix}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    private fun loadServer(prefix: String): ShareCredentials? {
        val url = prefs.getString("${prefix}_url", null) ?: return null
        val user = prefs.getString("${prefix}_user", "").orEmpty()
        val encrypted = prefs.getString("${prefix}_pass", null)
        val iv = prefs.getString("${prefix}_iv", null)
        val password = if (encrypted != null && iv != null) {
            decrypt(encrypted, iv)
        } else ""
        return ShareCredentials(url, user, password)
    }

    /** Encrypts [value] under the Keystore key and stores it as `${key}_data` + `${key}_iv`. */
    private fun putEncrypted(key: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val encrypted = cipher.doFinal(value.toByteArray())
        prefs.edit()
            .putString("${key}_data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("${key}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    private fun getEncrypted(key: String): String? {
        val encrypted = prefs.getString("${key}_data", null) ?: return null
        val iv = prefs.getString("${key}_iv", null) ?: return null
        return decrypt(encrypted, iv)
    }

    private fun decrypt(encryptedBase64: String, ivBase64: String): String =
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                obtainKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivBase64, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(encryptedBase64, Base64.NO_WRAP)))
        }.getOrDefault("")

    fun clear() {
        prefs.edit().clear().apply()
    }

    // ── Cloud folder (SAF) preferences ────────────────────────────────────────

    fun saveCloudFolder(treeUri: String) {
        prefs.edit().putString("cloud_folder", treeUri).apply()
    }

    fun loadCloudFolder(): String? = prefs.getString("cloud_folder", null)

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
