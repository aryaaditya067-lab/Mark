package com.example.mark.network

import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Defers to whatever network NetworkProvider currently considers usable.
 * OkHttp caches its socket factory, so the indirection has to live here.
 */
object DelegatingSocketFactory : SocketFactory() {

    private val delegate: SocketFactory get() = NetworkProvider.socketFactory()

    override fun createSocket(): Socket = delegate.createSocket()

    override fun createSocket(host: String, port: Int): Socket =
        delegate.createSocket(host, port)

    override fun createSocket(
        host: String, port: Int, localHost: InetAddress, localPort: Int
    ): Socket = delegate.createSocket(host, port, localHost, localPort)

    override fun createSocket(host: InetAddress, port: Int): Socket =
        delegate.createSocket(host, port)

    override fun createSocket(
        address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int
    ): Socket = delegate.createSocket(address, port, localAddress, localPort)
}
