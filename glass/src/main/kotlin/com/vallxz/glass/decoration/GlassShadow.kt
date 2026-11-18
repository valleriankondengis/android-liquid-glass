// GlassShadow: the drop shadow and inner shadow recipes - radius,
// offset, color, and alpha.

package com.vallxz.glass.decoration

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Drop shadow drawn beneath a glass shape.
 *
 * Defaults are the canonical values: 24 dp radius, offset down by a sixth
 * of the radius, black at 10%.
 */
@Immutable
data class GlassShadow(
    val radius: Dp = 24.dp,
    val offset: DpOffset = DpOffset(0.dp, 4.dp),
    val color: Color = Color.Black.copy(alpha = 0.10f),
    val alpha: Float = 1f,
)

/**
 * Inner shadow drawn inside a glass shape, for pressed states and inset wells.
 *
 * Defaults are the canonical values: 24 dp radius, offset down by the full
 * radius, black at 15%.
 */
@Immutable
data class GlassInnerShadow(
    val radius: Dp = 24.dp,
    val offset: DpOffset = DpOffset(0.dp, 24.dp),
    val color: Color = Color.Black.copy(alpha = 0.15f),
    val alpha: Float = 1f,
)
