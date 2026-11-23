package com.chrogecko.browser.core.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * One place for haptic feedback across the browser.
 *
 * We deliberately use platform feedback constants through [View.performHapticFeedback] instead of a
 * generic long vibration: on modern/premium devices Android maps CONFIRM, SEGMENT_TICK, GESTURE_*
 * and TOGGLE_* to the device's own haptic model (short, crisp effects with the right amplitude),
 * and it still honours the user's system haptic setting. Older devices get simple VIRTUAL_KEY /
 * CLOCK_TICK fallbacks.
 */
object Haptics {

    fun gestureStart(view: View) = perform(
        view,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.GESTURE_START
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        },
    )

    fun gestureEnd(view: View) = perform(
        view,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.GESTURE_END
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        },
    )

    fun dragStart(view: View) = perform(
        view,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.DRAG_START
        } else {
            HapticFeedbackConstants.LONG_PRESS
        },
    )

    /** Very light tick for list/selection style interactions. */
    fun tick(view: View) = perform(view, HapticFeedbackConstants.CLOCK_TICK)

    /** Standard touch feedback for a button press. */
    fun click(view: View) = perform(view, HapticFeedbackConstants.CONTEXT_CLICK)

    /** A decisive but still subtle effect for committed actions. */
    fun confirm(view: View) = perform(
        view,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        },
    )

    fun toggle(view: View, on: Boolean) = perform(
        view,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        },
    )

    private fun perform(view: View, feedback: Int) {
        view.performHapticFeedback(feedback)
    }
}
