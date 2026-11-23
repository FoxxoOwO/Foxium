package com.chrogecko.browser.core.preferences

import java.net.URI
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntimeSettings

/** Controls whether Gecko enforces HTTPS upgrades across normal and private tabs. */
enum class HttpsOnlyMode(val id: String, val geckoValue: Int) {
    OFF("off", GeckoRuntimeSettings.ALLOW_ALL),
    PRIVATE_ONLY("private", GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE),
    ALL("all", GeckoRuntimeSettings.HTTPS_ONLY),
    ;

    companion object {
        fun fromId(id: String?): HttpsOnlyMode = entries.firstOrNull { it.id == id } ?: PRIVATE_ONLY
    }
}

/** Third-party and cross-site cookie policy backed by Gecko's ContentBlocking settings. */
enum class CookiePolicy(val id: String, val geckoValue: Int) {
    ISOLATE_CROSS_SITE("isolate", ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS),
    BLOCK_TRACKERS("trackers", ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS),
    BLOCK_THIRD_PARTY("third_party", ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY),
    BLOCK_ALL("none", ContentBlocking.CookieBehavior.ACCEPT_NONE),
    ALLOW_ALL("all", ContentBlocking.CookieBehavior.ACCEPT_ALL),
    ;

    companion object {
        fun fromId(id: String?): CookiePolicy =
            entries.firstOrNull { it.id == id } ?: ISOLATE_CROSS_SITE
    }
}

/** Automatic cookie-consent banner handling powered by Gecko's ContentBlocking engine. */
enum class CookieBannerMode(val id: String, val geckoValue: Int) {
    DISABLED("off", ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_DISABLED),
    REJECT("reject", ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT),
    REJECT_OR_ACCEPT(
        "reject_or_accept",
        ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT_OR_ACCEPT,
    ),
    ;

    companion object {
        fun fromId(id: String?): CookieBannerMode = entries.firstOrNull { it.id == id } ?: REJECT
    }
}

/** Trusted Recursive Resolver (DNS-over-HTTPS) operating mode in GeckoView. */
enum class DohMode(val id: String, val geckoMode: Int) {
    OFF("off", GeckoRuntimeSettings.TRR_MODE_OFF),
    INCREASED("first", GeckoRuntimeSettings.TRR_MODE_FIRST),
    MAX("only", GeckoRuntimeSettings.TRR_MODE_ONLY),
    ;

    companion object {
        fun fromId(id: String?): DohMode = entries.firstOrNull { it.id == id } ?: OFF
    }
}

/** Built-in and user-configured DNS-over-HTTPS resolver endpoints. */
enum class DohProvider(val id: String, val uri: String) {
    CLOUDFLARE("cloudflare", "https://mozilla.cloudflare-dns.com/dns-query"),
    QUAD9("quad9", "https://dns.quad9.net/dns-query"),
    NEXTDNS("nextdns", "https://firefox.dns.nextdns.io/"),
    CUSTOM("custom", ""),
    ;

    companion object {
        fun fromId(id: String?): DohProvider = entries.firstOrNull { it.id == id } ?: CLOUDFLARE
    }
}

/** Validates and normalizes custom DNS-over-HTTPS resolver URLs before passing them to Gecko. */
object DohUriValidator {
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it.isISOControl() }) {
            return null
        }
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null) return null
        return trimmed
    }
}
