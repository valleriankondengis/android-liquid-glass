// GlassButton: a pressable glass button with press growth, stretch,
// and a shader ripple that follows the touch point.

package com.vallxz.glass.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import android.graphics.RuntimeShader
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.engine.GlassBackdrop
import com.vallxz.glass.engine.GlassShaders
import com.vallxz.glass.interaction.detectGlassDrag
import com.vallxz.glass.interaction.rememberGlassHaptics
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Press feedback shared by the glass buttons: a press progress spring and
 * an animated touch position. The drag offset is derived - the animated
 * position minus the down point - so it springs back to zero on release
 * instead of going stale. The gesture detector is the shared immediate,
 * bounds-blind drag inspector, so press feedback starts on pointer down
 * and persists while dragging outside the button; the clickable owns only
 * the click itself and carries no interaction source.
 */
internal class GlassButtonPress(private val scope: CoroutineScope) {
    val progress = Animatable(0f, Float.VectorConverter, 0.001f)
    val position = Animatable(Offset.Zero, Offset.VectorConverter)
    var sizePx by mutableStateOf(IntSize.Zero)
    private var startPosition = Offset.Zero
    private var trackedPosition = Offset.Zero

    /** Touch offset relative to the press start, decaying to zero on release. */
    val offset: Offset
        get() = position.value - startPosition

    private val pressSpring = spring<Float>(
        dampingRatio = 0.5f,
        stiffness = 300f,
        visibilityThreshold = 0.001f,
    )
    private val positionSpring = spring<Offset>(dampingRatio = 0.5f, stiffness = 300f)

    /**
     * Tracks press, drag, and release without consuming anything, so it
     * coexists with the clickable that owns the gesture. Drag deltas
     * accumulate into the tracked position, which the animated position
     * snaps to; on release or cancel the position springs back to the
     * down point, decaying the drag offset to zero.
     */
    fun gestureModifier(): Modifier = Modifier.pointerInput(Unit) {
        detectGlassDrag(
            onDragStart = { down ->
                startPosition = down
                trackedPosition = down
                scope.launch {
                    launch { progress.animateTo(1f, pressSpring) }
                    launch { position.snapTo(down) }
                }
            },
            onDrag = { delta ->
                trackedPosition += delta
                val current = trackedPosition
                scope.launch { position.snapTo(current) }
            },
            onDragEnd = { release() },
            onDragCancel = { release() },
        )
    }

    private fun release() {
        scope.launch {
            launch { progress.animateTo(0f, pressSpring) }
            launch { position.animateTo(startPosition, positionSpring) }
        }
    }

    /**
     * The press scale (uniform growth plus drag stretch), from the current
     * press progress, size, and drag offset. A plain function, evaluated
     * wherever it is called: the graphicsLayer lambda below and the
     * layer-scale provider in the button composables both call it in the
     * draw phase, so the inverse sampling transform always matches the
     * visible transform exactly.
     */
    fun scale(density: Density): Offset {
        val press = progress.value
        val size = sizePx
        if (size.width == 0 || size.height == 0) return Offset(1f, 1f)
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val liftPx = with(density) { 4.dp.toPx() }
        val baseScale = lerp(1f, 1f + liftPx / height, press)
        val drag = offset
        val maxDimension = width.coerceAtLeast(height)
        val angle = atan2(drag.y, drag.x)
        val maxStretch = liftPx / height
        val stretchX = maxStretch * abs(cos(angle) * drag.x / maxDimension) *
            (width / height).coerceAtMost(1f)
        val stretchY = maxStretch * abs(sin(angle) * drag.y / maxDimension) *
            (height / width).coerceAtMost(1f)
        return Offset(baseScale + stretchX, baseScale + stretchY)
    }

    /**
     * The press transform, applied outside the glass draw so the whole
     * composited surface moves as one unit: uniform scale growing by
     * 4 dp / height with the press, translation following the drag offset
     * through a tanh curve, and extra drag stretch distributed by the
     * drag angle and clamped by the aspect ratio. All animation state is
     * read inside the graphicsLayer lambda, in the draw phase, so press
     * ticks never recompose the button.
     */
    @Composable
    fun transformModifier(): Modifier {
        val density = LocalDensity.current
        return Modifier.graphicsLayer {
            val size = sizePx
            if (size.width != 0 && size.height != 0) {
                val buttonScale = scale(density)
                scaleX = buttonScale.x
                scaleY = buttonScale.y
                val minDimension = size.width.coerceAtMost(size.height).toFloat()
                val drag = offset
                translationX = minDimension * tanh(0.05f * drag.x / minDimension)
                translationY = minDimension * tanh(0.05f * drag.y / minDimension)
            }
        }
    }

}

