// GlassRecord: the single recording path for graphics layers,
// with a working density installed during recording.

package com.vallxz.glass.engine

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize

/**
 * Records [block] into [layer] at [size] with a working [Density].
 *
 * A record block's own [DrawScope] has a broken density: reading it recurses
 * forever between the layout node's draw scope and the canvas draw scope and
 * crashes with a `StackOverflowError`. So the
 * recording runs with the caller's density installed on the draw context,
 * restored afterwards. Every layer recording in this library goes through
 * here, so no code running inside a recording - ours or a descendant's -
 * can hit the broken density.
 */
internal fun DrawScope.recordGlassLayer(
    layer: GraphicsLayer,
    size: IntSize,
    block: DrawScope.() -> Unit,
) {
    val density: Density = this
    layer.record(size) {
        val previousDensity = drawContext.density
        drawContext.density = density
        try {
            block()
        } finally {
            drawContext.density = previousDensity
        }
    }
}
