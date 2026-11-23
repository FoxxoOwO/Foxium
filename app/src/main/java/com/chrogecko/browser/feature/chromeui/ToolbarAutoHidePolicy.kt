package com.chrogecko.browser.feature.chromeui

import kotlin.math.abs

/** Pure Chrome-style toolbar auto-hide decision, kept testable without a ViewModel. */
internal object ToolbarAutoHidePolicy {

    data class Result(val lastScrollY: Int, val hidden: Boolean)

    fun next(currentY: Int, lastScrollY: Int, hidden: Boolean): Result {
        if (currentY <= 0) return Result(currentY, false)
        val delta = currentY - lastScrollY
        if (abs(delta) < SCROLL_EPSILON) return Result(lastScrollY, hidden)
        val nextHidden = when {
            delta > 0 && currentY > REVEAL_THRESHOLD -> true
            delta < 0 -> false
            else -> hidden
        }
        return Result(currentY, nextHidden)
    }

    private const val SCROLL_EPSILON = 4
    private const val REVEAL_THRESHOLD = 96
}
