package com.example.mark.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Wakes a sleeping PC with a Wake-on-LAN "magic packet". */
object WakeOnLan {

    /** "AA:BB:CC:DD:EE:FF", "aa-bb-cc-dd-ee-ff" or "aabbccddeeff"; null when malformed. */
    fun parseMac(mac: String): ByteArray? {
        val hex = mac.trim().replace(":", "").replace("-", "").replace(".", "")
        if (hex.length != 12 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    /** 6 x 0xFF followed by the MAC 16 times. */
    fun magicPacket(mac: ByteArray): ByteArray {
        require(mac.size == 6)
        return ByteArray(6) { 0xFF.toByte() } + ByteArray(16 * 6) { mac[it % 6] }
    }

    /** Broadcasts the packet on the usual WoL ports. */
    suspend fun send(mac: ByteArray) = withContext(Dispatchers.IO) {
        val packet = magicPacket(mac)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (port in intArrayOf(9, 7)) {
                socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName("255.255.255.255"), port))
            }
        }
    }
}
