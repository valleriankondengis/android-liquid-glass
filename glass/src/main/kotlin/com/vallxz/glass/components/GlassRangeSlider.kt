// GlassRangeSlider: a two-thumb range slider on a glass track.

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
import androidx.compose.ui.util.lerp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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
 * A glass range slider: two 40 x 24 dp glass thumbs on a 6 dp capsule
 * track, with the segment between them filled in accent blue. The thumbs
 * cannot cross and keep at least [minGap] between them.
 *
 * Hold and drag either thumb: the press starts immediately on touch-down,
 * the thumb chases the finger on a spring, and releasing springs it to
 * the drag target. A tap moves the nearer thumb to the tapped position
 * with the same press feedback. A light haptic tick fires on every 2.5%
 * step of the dragged thumb. Each thumb carries progress-bar semantics
 * ("Range start" / "Range end"), and the dragged thumb draws on top.
 *
 * The thumbs share the single slider's press recipe (blur 8 to 0, lens
 * 10/14 x press, dispersion on, 4 dp shadow, inner shadow x press) and
 * sample a merged backdrop - the outer backdrop plus the track's own
 * recorded layer. The look is fully specified by the recipe; there is no
 * style parameter. The backdrop resolves like [GlassSurface] (explicit
 * argument, else the nearest `LocalGlassBackdrop`).
 */
