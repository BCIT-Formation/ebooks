package com.ebooks.reader

import com.ebooks.reader.data.sync.SftpHostKeyStore
import com.ebooks.reader.data.sync.TofuHostKeyVerifier
import java.security.KeyPairGenerator
import java.security.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TofuHostKeyVerifierTest {

    private class FakeStore : SftpHostKeyStore {
        val stored = mutableMapOf<String, String>()
        override fun knownFingerprint(host: String, port: Int): String? = stored["$host:$port"]
        override fun rememberFingerprint(host: String, port: Int, fingerprint: String) {
            stored["$host:$port"] = fingerprint
        }
    }

    private fun newKey(): PublicKey =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public

    @Test
    fun `first key is trusted and remembered`() {
        val store = FakeStore()
        val verifier = TofuHostKeyVerifier(store)
        assertTrue(verifier.verify("nas.example.com", 22, newKey()))
        assertEquals(1, store.stored.size)
    }

    @Test
    fun `same key is accepted on later connections`() {
        val store = FakeStore()
        val verifier = TofuHostKeyVerifier(store)
        val key = newKey()
        assertTrue(verifier.verify("nas.example.com", 22, key))
        assertTrue(verifier.verify("nas.example.com", 22, key))
        assertEquals(1, store.stored.size)
    }

    @Test
    fun `a changed key is rejected`() {
        val store = FakeStore()
        val verifier = TofuHostKeyVerifier(store)
        assertTrue(verifier.verify("nas.example.com", 22, newKey()))
        assertFalse(verifier.verify("nas.example.com", 22, newKey()))
    }

    @Test
    fun `pins are scoped per host and port`() {
        val store = FakeStore()
        val verifier = TofuHostKeyVerifier(store)
        assertTrue(verifier.verify("nas.example.com", 22, newKey()))
        // A different host or port is a fresh first-use, not a mismatch.
        assertTrue(verifier.verify("other.example.com", 22, newKey()))
        assertTrue(verifier.verify("nas.example.com", 2222, newKey()))
        assertEquals(3, store.stored.size)
    }
}
