// GlassPress: press-scale feedback for tappable glass content.

package com.vallxz.glass.interaction

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Press scale for glass components: while [interactionSource] reports a
 * press, the scale animates down to [pressedScale] on a spring and back to
 * 1 when released.
 *
 * The scale is applied with `graphicsLayer` *outside* the glass draw, so
 * the whole composited surface (sampled backdrop, tint, decorations, and
 * content) scales as one unit and the backdrop alignment never drifts: no
 * inverse-transform compensation is needed.
 */
@Composable
internal fun rememberGlassPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.94f,
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "glassPressScale",
    )
    return scale
}

/** Applies a uniform press [scale] around the layout's center. */
internal fun Modifier.glassPressScale(scale: Float): Modifier =
    graphicsLayer(scaleX = scale, scaleY = scale)
