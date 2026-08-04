package com.example.mark.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory

/**
 * Holds the network that actually has internet.
 *
 * On Wear OS the watch may reach the internet through a Bluetooth proxy to the
 * phone. That network is never the process default, so OkHttp must be told to
 * bind its sockets to it explicitly. Without this, requests only succeed when
 * the watch has its own Wi-Fi or LTE.
 */
object NetworkProvider {

    private val current = AtomicReference<Network?>(null)

    /** Call once at app start. Safe to call again. */
    fun start(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return

        // Seed with whatever is active right now, so the first request is not blind.
        current.compareAndSet(null, cm.activeNetwork)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                current.set(network)
            }

            override fun onLost(network: Network) {
                if (current.get() == network) current.set(cm.activeNetwork)
            }
        })
    }

    /** Falls back to the platform default when no validated network is known. */
    fun socketFactory(): SocketFactory =
        current.get()?.socketFactory ?: SocketFactory.getDefault()

    /** Resolves through the same network the sockets bind to. */
    fun dns(): okhttp3.Dns = object : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            val network = current.get()
            return if (network != null) {
                runCatching { network.getAllByName(hostname).toList() }
                    .getOrElse { okhttp3.Dns.SYSTEM.lookup(hostname) }
            } else {
                okhttp3.Dns.SYSTEM.lookup(hostname)
            }
        }
    }
}
