package com.ebooks.reader

import com.ebooks.reader.data.sync.DEFAULT_FTPS_PORT
import com.ebooks.reader.data.sync.FtpsEndpoint
import com.ebooks.reader.data.sync.parseFtpsUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtpsUrlParseTest {

    @Test
    fun `host only defaults to port 21 and root path`() {
        assertEquals(
            FtpsEndpoint("files.example.com", DEFAULT_FTPS_PORT, "/"),
            parseFtpsUrl("ftps://files.example.com")
        )
    }

    @Test
    fun `explicit port and path are parsed`() {
        assertEquals(
            FtpsEndpoint("files.example.com", 2121, "/books/comics"),
            parseFtpsUrl("ftps://files.example.com:2121/books/comics/")
        )
    }

    @Test
    fun `scheme is case-insensitive and whitespace is trimmed`() {
        assertEquals(
            FtpsEndpoint("host", DEFAULT_FTPS_PORT, "/books"),
            parseFtpsUrl("  FTPS://host/books  ")
        )
    }

    @Test
    fun `plain ftp is rejected`() {
        assertNull(parseFtpsUrl("ftp://files.example.com/books"))
    }

    @Test
    fun `other schemes are rejected`() {
        assertNull(parseFtpsUrl("https://files.example.com"))
        assertNull(parseFtpsUrl("sftp://files.example.com"))
        assertNull(parseFtpsUrl("files.example.com"))
        assertNull(parseFtpsUrl(""))
    }

    @Test
    fun `missing host is rejected`() {
        assertNull(parseFtpsUrl("ftps://"))
        assertNull(parseFtpsUrl("ftps:///books"))
        assertNull(parseFtpsUrl("ftps://:21/books"))
    }

    @Test
    fun `invalid ports are rejected`() {
        assertNull(parseFtpsUrl("ftps://host:abc/books"))
        assertNull(parseFtpsUrl("ftps://host:0/books"))
        assertNull(parseFtpsUrl("ftps://host:99999/books"))
    }
}
