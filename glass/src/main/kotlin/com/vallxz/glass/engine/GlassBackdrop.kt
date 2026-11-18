// GlassBackdrop: a recorded layer of background content that glass
// surfaces sample, plus merged and wrapped variants.

package com.vallxz.glass.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

/**
 * The nearest backdrop a glass surface samples when it is not given one
 * explicitly. A [GlassSurface] with `exportBackdrop = true` provides its own
 * glass background here, which is how nested glass works: an inner surface
 * samples the outer surface's real glass background instead of the root backdrop,
 * so glass-on-glass looks like glass instead of a hole.
 *
 * Null when no backdrop is in scope; surfaces without a backdrop degrade to
 * a plain tinted surface instead of crashing.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * The recorded pixels a glass surface samples from.
 *
 * A backdrop owns one [GraphicsLayer] holding the content drawn behind the
 * glass. Glass surfaces are siblings drawn after the backdrop, never children
 * of it: a surface must never sample a recording that contains itself.
 *
 * Create one with [rememberGlassBackdrop], attach it to the background content
 * with [glassBackdrop], and pass it to every glass surface in front of it.
 */
class GlassBackdrop internal constructor(
    internal val layer: GraphicsLayer,
) {
    /**
     * The backdrop this node wraps, when this node is a wrapped source
     * (see [rememberGlassWrappedBackdrop]): the wrapped node samples the
     * inner backdrop's layer, scaled by [wrapScaleProvider] about the
     * sampling surface's center. Null for ordinary and merged backdrops.
     */
    internal var wrappedSource: GlassBackdrop? = null

    /**
     * The per-source sampling scale for a wrapped source, evaluated in
     * the draw phase so press/drag animation state read inside it
     * invalidates the sampling draw only. Null for unwrapped sources,
     * which sample at their natural scale.
     */
    internal var wrapScaleProvider: (() -> Offset)? = null

    private var layerSizeBacking: IntSize = IntSize.Zero

    /** Pixel size of the last recording. Zero until the first draw. */
    internal var layerSize: IntSize
        get() = wrappedSource?.layerSize ?: layerSizeBacking
        set(value) {
            layerSizeBacking = value
        }

    private var coordinatesBacking: LayoutCoordinates? by mutableStateOf<LayoutCoordinates?>(
        null,
        neverEqualPolicy(),
    )

    /**
     * Coordinates of the recorded content, for offset math in glass
     * surfaces. Snapshot state with a never-equal policy: the coordinates
     * object is the same instance across placements, so the default policy
     * would swallow every move. With never-equal, every placement - a
     * scroll, a drag, a layout shift - invalidates the glass surfaces
     * reading this, and they re-record with the fresh offset instead of
     * showing a stuck patch. A wrapped
     * source delegates to its inner backdrop's coordinates, so reads
     * subscribe to the inner backdrop's placement state.
     */
    internal var coordinates: LayoutCoordinates?
        get() = wrappedSource?.coordinates ?: coordinatesBacking
        set(value) {
            coordinatesBacking = value
        }

    /**
     * Bumped after every re-recording of [layer]. At API 33 and above,
     * glass surfaces do NOT read this - the renderer picks up a
     * re-recorded layer's new content through the render-node reference
     * graph, so a fixed surface shows fresh backdrop pixels without
     * re-recording. Reading the version in every glass draw would force
     * every surface to re-record its effected layer on every backdrop
     * recording, which janks scrolling. This library targets API 33+,
     * so the counter is never read.
     */
    internal var contentVersion by mutableIntStateOf(0)

    internal val isReady: Boolean
        get() = layerSize.width > 0 && layerSize.height > 0 && coordinates != null

    /**
     * The backdrops a sampling surface actually draws, in stacking order.
     * Normally just this backdrop; a merged backdrop (see
     * [rememberGlassMergedBackdrop]) samples several layers at once - for
     * example a slider thumb sampling both the outer backdrop and the
     * track's own recorded layer. The surface computes a separate offset
     * per source. Merged sources flatten recursively, so a merged
     * backdrop sampling another merged backdrop still yields every real
     * recorded layer, each with its own coordinates. A wrapped source
     * (see [rememberGlassWrappedBackdrop]) stays a single leaf: it
     * carries its own sampling scale, which flattening must not strip.
     */
    internal var sampledSources: List<GlassBackdrop>? = null

    internal val sampled: List<GlassBackdrop>
        get() = sampledSources?.flatMap { it.sampled } ?: listOf(this)

    internal fun notifyRecorded() {
        contentVersion++
    }
}

