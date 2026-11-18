// GlassSurface: the glass container. Samples a recorded backdrop through
// the effect chain and draws tint, content, and decorations.

package com.vallxz.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import com.vallxz.glass.engine.GlassBackdrop
import com.vallxz.glass.engine.GlassEffectState
import com.vallxz.glass.engine.GlassSurfaceState
import com.vallxz.glass.engine.LocalGlassBackdrop
import com.vallxz.glass.engine.glassDraw

/**
 * A glass surface: a container whose background is the live backdrop behind
 * it, blurred and refracted through a lens, following [style] and clipped
 * to [shape], with edge highlight, shadow, tint, and surface fill on top.
 *
 * The backdrop is [backdrop] when given, otherwise the nearest
 * [LocalGlassBackdrop] in scope. The backdrop must be attached to background
 * content with `Modifier.glassBackdrop`, and this surface must be a sibling
 * drawn after that content, never a child of it. See `GlassBackdrop` for
 * why. Without any backdrop the surface degrades to a plain tinted
 * container instead of crashing.
 *
 * Only rounded-rectangle shapes are supported (see the lens shader):
 * [RoundedCornerShape], [androidx.compose.foundation.shape.CircleShape],
 * and [androidx.compose.ui.graphics.RectangleShape] work; other shapes
 * throw.
 *
 * @param exportBackdrop When true, this surface's glass background
 *   (effected patch, tint, and surface fill - not its content or
 *   decorations) is provided as the [LocalGlassBackdrop] for its content,
 *   so glass surfaces inside it sample the outer surface's real glass
 *   background (nested glass).
 * @param sampleScale Magnification of the sampled backdrop around the
 *   surface center (1 = none). Used by magnifier-style components.
 * @param styleProvider Animated styles: a provider evaluated in the draw
 *   phase, so press/drag state read inside it invalidates the draw only.
 *   When given it replaces [style]. Prefer it over rebuilding [style]
 *   during composition on every animation tick.
 * @param sampleScaleProvider Animated magnification, evaluated in the
 *   draw phase like [styleProvider]. When given it replaces [sampleScale].
 * @param layerScale The graphicsLayer scale currently applied to this
 *   surface's visible output, evaluated in the draw phase. The engine
 *   samples with the inverse scale so the lens stays aligned while the
 *   surface is scaled; report exactly the scale the surface's
 *   graphicsLayer sets (1,1 when unscaled). Translations need no report.
 */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop? = null,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Default,
    shape: Shape = RoundedCornerShape(22.dp),
    exportBackdrop: Boolean = false,
    sampleScale: Float = 1f,
    styleProvider: (() -> GlassStyle)? = null,
    sampleScaleProvider: (() -> Float)? = null,
    layerScale: () -> Offset = { Offset(1f, 1f) },
    content: @Composable BoxScope.() -> Unit,
) {
    val resolved = backdrop ?: LocalGlassBackdrop.current
    val glassLayer = rememberGraphicsLayer()
    val insetLayer = rememberGraphicsLayer()
    val surfaceState = remember { GlassSurfaceState() }
    val effectState = remember { GlassEffectState() }
    val exportLayer = rememberGraphicsLayer()
    val exportTarget = remember(exportLayer) { GlassBackdrop(exportLayer) }

    val surfaceModifier = if (resolved != null) {
        modifier.glassDraw(
            backdrop = resolved,
            glassLayer = glassLayer,
            insetLayer = insetLayer,
            surfaceState = surfaceState,
            effectState = effectState,
            styleProvider = styleProvider ?: { style },
            shape = shape,
            sampleScaleProvider = sampleScaleProvider ?: { sampleScale },
            layerScaleProvider = layerScale,
            exportTarget = if (exportBackdrop) exportTarget else null,
        )
    } else {
        modifier.tintFallback(style, shape)
    }

    if (exportBackdrop && resolved != null) {
        CompositionLocalProvider(LocalGlassBackdrop provides exportTarget) {
            Box(modifier = surfaceModifier, content = content)
        }
    } else {
        Box(modifier = surfaceModifier, content = content)
    }
}

/**
 * Turns any layout into a glass surface: same engine as [GlassSurface],
 * as a modifier, for content that already has its own container.
 *
 * The backdrop resolves the same way as [GlassSurface] (explicit argument,
 * else [LocalGlassBackdrop]), and the same no-backdrop tint fallback
 * applies. This modifier does not export a backdrop; use [GlassSurface]
 * with `exportBackdrop = true` for nested glass. Animated styles,
 * magnification, and layer scale work the same as on [GlassSurface]: pass
 * [styleProvider]/[sampleScaleProvider]/[layerScale] so animation state is
 * read in the draw phase.
 */
fun Modifier.glass(
    backdrop: GlassBackdrop? = null,
    style: GlassStyle = GlassStyle.Default,
    shape: Shape = RoundedCornerShape(22.dp),
    sampleScale: Float = 1f,
    styleProvider: (() -> GlassStyle)? = null,
    sampleScaleProvider: (() -> Float)? = null,
    layerScale: () -> Offset = { Offset(1f, 1f) },
): Modifier = composed {
    val resolved = backdrop ?: LocalGlassBackdrop.current
    val glassLayer = rememberGraphicsLayer()
    val insetLayer = rememberGraphicsLayer()
    val surfaceState = remember { GlassSurfaceState() }
    val effectState = remember { GlassEffectState() }
    if (resolved != null) {
        this.glassDraw(
            backdrop = resolved,
            glassLayer = glassLayer,
            insetLayer = insetLayer,
            surfaceState = surfaceState,
            effectState = effectState,
            styleProvider = styleProvider ?: { style },
            shape = shape,
            sampleScaleProvider = sampleScaleProvider ?: { sampleScale },
            layerScaleProvider = layerScale,
        )
    } else {
        this.tintFallback(style, shape)
    }
}

/** No-backdrop fallback: a plain tinted shape, or the modifier unchanged. */
private fun Modifier.tintFallback(style: GlassStyle, shape: Shape): Modifier =
    when {
        style.surfaceColor != Color.Unspecified ->
            background(color = style.surfaceColor, shape = shape)

        style.tint != Color.Unspecified ->
            background(color = style.tint.copy(alpha = style.tintAlpha), shape = shape)

        else -> this
    }
