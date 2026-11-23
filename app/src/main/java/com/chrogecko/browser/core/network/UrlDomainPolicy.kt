package com.chrogecko.browser.core.network

/** Domain helpers for deciding where captured credentials may be forwarded. */
object UrlDomainPolicy {

    private val MULTI_LABEL_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au",
        "co.jp", "ne.jp", "or.jp", "co.nz", "com.br", "com.cn", "com.mx",
        "co.in", "com.sg", "co.za", "com.tr", "com.hk", "com.tw",
    )

    /** Registrable-domain approximation with common multi-label public suffixes excluded. */
    fun registrableDomain(host: String): String {
        val labels = host.trim().lowercase().trimEnd('.').split('.').filter { it.isNotEmpty() }
        if (labels.size < 2) return labels.firstOrNull().orEmpty()
        val lastTwo = labels.takeLast(2).joinToString(".")
        if (labels.size >= 3 && lastTwo in MULTI_LABEL_SUFFIXES) {
            return labels.takeLast(3).joinToString(".")
        }
        return lastTwo
    }
}
