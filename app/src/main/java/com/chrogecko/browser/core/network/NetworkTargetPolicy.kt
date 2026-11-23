package com.chrogecko.browser.core.network

import java.net.InetAddress

/** Shared policy for deciding whether an IP address belongs to a non-public network. */
object NetworkTargetPolicy {

    fun isNonPublic(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isAnyLocalAddress ||
            address.isMulticastAddress || address.isSiteLocalAddress
        ) {
            return true
        }
        val bytes = address.address
        // fc00::/7 unique local (Android's Inet6Address has no isUniqueLocalAddress()).
        if (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc) return true
        val v4: ByteArray? = when {
            bytes.size == 4 -> bytes
            bytes.size == 16 && bytes.copyOfRange(0, 10).all { it == 0.toByte() } &&
                bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte() -> bytes.copyOfRange(12, 16)
            else -> null
        }
        if (v4 == null) return false
        val a = v4[0].toInt() and 0xff
        val b = v4[1].toInt() and 0xff
        return when {
            a == 100 && b in 64..127 -> true // 100.64.0.0/10 (CGNAT)
            a == 192 && b == 0 -> true // 192.0.0.0/24, 192.0.2.0/24
            a == 198 && (b == 18 || b == 19) -> true // 198.18.0.0/15
            a == 198 && b == 51 -> true // 198.51.100.0/24
            a == 203 && b == 0 -> true // 203.0.113.0/24
            a >= 240 -> true // 240.0.0.0/4 reserved
            else -> false
        }
    }
}
