package com.example.mark.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class LanHttpTest {

    @Test
    fun privateAddressesOnly() {
        listOf("192.168.1.20", "10.0.0.5", "172.16.3.4", "127.0.0.1", "169.254.1.1", "fd00::1")
            .forEach { assertTrue(it, LanHttp.isPrivate(InetAddress.getByName(it))) }
        listOf("8.8.8.8", "172.32.0.1", "2001:4860:4860::8888")
            .forEach { assertFalse(it, LanHttp.isPrivate(InetAddress.getByName(it))) }
    }

    @Test
    fun refusesPublicHostWithoutConnecting() = runBlocking {
        try {
            LanHttp.post("8.8.8.8", 80, "/command", "{}", connectTimeoutMs = 100)
            fail("must refuse a public address")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun roundTripsAgainstALocalServer() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        var request = ""
        val t = thread {
            server.accept().use { s ->
                val input = s.getInputStream().bufferedReader()
                val head = generateSequence { input.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                val length = head.first { it.startsWith("Content-Length") }.substringAfter(':').trim().toInt()
                val body = CharArray(length).also { input.read(it) }
                request = head.joinToString("\n") + "\n\n" + String(body)
                val reply = """{"ok":true,"text":"Locked."}"""
                s.getOutputStream().write("HTTP/1.0 200 OK\r\nContent-Length: ${reply.length}\r\n\r\n$reply".toByteArray())
            }
        }
        val (code, body) = LanHttp.post("127.0.0.1", server.localPort, "/command", """{"action":"lock"}""",
            headers = mapOf("X-Mark-Token" to "secret"))
        t.join(); server.close()
        assertEquals(200, code)
        assertEquals("""{"ok":true,"text":"Locked."}""", body)
        assertTrue(request, request.startsWith("POST /command HTTP/1.1"))
        assertTrue(request, request.contains("X-Mark-Token: secret"))
        assertTrue(request, request.endsWith("""{"action":"lock"}"""))
    }

    @Test
    fun parsesChunkedAndStatusCodes() {
        val chunked = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nWiki\r\n5\r\npedia\r\n0\r\n\r\n"
        assertEquals(200 to "Wikipedia", LanHttp.parseResponse(chunked.toByteArray()))
        val unauthorized = "HTTP/1.1 401 Unauthorized\r\nContent-Length: 3\r\n\r\nno!extra"
        assertEquals(401 to "no!", LanHttp.parseResponse(unauthorized.toByteArray()))
        val toClose = "HTTP/1.0 200 OK\r\n\r\nall of it"
        assertEquals(200 to "all of it", LanHttp.parseResponse(toClose.toByteArray()))
    }
}
