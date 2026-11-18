// GlassSlider: a glass slider with a thumb that grows and lenses
// the track while pressed.

package com.vallxz.glass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.decoration.GlassHighlight
import com.vallxz.glass.decoration.GlassHighlightStyle
import com.vallxz.glass.decoration.GlassInnerShadow
import com.vallxz.glass.decoration.GlassShadow
import com.vallxz.glass.engine.GlassBackdrop
import com.vallxz.glass.engine.LocalGlassBackdrop
import com.vallxz.glass.engine.glassBackdrop
import com.vallxz.glass.engine.rememberGlassBackdrop
import com.vallxz.glass.engine.rememberGlassMergedBackdrop
import com.vallxz.glass.engine.rememberGlassWrappedBackdrop
import com.vallxz.glass.interaction.GlassMotion
import com.vallxz.glass.interaction.detectGlassDrag
import com.vallxz.glass.interaction.glassHapticStep
import com.vallxz.glass.interaction.rememberGlassHaptics
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A glass slider: a 6 dp capsule track with an accent fill and a 40 x 24 dp
 * glass thumb that grows 1.5x while pressed.
 *
 * Hold and drag anywhere on the track: the press starts immediately on
 * touch-down (no touch slop), the thumb chases the finger on a spring, and
 * releasing springs it to the drag target. A tap jumps to the tapped
 * position with the same press feedback. A light haptic tick fires on
 * every 5% step. While pressed, the thumb's lens, highlight, shadow, and
 * inner shadow all follow the press progress, and its surface overlay
 * fades from opaque white to transparent.
 *
 * The thumb samples a merged backdrop - the outer backdrop plus the
 * track's own recorded layer, sampled scaled by the press so the track
 * grows through the lens as the thumb is pressed - so the track stays
 * visible through the lens. The look is fully specified by the press
 * recipe; there is no style parameter. The backdrop resolves like
 * [GlassSurface] (explicit argument, else the nearest
 * `LocalGlassBackdrop`).
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    haptic: Boolean = true,
    trackHeight: Dp = 6.dp,
    thumbWidth: Dp = 40.dp,
    thumbHeight: Dp = 24.dp,
    visibilityThreshold: Float = 0.01f,
    onValueChangeFinished: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val motion = remember(valueRange, visibilityThreshold) {
        GlassMotion(value, valueRange, visibilityThreshold, scope)
    }
    LaunchedEffect(value) { motion.syncTo(value) }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val darkTheme = isSystemInDarkTheme()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val haptics = rememberGlassHaptics(haptic)

    val trackBackdrop = rememberGlassBackdrop()
    val outerBackdrop = LocalGlassBackdrop.current ?: backdrop
    // The track layer is sampled through the thumb's lens scaled by the
    // press: two-thirds wide and vertically collapsed at rest, growing
    // to its natural size at full press. The scale is evaluated in the
    // draw phase, so press ticks never recompose the slider, and it
    // applies to the track layer only - the outer backdrop is unscaled.
    val wrappedTrackBackdrop = rememberGlassWrappedBackdrop(trackBackdrop) {
        val press = motion.pressProgress.value
        Offset(lerp(2f / 3f, 1f, press), lerp(0f, 1f, press))
    }
    val mergedBackdrop = if (outerBackdrop != null) {
        rememberGlassMergedBackdrop(outerBackdrop, wrappedTrackBackdrop)
    } else {
        null
    }
    val thumbBackdrop = mergedBackdrop ?: wrappedTrackBackdrop

    val trackColor = if (darkTheme) {
        Color(0xFF787880).copy(alpha = 0.36f)
    } else {
        Color(0xFF787878).copy(alpha = 0.20f)
    }
    val accentColor = if (darkTheme) Color(0xFF0091FF) else Color(0xFF0088FF)

    var dragTarget by remember { mutableFloatStateOf(value) }
    var lastReported by remember { mutableFloatStateOf(value) }
    var downX by remember { mutableFloatStateOf(0f) }
    var lastStep by remember {
        mutableIntStateOf(
            glassHapticStep(value, valueRange.start, valueRange.endInclusive, 20),
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbHeight * 1.5f)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat()
        val thumbWidthPx = with(density) { thumbWidth.toPx() }
        // Tap dead-zone: a tap that jitters a couple of dp under the
        // finger must still count as a tap (see GlassMotion.noteMovement).
        val dragThresholdPx = with(density) { 2.dp.toPx() }

        // The track records itself so the thumb can sample it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .glassBackdrop(trackBackdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(trackColor, RoundedCornerShape(50)),
            )
            // Accent fill, drawn in the draw phase: the animated progress
            // is read inside drawBehind, so animation ticks redraw the
            // fill without recomposing the slider.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val progress = ((motion.value.value - valueRange.start) / motion.span)
                            .coerceIn(0f, 1f)
                        val fillWidth = progress * size.width
                        if (fillWidth > 0f) {
                            val left = if (layoutDirection == LayoutDirection.Ltr) {
                                0f
                            } else {
                                size.width - fillWidth
                            }
                            drawRoundRect(
                                color = accentColor,
                                topLeft = Offset(left, 0f),
                                size = Size(fillWidth, size.height),
                                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                            )
                        }
                    },
            )
        }

        SliderThumb(
            motion = motion,
            backdrop = thumbBackdrop,
            thumbWidth = thumbWidth,
            thumbHeight = thumbHeight,
            trackWidthPx = trackWidthPx,
            thumbWidthPx = thumbWidthPx,
            layoutDirection = layoutDirection,
            valueRange = valueRange,
        )

        // Haptic detents: the step index rounds to the nearest of 20
        // detents, and a tick fires whenever the step changes - while
        // dragging, on the final drag-stop emission, and on taps.
        fun tickIfStepped(target: Float) {
            val step = glassHapticStep(target, valueRange.start, valueRange.endInclusive, 20)
            if (haptic && step != lastStep) {
                lastStep = step
                haptics.scrub()
            }
        }

        // The stop handler runs for a normal end AND for a cancelled
        // gesture: a gesture stolen by a parent still snaps instead of
        // leaving the thumb mid-press. Value callbacks fire synchronously,
        // before the release visuals run.
        fun stopGesture() {
            if (motion.didDrag) {
                if (dragTarget != lastReported) {
                    lastReported = dragTarget
                    currentOnValueChange(dragTarget)
                }
                tickIfStepped(dragTarget)
                currentOnValueChangeFinished()
                motion.onRelease(dragTarget)
            } else {
                val tapFraction = if (layoutDirection == LayoutDirection.Rtl) {
                    1f - downX / trackWidthPx
                } else {
                    downX / trackWidthPx
                }
                val tapTarget = valueRange.start +
                    tapFraction.coerceIn(0f, 1f) * motion.span
                lastReported = tapTarget
                currentOnValueChange(tapTarget)
                tickIfStepped(tapTarget)
                currentOnValueChangeFinished()
                motion.moveTo(tapTarget)
            }
        }

        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(trackWidthPx, valueRange, layoutDirection) {
                        detectGlassDrag(
                            onDragStart = { down ->
                                downX = down.x
                                dragTarget = motion.value.value
                                lastReported = motion.value.value
                                // Re-seat the detent guard on the live
                                // value: an external change since the last
                                // gesture must not leave a stale guard.
                                lastStep = glassHapticStep(
                                    dragTarget,
                                    valueRange.start,
                                    valueRange.endInclusive,
                                    20,
                                )
                                motion.onPress()
                            },
                            onDrag = { delta ->
                                if (delta.x != 0f) {
                                    motion.noteMovement(delta.x, dragThresholdPx)
                                    val signed = if (layoutDirection == LayoutDirection.Rtl) {
                                        -delta.x
                                    } else {
                                        delta.x
                                    }
                                    dragTarget = (dragTarget + motion.span * signed / trackWidthPx)
                                        .coerceIn(valueRange)
                                    motion.onDragTo(dragTarget)
                                    if (abs(dragTarget - lastReported) >= visibilityThreshold) {
                                        lastReported = dragTarget
                                        currentOnValueChange(dragTarget)
                                    }
                                    tickIfStepped(dragTarget)
                                }
                            },
                            onDragEnd = { stopGesture() },
                            onDragCancel = { stopGesture() },
                        )
                    },
            )
        }
    }
}

