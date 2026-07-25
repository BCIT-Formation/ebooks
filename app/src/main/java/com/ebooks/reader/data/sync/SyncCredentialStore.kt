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
            runCatching {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    obtainKey(),
                    GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP))
                )
                String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)))
            }.getOrDefault("")
        } else ""
        return ShareCredentials(url, user, password)
    }

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
