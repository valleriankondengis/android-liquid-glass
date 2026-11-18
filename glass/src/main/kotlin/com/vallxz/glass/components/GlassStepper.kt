// GlassStepper: a two-half stepper with tap-to-step, hold-to-repeat,
// and a press glow that follows the touch point.

package com.vallxz.glass.components

import android.graphics.RuntimeShader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.engine.GlassBackdrop
import com.vallxz.glass.engine.GlassShaders
import com.vallxz.glass.engine.LocalGlassBackdrop
import com.vallxz.glass.interaction.detectGlassDrag
import com.vallxz.glass.interaction.rememberGlassHaptics
import kotlinx.coroutines.delay

/**
 * An iOS-style −/+ stepper: a 104 x 40 dp glass capsule split into two
 * equal halves by a 1 dp vertical divider (45% of the height, glyph color
 * at 18%). The glyphs are drawn as lines on a 14 dp canvas with a 2 dp
 * rounded stroke: minus is a horizontal line, plus adds a vertical line.
 * A half at its range bound renders at 30% alpha.
 *
 * A tap steps once on release; holding repeats - the first repeat
 * after 400 ms, then every 80 ms until release, with no extra step on
 * release once a repeat has fired - with a haptic tick per step. The
 * press binds to the half it started on: sliding across the midpoint
 * never switches halves. Pressing lights a touch-centered glow (flat
 * wash plus a radial glow following the finger) fading in and out on a
 * spring. The halves swap in RTL layouts. The glass recipe is 8 dp blur,
 * a 10/16 dp lens, and a container fill (`#FAFAFA` 40% light / `#121212`
 * 40% dark).
 *
 * The backdrop resolves with the nearest `LocalGlassBackdrop` (an
 * enclosing exporting surface) winning over the explicit argument, so
 * the stepper lenses the surface it sits on: its underlay is the parent
 * glass layer.
 */
