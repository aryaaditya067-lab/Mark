package com.example.mark.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeOnLanTest {

    private val mac = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0x01, 0x02, 0x03)

    @Test
    fun parsesCommonFormats() {
        listOf("AA:BB:CC:01:02:03", "aa-bb-cc-01-02-03", "aabbcc010203", " AABB.CC01.0203 ")
            .forEach { assertArrayEquals(it, mac, WakeOnLan.parseMac(it)) }
        listOf("", "AA:BB:CC:01:02", "GG:BB:CC:01:02:03").forEach { assertNull(it, WakeOnLan.parseMac(it)) }
    }

    @Test
    fun magicPacketIsSixFFsThenMacSixteenTimes() {
        val packet = WakeOnLan.magicPacket(mac)
        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        for (i in 0 until 16) assertArrayEquals(mac, packet.copyOfRange(6 + i * 6, 12 + i * 6))
    }
}
