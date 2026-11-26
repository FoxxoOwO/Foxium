package com.chrogecko.browser.core.network

/** Domain helpers for deciding where captured credentials may be forwarded. */
object UrlDomainPolicy {

    private val MULTI_LABEL_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au",
        "co.jp", "ne.jp", "or.jp", "co.nz", "com.br", "com.cn", "com.mx",
        "co.in", "com.sg", "co.za", "com.tr", "com.hk", "com.tw",
        "co.kr", "or.kr", "ne.kr", "co.id", "or.id", "com.ar", "com.il",
        "com.pl", "net.nz", "org.nz", "com.ph", "co.th", "com.vn", "com.pk",
        // Shared hosting / CDN suffixes must never be treated as one registrable domain.
        "github.io", "blogspot.com", "appspot.com", "herokuapp.com",
        "amazonaws.com", "s3.amazonaws.com", "azurewebsites.net", "vercel.app", "cloudfront.net",
        "pages.dev", "workers.dev", "netlify.app", "firebaseapp.com",
    )

    /** Registrable-domain approximation with common multi-label public suffixes excluded. */
    fun registrableDomain(host: String): String {
        val labels = host.trim().lowercase().trimEnd('.').split('.').filter { it.isNotEmpty() }
        if (labels.size < 2) return labels.firstOrNull().orEmpty()
        val host = labels.joinToString(".")
        val suffix = MULTI_LABEL_SUFFIXES
            .filter { host == it || host.endsWith(".$it") }
            .maxByOrNull { it.length }
            ?: return labels.takeLast(2).joinToString(".")
        val suffixLength = suffix.split('.').size
        if (labels.size <= suffixLength) return suffix
        return labels.takeLast(suffixLength + 1).joinToString(".")
    }
}