@Composable
fun GlassStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    valueRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
    step: Int = 1,
    enabled: Boolean = true,
    haptic: Boolean = true,
    contentColor: Color = Color.Unspecified,
) {
    val darkTheme = isSystemInDarkTheme()
    val glyphColor = if (contentColor == Color.Unspecified) {
        glassLabelColor(darkTheme)
    } else {
        contentColor
    }
    val layoutDirection = LocalLayoutDirection.current
    val haptics = rememberGlassHaptics(haptic)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    // The step logic runs from a long-lived pointer-input block and a
    // hold-repeat loop, and both capture the composition they started in:
    // the live value, range, and step must be read through updated state,
    // or every step computes from the stale first value - plus stuck on
    // the same number, minus jumping straight to the bottom.
    val currentValue by rememberUpdatedState(value)
    val currentRange by rememberUpdatedState(valueRange)
    val currentStep by rememberUpdatedState(step)

    // Pressed half, in visual order: 0 = left half, 1 = right half. The
    // minus side is the left half in LTR and the right half in RTL.
    // repeatFired tracks whether the hold loop has stepped yet: a tap
    // steps on release only when no repeat fired, so a hold just over the
    // repeat delay counts exactly one step, not two.
    var pressedHalf by remember { mutableStateOf<Int?>(null) }
    var repeatFired by remember { mutableStateOf(false) }
    var touchPoint by remember { mutableStateOf(Offset.Zero) }
    // Glow progress: fades in and out on a spring instead of snapping.
    // Read inside the draw block below, so the fade never recomposes.
    val glowProgress = animateFloatAsState(
        targetValue = if (pressedHalf != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "glassStepperGlow",
    )
    // Press-ripple shader for the radial glow (smoothstep falloff).
    val rippleShader = remember { RuntimeShader(GlassShaders.PRESS_RIPPLE) }
    val rippleBrush = remember { ShaderBrush(rippleShader) }

    fun isMinusHalf(half: Int): Boolean =
        if (layoutDirection == LayoutDirection.Ltr) half == 0 else half == 1

    fun stepOnce(half: Int) {
        val value = currentValue
        val next = if (isMinusHalf(half)) {
            (value - currentStep).coerceAtLeast(currentRange.first)
        } else {
            (value + currentStep).coerceAtMost(currentRange.last)
        }
        if (next != value) {
            if (haptic) haptics.tick()
            currentOnValueChange(next)
        }
    }

    // Hold-to-repeat: first repeat after 400 ms, then every 80 ms. The
    // flag is set before stepping, so a release racing the first repeat
    // cannot double-step.
    LaunchedEffect(pressedHalf) {
        val half = pressedHalf ?: return@LaunchedEffect
        delay(400)
        while (true) {
            repeatFired = true
            stepOnce(half)
            delay(80)
        }
    }

    val resolvedBackdrop = LocalGlassBackdrop.current ?: backdrop
    val minusDisabled = value <= valueRange.first
    val plusDisabled = value >= valueRange.last

    BoxWithConstraints(
        modifier = modifier
            .size(width = 104.dp, height = 40.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        contentAlignment = Alignment.Center,
    ) {
        val controlWidthPx = constraints.maxWidth.toFloat()
        GlassSurface(
            backdrop = resolvedBackdrop,
            modifier = Modifier.fillMaxSize(),
            style = GlassStyle(
                blurRadius = 8.dp,
                refractionHeight = 10.dp,
                refractionAmount = 16.dp,
                saturation = 1.5f,
                depth = false,
                surfaceColor = if (darkTheme) {
                    Color(0xFF121212).copy(alpha = 0.4f)
                } else {
                    Color(0xFFFAFAFA).copy(alpha = 0.4f)
                },
            ),
            shape = RoundedCornerShape(50),
        ) {
            // Touch-centered press glow, drawn over the glass. The
            // progress is read inside the draw block, so the spring fade
            // never recomposes; at zero progress nothing is drawn.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val progress = glowProgress.value
                if (progress > 0f) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.08f * progress),
                        blendMode = BlendMode.Plus,
                    )
                    val radius = 1.5f * size.minDimension
                    rippleShader.setColorUniform(
                        "color",
                        Color.White.copy(alpha = 0.15f * progress).toArgb(),
                    )
                    rippleShader.setFloatUniform("radius", radius)
                    rippleShader.setFloatUniform(
                        "position",
                        touchPoint.x,
                        touchPoint.y,
                    )
                    drawRect(
                        brush = rippleBrush,
                        blendMode = BlendMode.Plus,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepperHalf(
                    isMinus = isMinusHalf(0),
                    disabled = if (isMinusHalf(0)) minusDisabled else plusDisabled,
                    glyphColor = glyphColor,
                    description = if (isMinusHalf(0)) "Decrease" else "Increase",
                    weight = 1f,
                    onActivate = { stepOnce(0) },
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.45f)
                        .background(glyphColor.copy(alpha = 0.18f)),
                )
                StepperHalf(
                    isMinus = isMinusHalf(1),
                    disabled = if (isMinusHalf(1)) minusDisabled else plusDisabled,
                    glyphColor = glyphColor,
                    description = if (isMinusHalf(1)) "Decrease" else "Increase",
                    weight = 1f,
                    onActivate = { stepOnce(1) },
                )
            }
        }

        if (enabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(layoutDirection, controlWidthPx) {
                        detectGlassDrag(
                            onDragStart = { down ->
                                touchPoint = down
                                // The press only binds the half and
                                // starts the hold-repeat delay; the
                                // step happens on release.
                                repeatFired = false
                                pressedHalf =
                                    if (down.x < controlWidthPx / 2f) 0 else 1
                            },
                            onDrag = { delta ->
                                if (delta != Offset.Zero) {
                                    // The glow center follows the
                                    // finger, clamped into bounds. The
                                    // pressed half never changes
                                    // mid-gesture.
                                    touchPoint = Offset(
                                        (touchPoint.x + delta.x).coerceIn(
                                            0f, controlWidthPx,
                                        ),
                                        (touchPoint.y + delta.y).coerceIn(
                                            0f, constraints.maxHeight.toFloat(),
                                        ),
                                    )
                                }
                            },
                            onDragEnd = {
                                val half = pressedHalf
                                pressedHalf = null
                                // A tap steps once on release, unless
                                // the hold loop already stepped.
                                if (half != null && !repeatFired) stepOnce(half)
                            },
                            onDragCancel = {
                                // Not a clean release: no step.
                                pressedHalf = null
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun RowScope.StepperHalf(
    isMinus: Boolean,
    disabled: Boolean,
    glyphColor: Color,
    description: String,
    weight: Float,
    onActivate: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .semantics {
                role = Role.Button
                contentDescription = description
                if (disabled) disabled()
                onClick(label = description) {
                    onActivate()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        Canvas(modifier = Modifier.size(14.dp)) {
            val color = glyphColor.copy(alpha = if (disabled) 0.3f else 1f)
            val strokeWidth = with(density) { 2.dp.toPx() }
            val w = size.width
            val h = size.height
            val cy = h / 2f
            val cx = w / 2f
            val half = w / 2f
            drawLine(
                color = color,
                start = Offset(cx - half, cy),
                end = Offset(cx + half, cy),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            if (!isMinus) {
                drawLine(
                    color = color,
                    start = Offset(cx, cy - half),
                    end = Offset(cx, cy + half),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
