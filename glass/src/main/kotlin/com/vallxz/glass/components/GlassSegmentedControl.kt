// GlassSegmentedControl: a segmented control with a liquid droplet
// that slides and stretches while pressed, over a flat track at rest.

package com.vallxz.glass.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.decoration.GlassHighlight
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
import com.vallxz.glass.interaction.rememberGlassHaptics
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * A glass segmented control: a 32 dp capsule track with 2 dp padding and
 * 4 dp gaps between segments. At rest the selected segment is a flat
 * 28 dp button with 20 dp corners, and the labels are 13.33 sp with
 * 18 sp line height - semibold when selected, medium otherwise.
 *
 * Hold and drag anywhere on the control: the liquid droplet follows the
 * finger on a spring, stretches with its speed, and rubber-bands past
 * the ends (at most 5 dp, with exponential resistance). Releasing snaps
 * to the nearest segment. A tap animates the droplet to the tapped
 * segment. While pressed, the droplet grows taller, its lens refracts,
 * its shadow and highlight fade in, the labels fade toward 75% alpha
 * with distance from the droplet center, and the sampled track
 * shrinks to 0.9x; at rest the lens, shadow, and highlight are gone,
 * the labels sit at full alpha, and the track samples at its natural
 * size.
 *
 * The droplet samples a merged backdrop - the outer backdrop, the
 * control's recorded track layer (track plus resting thumb) sampled
 * scaled by the press, and the recorded labels layer. The look is fully
 * specified by the press recipe; there is no style parameter. The
 * backdrop resolves like [GlassSurface] (explicit argument, else the
 * nearest `LocalGlassBackdrop`).
 */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    contentColor: Color = Color.Unspecified,
) {
    require(options.isNotEmpty()) { "GlassSegmentedControl needs at least one option" }
    val count = options.size
    val selectedCoerced = selectedIndex.coerceIn(options.indices)

    val scope = rememberCoroutineScope()
    val motion = remember(count) {
        GlassMotion(selectedCoerced.toFloat(), 0f..(count - 1).toFloat(), 0.001f, scope).apply {
            pressedScale = 1.14f
        }
    }
    LaunchedEffect(selectedCoerced) { motion.syncTo(selectedCoerced.toFloat()) }

    val currentOnSelected by rememberUpdatedState(onSelected)
    val currentSelected by rememberUpdatedState(selectedCoerced)
    val darkTheme = isSystemInDarkTheme()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val haptics = rememberGlassHaptics(haptic)

    val labelsBackdrop = rememberGlassBackdrop()
    val trackBackdrop = rememberGlassBackdrop()
    val outerBackdrop = LocalGlassBackdrop.current ?: backdrop
    // The track layer is sampled scaled by the press: at its natural
    // size at rest, shrinking to 0.9x at full press, about the droplet
    // center. The scale is evaluated in the draw phase, and it applies
    // to the track layer only - the labels and the outer backdrop are
    // unscaled.
    val wrappedTrackBackdrop = rememberGlassWrappedBackdrop(trackBackdrop) {
        val scale = lerp(1f, 0.9f, motion.pressProgress.value)
        Offset(scale, scale)
    }
    val innerMergedBackdrop = rememberGlassMergedBackdrop(wrappedTrackBackdrop, labelsBackdrop)
    val mergedBackdrop = if (outerBackdrop != null) {
        rememberGlassMergedBackdrop(outerBackdrop, innerMergedBackdrop)
    } else {
        null
    }
    val dropletBackdrop = mergedBackdrop ?: innerMergedBackdrop

    val resolvedContentColor = if (contentColor != Color.Unspecified) {
        contentColor
    } else if (darkTheme) {
        Color.White
    } else {
        Color.Black
    }

    val trackColor = if (darkTheme) {
        Color(0xFF767680).copy(alpha = 0.24f)
    } else {
        Color(0xFF767680).copy(alpha = 0.12f)
    }
    val restingThumbColor = if (darkTheme) {
        Color.White.copy(alpha = 0.27f)
    } else {
        Color.White
    }

    var dragValue by remember { mutableFloatStateOf(selectedCoerced.toFloat()) }
    var overdragPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var downX by remember { mutableFloatStateOf(0f) }
    // Tap dead-zone: a tap that jitters a couple of dp under the finger
    // must still count as a tap (see GlassMotion.noteMovement).
    val dragThresholdPx = with(density) { 2.dp.toPx() }
    val displayedOverdrag by animateFloatAsState(
        targetValue = if (dragging) overdragPx else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "glassSegmentOverdrag",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val insetPx = with(density) { 2.dp.toPx() }
        val gapPx = with(density) { 4.dp.toPx() }
        val segmentWidthPx = (widthPx - insetPx * 2f - gapPx * (count - 1)) / count
        val pitchPx = segmentWidthPx + gapPx
        val dropletHeightPx = with(density) { 28.dp.toPx() }
        val maxDropletWidthPx = with(density) { (2.2f * 28f).dp.toPx() }
        val dropletWidthPx = segmentWidthPx.coerceIn(
            minOf(dropletHeightPx, segmentWidthPx),
            maxDropletWidthPx,
        )

        fun dropletX(index: Float): Float {
            val slotX = if (layoutDirection == LayoutDirection.Ltr) {
                insetPx + index * pitchPx
            } else {
                widthPx - insetPx - index * pitchPx - segmentWidthPx
            }
            return slotX + (segmentWidthPx - dropletWidthPx) / 2f
        }

        // Track and resting thumb: drawn normally and recorded in their
        // own layer for the droplet to sample scaled by the press.
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassBackdrop(trackBackdrop),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(trackColor, RoundedCornerShape(50)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        IntOffset(
                            (dropletX(motion.value.value) + displayedOverdrag).roundToInt(),
                            0,
                        )
                    }
                    .size(
                        with(density) { dropletWidthPx.toDp() },
                        28.dp,
                    )
                    .background(restingThumbColor, RoundedCornerShape(20.dp))
                    .graphicsLayer { alpha = 1f - motion.pressProgress.value },
            )
        }

        // Labels: drawn normally and recorded in their own layer, so the
        // droplet samples them unscaled while the track scales by press.
        Row(
            modifier = Modifier
                .matchParentSize()
                .glassBackdrop(labelsBackdrop)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
                options.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics {
                                role = Role.Tab
                                selected = index == currentSelected
                                onClick {
                                    if (enabled) {
                                        if (index != currentSelected) {
                                            if (haptic) haptics.tick()
                                            currentOnSelected(index)
                                        }
                                        motion.moveTo(index.toFloat())
                                    }
                                    true
                                }
                            }
                            .padding(horizontal = 6.dp)
                            .graphicsLayer {
                                // Label fade toward 75% alpha with distance
                                // from the droplet center, only while
                                // pressed: at rest every label sits at full
                                // alpha. Read here in the draw phase so the
                                // labels never recompose on animation ticks.
                                val distance = abs(index - motion.value.value).coerceIn(0f, 1f)
                                val pressedAlpha = lerp(1f, 0.75f, distance)
                                alpha = lerp(1f, pressedAlpha, motion.pressProgress.value)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = label,
                            style = TextStyle(
                                color = resolvedContentColor,
                                fontSize = 13.33.sp,
                                fontWeight = if (index == currentSelected) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Medium
                                },
                                lineHeight = 18.sp,
                                letterSpacing = (-0.08).sp,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
        }

        SegmentedDroplet(
            motion = motion,
            backdrop = dropletBackdrop,
            dropletX = { index -> dropletX(index) },
            displayedOverdrag = displayedOverdrag,
            dropletWidth = with(density) { dropletWidthPx.toDp() },
            dropletHeight = 28.dp,
            pitchPx = pitchPx,
            density = density,
        )

        // The stop handler runs for a normal end AND for a cancelled
        // gesture: a gesture stolen by a parent still snaps instead of
        // leaving the droplet mid-press. Selection callbacks fire
        // synchronously, before the release visuals run.
        fun stopGesture() {
            dragging = false
            if (motion.didDrag) {
                val snap = motion.value.value.roundToInt()
                    .coerceIn(0, count - 1)
                if (snap != currentSelected) {
                    if (haptic) haptics.tick()
                    currentOnSelected(snap)
                }
                motion.onRelease(snap.toFloat())
            } else {
                val tapIndex = if (layoutDirection == LayoutDirection.Rtl) {
                    ((widthPx - downX - insetPx) / pitchPx).toInt()
                } else {
                    ((downX - insetPx) / pitchPx).toInt()
                }.coerceIn(0, count - 1)
                if (tapIndex != currentSelected) {
                    if (haptic) haptics.tick()
                    currentOnSelected(tapIndex)
                }
                motion.moveTo(tapIndex.toFloat())
            }
        }

        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(pitchPx, count, layoutDirection) {
                        detectGlassDrag(
                            onDragStart = { down ->
                                downX = down.x
                                // Baseline at the animation's target, so
                                // grabbing mid-flight starts from where
                                // the droplet is headed, not where it is.
                                dragValue = motion.value.targetValue
                                overdragPx = 0f
                                dragging = true
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
                                    dragValue += signed / pitchPx
                                    val clamped = dragValue.coerceIn(0f, (count - 1).toFloat())
                                    val excessPx = (dragValue - clamped) * pitchPx
                                    val maxOverdragPx = 5f * density.density
                                    overdragPx = if (excessPx == 0f) {
                                        0f
                                    } else {
                                        val sign = if (excessPx > 0f) 1f else -1f
                                        sign * maxOverdragPx *
                                            (1f - exp(-abs(excessPx) / maxOverdragPx))
                                    }
                                    motion.onDragTo(clamped)
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
 * The segmented droplet, on its own so the control never recomposes on
 * animation ticks. Its position, speed stretch, overdrag stretch, and
 * press recipe are all read in the layout/draw phase (the offset and
 * graphicsLayer lambdas, the style provider, and the layer-scale
 * provider), so animation ticks cost a relayout/redraw, never a
 * recomposition - and sampling inverts the same scale the graphicsLayer
 * applies, so the sampled track stays aligned with the real one instead
 * of doubling while dragging.
 */
@Composable
private fun SegmentedDroplet(
    motion: GlassMotion,
    backdrop: GlassBackdrop,
    dropletX: (Float) -> Float,
    displayedOverdrag: Float,
    dropletWidth: Dp,
    dropletHeight: Dp,
    pitchPx: Float,
    density: Density,
) {
    // The droplet's visible scale, speed and overdrag stretch included.
    // One function feeds both the graphicsLayer below and the layerScale
    // provider, so the inverse sampling transform always matches the
    // visible transform exactly; both read animation state in the draw
    // phase, so the sampled track stays aligned with the real one while
    // the droplet stretches instead of doubling.
    fun dropletScale(): Offset {
        val velocityDpPerSec = abs(motion.velocity.value) * motion.span *
            pitchPx / density.density
        val stretch = (velocityDpPerSec / 1600f).coerceIn(0f, 1f)
        val overdragFactor = (abs(displayedOverdrag) / with(density) { 5.dp.toPx() })
            .coerceIn(0f, 1f)
        return Offset(
            motion.scaleX.value * (1f + stretch * 0.3f + overdragFactor * 0.12f),
            motion.scaleY.value * (1f - stretch * 0.16f - overdragFactor * 0.06f),
        )
    }
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .offset {
                IntOffset((dropletX(motion.value.value) + displayedOverdrag).roundToInt(), 0)
            }
            .size(dropletWidth, dropletHeight)
            .layout { measurable, constraints ->
                // The droplet is drawn taller while pressed, from its
                // resting height to 48 dp at full press. Growing the
                // drawn height keeps the capsule corners circular at
                // every press value, where scaling a short pill taller
                // would stretch them. Read in the layout phase, so
                // press ticks relayout, never recompose.
                val heightPx = lerp(
                    with(density) { dropletHeight.toPx() },
                    with(density) { 48.dp.toPx() },
                    motion.pressProgress.value,
                ).roundToInt()
                val placeable = measurable.measure(
                    constraints.copy(minHeight = heightPx, maxHeight = heightPx),
                )
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                // Speed stretch from the droplet velocity, plus overdrag
                // stretch. Read here, in the draw phase.
                val dropletScale = dropletScale()
                scaleX = dropletScale.x
                scaleY = dropletScale.y
            },
        styleProvider = {
            // Evaluated in the draw phase, so press ticks invalidate the
            // draw only instead of recomposing the droplet.
            val press = motion.pressProgress.value
            // The lens follows the drawn height, so the refraction at
            // full press matches the previous 48 dp droplet.
            val drawnHeightPx = lerp(
                with(density) { dropletHeight.toPx() },
                with(density) { 48.dp.toPx() },
                press,
            )
            val halfMinPx = minOf(with(density) { dropletWidth.toPx() }, drawnHeightPx) / 2f
            GlassStyle(
            blurRadius = 0.dp,
            refractionHeight = with(density) { (halfMinPx * 0.58f * press).toDp() },
            refractionAmount = with(density) { (halfMinPx * 0.8f * press).toDp() },
            saturation = 1f,
            depth = true,
            chromaticAberration = 0.8f,
            // The highlight, shadow, and inner shadow fade in with the
            // press: at rest the droplet adds nothing over the flat
            // selected button beneath it.
            highlight = GlassHighlight(alpha = press),
            shadow = GlassShadow(alpha = press),
            innerShadow = GlassInnerShadow(
                radius = (8f * lerp(0.75f, 1f, press)).dp,
                offset = DpOffset(0.dp, (8f * lerp(0.75f, 1f, press)).dp),
                alpha = press,
            ),
            )
        },
        shape = RoundedCornerShape(50),
        layerScale = { dropletScale() },
    ) { }
}
