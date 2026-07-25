package com.ebooks.reader

import com.ebooks.reader.data.sync.DEFAULT_SMB_PORT
import com.ebooks.reader.data.sync.SmbEndpoint
import com.ebooks.reader.data.sync.parseSmbUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmbUrlParseTest {

    @Test
    fun `host and share default to port 445`() {
        assertEquals(
            SmbEndpoint("nas.local", DEFAULT_SMB_PORT, "/books"),
            parseSmbUrl("smb://nas.local/books")
        )
    }

    @Test
    fun `explicit port and nested path are parsed`() {
        assertEquals(
            SmbEndpoint("nas.local", 1445, "/media/books/comics"),
            parseSmbUrl("smb://nas.local:1445/media/books/comics/")
        )
    }

    @Test
    fun `scheme is case-insensitive and whitespace is trimmed`() {
        assertEquals(
            SmbEndpoint("host", DEFAULT_SMB_PORT, "/share"),
            parseSmbUrl("  SMB://host/share  ")
        )
    }

    @Test
    fun `share segment is required`() {
        assertNull(parseSmbUrl("smb://nas.local"))
        assertNull(parseSmbUrl("smb://nas.local/"))
        assertNull(parseSmbUrl("smb://nas.local:445/"))
    }

    @Test
    fun `other schemes are rejected`() {
        assertNull(parseSmbUrl("https://nas.local/books"))
        assertNull(parseSmbUrl("ftps://nas.local/books"))
        assertNull(parseSmbUrl("cifs://nas.local/books"))
        assertNull(parseSmbUrl("nas.local/books"))
        assertNull(parseSmbUrl(""))
    }

    @Test
    fun `missing host is rejected`() {
        assertNull(parseSmbUrl("smb://"))
        assertNull(parseSmbUrl("smb:///books"))
        assertNull(parseSmbUrl("smb://:445/books"))
    }

    @Test
    fun `invalid ports are rejected`() {
        assertNull(parseSmbUrl("smb://host:abc/books"))
        assertNull(parseSmbUrl("smb://host:0/books"))
        assertNull(parseSmbUrl("smb://host:99999/books"))
    }
}
