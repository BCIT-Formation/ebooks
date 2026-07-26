package com.ebooks.reader

import com.ebooks.reader.data.sync.isLikelySshPrivateKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpPrivateKeyTest {

    @Test
    fun `openssh private key is accepted`() {
        val pem = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gt
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()
        assertTrue(isLikelySshPrivateKey(pem))
    }

    @Test
    fun `pkcs1 and pkcs8 private keys are accepted`() {
        assertTrue(isLikelySshPrivateKey("-----BEGIN RSA PRIVATE KEY-----\nMII...\n-----END RSA PRIVATE KEY-----"))
        assertTrue(isLikelySshPrivateKey("-----BEGIN PRIVATE KEY-----\nMII...\n-----END PRIVATE KEY-----"))
        assertTrue(isLikelySshPrivateKey("-----BEGIN EC PRIVATE KEY-----\nMHc...\n-----END EC PRIVATE KEY-----"))
    }

    @Test
    fun `putty ppk keys are accepted`() {
        assertTrue(isLikelySshPrivateKey("PuTTY-User-Key-File-3: ssh-ed25519\nEncryption: none\n"))
    }

    @Test
    fun `leading whitespace is tolerated`() {
        assertTrue(isLikelySshPrivateKey("\n\n   -----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----"))
    }

    @Test
    fun `public keys and junk are rejected`() {
        assertFalse(isLikelySshPrivateKey("ssh-rsa AAAAB3NzaC1yc2E user@host"))
        assertFalse(isLikelySshPrivateKey("-----BEGIN PUBLIC KEY-----\nMFk...\n-----END PUBLIC KEY-----"))
        assertFalse(isLikelySshPrivateKey("-----BEGIN CERTIFICATE-----\nMII...\n-----END CERTIFICATE-----"))
        assertFalse(isLikelySshPrivateKey("not a key at all"))
        assertFalse(isLikelySshPrivateKey(""))
    }
}
