// GlassGradientPanel: progressive glass whose refraction fades from
// full at the bottom to transparent at the top.

package com.vallxz.glass.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.effects.GlassGradient
import com.vallxz.glass.engine.GlassBackdrop

/**
 * A gradient glass panel: refraction follows a vertical profile (flat at
 * the top, fully refractive at the bottom by default) and the output fades
 * toward the tint below the blur-fade band. The recipe is the panel
 * default: 20 dp blur, a 28/36 dp lens, weights 0 / 0 / 1, fade from 0 to
 * 0.8 of the height, and a white 20% (light theme) or black 15% (dark
 * theme) tint. The panel is a sharp rectangle by default and uses the
 * plain glass draw: no saturation step, no highlight, no shadow.
 *
 * The backdrop resolves like [GlassSurface] (the explicit argument,
 * else the nearest `LocalGlassBackdrop`).
 */
@Composable
fun GlassGradientPanel(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    shape: Shape = RectangleShape,
    gradient: GlassGradient = GlassGradient(),
    content: @Composable BoxScope.() -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        style = GlassStyle(
            blurRadius = 20.dp,
            refractionHeight = 28.dp,
            refractionAmount = 36.dp,
            saturation = 1f,
            tint = if (darkTheme) Color.Black else Color.White,
            tintAlpha = if (darkTheme) 0.15f else 0.20f,
            highlight = null,
            shadow = null,
            gradient = gradient,
        ),
        shape = shape,
        content = content,
    )
}
