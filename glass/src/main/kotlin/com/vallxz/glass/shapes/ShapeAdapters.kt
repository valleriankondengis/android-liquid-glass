// ShapeAdapters: converts Compose shapes into outlines and corner
// radii for the glass engine.

package com.vallxz.glass.shapes

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Resolves a [Shape] into physical [CornerRadii] in pixels for the refraction
 * shader.
 *
 * Only rounded-rectangle shapes are supported: the lens shader's signed
 * distance field assumes circular corner arcs. [CornerBasedShape] (for
 * example [androidx.compose.foundation.shape.RoundedCornerShape]) resolves
 * its four logical corners, [CircleShape] uses half the smallest dimension
 * for every corner, and [RectangleShape] uses zero radii. Anything else is a
 * programming error and fails loudly instead of refracting the wrong shape.
 */
internal fun Shape.resolveCornerRadii(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
): CornerRadii {
    val maxRadiusPx = minOf(size.width, size.height) / 2f
    return when (this) {
        is CornerBasedShape -> resolveCornerRadii(
            topStartPx = topStart.toPx(size, density),
            topEndPx = topEnd.toPx(size, density),
            bottomEndPx = bottomEnd.toPx(size, density),
            bottomStartPx = bottomStart.toPx(size, density),
            isLeftToRight = layoutDirection == LayoutDirection.Ltr,
            maxRadiusPx = maxRadiusPx,
        )

        CircleShape -> CornerRadii(maxRadiusPx, maxRadiusPx, maxRadiusPx, maxRadiusPx)
        RectangleShape -> CornerRadii(0f, 0f, 0f, 0f)
        else -> throw IllegalArgumentException(
            "Glass lens supports only CornerBasedShape, CircleShape, or RectangleShape, got $this",
        )
    }
}

/**
 * Converts a [Shape] outline into a [Path] for clipping and tint fills.
 * Generic outlines keep their own path.
 */
internal fun Shape.toPath(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
): Path {
    return when (val outline = createOutline(size, layoutDirection, density)) {
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
    }
}
