// Pure color-matrix math for backdrop color adjustments
// (saturation, contrast, brightness).

package com.vallxz.glass.effects

/**
 * Pure color-matrix math for backdrop color adjustments.
 *
 * Each function returns a 4x5 color matrix in row-major order, ready to be
 * wrapped in a `ColorMatrix`. Nothing here touches Compose or Android types,
 * so every function is covered by plain JVM unit tests.
 *
 * The brightness/contrast/saturation matrix is the standard one: saturation
 * interpolates each channel toward luma using the Rec. 709 luma weights
 * (0.213 / 0.715 / 0.072), contrast scales around mid-gray, and brightness
 * shifts the offset column.
 */
object ColorMatrices {

    /**
     * Combined brightness, contrast, and saturation adjustment.
     * The defaults (0, 1, 1) produce the identity matrix.
     */
    fun colorControls(
        brightness: Float = 0f,
        contrast: Float = 1f,
        saturation: Float = 1f,
    ): FloatArray {
        val invSat = 1f - saturation
        val r = 0.213f * invSat
        val g = 0.715f * invSat
        val b = 0.072f * invSat

        val c = contrast
        val t = (0.5f - c * 0.5f + brightness) * 255f
        val s = saturation

        val cr = c * r
        val cg = c * g
        val cb = c * b
        val cs = c * s

        return floatArrayOf(
            cr + cs, cg, cb, 0f, t,
            cr, cg + cs, cb, 0f, t,
            cr, cg, cb + cs, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Fixed vibrancy boost: saturation only, no brightness/contrast change. */
    fun vibrant(): FloatArray = colorControls(saturation = 1.5f)

    /** Multiplies the backdrop alpha by [alpha], leaving RGB untouched. */
    fun opacity(alpha: Float): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, alpha, 0f,
    )
}
