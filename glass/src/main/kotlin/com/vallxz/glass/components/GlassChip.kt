// GlassChip: a small selectable glass chip with an optional icon.

package com.vallxz.glass.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.engine.GlassBackdrop
import com.vallxz.glass.interaction.glassPressScale
import com.vallxz.glass.interaction.rememberGlassHaptics
import com.vallxz.glass.interaction.rememberGlassPressScale

/**
 * A small selectable glass pill. The selected state brightens the tint so
 * selection reads over any backdrop; unselected chips stay quiet.
 *
 * The backdrop is the explicit argument, falling back to the nearest
 * `LocalGlassBackdrop` only when null.
 */
@Composable
fun GlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    icon: Painter? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    style: GlassStyle = GlassStyle.Default,
    contentColor: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberGlassPressScale(interactionSource)
    val haptics = rememberGlassHaptics(haptic)
    val alpha = if (enabled) 1f else 0.5f
    val darkTheme = isSystemInDarkTheme()
    val resolvedContentColor = if (contentColor == Color.Unspecified) {
        glassLabelColor(darkTheme)
    } else {
        contentColor
    }

    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .glassPressScale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    if (haptic) haptics.confirm()
                    onClick()
                },
            ),
        style = if (selected) {
            style.copy(tint = Color.White, tintAlpha = 0.35f)
        } else {
            style
        },
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Image(
                    painter = icon,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(resolvedContentColor.copy(alpha = alpha)),
                    modifier = Modifier.size(16.dp),
                )
            }
            BasicText(
                text = text,
                style = TextStyle(
                    color = resolvedContentColor.copy(alpha = if (selected) alpha else alpha * 0.75f),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        }
    }
}
