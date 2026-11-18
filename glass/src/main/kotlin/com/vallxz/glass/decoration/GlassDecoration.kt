// Edge decoration drawing: drop shadow and edge highlight for
// glass surfaces.

package com.vallxz.glass.decoration

import android.graphics.BlurMaskFilter
import android.graphics.RuntimeShader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.vallxz.glass.engine.GlassShaders
import com.vallxz.glass.shapes.CornerRadii
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Edge decoration drawing for glass surfaces: drop shadow and edge
 * highlight. (The inner shadow is drawn by the engine in `GlassDraw`: it
 * needs its own offscreen layer so the blur softens both edges of the
 * crescent.)
 *
 * Draw stacking, bottom to top: drop shadow, effected backdrop layer,
 * surface tint, content, edge highlight, inner shadow. The highlight and
 * inner shadow sit above the content so the rim stays visible over
 * anything drawn inside the surface.
 *
 * The shadows use the Compose [Canvas] API with a Compose [Paint] whose
 * native paint (the `nativePaint` extension property) carries the blur
 * mask filter. The framework canvas is never touched directly:
 * `Canvas.nativeCanvas` does not resolve on this BOM, and the Compose
 * canvas already exposes everything needed (clip with [ClipOp.Difference],
 * translate, save/restore, drawPath, saveLayer). `Paint.asFrameworkPaint()`
 * is deprecated on this BOM; `nativePaint` is its replacement.
 *
 * The callers resolve every Dp to pixels and pass plain [Float]s in: these
 * functions may run inside a `GraphicsLayer.record` block, and any
 * [androidx.compose.ui.unit.Density] access (`.toPx()`, `.dp`, `density`)
 * on a record block's [DrawScope] receiver recurses forever between
 * `LayoutNodeDrawScope.getDensity` and `CanvasDrawScope.getDensity` and
 * crashes with a `StackOverflowError`.
 * Never call a Density API in here.
 */

/**
 * Draws [shadow] as a blurred fill of [path], clipped to the area *outside*
 * the path, so the shadow never darkens the glass interior. Call this before
 * drawing the glass content, unclipped.
 */
internal fun DrawScope.drawGlassDropShadow(
    path: Path,
    shadow: GlassShadow,
    radiusPx: Float,
    offsetPx: Offset,
) {
    if (radiusPx <= 0f || shadow.alpha <= 0f) return
    val shadowColor = shadow.color.copy(alpha = shadow.color.alpha * shadow.alpha)
    if (shadowColor.alpha <= 0f) return
    drawIntoCanvas { canvas ->
        canvas.save()
        // DIFFERENCE keeps the blur strictly outside the shape.
        canvas.clipPath(path, ClipOp.Difference)
        canvas.translate(offsetPx.x, offsetPx.y)
        val paint = Paint().apply {
            style = PaintingStyle.Fill
            color = shadowColor
            nativePaint.apply {
                isAntiAlias = true
                maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
            }
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}

/** The highlight shader, compiled once and shared: drawing is single-threaded. */
private val highlightShader: RuntimeShader by lazy { RuntimeShader(GlassShaders.HIGHLIGHT) }

/**
 * Light direction at 45 degrees, pointing down-right in screen
 * coordinates. The angled mode takes the absolute dot product, so only
 * the split mode's white/black sides depend on the sign: white falls on
 * the bottom/right edges, black on the top/left.
 */
private val defaultLightDirection: Offset =
    Offset(cos(Math.toRadians(45.0)).toFloat(), sin(Math.toRadians(45.0)).toFloat())

/**
 * Draws [highlight] as a single stroke along [path]. The stroke is twice
 * the highlight width and is drawn inside the shape clip by the caller, so
 * only its inner half is visible: the rim glow sits on the glass edge
 * without haloing the backdrop. The stroke carries a true blur mask
 * filter, so the rim softens evenly instead of showing a hard inner edge
 * under a faint halo.
 *
 * [GlassHighlightStyle.Angled] and [GlassHighlightStyle.Split] evaluate a
 * shader over the stroke: brightness follows the dot product between the
 * shape's edge normal and the light direction, raised to the falloff.
 * [GlassHighlightStyle.Flat] is a solid stroke with no shader. The
 * shader's color already carries the style's white level (50% angled,
 * 38% split intensity), and the paint alpha carries [GlassHighlight.alpha].
 */
internal fun DrawScope.drawGlassHighlight(
    path: Path,
    size: Size,
    radii: CornerRadii,
    highlight: GlassHighlight,
    widthPx: Float,
    blurPx: Float,
) {
    if (widthPx <= 0f || highlight.alpha <= 0f) return
    val minDimension = minOf(size.width, size.height)
    val strokeWidthPx = ceil(minOf(widthPx, minDimension / 2f)) * 2f
    val useShader = highlight.style != GlassHighlightStyle.Flat
    if (useShader) {
        val (mode, strength, color) = when (highlight.style) {
            GlassHighlightStyle.Angled -> Triple(0, 1f, Color.White.copy(alpha = 0.5f))
            GlassHighlightStyle.Split -> Triple(1, 0.38f, Color.White)
            GlassHighlightStyle.Flat -> Triple(0, 1f, Color.White)
        }
        highlightShader.setFloatUniform("glassSize", size.width, size.height)
        highlightShader.setFloatUniform(
            "cornerRadii",
            radii.topLeft,
            radii.topRight,
            radii.bottomRight,
            radii.bottomLeft,
        )
        highlightShader.setFloatUniform(
            "lightDirection",
            defaultLightDirection.x,
            defaultLightDirection.y,
        )
        highlightShader.setFloatUniform("falloff", highlight.falloff)
        highlightShader.setFloatUniform("mode", mode.toFloat())
        highlightShader.setFloatUniform("strength", strength)
        highlightShader.setFloatUniform(
            "highlightColor",
            color.red,
            color.green,
            color.blue,
            color.alpha,
        )
    }
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            style = PaintingStyle.Stroke
            strokeWidth = strokeWidthPx
            alpha = highlight.alpha
            blendMode = if (highlight.style == GlassHighlightStyle.Split) {
                BlendMode.SrcOver
            } else {
                BlendMode.Plus
            }
            color = if (useShader) {
                Color.White
            } else {
                Color.White.copy(alpha = 0.38f)
            }
            nativePaint.apply {
                isAntiAlias = true
                maskFilter = if (blurPx > 0f) {
                    BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                } else {
                    null
                }
                shader = if (useShader) highlightShader else null
            }
        }
        canvas.drawPath(path, paint)
    }
}