@Composable
fun GlassRangeSlider(
    start: Float,
    end: Float,
    onRangeChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    minGap: Float = 0f,
    enabled: Boolean = true,
    haptic: Boolean = true,
    thumbWidth: Dp = 40.dp,
    thumbHeight: Dp = 24.dp,
    visibilityThreshold: Float = 0.01f,
    onRangeChangeFinished: () -> Unit = {},
) {
    require(start <= end) { "GlassRangeSlider needs start <= end" }
    val scope = rememberCoroutineScope()
    val startMotion = remember(valueRange, visibilityThreshold) {
        GlassMotion(start, valueRange, visibilityThreshold, scope)
    }
    val endMotion = remember(valueRange, visibilityThreshold) {
        GlassMotion(end, valueRange, visibilityThreshold, scope)
    }
    LaunchedEffect(start) { startMotion.syncTo(start) }
    LaunchedEffect(end) { endMotion.syncTo(end) }

    val currentOnRangeChange by rememberUpdatedState(onRangeChange)
    val currentOnRangeChangeFinished by rememberUpdatedState(onRangeChangeFinished)
    val darkTheme = isSystemInDarkTheme()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val haptics = rememberGlassHaptics(haptic)

    val trackBackdrop = rememberGlassBackdrop()
    val outerBackdrop = LocalGlassBackdrop.current ?: backdrop
    // Each thumb builds its own merged backdrop from these: the track
    // layer is sampled scaled by that thumb's own press, so the two
    // thumbs cannot share one merged backdrop.

    val trackColor = if (darkTheme) {
        Color(0xFF787880).copy(alpha = 0.36f)
    } else {
        Color(0xFF787878).copy(alpha = 0.20f)
    }
    val accentColor = if (darkTheme) Color(0xFF0091FF) else Color(0xFF0088FF)
    val span = valueRange.endInclusive - valueRange.start

    var startTarget by remember { mutableFloatStateOf(start) }
    var endTarget by remember { mutableFloatStateOf(end) }
    var lastReportedStart by remember { mutableFloatStateOf(start) }
    var lastReportedEnd by remember { mutableFloatStateOf(end) }
    var activeThumb by remember { mutableIntStateOf(0) }
    var downX by remember { mutableFloatStateOf(0f) }
    var lastStartStep by remember {
        mutableIntStateOf(glassHapticStep(start, valueRange.start, valueRange.endInclusive, 40))
    }
    var lastEndStep by remember {
        mutableIntStateOf(glassHapticStep(end, valueRange.start, valueRange.endInclusive, 40))
    }
    // Tap dead-zone: a tap that jitters a couple of dp under the finger
    // must still count as a tap (see GlassMotion.noteMovement).
    val dragThresholdPx = with(density) { 2.dp.toPx() }

    fun reportIfNeeded() {
        if (abs(startTarget - lastReportedStart) >= visibilityThreshold ||
            abs(endTarget - lastReportedEnd) >= visibilityThreshold
        ) {
            lastReportedStart = startTarget
            lastReportedEnd = endTarget
            currentOnRangeChange(startTarget, endTarget)
        }
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

        fun thumbX(progress: Float): Float {
            val raw = if (layoutDirection == LayoutDirection.Ltr) {
                progress * trackWidthPx - thumbWidthPx / 2f
            } else {
                trackWidthPx - progress * trackWidthPx - thumbWidthPx / 2f
            }
            return raw.coerceIn(-thumbWidthPx / 4f, trackWidthPx - thumbWidthPx * 3f / 4f)
        }

        fun valueAt(x: Float): Float {
            val fraction = if (layoutDirection == LayoutDirection.Ltr) {
                x / trackWidthPx
            } else {
                1f - x / trackWidthPx
            }
            return valueRange.start + fraction.coerceIn(0f, 1f) * span
        }

        // The track records itself so the thumbs can sample it. The accent
        // fill between the thumb centers is drawn in the draw phase: the
        // animated values are read inside drawBehind, so animation ticks
        // redraw the fill without recomposing the slider.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .glassBackdrop(trackBackdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(trackColor, RoundedCornerShape(50)),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val startProgress =
                            ((startMotion.value.value - valueRange.start) / span).coerceIn(0f, 1f)
                        val endProgress =
                            ((endMotion.value.value - valueRange.start) / span).coerceIn(0f, 1f)
                        // The fill spans the progress positions, reaching
                        // both track ends at full range - not the clamped
                        // thumb centers, which stop short of the ends.
                        val startX = startProgress * size.width
                        val endX = endProgress * size.width
                        val fillLeft = minOf(startX, endX)
                        val fillWidth = abs(endX - startX)
                        if (fillWidth > 0f) {
                            drawRoundRect(
                                color = accentColor,
                                topLeft = Offset(fillLeft, 0f),
                                size = Size(fillWidth, size.height),
                                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                            )
                        }
                    },
            )
        }

        val thumbs: List<@Composable () -> Unit> = listOf(
            {
                RangeThumb(
                    motion = startMotion,
                    outerBackdrop = outerBackdrop,
                    trackBackdrop = trackBackdrop,
                    thumbX = { progress -> thumbX(progress) },
                    thumbWidth = thumbWidth,
                    thumbHeight = thumbHeight,
                    description = "Range start",
                    valueRange = valueRange,
                )
            },
            {
                RangeThumb(
                    motion = endMotion,
                    outerBackdrop = outerBackdrop,
                    trackBackdrop = trackBackdrop,
                    thumbX = { progress -> thumbX(progress) },
                    thumbWidth = thumbWidth,
                    thumbHeight = thumbHeight,
                    description = "Range end",
                    valueRange = valueRange,
                )
            },
        )
        // The dragged thumb draws on top.
        if (activeThumb == 0) {
            thumbs[1]()
            thumbs[0]()
        } else {
            thumbs[0]()
            thumbs[1]()
        }

        // Haptic detents: one guard per thumb, rounded to the nearest of
        // 40 detents, ticking whenever either thumb crosses a step - while
        // dragging, on the final drag-stop emission, and on taps.
        fun tickIfStepped() {
            val startStep = glassHapticStep(
                startTarget,
                valueRange.start,
                valueRange.endInclusive,
                40,
            )
            val endStep = glassHapticStep(
                endTarget,
                valueRange.start,
                valueRange.endInclusive,
                40,
            )
            if (haptic && (startStep != lastStartStep || endStep != lastEndStep)) {
                lastStartStep = startStep
                lastEndStep = endStep
                haptics.scrub()
            }
        }

        // The stop handler runs for a normal end AND for a cancelled
        // gesture: a gesture stolen by a parent still snaps instead of
        // leaving the thumb mid-press. Range callbacks fire synchronously,
        // before the release visuals run.
        fun stopGesture() {
            val motion = if (activeThumb == 0) startMotion else endMotion
            if (motion.didDrag) {
                val target = if (activeThumb == 0) startTarget else endTarget
                if (startTarget != lastReportedStart ||
                    endTarget != lastReportedEnd
                ) {
                    lastReportedStart = startTarget
                    lastReportedEnd = endTarget
                    currentOnRangeChange(startTarget, endTarget)
                }
                tickIfStepped()
                currentOnRangeChangeFinished()
                motion.onRelease(target)
            } else {
                val tapValue = valueAt(downX)
                if (activeThumb == 0) {
                    startTarget = tapValue.coerceIn(
                        valueRange.start,
                        endTarget - minGap,
                    )
                } else {
                    endTarget = tapValue.coerceIn(
                        startTarget + minGap,
                        valueRange.endInclusive,
                    )
                }
                lastReportedStart = startTarget
                lastReportedEnd = endTarget
                currentOnRangeChange(startTarget, endTarget)
                tickIfStepped()
                currentOnRangeChangeFinished()
                motion.moveTo(if (activeThumb == 0) startTarget else endTarget)
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
                                val tapValue = valueAt(down.x)
                                activeThumb = if (
                                    abs(tapValue - startMotion.value.value) <=
                                    abs(tapValue - endMotion.value.value)
                                ) 0 else 1
                                startTarget = startMotion.value.value
                                endTarget = endMotion.value.value
                                lastReportedStart = startTarget
                                lastReportedEnd = endTarget
                                // Re-seat the detent guards on the live
                                // values: an external change since the
                                // last gesture must not leave stale guards.
                                lastStartStep = glassHapticStep(
                                    startTarget,
                                    valueRange.start,
                                    valueRange.endInclusive,
                                    40,
                                )
                                lastEndStep = glassHapticStep(
                                    endTarget,
                                    valueRange.start,
                                    valueRange.endInclusive,
                                    40,
                                )
                                if (activeThumb == 0) startMotion.onPress() else endMotion.onPress()
                            },
                            onDrag = { delta ->
                                if (delta.x != 0f) {
                                    val motion = if (activeThumb == 0) startMotion else endMotion
                                    motion.noteMovement(delta.x, dragThresholdPx)
                                    val signed = if (layoutDirection == LayoutDirection.Rtl) {
                                        -delta.x
                                    } else {
                                        delta.x
                                    }
                                    val move = span * signed / trackWidthPx
                                    if (activeThumb == 0) {
                                        startTarget = (startTarget + move)
                                            .coerceIn(valueRange.start, endTarget - minGap)
                                        motion.onDragTo(startTarget)
                                    } else {
                                        endTarget = (endTarget + move)
                                            .coerceIn(startTarget + minGap, valueRange.endInclusive)
                                        motion.onDragTo(endTarget)
                                    }
                                    reportIfNeeded()
                                    tickIfStepped()
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

@Composable
private fun RangeThumb(
    motion: GlassMotion,
    outerBackdrop: GlassBackdrop?,
    trackBackdrop: GlassBackdrop,
    thumbX: (Float) -> Float,
    thumbWidth: Dp,
    thumbHeight: Dp,
    description: String,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    // The track layer is sampled through this thumb's lens scaled by
    // this thumb's own press: two-thirds wide and vertically collapsed
    // at rest, growing to its natural size at full press. The scale is
    // evaluated in the draw phase, and it applies to the track layer
    // only - the outer backdrop is unscaled.
    val wrappedTrackBackdrop = rememberGlassWrappedBackdrop(trackBackdrop) {
        val press = motion.pressProgress.value
        Offset(lerp(2f / 3f, 1f, press), lerp(0f, 1f, press))
    }
    val mergedBackdrop = if (outerBackdrop != null) {
        rememberGlassMergedBackdrop(outerBackdrop, wrappedTrackBackdrop)
    } else {
        null
    }
    val backdrop = mergedBackdrop ?: wrappedTrackBackdrop
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
                // Read the animated value here, in the layout phase, so
                // value ticks relayout the thumb without recomposing it.
                val progress = ((motion.value.value - valueRange.start) / motion.span)
                    .coerceIn(0f, 1f)
                IntOffset(thumbX(progress).roundToInt(), 0)
            }
            .size(thumbWidth, thumbHeight)
            .graphicsLayer {
                // Velocity squash, read here in the draw phase.
                val thumbScale = thumbScale()
                scaleX = thumbScale.x
                scaleY = thumbScale.y
            }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = motion.value.value,
                    range = valueRange,
                    steps = 0,
                )
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
