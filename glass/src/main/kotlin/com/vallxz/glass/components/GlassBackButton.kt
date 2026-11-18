// GlassBackButton: a circular glass back button with the button press
// behavior. The content is caller-provided: an icon, a text label,
// or both.

package com.vallxz.glass.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.engine.GlassBackdrop
import com.vallxz.glass.interaction.rememberGlassHaptics

/**
 * A circular glass button for back navigation, with the same press
 * behavior as [GlassButton]: press growth, drag stretch, and a ripple
 * following the touch point. A tap haptic confirms the click.
 *
 * Nothing about the content is fixed: [content] is a caller-provided
 * slot, so the button can hold an icon, a text label, or any
 * combination. The slot runs inside the surface's [BoxScope], so
 * content aligns with the usual box alignment modifiers. Disabled
 * state fades the content only; the glass itself stays unchanged.
 *
 * The default lens recipe is 8 dp blur, a 12/24 dp lens, vibrancy, no
 * depth, no dispersion; [style] overrides it entirely. The backdrop is
 * the explicit argument, falling back to the nearest
 * `LocalGlassBackdrop` only when null: the button never samples an
 * enclosing exporting surface's layer, so it always lenses the backdrop
 * it was given instead of a pre-blurred copy of it.
 */
@Composable
fun GlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    style: GlassStyle = buttonDefaultStyle,
    shape: Shape = RoundedCornerShape(50),
    size: Dp = 48.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val press = rememberGlassButtonPress()
    val pressTransform = press.transformModifier()
    val density = LocalDensity.current
    val haptics = rememberGlassHaptics(haptic)
    val alpha = if (enabled) 1f else 0.5f

    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .size(size)
            .onSizeChanged { press.sizePx = it }
            .then(pressTransform)
            .then(press.gestureModifier())
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.confirm()
                    onClick()
                },
            ),
        style = style,
        shape = shape,
        layerScale = { press.scale(density) },
    ) {
        rippleOverlay(press)
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { this.alpha = alpha },
        ) {
            content()
        }
    }
}
