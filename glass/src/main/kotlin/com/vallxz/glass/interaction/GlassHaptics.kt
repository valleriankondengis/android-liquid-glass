// GlassHaptics: platform haptic feedback for glass interactions -
// ticks, scrub steps, toggles, and confirmations.

package com.vallxz.glass.interaction

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt

/**
 * Haptics for glass components, driven through the view with platform
 * feedback constants. Each feedback picks a modern constant on recent
 * platforms and a fallback below, so ticks, scrubs, switch flips, and
 * confirmations feel like the platform's own. Failures are swallowed:
 * haptics must never break a gesture.
 *
 * @param view The view the feedback is performed on.
 * @param enabled Whether feedback is currently allowed; read fresh on
 *   every call so a toggled setting takes effect immediately.
 */
@Stable
internal class GlassHaptics(
    private val view: View,
    private val enabled: () -> Boolean,
) {
    private fun perform(modern: Int, minSdk: Int, fallback: Int) {
        if (!enabled()) return
        val constant = if (Build.VERSION.SDK_INT >= minSdk) modern else fallback
        try {
            view.performHapticFeedback(constant)
        } catch (_: Exception) {
            // Swallowed on purpose: haptics must never break a gesture.
        }
    }

    /** Crisp tick for discrete steps and selection changes. */
    fun tick() = perform(
        HapticFeedbackConstants.SEGMENT_TICK,
        Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        HapticFeedbackConstants.CLOCK_TICK,
    )

    /** Frequent tick for continuous scrubbing along detents. */
    fun scrub() = perform(
        HapticFeedbackConstants.SEGMENT_FREQUENT_TICK,
        Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        HapticFeedbackConstants.CLOCK_TICK,
    )

    /** Switch feedback, distinct for on and off. */
    fun toggle(on: Boolean) = perform(
        if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF,
        Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        HapticFeedbackConstants.VIRTUAL_KEY,
    )

    /** Confirmation for button presses. */
    fun confirm() = perform(
        HapticFeedbackConstants.CONFIRM,
        Build.VERSION_CODES.R,
        HapticFeedbackConstants.VIRTUAL_KEY,
    )
}

/** Remembers a [GlassHaptics] bound to the current view and [enabled] flag. */
@Composable
internal fun rememberGlassHaptics(enabled: Boolean): GlassHaptics {
    val view = LocalView.current
    val enabledState = rememberUpdatedState(enabled)
    return remember(view) { GlassHaptics(view) { enabledState.value } }
}

/**
 * The haptic detent step for [value] in [start]..[end], divided into
 * [steps] detents and rounded to the nearest step, so tick boundaries sit
 * halfway between detents.
 */
internal fun glassHapticStep(value: Float, start: Float, end: Float, steps: Int): Int {
    val span = (end - start).coerceAtLeast(1e-6f)
    return (((value - start) / span) * steps).roundToInt()
}