/**
 * Creates a [GlassBackdrop] whose layer is released automatically when this
 * composition leaves the composition.
 */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/**
 * Creates a backdrop that samples [primary] and [secondary] stacked together:
 * a surface sampling the merged backdrop sees both layers at once, each
 * aligned by its own coordinates. Used by components whose moving part
 * (a slider thumb, a tab droplet) must see both the outer backdrop and the
 * component's own recorded layer through the lens.
 *
 * Both sources must be recorded by their own [glassBackdrop] modifiers; the
 * merged backdrop itself records nothing.
 */
@Composable
fun rememberGlassMergedBackdrop(
    primary: GlassBackdrop,
    secondary: GlassBackdrop,
): GlassBackdrop {
    return remember(primary, secondary) {
        GlassBackdrop(primary.layer).apply {
            sampledSources = listOf(primary, secondary)
        }
    }
}

/**
 * Creates a backdrop that samples [inner] scaled by [scale] about the
 * sampling surface's center: the scale wraps the source's own sampling
 * transform (inverse layer scale plus offset), so only this source is
 * scaled while the other merged sources keep their natural scale. Used
 * where one layer must shrink or magnify through the lens independently
 * of the rest - a slider track collapsing vertically as the thumb is
 * pressed, a segmented track magnifying at rest and shrinking on press,
 * a tab row shrinking inside the bottom-bar droplet.
 *
 * [scale] is evaluated in the draw phase on every sampling draw, so
 * animation state read inside it (press progress, velocity) invalidates
 * the draw only. The wrapped backdrop records nothing itself; [inner]
 * must be recorded by its own [glassBackdrop] modifier.
 */
@Composable
fun rememberGlassWrappedBackdrop(
    inner: GlassBackdrop,
    scale: () -> Offset,
): GlassBackdrop {
    val wrapped = remember(inner) {
        GlassBackdrop(inner.layer).apply { wrappedSource = inner }
    }
    // Updated every composition: the provider is only ever read in the
    // draw phase, so the latest lambda is always what sampling sees.
    wrapped.wrapScaleProvider = scale
    return wrapped
}

/**
 * Records everything this modifier draws into [backdrop]'s layer, on every
 * draw of this node, then draws it normally so the background stays
 * visible. There is deliberately no frame-clock loop here: a loop that
 * re-records every frame forces every glass surface to re-render its
 * effected layer every frame too, which janks the whole screen. Freshness comes from draw-driven invalidation
 * instead - this node re-records when its content changes, moving glass
 * surfaces re-record when their coordinates change, and fixed surfaces
 * show the fresh pixels through the render-node reference graph without
 * re-recording (at API 33+ the renderer picks up layer updates through
 * the layer reference) - so scrolling and animation stay fresh with no
 * work while the screen is idle.
 *
 * Only content drawn *inside* this modifier is recorded: children of this
 * node, and draw modifiers placed AFTER this one in the chain (inner
 * modifiers). A `background` or `drawBehind` placed BEFORE this modifier
 * draws outside the recording, so the recorded layer comes out empty and
 * every surface sampling it shows a transparent patch. Put backgrounds inside the node - as child
 * content or as an inner modifier.
 *
 * Apply this to the background content only. Glass surfaces sampling this
 * backdrop must be siblings drawn after it in the same parent.
 */
fun Modifier.glassBackdrop(backdrop: GlassBackdrop): Modifier =
    this
        .onGloballyPositioned { backdrop.coordinates = it }
        .drawWithContent {
            val recordSize = IntSize(size.width.toInt(), size.height.toInt())
            recordGlassLayer(backdrop.layer, recordSize) { this@drawWithContent.drawContent() }
            backdrop.layerSize = recordSize
            backdrop.notifyRecorded()
            drawContent()
        }
