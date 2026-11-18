// GlassGradient: the gradient glass recipe - refraction weights,
// fade band, and tint intensity.

package com.vallxz.glass.effects

import androidx.compose.runtime.Immutable

/**
 * Gradient (progressive) glass: refraction strength follows a vertical
 * profile built from three weights, and the output fades toward the tint
 * color below a blur-fade band.
 *
 * The profile is the weighted sum of three terms, normalized by the sum of
 * the weights: the top term fades out over the top half of the surface, the
 * bottom term fades in over the bottom half, and the middle term is a
 * triangle peaking at the center. The defaults (0 / 0 / 1) give a panel
 * that is flat at the top and fully refractive at the bottom.
 *
 * @param topWeight Refraction weight at the top edge.
 * @param middleWeight Refraction weight at the vertical center.
 * @param bottomWeight Refraction weight at the bottom edge.
 * @param fadeStart Blur-fade band start, as a fraction of the height.
 * @param fadeEnd Blur-fade band end, as a fraction of the height. Must be
 *   greater than [fadeStart]; the effect chain enforces a minimum gap.
 * @param tintIntensity Strength of the tint mix below the fade band.
 */
@Immutable
data class GlassGradient(
    val topWeight: Float = 0f,
    val middleWeight: Float = 0f,
    val bottomWeight: Float = 1f,
    val fadeStart: Float = 0f,
    val fadeEnd: Float = 0.8f,
    val tintIntensity: Float = 0.6f,
) {

    /**
     * The refraction profile at [fraction] of the height (0 = top, 1 =
     * bottom), in [0, 1]. This mirrors the gradient shader's profile
     * computation so the shape of the curve is unit-testable without a GPU.
     */
    fun profileAt(fraction: Float): Float {
        val ny = fraction.coerceIn(0f, 1f)
        val sum = topWeight + middleWeight + bottomWeight
        if (sum <= 0f) return 0f
        val top = topWeight * (1f - smoothstep(0f, 0.5f, ny))
        val bottom = bottomWeight * smoothstep(0.5f, 1f, ny)
        val middle = middleWeight * (1f - kotlin.math.abs(ny * 2f - 1f))
        return ((top + middle + bottom) / sum).coerceIn(0f, 1f)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
