// GlassDraw: the draw pipeline that turns a backdrop and a style into
// a glass surface - sampling, tint, content, and decorations.

package com.vallxz.glass.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.decoration.GlassInnerShadow
import com.vallxz.glass.decoration.drawGlassDropShadow
import com.vallxz.glass.decoration.drawGlassHighlight
import com.vallxz.glass.shapes.CornerRadii
import com.vallxz.glass.shapes.resolveCornerRadii
import com.vallxz.glass.shapes.toPath
import kotlin.math.ceil

/** Cache key for a surface's shape outline: everything the path depends on. */
internal data class OutlineKey(
    val shape: Shape,
    val size: Size,
    val layoutDirection: LayoutDirection,
    val density: Float,
    val fontScale: Float,
)

/** Cache key for a surface's inner-shadow layer: everything the recording depends on. */
internal data class InsetKey(
    val radiusPx: Float,
    val offset: Offset,
    val color: Color,
    val outlineKey: OutlineKey,
)
internal class GlassSurfaceState {
    /**
     * This surface's coordinates, with a never-equal policy: the
     * coordinates object is the same instance across placements, so the
     * default policy would swallow every move and the surface would keep
     * sampling a stale offset while scrolling. Never-equal invalidates the draw on every placement.
     */
    var coordinates: LayoutCoordinates? by mutableStateOf<LayoutCoordinates?>(
        null,
        neverEqualPolicy(),
    )

    /**
     * The effect last assigned to this surface's glass layer. Reassigning
     * the same effect instance on every draw would mark the layer dirty
     * for no reason, so the draw code assigns only when the built effect
     * actually changes.
     */
    var lastRenderEffect: RenderEffect? = null

    /**
     * The cached shape outline (clip path + corner radii) for the last
     * draw, keyed by shape, size, layout direction, and density. The
     * path is rebuilt only when the key changes, so animation ticks
     * reuse the outline instead of allocating a new path on every draw.
     */
    var outlineKey: OutlineKey? = null
    var outlinePath: Path? = null
    var outlineRadii: CornerRadii? = null

    /**
     * The inner-shadow layer's recording key (alpha excluded: alpha rides
     * the layer, so alpha-only changes never re-record) and the blur
     * radius of the effect currently assigned to the layer.
     */
    var insetKey: InsetKey? = null
    var lastInsetBlurPx: Float = Float.NaN
}

/**
 * Draws the glass surface: the [backdrop] patch behind this layout, run
 * through the style effect chain, clipped to [shape], with the style tint
 * and decorations on top, then the surface's own content.
 *
 * How the patch lines up: each backdrop layer is recorded in its own
 * coordinates, so it is translated by the negative offset of this surface's
 * origin inside that backdrop (from [LayoutCoordinates], with a
 * window-position fallback when the coordinate transform fails). The effect
 * chain lives on [glassLayer], so blur, color, and refraction apply to the
 * recorded patch only, never to the content drawn above it. The glass layer
 * is recorded larger than the surface by the effect padding on all sides
 * and drawn shifted back by the padding, so blur samples at the edge read
 * real pixels instead of clamped ones.
 *
 * The style, sample scale, and layer scale arrive as providers evaluated
 * inside the draw block, never as values computed during composition:
 * animation state (press progress, velocity squash) read here invalidates
 * this draw only, while the same reads during composition would recompose
 * the surface on every animation tick. Callers with a static style pass a
 * provider returning the composition value.
 *
 * [layerScaleProvider] reports the graphicsLayer scale currently applied
 * to this surface's visible output (1,1 when unscaled). The sampled
 * content is recorded with the inverse scale about the recording origin,
 * inside the sample-scale transform and outside the offset translation:
 * the surface's coordinates already include its own layer transform, so
 * the offset is the transformed origin, and the inverse cancels the
 * visible scale exactly - without it, the sampled patch scales along with
 * the node while the real content outside does not, so text and icons
 * inside the lens drift, double, and look like they fly off while pressed.
 * The inverse is pivot-agnostic: it holds
 * for any graphicsLayer transform origin. Translations need no inverse -
 * the offset math already accounts for them - and rotation is not
 * supported. Callers that scale a glass surface must report the same
 * scale here that their graphicsLayer sets, or the lens will misalign
 * while scaled. A wrapped source's own sampling scale (see
 * `rememberGlassWrappedBackdrop`) wraps the inverse scale and the offset
 * translation for that source only, pivoting on the surface center, so
 * one merged layer can shrink or magnify independently of the rest.
 *
 * Draw stacking, bottom to top: drop shadow, effected backdrop layer,
 * surface tint (hue blend plus overlay) and solid surface fill, content,
 * edge highlight, inner shadow. The content is clipped to the shape, and
 * the highlight and inner shadow sit above it so the rim stays visible
 * over anything drawn inside. Gradient styles draw neither the tint nor
 * the surface fill: their tint lives only inside the gradient shader's
 * fade mix, and a uniform overlay would flatten the progressive fade
 * into a frosted panel.
 *
 * Until every sampled backdrop has recorded its first frame, only the tint
 * and decorations are drawn, so the surface never flashes empty content.
 *
 * [sampleScaleProvider] magnifies the sampled content around the surface
 * center (1 = no magnification): the sources are drawn scaled by the
 * provided value about the center when the glass layer is recorded, so
 * each surface pixel samples a smaller backdrop region.
 *
 * When [exportTarget] is given, the surface's glass background (effected
 * patch and tint only) is recorded into the export backdrop's layer on
 * every draw of this surface, and that layer is what gets drawn. Nested glass surfaces sampling
 * the export backdrop then see the outer surface's real glass background.
 * The surface's content is deliberately NOT recorded into the export layer:
 * content may hold nested glass surfaces sampling the export backdrop, and
 * recording them would make the export RenderNode contain itself
 * (transitively), which recurses forever in `RenderNode::prepareTreeImpl`
 * and kills the RenderThread with a native SIGSEGV. The drop shadow, highlight, and inner shadow are likewise
 * kept out of the export layer - they are per-surface decorations drawn in
 * the main pass. The export layer is recorded after [glassLayer], never
 * nested inside its recording.
 */
