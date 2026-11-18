// GlassStyle: the complete recipe for one glass surface - blur, lens,
// tint, highlight, and shadow - plus ready-made presets.

package com.vallxz.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vallxz.glass.decoration.GlassHighlight
import com.vallxz.glass.decoration.GlassInnerShadow
import com.vallxz.glass.decoration.GlassShadow
import com.vallxz.glass.effects.GlassGradient

/**
 * Visual recipe for one glass surface: how strongly the backdrop behind it is
 * blurred and refracted, how its color is adjusted, how it is tinted, and
 * which edge decorations it carries.
 *
 * The defaults follow the canonical glass recipe: 8 dp blur, a 16/32 dp
 * lens, vibrancy (saturation 1.5), a 0.5 dp edge highlight, and a 24 dp
 * drop shadow. Pass a copy with overrides to tune a single surface; the
 * engine itself reads only this class, so tuning never touches engine code.
 *
 * @param blurRadius Backdrop blur radius. Zero disables blur.
 * @param refractionHeight Height of the curved refraction zone at the shape edge.
 * @param refractionAmount Displacement strength at the shape edge.
 * @param saturation Color saturation applied to the backdrop (1 = unchanged).
 * @param contrast Color contrast applied to the backdrop (1 = unchanged).
 * @param brightness Brightness offset applied to the backdrop (0 = unchanged).
 * @param depth When true, the lens gradient is pulled toward the surface
 *   center, deepening the refraction.
 * @param tint Color drawn over the glass surface as a hue blend at full
 *   strength plus a solid overlay at [tintAlpha]: the hue pass re-hues the
 *   backdrop behind the glass instead of merely veiling it, so a colored
 *   tint reads as colored glass. [Color.Unspecified] disables tinting.
 *   For achromatic tints (white, black, gray) the hue pass is near-inert
 *   and only the overlay shows. Gradient styles ignore [tint] as an
 *   overlay: their tint lives only inside the gradient shader's fade mix.
 * @param tintAlpha Overlay alpha for [tint]. The overlay replaces the
 *   tint color's own alpha with this value.
 * @param surfaceColor Solid fill drawn over the glass surface after the
 *   tint, with its own baked alpha. This is the plain container fill used
 *   by droplets, thumbs, menus, and modal panels - it never hue-blends.
 *   [Color.Unspecified] disables it. Gradient styles ignore it.
 * @param chromaticAberration Dispersion strength, nominally in [0, 1]. Zero
 *   disables dispersion. Dispersion splits the refracted sample into a
 *   seven-sample spectral spread, so it only shows where refraction bends
 *   light (near the edge) and needs a non-zero [refractionAmount].
 * @param highlight Edge highlight stroke, or null to disable it.
 * @param shadow Drop shadow under the shape, or null to disable it.
 * @param innerShadow Inner shadow inside the shape, or null to disable it.
 * @param gradient Gradient (progressive) refraction profile, or null for
 *   uniform refraction across the whole surface.
 */
@Immutable
data class GlassStyle(
    val blurRadius: Dp = 8.dp,
    val refractionHeight: Dp = 16.dp,
    val refractionAmount: Dp = 32.dp,
    val saturation: Float = 1.5f,
    val contrast: Float = 1f,
    val brightness: Float = 0f,
    val depth: Boolean = false,
    val tint: Color = Color.Unspecified,
    val tintAlpha: Float = 0.28f,
    val surfaceColor: Color = Color.Unspecified,
    val chromaticAberration: Float = 0f,
    val highlight: GlassHighlight? = GlassHighlight(),
    val shadow: GlassShadow? = GlassShadow(),
    val innerShadow: GlassInnerShadow? = null,
    val gradient: GlassGradient? = null,
) {
    companion object {
        /** Balanced default: blur + lens + subtle highlight and shadow. */
        val Default: GlassStyle = GlassStyle()

        /** Barely-there glass: almost no blur, strong lens, no tint. */
        val Clear: GlassStyle = GlassStyle(
            blurRadius = 2.dp,
            refractionHeight = 20.dp,
            refractionAmount = 40.dp,
            saturation = 1.2f,
            tint = Color.Unspecified,
        )

        /** Frosted glass: heavy blur with a milky white tint. */
        val Frosted: GlassStyle = GlassStyle(
            blurRadius = 16.dp,
            refractionHeight = 12.dp,
            refractionAmount = 16.dp,
            saturation = 1.1f,
            tint = Color.White,
            tintAlpha = 0.35f,
        )

        /** Punchy glass: oversaturated backdrop, for colorful content. */
        val Vibrant: GlassStyle = GlassStyle(
            blurRadius = 6.dp,
            refractionHeight = 16.dp,
            refractionAmount = 32.dp,
            saturation = 2f,
            tint = Color.White,
            tintAlpha = 0.12f,
        )

        /** Dark glass: smoked tint for bright backdrops. */
        val Dark: GlassStyle = GlassStyle(
            blurRadius = 10.dp,
            refractionHeight = 16.dp,
            refractionAmount = 28.dp,
            saturation = 1.2f,
            tint = Color.Black,
            tintAlpha = 0.3f,
        )
    }
}
