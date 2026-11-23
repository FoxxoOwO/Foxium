package com.chrogecko.browser.core.engine

/** Ids of the privileged extensions bundled in `assets/extensions`. */
object SystemExtensionIds {
    const val USERSCRIPTS = "sys_userscripts@chrogecko.browser"
    const val DARKMODE = "sys_darkmode@chrogecko.browser"
    const val MEDIASNIFFER = "sys_mediasniffer@chrogecko.browser"
    const val GESTURES = "sys_gestures@chrogecko.browser"
    const val AUTOFILL = "sys_autofill@chrogecko.browser"
    const val EXPANDER = "sys_expander@chrogecko.browser"
    const val READER = "sys_reader@chrogecko.browser"

    val ALL = setOf(USERSCRIPTS, DARKMODE, MEDIASNIFFER, GESTURES, AUTOFILL, EXPANDER, READER)
}
