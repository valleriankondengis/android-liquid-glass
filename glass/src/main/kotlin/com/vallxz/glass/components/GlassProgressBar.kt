// GlassProgressBar: a glass progress bar with determinate and
// indeterminate modes.

package com.vallxz.glass.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.engine.GlassBackdrop
import kotlin.math.roundToInt

/**
 * A glass progress bar: an 8 dp capsule track with 4 dp blur and no
 * refraction (refraction adds little at this size), a gray track tint
 * (`#787880` at 20% light / 36% dark), and an accent-blue fill at 85%
 * alpha with fully rounded ends. The fill is drawn in the surface pass,
 * not as a nested glass layer.
 *
 * In indeterminate mode a 35%-wide fill segment loops across the track
 * over 1400 ms, linear, restarting. Determinate progress reads the value
 * directly, with no animation.
 *
 * The backdrop resolves like [GlassSurface] (the explicit argument,
 * else the nearest `LocalGlassBackdrop`).
 */
@Composable
fun GlassProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    indeterminate: Boolean = false,
) {
    val darkTheme = isSystemInDarkTheme()
    val density = LocalDensity.current
    val accentColor = if (darkTheme) Color(0xFF0091FF) else Color(0xFF0088FF)

    val infiniteTransition = rememberInfiniteTransition(label = "glassProgressIndeterminate")
    val sweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glassProgressSweep",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat()
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            style = GlassStyle(
                blurRadius = 4.dp,
                refractionHeight = 0.dp,
                refractionAmount = 0.dp,
                saturation = 1.5f,
                depth = false,
                surfaceColor = Color(0xFF787880).copy(
                    alpha = if (darkTheme) 0.36f else 0.20f,
                ),
                highlight = null,
                shadow = null,
            ),
            shape = RoundedCornerShape(50),
        ) {
            if (indeterminate) {
                val segmentWidthPx = trackWidthPx * 0.35f
                val x = (sweep * 1.35f - 0.35f) * trackWidthPx
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x.roundToInt(), 0) }
                        .size(
                            width = with(density) { segmentWidthPx.toDp() },
                            height = 8.dp,
                        )
                        .background(
                            accentColor.copy(alpha = 0.85f),
                            RoundedCornerShape(50),
                        ),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(
                            accentColor.copy(alpha = 0.85f),
                            RoundedCornerShape(50),
                        ),
                )
            }
        }
    }
}
