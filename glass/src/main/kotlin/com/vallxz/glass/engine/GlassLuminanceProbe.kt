// GlassLuminanceProbe: samples the average luminance of a backdrop
// region for adaptive glass.

package com.vallxz.glass.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.vallxz.glass.effects.GlassAdaptive

/**
 * Best-effort backdrop luminance probe: draws a [GlassBackdrop]'s layer into
 * a tiny thumbnail and averages the luminance of its opaque pixels.
 *
 * This is deliberately throttled and fail-soft. Reading pixels back from the
 * GPU can block, and drawing a recorded layer into a software bitmap may
 * come back empty on some devices, so [sample] returns null on any failure
 * and callers keep their last good value. Never call it on the draw path:
 * sample from a frame loop (see [rememberGlassLuminance]) at most a few
 * times per second.
 */
internal class GlassLuminanceProbe {

    private val thumbnailSize = 16
    private val thumbnail = ImageBitmap(thumbnailSize, thumbnailSize)
    private val canvas = Canvas(thumbnail)
    private val pixels = IntArray(thumbnailSize * thumbnailSize)
    private val drawScope = CanvasDrawScope()

    /**
     * Samples the average relative luminance of [backdrop]'s current
     * recording, in [0, 1], or null when the backdrop is not ready, the
     * thumbnail comes back empty, or anything fails.
     */
    fun sample(backdrop: GlassBackdrop): Float? {
        if (!backdrop.isReady) return null
        val layerSize = backdrop.layerSize
        if (layerSize.width <= 0 || layerSize.height <= 0) return null
        return try {
            drawScope.draw(
                density = Density(1f),
                layoutDirection = LayoutDirection.Ltr,
                canvas = canvas,
                size = Size(thumbnailSize.toFloat(), thumbnailSize.toFloat()),
            ) {
                // Pivot at the origin: the layer's top-left must land on the
                // thumbnail's top-left (the default pivot is the center).
                scale(
                    thumbnailSize.toFloat() / layerSize.width,
                    thumbnailSize.toFloat() / layerSize.height,
                    pivot = Offset.Zero,
                ) {
                    drawLayer(backdrop.layer)
                }
            }
            thumbnail.readPixels(pixels)
            var sum = 0f
            var count = 0
            for (argb in pixels) {
                val alpha = (argb ushr 24) and 0xFF
                if (alpha < 16) continue
                sum += GlassAdaptive.pixelLuminance(
                    red = (argb shr 16) and 0xFF,
                    green = (argb shr 8) and 0xFF,
                    blue = argb and 0xFF,
                )
                count++
            }
            if (count == 0) null else (sum / count).coerceIn(0f, 1f)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Remembers the live luminance of [backdrop]'s recording while [enabled],
 * sampled about every [sampleEveryFrames] frames. Starts at mid-gray (0.5)
 * and keeps its last good value whenever a probe fails, so adaptive styles
 * degrade to neutral instead of flickering.
 */
@Composable
fun rememberGlassLuminance(
    backdrop: GlassBackdrop,
    enabled: Boolean,
    sampleEveryFrames: Int = 30,
): Float {
    var luminance by remember { mutableStateOf(0.5f) }
    LaunchedEffect(backdrop, enabled) {
        if (!enabled) return@LaunchedEffect
        val probe = GlassLuminanceProbe()
        var frames = 0
        while (true) {
            withFrameNanos { }
            if (++frames >= sampleEveryFrames.coerceAtLeast(1)) {
                frames = 0
                probe.sample(backdrop)?.let { luminance = it }
            }
        }
    }
    return luminance
}
