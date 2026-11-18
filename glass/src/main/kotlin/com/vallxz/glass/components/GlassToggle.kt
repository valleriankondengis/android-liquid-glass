// GlassToggle: a glass switch with a thumb that slides and lenses
// the track while pressed.

package com.vallxz.glass.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.Painter
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
import com.vallxz.glass.interaction.rememberGlassHaptics
import kotlin.math.roundToInt

/**
 * A glass toggle switch: a 64 x 28 dp capsule track with a 40 x 24 dp glass
 * thumb that travels 20 dp between the ends.
 *
 * Hold and drag the thumb across: the fraction follows the finger, and
 * releasing snaps to the nearest end (at or past the middle means on). A
 * tap without horizontal movement flips the state. While pressed, the
 * thumb grows 1.5x and its lens, highlight, shadow, and inner shadow
 * follow the press progress. The track color lerps from gray to the accent
 * green by the animated fraction, and a haptic fires when the state
 * actually flips.
 *
 * The thumb samples a merged backdrop - the outer backdrop plus the
 * track's own recorded layer. The look is fully specified by the press
 * recipe; there is no style parameter. The backdrop resolves like
 * [GlassSurface] (explicit argument, else the nearest `LocalGlassBackdrop`).
 */
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    thumbIcon: Painter? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val motion = remember {
        GlassMotion(if (checked) 1f else 0f, 0f..1f, 0.001f, scope)
    }
    // An external state change runs the full press-and-release sequence,
    // so the thumb visibly grows as it slides. Skipped when the animation
    // already targets the new state, so a user-driven flip is not
    // restarted by its own callback echo.
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (motion.value.targetValue != target) {
            motion.moveTo(target)
        }
    }

    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val currentChecked by rememberUpdatedState(checked)
    val darkTheme = isSystemInDarkTheme()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val haptics = rememberGlassHaptics(haptic)

    val trackBackdrop = rememberGlassBackdrop()
    val outerBackdrop = LocalGlassBackdrop.current ?: backdrop
    // The track layer is sampled through the thumb's lens scaled by the
    // press: two-thirds wide and vertically collapsed at rest, growing
    // to three-quarters of its size at full press - never full size. The
    // scale is evaluated in the draw phase, so press ticks never
    // recompose the toggle, and it applies to the track layer only.
    val wrappedTrackBackdrop = rememberGlassWrappedBackdrop(trackBackdrop) {
        val press = motion.pressProgress.value
        Offset(
            2f / 3f + (0.75f - 2f / 3f) * press,
            0.75f * press,
        )
    }
    val mergedBackdrop = if (outerBackdrop != null) {
        rememberGlassMergedBackdrop(outerBackdrop, wrappedTrackBackdrop)
    } else {
        null
    }
    val thumbBackdrop = mergedBackdrop ?: wrappedTrackBackdrop

    val trackGray = if (darkTheme) {
        Color(0xFF787880).copy(alpha = 0.36f)
    } else {
        Color(0xFF787878).copy(alpha = 0.20f)
    }
    val accentColor = if (darkTheme) Color(0xFF30D158) else Color(0xFF34C759)

    val travelPx = with(density) { 20.dp.toPx() }
    val insetPx = with(density) { 2.dp.toPx() }
    // Tap dead-zone: a tap that jitters a couple of dp under the finger
    // must still count as a tap (see GlassMotion.noteMovement).
    val dragThresholdPx = with(density) { 2.dp.toPx() }

    var dragFraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    fun flipTo(target: Boolean) {
        if (target != currentChecked) {
            if (haptic) haptics.toggle(target)
            currentOnCheckedChange(target)
        }
    }

    // The stop handler runs for a normal end AND for a cancelled gesture:
    // a gesture stolen by a parent still flips or snaps instead of
    // leaving the thumb mid-press. The state callback fires synchronously,
    // before the release visuals run, so the flip feels immediate.
    fun stopGesture() {
        if (motion.didDrag) {
            val target = dragFraction >= 0.5f
            flipTo(target)
            motion.onRelease(if (target) 1f else 0f)
        } else {
            val target = !currentChecked
            flipTo(target)
            motion.moveTo(if (target) 1f else 0f)
        }
    }

    Box(
        modifier = modifier
            .size(64.dp, 28.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                onClick {
                    if (enabled) flipTo(!checked)
                    true
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // The track records itself so the thumb can sample it. The track
        // color is drawn by an inner drawBehind, so it lands inside the
        // recording (a background modifier placed before glassBackdrop
        // draws outside it and the recorded layer comes out empty) and
        // the animated fraction is read in the draw phase, so value ticks
        // redraw the track without recomposing the toggle.
        Box(
            modifier = Modifier
                .size(64.dp, 28.dp)
                .glassBackdrop(trackBackdrop)
                .drawBehind {
                    drawRoundRect(
                        color = lerp(trackGray, accentColor, motion.value.value),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    )
                },
        )

        // The thumb's visible scale, velocity squash included. One
        // function feeds both the graphicsLayer below and the layerScale
        // provider, so the inverse sampling transform always matches the
        // visible transform exactly; both read animation state in the draw
        // phase.
        fun thumbScale(): Offset {
            val normalizedVelocity = motion.velocity.value / 50f
            val squashX = (normalizedVelocity * 0.75f).coerceIn(-0.2f, 0.2f)
            val squashY = (normalizedVelocity * 0.25f).coerceIn(-0.2f, 0.2f)
            return Offset(
                motion.scaleX.value / (1f - squashX),
                motion.scaleY.value * (1f - squashY),
            )
        }
        GlassSurface(
            backdrop = thumbBackdrop,
            modifier = Modifier
                .offset {
                    // Read the animated fraction here, in the layout
                    // phase, so value ticks relayout the thumb without
                    // recomposing the whole toggle.
                    val animatedFraction = motion.value.value
                    val thumbX = if (layoutDirection == LayoutDirection.Ltr) {
                        insetPx + animatedFraction * travelPx
                    } else {
                        insetPx + (1f - animatedFraction) * travelPx
                    }
                    IntOffset(thumbX.roundToInt(), 0)
                }
                .size(40.dp, 24.dp)
                .graphicsLayer {
                    // Velocity squash, gentler than the slider's. Read
                    // here, in the draw phase.
                    val thumbScale = thumbScale()
                    scaleX = thumbScale.x
                    scaleY = thumbScale.y
                },
            styleProvider = {
                // Evaluated in the draw phase, so press ticks invalidate
                // the draw only instead of recomposing the toggle.
                val press = motion.pressProgress.value
                GlassStyle(
                blurRadius = (8f * (1f - press)).dp,
                refractionHeight = (5f * press).dp,
                refractionAmount = (10f * press).dp,
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
        ) {
            if (thumbIcon != null && checked) {
                Image(
                    painter = thumbIcon,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(13.dp),
                )
            }
        }

        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(layoutDirection) {
                        detectGlassDrag(
                            onDragStart = {
                                dragFraction = motion.value.value
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
                                    dragFraction = (dragFraction + signed / travelPx)
                                        .coerceIn(0f, 1f)
                                    motion.onDragTo(dragFraction)
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
