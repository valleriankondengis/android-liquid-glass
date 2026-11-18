// CornerRadii: the four corner radii of a rounded rectangle, in the
// order the refraction shader expects.

package com.vallxz.glass.shapes

/**
 * The four corner radii of a rounded rectangle in physical order, in pixels.
 *
 * The refraction shader reads radii in exactly this order
 * (top-left, top-right, bottom-right, bottom-left), so [toFloatArray] feeds
 * its `cornerRadii` uniform directly.
 */
data class CornerRadii(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
) {
    fun toFloatArray(): FloatArray = floatArrayOf(topLeft, topRight, bottomRight, bottomLeft)
}

/**
 * Resolves logical (start/end) corner sizes in pixels into physical
 * [CornerRadii], clamping every value into [0, [maxRadiusPx]].
 *
 * Logical corners come from a shape's start/end definition; in right-to-left
 * layouts start and end swap sides. Keeping this mapping in pure Kotlin
 * (no `Shape`, no `Density`) is what makes it unit-testable: the engine's
 * Compose adapter only converts a shape to pixels and calls this.
 *
 * @param maxRadiusPx Largest radius that fits the surface, normally half of
 *   its smallest dimension.
 */
fun resolveCornerRadii(
    topStartPx: Float,
    topEndPx: Float,
    bottomEndPx: Float,
    bottomStartPx: Float,
    isLeftToRight: Boolean,
    maxRadiusPx: Float,
): CornerRadii {
    fun clamped(value: Float): Float = value.coerceIn(0f, maxRadiusPx)

    return if (isLeftToRight) {
        CornerRadii(
            topLeft = clamped(topStartPx),
            topRight = clamped(topEndPx),
            bottomRight = clamped(bottomEndPx),
            bottomLeft = clamped(bottomStartPx),
        )
    } else {
        CornerRadii(
            topLeft = clamped(topEndPx),
            topRight = clamped(topStartPx),
            bottomRight = clamped(bottomStartPx),
            bottomLeft = clamped(bottomEndPx),
        )
    }
}