/**
 * The press ripple, drawn under the button content: a flat white wash at
 * 8% x progress plus a radial glow at 15% x progress, radius 1.5x the
 * smallest dimension, fading with a smooth S-curve from half the radius
 * out, both additive, centered on the touch point clamped into bounds.
 * Call it inside the surface content, before the content, where the
 * [BoxScope] is in range. The press state is read inside the draw block,
 * so press ticks redraw the ripple without recomposing the button;
 * nothing is drawn until the press starts.
 */
@Composable
internal fun BoxScope.rippleOverlay(press: GlassButtonPress) {
    val rippleShader = remember { RuntimeShader(GlassShaders.PRESS_RIPPLE) }
    val rippleBrush = remember { ShaderBrush(rippleShader) }
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawBehind {
                val progress = press.progress.value
                if (progress > 0f) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.08f * progress),
                        blendMode = BlendMode.Plus,
                    )
                    val touchPoint = Offset(
                        press.position.value.x.coerceIn(0f, size.width),
                        press.position.value.y.coerceIn(0f, size.height),
                    )
                    val radius = 1.5f * size.width.coerceAtMost(size.height)
                    rippleShader.setColorUniform(
                        "color",
                        Color.White.copy(alpha = 0.15f * progress).toArgb(),
                    )
                    rippleShader.setFloatUniform("radius", radius)
                    rippleShader.setFloatUniform("position", touchPoint.x, touchPoint.y)
                    drawRect(
                        brush = rippleBrush,
                        blendMode = BlendMode.Plus,
                    )
                }
            },
    )
}

@Composable
internal fun rememberGlassButtonPress(): GlassButtonPress {
    val scope = rememberCoroutineScope()
    return remember { GlassButtonPress(scope) }
}

/** The default button lens: 8 dp blur, a 12/24 dp lens, vibrancy, no depth, no dispersion. */
internal val buttonDefaultStyle: GlassStyle = GlassStyle.Default.copy(
    blurRadius = 8.dp,
    refractionHeight = 12.dp,
    refractionAmount = 24.dp,
)

/**
 * A glass pill button: 48 dp tall, capsule, label with an optional leading
 * icon. Pressing grows the button and shows a ripple following the touch
 * point; dragging stretches it along the drag angle. A tap haptic confirms
 * the click.
 *
 * The default lens recipe is 8 dp blur, a 12/24 dp lens, vibrancy, no
 * depth, no dispersion; [style] overrides it entirely, so a caller can
 * pass a settings-derived style to retune the button. The backdrop is
 * the explicit argument, falling back to the nearest
 * `LocalGlassBackdrop` only when null: the button never samples an
 * enclosing exporting surface's layer, so it always lenses the backdrop
 * it was given instead of a pre-blurred copy of it.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    icon: Painter? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    style: GlassStyle = buttonDefaultStyle,
    contentColor: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val press = rememberGlassButtonPress()
    val pressTransform = press.transformModifier()
    val density = LocalDensity.current
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
            .height(48.dp)
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
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Image(
                    painter = icon,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(resolvedContentColor.copy(alpha = alpha)),
                    modifier = Modifier.size(20.dp),
                )
            }
            BasicText(
                text = text,
                style = TextStyle(
                    color = resolvedContentColor.copy(alpha = alpha),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

/**
 * A circular glass icon button. Same press behavior as [GlassButton].
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    icon: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    style: GlassStyle = buttonDefaultStyle,
    contentColor: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(50),
    size: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val press = rememberGlassButtonPress()
    val pressTransform = press.transformModifier()
    val density = LocalDensity.current
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
        Image(
            painter = icon,
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(resolvedContentColor.copy(alpha = alpha)),
            modifier = Modifier
                .align(Alignment.Center)
                .size(iconSize),
        )
    }
}
