package com.ebooks.reader

import com.ebooks.reader.data.sync.DEFAULT_SFTP_PORT
import com.ebooks.reader.data.sync.SftpEndpoint
import com.ebooks.reader.data.sync.parseSftpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SftpUrlParseTest {

    @Test
    fun `host only defaults to port 22 and root path`() {
        assertEquals(
            SftpEndpoint("nas.example.com", DEFAULT_SFTP_PORT, "/"),
            parseSftpUrl("sftp://nas.example.com")
        )
    }

    @Test
    fun `explicit port and path are parsed`() {
        assertEquals(
            SftpEndpoint("nas.example.com", 2222, "/home/user/books"),
            parseSftpUrl("sftp://nas.example.com:2222/home/user/books/")
        )
    }

    @Test
    fun `scheme is case-insensitive and whitespace is trimmed`() {
        assertEquals(
            SftpEndpoint("host", DEFAULT_SFTP_PORT, "/books"),
            parseSftpUrl("  SFTP://host/books  ")
        )
    }

    @Test
    fun `other schemes are rejected`() {
        assertNull(parseSftpUrl("ftp://nas.example.com/books"))
        assertNull(parseSftpUrl("ftps://nas.example.com/books"))
        assertNull(parseSftpUrl("https://nas.example.com"))
        assertNull(parseSftpUrl("ssh://nas.example.com"))
        assertNull(parseSftpUrl("nas.example.com"))
        assertNull(parseSftpUrl(""))
    }

    @Test
    fun `missing host is rejected`() {
        assertNull(parseSftpUrl("sftp://"))
        assertNull(parseSftpUrl("sftp:///books"))
        assertNull(parseSftpUrl("sftp://:22/books"))
    }

    @Test
    fun `invalid ports are rejected`() {
        assertNull(parseSftpUrl("sftp://host:abc/books"))
        assertNull(parseSftpUrl("sftp://host:0/books"))
        assertNull(parseSftpUrl("sftp://host:99999/books"))
    }
}
