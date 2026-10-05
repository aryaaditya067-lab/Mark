package com.example.mark.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal plain-HTTP POST for talking to devices on the home network (the
 * laptop agent), which have no certificate.
 *
 * Android's network security config cannot express address ranges (its
 * <domain> entries are host names), so allowing cleartext "for the LAN" there
 * only ever worked for one fixed IP. Instead the app keeps cleartext blocked
 * everywhere, and this client refuses any address that is not private, so the
 * agent token is never sent unencrypted over the internet.
 */
object LanHttp {

    /** The host is not a private address; distinct from a malformed reply. */
    class NotPrivateException(host: String) : IllegalArgumentException("$host is not on the local network")

    fun isPrivate(address: InetAddress): Boolean =
        address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            (address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc) // fc00::/7

    /** @return HTTP status code and body. */
    suspend fun post(
        host: String,
        port: Int,
        path: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = 1500,
        readTimeoutMs: Int = 4000,
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val address = InetAddress.getByName(host)
        if (!isPrivate(address)) throw NotPrivateException(host)
        Socket().use { socket ->
            socket.connect(InetSocketAddress(address, port), connectTimeoutMs)
            socket.soTimeout = readTimeoutMs
            val body = json.toByteArray(Charsets.UTF_8)
            val head = buildString {
                append("POST ").append(path).append(" HTTP/1.1\r\n")
                append("Host: ").append(host).append(':').append(port).append("\r\n")
                append("Content-Type: application/json\r\n")
                append("Content-Length: ").append(body.size).append("\r\n")
                append("Connection: close\r\n")
                headers.forEach { (k, v) -> append(k).append(": ").append(v).append("\r\n") }
                append("\r\n")
            }
            val out = socket.getOutputStream()
            out.write(head.toByteArray(Charsets.ISO_8859_1))
            out.write(body)
            out.flush()
            parseResponse(socket.getInputStream().readBytes())
        }
    }

    /** Parses a complete HTTP/1.x response; handles Content-Length, chunked and read-to-close bodies. */
    fun parseResponse(raw: ByteArray): Pair<Int, String> {
        val split = indexOf(raw, "\r\n\r\n".toByteArray())
        require(split >= 0) { "Incomplete HTTP response" }
        val head = String(raw, 0, split, Charsets.ISO_8859_1).split("\r\n")
        val code = head.first().split(' ').getOrNull(1)?.toIntOrNull()
            ?: throw IllegalArgumentException("Bad status line: ${head.first()}")
        val fields = head.drop(1).mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
        }.toMap()
        var body = raw.copyOfRange(split + 4, raw.size)
        if (fields["transfer-encoding"]?.lowercase()?.contains("chunked") == true) body = dechunk(body)
        else fields["content-length"]?.toIntOrNull()?.let { if (it <= body.size) body = body.copyOf(it) }
        return code to String(body, Charsets.UTF_8)
    }

    private fun dechunk(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = 0
        while (pos < data.size) {
            val lineEnd = indexOf(data, "\r\n".toByteArray(), pos)
            if (lineEnd < 0) break
            val size = String(data, pos, lineEnd - pos, Charsets.ISO_8859_1).substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) break
            val start = lineEnd + 2
            if (start + size > data.size) break
            out.write(data, start, size)
            pos = start + size + 2
        }
        return out.toByteArray()
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..data.size - pattern.size) {
            for (j in pattern.indices) if (data[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }
}
