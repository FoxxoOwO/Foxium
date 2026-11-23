package com.chrogecko.browser.core.network

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cast proxy refuses to fetch `?u=` targets that resolve to these addresses, so a wrong answer
 * here turns into an SSRF hole on the user's LAN.
 */
class NetworkTargetPolicyTest {

    private fun v4(a: Int, b: Int, c: Int, d: Int): InetAddress =
        InetAddress.getByAddress(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()))

    private fun v6(vararg prefix: Int): InetAddress {
        val bytes = ByteArray(16)
        prefix.forEachIndexed { index, value -> bytes[index] = value.toByte() }
        bytes[15] = 1
        return InetAddress.getByAddress(bytes)
    }

    @Test
    fun `loopback link local and site local addresses are non public`() {
        assertTrue(NetworkTargetPolicy.isNonPublic(v4(127, 0, 0, 1)))
        assertTrue(NetworkTargetPolicy.isNonPublic(v4(169, 254, 1, 2)))
        assertTrue(NetworkTargetPolicy.isNonPublic(v4(10, 1, 2, 3)))
        assertTrue(NetworkTargetPolicy.isNonPublic(v4(192, 168, 1, 20)))
        assertTrue(NetworkTargetPolicy.isNonPublic(v4(172, 16, 0, 1)))
    }

    @Test
    fun `documentation and carrier grade ranges are non public`() {
        assertTrue(NetworkTargetPolicy.isNonPublic(v4(100, 64, 0, 1)))
        assertTrue(NetworkTargetPolicy.isNonPublic(v4(100, 127, 255, 254)))
        assertTrue(NetworkTargetPolicy.isNonPublic(v4(198, 18, 0, 1)))
        assertTrue(NetworkTargetPolicy.isNonPublic(v4(198, 51, 100, 7)))
        assertTrue(NetworkTargetPolicy.isNonPublic(v4(203, 0, 113, 9)))
        assertTrue(NetworkTargetPolicy.isNonPublic(v4(240, 0, 0, 1)))
        assertTrue(NetworkTargetPolicy.isNonPublic(v4(0, 0, 0, 0)))
        assertTrue(NetworkTargetPolicy.isNonPublic(v4(224, 0, 0, 1)))
    }

    @Test
    fun `public addresses stay reachable`() {
        assertFalse(NetworkTargetPolicy.isNonPublic(v4(8, 8, 8, 8)))
        assertFalse(NetworkTargetPolicy.isNonPublic(v4(1, 1, 1, 1)))
        assertFalse(NetworkTargetPolicy.isNonPublic(v4(100, 63, 255, 255)))
        assertFalse(NetworkTargetPolicy.isNonPublic(v4(172, 32, 0, 1)))
        assertFalse(NetworkTargetPolicy.isNonPublic(v4(192, 169, 1, 1)))
    }

    @Test
    fun `ipv6 loopback unique local and v4 mapped loopback are non public`() {
        assertTrue(NetworkTargetPolicy.isNonPublic(v6(0xfd)))
        assertTrue(NetworkTargetPolicy.isNonPublic(v6(0xfc)))
        assertTrue(NetworkTargetPolicy.isNonPublic(v6(0xfe, 0x80)))
        assertTrue(
            NetworkTargetPolicy.isNonPublic(
                InetAddress.getByAddress(
                    byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff.toByte(), 0xff.toByte(), 127, 0, 0, 1),
                ),
            ),
        )
    }

    @Test
    fun `public ipv6 stays reachable`() {
        assertFalse(NetworkTargetPolicy.isNonPublic(v6(0x26, 0x06)))
        assertFalse(NetworkTargetPolicy.isNonPublic(v6(0x20, 0x01, 0x0d, 0xb8)))
    }
}