/**
 * The slider thumb, on its own so the track and the drag overlay never
 * recompose on animation ticks. The thumb position, the velocity squash,
 * and the press recipe are all read in the layout/draw phase (the offset
 * and graphicsLayer lambdas, the style provider, and the layer-scale
 * provider), so animation ticks cost a relayout/redraw, never a
 * recomposition - and the sampled patch stays aligned with the track
 * while the thumb grows, because sampling inverts the same scale the
 * graphicsLayer applies.
 */
@Composable
private fun SliderThumb(
    motion: GlassMotion,
    backdrop: GlassBackdrop,
    thumbWidth: Dp,
    thumbHeight: Dp,
    trackWidthPx: Float,
    thumbWidthPx: Float,
    layoutDirection: LayoutDirection,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    // The thumb's visible scale, velocity squash included. One function
    // feeds both the graphicsLayer below and the layerScale provider, so
    // the inverse sampling transform always matches the visible transform
    // exactly; both read animation state in the draw phase.
    fun thumbScale(): Offset {
        val normalizedVelocity = motion.velocity.value / 10f
        val squashX = (normalizedVelocity * 0.75f).coerceIn(-0.2f, 0.2f)
        val squashY = (normalizedVelocity * 0.25f).coerceIn(-0.2f, 0.2f)
        return Offset(
            motion.scaleX.value / (1f - squashX),
            motion.scaleY.value * (1f - squashY),
        )
    }
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .offset {
                // Thumb center at progress x track width, clamped so its
                // translation stays within [-1/4 thumb width, track width
                // - 3/4 thumb width]. Read here, in the layout phase.
                val progress = ((motion.value.value - valueRange.start) / motion.span)
                    .coerceIn(0f, 1f)
                val thumbX = if (layoutDirection == LayoutDirection.Ltr) {
                    (progress * trackWidthPx - thumbWidthPx / 2f)
                        .coerceIn(-thumbWidthPx / 4f, trackWidthPx - thumbWidthPx * 3f / 4f)
                } else {
                    (trackWidthPx - progress * trackWidthPx - thumbWidthPx / 2f)
                        .coerceIn(-thumbWidthPx / 4f, trackWidthPx - thumbWidthPx * 3f / 4f)
                }
                IntOffset(thumbX.roundToInt(), 0)
            }
            .size(thumbWidth, thumbHeight)
            .graphicsLayer {
                // Velocity squash: horizontal scale stretches, vertical
                // squashes. Read here, in the draw phase.
                val thumbScale = thumbScale()
                scaleX = thumbScale.x
                scaleY = thumbScale.y
            },
        styleProvider = {
            // Evaluated in the draw phase, so press ticks invalidate the
            // draw only instead of recomposing the thumb.
            val press = motion.pressProgress.value
            GlassStyle(
            blurRadius = (8f * (1f - press)).dp,
            refractionHeight = (10f * press).dp,
            refractionAmount = (14f * press).dp,
            saturation = 1f,
            depth = false,
            surfaceColor = Color.White.copy(alpha = 1f - press),
            chromaticAberration = 1f,
            highlight = GlassHighlight(
                width = (0.5f / 1.5f).dp,
                blurRadius = (0.25f / 1.5f).dp,
                alpha = press,
                style = GlassHighlightStyle.Split,
            ),
            shadow = GlassShadow(
                radius = 4.dp,
                offset = DpOffset(0.dp, (4f / 6f).dp),
                color = Color.Black.copy(alpha = 0.05f),
            ),
            innerShadow = GlassInnerShadow(
                radius = (4f * press).dp,
                offset = DpOffset(0.dp, (4f * press).dp),
                alpha = press,
            ),
            )
        },
        shape = RoundedCornerShape(50),
        layerScale = { thumbScale() },
    ) { }
}
