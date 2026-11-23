package com.chrogecko.browser.core.network

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import java.net.URI

/**
 * Safely launches Android `intent://` links and standard external app URIs (`mailto:`, `tel:`,
 * `sms:`, `geo:`, `market:`) from web pages, falling back to `browser_fallback_url` when the
 * target app is unavailable.
 */
object ExternalIntentHandler {

    private val EXTERNAL_APP_SCHEMES = setOf("mailto", "tel", "sms", "smsto", "geo", "market")

    fun isIntentUri(url: String): Boolean = url.startsWith("intent:", ignoreCase = true)

    fun isExternalAppScheme(url: String): Boolean {
        val colon = url.indexOf(':')
        if (colon <= 0) return false
        return url.substring(0, colon).trim().lowercase() in EXTERNAL_APP_SCHEMES
    }

    fun launchExternalUri(context: Context, uri: String): Boolean {
        if (!isExternalAppScheme(uri)) return false
        val intent = Intent(Intent.ACTION_VIEW, uri.trim().toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            true
        } catch (_: SecurityException) {
            true
        }
    }

    fun launchIntentUri(context: Context, uri: String, onFallbackUrl: (String) -> Unit): Boolean {
        if (!isIntentUri(uri)) return false
        val intent = runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull()
        if (intent == null) {
            extractFallbackUrlFromIntentUri(uri)?.let(onFallbackUrl)
            return true
        }
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        val dataScheme = intent.data?.scheme?.lowercase()
        if (dataScheme == "file" || dataScheme == "content") return true
        intent.removeFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            extractSafeFallbackUrl(intent)?.let(onFallbackUrl)
            true
        } catch (_: SecurityException) {
            extractSafeFallbackUrl(intent)?.let(onFallbackUrl)
            true
        }
    }

    fun extractSafeFallbackUrl(intent: Intent): String? =
        safeHttpUrl(intent.getStringExtra(EXTRA_BROWSER_FALLBACK_URL))

    internal fun extractFallbackUrlFromIntentUri(uri: String): String? {
        val markerIndex = uri.indexOf(INTENT_FRAGMENT_MARKER)
        if (markerIndex < 0) return null
        val fragment = uri.substring(markerIndex + INTENT_FRAGMENT_MARKER.length)
        for (segment in fragment.split(';')) {
            if (segment == "end") break
            if (segment.startsWith(FALLBACK_PARAM_PREFIX)) {
                val encoded = segment.substring(FALLBACK_PARAM_PREFIX.length)
                val decoded = decodePercentUtf8(encoded) ?: return null
                return safeHttpUrl(decoded)
            }
        }
        return null
    }

    private fun safeHttpUrl(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase()
        if ((scheme != "http" && scheme != "https") || parsed.host.isNullOrBlank()) return null
        return trimmed
    }

    private fun decodePercentUtf8(encoded: String): String? {
        val out = ByteArrayOutputStream(encoded.length)
        var i = 0
        while (i < encoded.length) {
            val ch = encoded[i]
            if (ch == '%') {
                if (i + 2 >= encoded.length) return null
                val hi = Character.digit(encoded[i + 1], 16)
                val lo = Character.digit(encoded[i + 2], 16)
                if (hi < 0 || lo < 0) return null
                out.write((hi shl 4) or lo)
                i += 3
            } else {
                val bytes = ch.toString().toByteArray(Charsets.UTF_8)
                out.write(bytes, 0, bytes.size)
                i++
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private const val EXTRA_BROWSER_FALLBACK_URL = "browser_fallback_url"
    private const val INTENT_FRAGMENT_MARKER = "#Intent;"
    private const val FALLBACK_PARAM_PREFIX = "S.browser_fallback_url="
}
