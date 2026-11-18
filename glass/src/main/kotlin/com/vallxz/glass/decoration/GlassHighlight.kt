// GlassHighlight: the edge highlight recipe - style, width, blur,
// falloff, and alpha.

package com.vallxz.glass.decoration

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How a glass edge highlight is rendered. */
enum class GlassHighlightStyle {
    /**
     * Angled light: brightness follows the dot product between the shape's
     * edge normal and a 45-degree light from the top-left, raised to the
     * highlight falloff. White at 50%, drawn additively.
     */
    Angled,

    /**
     * Split rim: a black/white step from the sign of the same dot product -
     * white on the bottom/right edges, black on the top/left - at 0.38
     * intensity, drawn normally.
     */
    Split,

    /** Flat stroke with no shader: solid white at 38%, drawn additively. */
    Flat,
}

/**
 * Edge highlight drawn as a stroke around a glass shape.
 *
 * Defaults are the canonical values: 0.5 dp wide, blurred by half the
 * width, full alpha, angled light with falloff 1.
 */
@Immutable
data class GlassHighlight(
    val width: Dp = 0.5.dp,
    val blurRadius: Dp = 0.25.dp,
    val alpha: Float = 1f,
    val style: GlassHighlightStyle = GlassHighlightStyle.Angled,
    val falloff: Float = 1f,
)