internal fun Modifier.glassDraw(
    backdrop: GlassBackdrop,
    glassLayer: GraphicsLayer,
    insetLayer: GraphicsLayer,
    surfaceState: GlassSurfaceState,
    effectState: GlassEffectState,
    styleProvider: () -> GlassStyle,
    shape: Shape,
    sampleScaleProvider: () -> Float = { 1f },
    layerScaleProvider: () -> Offset = { Offset(1f, 1f) },
    exportTarget: GlassBackdrop? = null,
): Modifier =
    this
        .onGloballyPositioned {
            surfaceState.coordinates = it
            if (exportTarget != null) {
                exportTarget.coordinates = it
            }
        }
        .drawWithContent {
            // The surface's own coordinates (never-equal snapshot state)
            // invalidate this draw on every move, so the sampled offset
            // stays fresh while scrolling. Backdrop re-recordings do NOT
            // invalidate this draw: at API 33+ the renderer picks up a
            // re-recorded layer's new content through the render-node
            // reference, so a fixed surface shows fresh backdrop pixels
            // without re-recording its effected layer. (Reading a
            // recording version here would force every surface to re-record on
            // every backdrop recording and jank scrolling.)
            val sources = backdrop.sampled
            // Providers are evaluated here, in the draw phase: animation
            // state read inside them invalidates this draw, never the
            // composition that built the surface.
            val style = styleProvider()
            val sampleScale = sampleScaleProvider()
            val layerScale = layerScaleProvider()
            val glassCoordinates = surfaceState.coordinates
            val surfaceSize = size
            // The shape outline is cached on the surface state and rebuilt
            // only when the shape, size, direction, or density changes, so
            // animation ticks reuse the path instead of allocating a new
            // one on every draw. The path is only ever read (clip + fill),
            // never mutated, so reuse across draws is safe.
            val outlineKey = OutlineKey(shape, surfaceSize, layoutDirection, density, fontScale)
            val cachedPath = surfaceState.outlinePath
            val cachedRadii = surfaceState.outlineRadii
            val (path, radii) =
                if (surfaceState.outlineKey == outlineKey && cachedPath != null && cachedRadii != null) {
                    cachedPath to cachedRadii
                } else {
                    val newPath = shape.toPath(surfaceSize, layoutDirection, this)
                    val newRadii = shape.resolveCornerRadii(surfaceSize, layoutDirection, this)
                    surfaceState.outlineKey = outlineKey
                    surfaceState.outlinePath = newPath
                    surfaceState.outlineRadii = newRadii
                    newPath to newRadii
                }
            val sourcesReady = sources.all { source ->
                source.isReady &&
                    source.coordinates != null && source.coordinates!!.isAttached
            }
            val ready = sourcesReady &&
                glassCoordinates != null && glassCoordinates.isAttached

            var paddingPx = 0f
            if (ready) {
                val built = effectState.buildEffect(
                    style = style,
                    widthPx = surfaceSize.width,
                    heightPx = surfaceSize.height,
                    radii = radii,
                    blurPx = style.blurRadius.toPx(),
                    refractionHeightPx = style.refractionHeight.toPx(),
                    refractionAmountPx = style.refractionAmount.toPx(),
                )
                paddingPx = built.paddingPx
                if (built.effect !== surfaceState.lastRenderEffect) {
                    surfaceState.lastRenderEffect = built.effect
                    glassLayer.renderEffect = built.effect
                }

                val recordSize = IntSize(
                    ceil(surfaceSize.width + paddingPx * 2f).toInt(),
                    ceil(surfaceSize.height + paddingPx * 2f).toInt(),
                )
                val center = Offset(surfaceSize.width / 2f, surfaceSize.height / 2f)
                if (recordSize.width > 0 && recordSize.height > 0) {
                    // Inverse of the surface's visible layer scale, about
                    // the recording origin, outside the offset translation:
                    // the offset below is the transformed origin (the
                    // coordinates include the layer transform), so scaling
                    // the sample by 1/scale about zero and translating by
                    // the transformed offset lands every sampled pixel
                    // exactly under its real counterpart. See the KDoc.
                    val inverseScaleX =
                        if (layerScale.x != 0f) 1f / layerScale.x else 1f
                    val inverseScaleY =
                        if (layerScale.y != 0f) 1f / layerScale.y else 1f
                    recordGlassLayer(glassLayer, recordSize) {
                        translate(paddingPx, paddingPx) {
                            scale(sampleScale, sampleScale, pivot = center) {
                                sources.forEach { source ->
                                    val sourceCoordinates = source.coordinates
                                    if (sourceCoordinates != null) {
                                        val offset = try {
                                            sourceCoordinates.localPositionOf(
                                                glassCoordinates,
                                                Offset.Zero,
                                            )
                                        } catch (_: IllegalArgumentException) {
                                            // Unclipped window bounds: clipping
                                            // must not shift the offset math.
                                            glassCoordinates.boundsInWindow(clipBounds = false).topLeft -
                                                sourceCoordinates.boundsInWindow(clipBounds = false).topLeft
                                        }
                                        // A wrapped source's sampling scale
                                        // wraps its inverse scale and offset
                                        // translation, pivoting on the
                                        // surface center; unwrapped sources
                                        // scale by (1, 1), which is the
                                        // identity and keeps the previous
                                        // transform exactly.
                                        val wrapScale = source.wrapScaleProvider?.invoke()
                                            ?: Offset(1f, 1f)
                                        scale(wrapScale.x, wrapScale.y, pivot = center) {
                                            scale(inverseScaleX, inverseScaleY, pivot = Offset.Zero) {
                                                translate(-offset.x, -offset.y) {
                                                    drawLayer(source.layer)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Resolve every decoration Dp to pixels here, in the outer draw
            // scope: drawInterior also runs inside the export record block
            // below, where any Density access on the record receiver recurses
            // into a StackOverflowError.
            val shadow = style.shadow
            val shadowRadiusPx = shadow?.radius?.toPx() ?: 0f
            val shadowOffsetPx = shadow?.let {
                Offset(it.offset.x.toPx(), it.offset.y.toPx())
            } ?: Offset.Zero
            val innerShadow = style.innerShadow
            val innerShadowRadiusPx = innerShadow?.radius?.toPx() ?: 0f
            val innerShadowOffsetPx = innerShadow?.let {
                Offset(it.offset.x.toPx(), it.offset.y.toPx())
            } ?: Offset.Zero
            val highlight = style.highlight
            val highlightWidthPx = highlight?.width?.toPx() ?: 0f
            val highlightBlurPx = highlight?.blurRadius?.toPx() ?: 0f

            // The clipped interior: effected patch + tint. This - and only
            // this - is what an exporting surface records into its export
            // layer. It must never contain the content (see the KDoc above:
            // a RenderNode cycle kills the RenderThread), the drop shadow
            // (it would be clipped to layer bounds), or the highlight and
            // inner shadow (per-surface decorations drawn over the content).
            val drawInterior: DrawScope.() -> Unit = {
                clipPath(path) {
                    if (ready) {
                        translate(-paddingPx, -paddingPx) {
                            drawLayer(glassLayer)
                        }
                    }
                    // Gradient styles carry their tint only inside the
                    // shader's fade mix: a uniform overlay here would
                    // flatten the progressive fade into a frosted panel.
                    if (style.gradient == null) {
                        if (style.tint != Color.Unspecified) {
                            // Hue blend at full strength, then a solid
                            // overlay at the tint alpha (which replaces
                            // the tint color's own alpha): a colored tint
                            // re-hues the backdrop instead of veiling it.
                            drawPath(path, color = style.tint, blendMode = BlendMode.Hue)
                            drawPath(
                                path,
                                color = style.tint.copy(alpha = style.tintAlpha),
                            )
                        }
                        if (style.surfaceColor != Color.Unspecified) {
                            drawPath(path, color = style.surfaceColor)
                        }
                    }
                }
            }

            if (exportTarget != null) {
                exportTarget.layerSize = IntSize(
                    surfaceSize.width.toInt(),
                    surfaceSize.height.toInt(),
                )
                if (exportTarget.layerSize.width > 0 && exportTarget.layerSize.height > 0) {
                    recordGlassLayer(exportTarget.layer, exportTarget.layerSize) { drawInterior() }
                    exportTarget.notifyRecorded()
                }
                shadow?.let { drawGlassDropShadow(path, it, shadowRadiusPx, shadowOffsetPx) }
                drawLayer(exportTarget.layer)
            } else {
                shadow?.let { drawGlassDropShadow(path, it, shadowRadiusPx, shadowOffsetPx) }
                drawInterior()
            }

            // Content and rim decorations, clipped to the shape, with the
            // highlight and inner shadow above the content.
            clipPath(path) {
                this@drawWithContent.drawContent()
                highlight?.let {
                    drawGlassHighlight(
                        path,
                        surfaceSize,
                        radii,
                        it,
                        highlightWidthPx,
                        highlightBlurPx,
                    )
                }
                innerShadow?.let {
                    drawGlassInnerShadow(
                        insetLayer,
                        surfaceState,
                        path,
                        outlineKey,
                        surfaceSize,
                        it,
                        innerShadowRadiusPx,
                        innerShadowOffsetPx,
                    )
                }
            }
        }

/**
 * Draws [innerShadow] inside [path]: a sharp fill of the shape, punched out by a sharp
 * clear-blend copy translated by the shadow offset, recorded into an
 * offscreen layer - and the blur applied to the WHOLE layer as a
 * [BlurEffect] with [TileMode.Decal], so both edges of the crescent (the
 * shape edge and the punch edge) fade smoothly. Blurring only the fill
 * and punching sharp leaves a hard line at the punch boundary, which
 * reads as a harsh band across the top of the pill. The layer carries the shadow alpha, so alpha-only changes
 * never re-record; the recording key covers radius, offset, color, and
 * the shape outline. The caller clips to the shape.
 */
private fun DrawScope.drawGlassInnerShadow(
    insetLayer: GraphicsLayer,
    surfaceState: GlassSurfaceState,
    path: Path,
    outlineKey: OutlineKey,
    surfaceSize: Size,
    innerShadow: GlassInnerShadow,
    radiusPx: Float,
    offsetPx: Offset,
) {
    val color = innerShadow.color
    if (innerShadow.alpha <= 0f || color.alpha <= 0f) return
    val key = InsetKey(radiusPx, offsetPx, color, outlineKey)
    if (surfaceState.insetKey != key) {
        surfaceState.insetKey = key
        val recordSize = IntSize(
            surfaceSize.width.toInt(),
            surfaceSize.height.toInt(),
        )
        if (recordSize.width > 0 && recordSize.height > 0) {
            recordGlassLayer(insetLayer, recordSize) {
                clipPath(path) {
                    drawPath(path, color = color)
                    translate(offsetPx.x, offsetPx.y) {
                        drawPath(path, color = Color.Transparent, blendMode = BlendMode.Clear)
                    }
                }
            }
        }
    }
    if (surfaceState.lastInsetBlurPx != radiusPx) {
        surfaceState.lastInsetBlurPx = radiusPx
        insetLayer.renderEffect = if (radiusPx > 0f) {
            BlurEffect(radiusPx, radiusPx, TileMode.Decal)
        } else {
            null
        }
    }
    insetLayer.alpha = innerShadow.alpha
    drawLayer(insetLayer)
}
