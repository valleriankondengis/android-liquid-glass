// Adaptive glass: retunes blur, saturation, and brightness from the
// measured luminance of the backdrop.

package com.vallxz.glass.effects

import androidx.compose.ui.unit.dp
import com.vallxz.glass.GlassStyle
import kotlin.math.sign

/**
 * Adaptive luminance mapping: pure functions that retune a [GlassStyle] for
 * the measured brightness of the backdrop behind it.
 *
 * The mapping itself is pure and unit-tested; measuring the luminance is a
 * separate, best-effort concern (see `GlassLuminanceProbe` in the engine
 * package). Keeping the two apart is what makes the tuning testable without
 * a device and honest about what a failed probe means: the style simply
 * keeps its last adapted value.
 *
 * Tuning (the standard profile): luminance is remapped as the signed
 * square of `(luminance x 2 - 1)`, so the response stays gentle near
 * mid-gray and grows toward the extremes. Brightness runs from -0.12 over
 * a black backdrop through 0.08 at mid-gray to 0.22 over a white one.
 * Contrast drops from 1 to a 0.75 floor on bright backdrops and stays 1 on
 * dark ones. Saturation is fixed at 1.5. Blur scales x2 on bright and x0.25
 * on dark backdrops (a zero base blur counts as 8 dp), floored at 0.5 dp
 * (about 1 px at common densities).
 */
object GlassAdaptive {

    /**
     * Returns [style] retuned for [luminance] (0 = black backdrop, 1 = white
     * backdrop). [strength] in [0, 1] scales the whole adjustment; zero
     * returns the style unchanged.
     */
    fun adapt(
        style: GlassStyle,
        luminance: Float,
        strength: Float = 1f,
    ): GlassStyle {
        val clampedStrength = strength.coerceIn(0f, 1f)
        if (clampedStrength == 0f) return style
        // -1 over a black backdrop, +1 over a white one; signed square keeps
        // the response gentle near mid-gray.
        val centered = luminance.coerceIn(0f, 1f) * 2f - 1f
        val remapped = sign(centered) * centered * centered

        val targetBrightness = if (remapped >= 0f) {
            lerp(0.08f, 0.22f, remapped)
        } else {
            lerp(0.08f, -0.12f, -remapped)
        }
        val targetContrast = if (remapped >= 0f) {
            lerp(1f, 0.75f, remapped)
        } else {
            1f
        }
        val blurFactor = if (remapped >= 0f) {
            lerp(1f, 2f, remapped)
        } else {
            lerp(1f, 0.25f, -remapped)
        }
        val baseBlur = if (style.blurRadius.value <= 0f) 8f else style.blurRadius.value
        val targetBlur = (baseBlur * blurFactor).coerceAtLeast(0.5f)

        return style.copy(
            brightness = lerp(style.brightness, targetBrightness, clampedStrength),
            contrast = lerp(style.contrast, targetContrast, clampedStrength),
            saturation = lerp(style.saturation, 1.5f, clampedStrength),
            blurRadius = lerp(style.blurRadius.value, targetBlur, clampedStrength).dp,
        )
    }

    private fun lerp(start: Float, stop: Float, fraction: Float): Float =
        start + (stop - start) * fraction

    /** Relative luminance of one sRGB pixel, in [0, 1] (Rec. 709 weights). */
    fun pixelLuminance(red: Int, green: Int, blue: Int): Float =
        (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f
}
