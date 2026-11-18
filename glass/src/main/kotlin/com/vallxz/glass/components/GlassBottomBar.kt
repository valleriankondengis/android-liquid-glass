// GlassBottomBar: a floating bottom tab bar with a liquid droplet that
// follows the selected tab and morphs while dragged.

package com.vallxz.glass.components

import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
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
import com.vallxz.glass.engine.GlassShaders
import com.vallxz.glass.engine.LocalGlassBackdrop
import com.vallxz.glass.engine.glassBackdrop
import com.vallxz.glass.engine.rememberGlassBackdrop
import com.vallxz.glass.engine.rememberGlassMergedBackdrop
import com.vallxz.glass.engine.rememberGlassWrappedBackdrop
import com.vallxz.glass.interaction.GlassMotion
import com.vallxz.glass.interaction.detectGlassDrag
import com.vallxz.glass.interaction.rememberGlassHaptics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One tab in a [GlassBottomBar]: a label and an icon pair. */
data class GlassTab(
    val label: String,
    val icon: Painter,
    val selectedIcon: Painter? = null,
)

/**
 * A floating glass bottom bar: a 64 dp capsule with a glass droplet that
 * slides between tabs.
 *
 * Hold and drag across the bar: the droplet follows the finger on a
 * spring, the bar rubber-bands up to 4 dp and grows slightly, and
 * releasing snaps to the nearest tab. A tap animates the droplet to the
 * tapped tab. A haptic tick fires whenever the highlighted tab changes,
 * mid-drag or by tap. The droplet samples a merged backdrop - the outer
 * backdrop plus an invisible duplicate of the tab row, itself a full
 * glass surface tinted with the accent color - so the selected tab's
 * icon and label refract through the lens, tinted, and the droplet reads
 * as glass even at rest, when its own lens is zero.
 *
 * The look is fully specified by the press recipe; there is no style
 * parameter. The backdrop resolves like [GlassSurface] (explicit argument,
 * else the nearest `LocalGlassBackdrop`).
 *
 * [showDroplet] hides the droplet - and the selected-tab styling - when
 * the active destination lives outside the bar, for example a separate
 * search button beside it: the droplet fades out, leaving the bar with
 * no highlighted tab, and fades back in when the selection returns.
 * Drags and taps on the bar still work while it is hidden, and
 * selecting a tab reports through [onSelected] as usual.
 */
@Composable
fun GlassBottomBar(
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    showLabels: Boolean = true,
    haptic: Boolean = true,
    contentColor: Color = Color.Unspecified,
    showDroplet: Boolean = true,
) {
    require(tabs.isNotEmpty()) { "GlassBottomBar needs at least one tab" }
    val count = tabs.size
    val selected = selectedIndex.coerceIn(tabs.indices)

    val scope = rememberCoroutineScope()
    val motion = remember(count) {
        GlassMotion(selected.toFloat(), 0f..(count - 1).toFloat(), 0.001f, scope).apply {
            pressedScale = 78f / 56f
        }
    }
    LaunchedEffect(selected) { motion.syncTo(selected.toFloat()) }

    val currentOnSelected by rememberUpdatedState(onSelected)
    // While the droplet is hidden, the bar has no current selection:
    // the active destination lives outside the bar, so every tap and
    // snap must report - including taps on the tab the droplet last
    // sat on, which would otherwise be swallowed by the
    // already-selected guard and leave the bar unable to navigate
    // back to it.
    val currentSelected by rememberUpdatedState(if (showDroplet) selected else -1)
    val darkTheme = isSystemInDarkTheme()
    val resolvedContentColor = if (contentColor == Color.Unspecified) {
        glassLabelColor(darkTheme)
    } else {
        contentColor
    }
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val haptics = rememberGlassHaptics(haptic)

    val tabsBackdrop = rememberGlassBackdrop()
    val outerBackdrop = LocalGlassBackdrop.current ?: backdrop
    // The tab row is sampled through the droplet's lens shrunk toward
    // 0.92x about the droplet center as the press grows, so the sampled
    // tab icon stays anchored instead of flying outward while the
    // droplet grows. The scale is evaluated in the draw phase, and it
    // applies to the tab row layer only - the outer backdrop is
    // unscaled.
    val wrappedTabsBackdrop = rememberGlassWrappedBackdrop(tabsBackdrop) {
        val scale = lerp(1f, 0.92f, motion.pressProgress.value)
        Offset(scale, scale)
    }
    val mergedBackdrop = if (outerBackdrop != null) {
        rememberGlassMergedBackdrop(outerBackdrop, wrappedTabsBackdrop)
    } else {
        null
    }
    val dropletBackdrop = mergedBackdrop ?: wrappedTabsBackdrop

    // Press ripple for the duplicate row: a dedicated press animation
    // (spring 0.5/300), driven by the bar's drag callbacks, read by the
    // ripple overlay inside the duplicate row's recording so the droplet
    // samples the brightening.
    val rippleProgress = remember { Animatable(0f, 0.001f) }
    val rippleSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)

    val dropletAlpha by animateFloatAsState(
        targetValue = if (showDroplet) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.001f),
        label = "glassBarDropletAlpha",
    )

    val accentColor = glassBarAccentColor(darkTheme)

    var dragValue by remember { mutableFloatStateOf(selected.toFloat()) }
    var rubberBandPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var downX by remember { mutableFloatStateOf(0f) }
    var lastHighlighted by remember { mutableIntStateOf(selected) }
    // Hiding the droplet clears the highlight baseline, so the first
    // tap back on the bar ticks again instead of matching the stale
    // highlighted tab.
    LaunchedEffect(showDroplet) {
        if (!showDroplet) lastHighlighted = -1
    }
    // Tap dead-zone: a tap that jitters a couple of dp under the finger
    // must still count as a tap (see GlassMotion.noteMovement).
    val dragThresholdPx = with(density) { 2.dp.toPx() }
    fun highlight(index: Int) {
        if (index != lastHighlighted) {
            lastHighlighted = index
            if (haptic) haptics.tick()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val barPaddingPx = with(density) { 4.dp.toPx() }
        val tabWidthPx = (widthPx - barPaddingPx * 2f) / count

        // Rubber-band offset: at most 4 dp, easing out over the bar width,
        // springing back to zero on release. Shared by the visible row,
        // the duplicate row, and the droplet, so all three stay aligned
        // while dragging past the ends.
        val barTranslation by animateFloatAsState(
            targetValue = if (dragging) {
                val maxTranslation = with(density) { 4.dp.toPx() }
                val fraction = (kotlin.math.abs(rubberBandPx) / widthPx)
                    .coerceIn(0f, 1f)
                val eased = 1f - (1f - fraction) * (1f - fraction) * (1f - fraction)
                if (rubberBandPx >= 0f) maxTranslation * eased else -maxTranslation * eased
            } else {
                0f
            },
            animationSpec = spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.5f),
            label = "glassBarRubberBand",
        )

        fun dropletX(index: Float): Float {
            return if (layoutDirection == LayoutDirection.Ltr) {
                barPaddingPx + index * tabWidthPx
            } else {
                widthPx - barPaddingPx - (index + 1f) * tabWidthPx
            }
        }

        // Invisible duplicate tab row, recorded for the droplet to sample.
        // It is a full glass surface - the same bar recipe, with a
        // press-driven highlight and no drop shadow - and the recording
        // modifier sits outside the glass node, so the layer holds the
        // complete glass output (effected patch + container tint + tinted
        // content), not just the content. That patch is what makes the
        // droplet read as glass at rest instead of a transparent hole:
        // the droplet itself has no blur and no lens until pressed, so it
        // re-displays this layer's already-blurred, tinted patch. The
        // alpha-0 hiding modifier sits outside the recording, so it hides
        // only the visible composite, never the recorded pixels. The row
        // carries the same rubber-band translation as the visible row, so
        // the sampled content stays aligned while dragging.
        GlassSurface(
            backdrop = outerBackdrop,
            modifier = Modifier
                .alpha(0f)
                .fillMaxWidth()
                .height(56.dp)
                .graphicsLayer { translationX = barTranslation }
                .glassBackdrop(tabsBackdrop),
            styleProvider = {
                glassBarStyle(darkTheme).copy(
                    highlight = GlassHighlight(alpha = motion.pressProgress.value),
                    shadow = null,
                )
            },
            shape = RoundedCornerShape(50),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
            ) {
                // Press ripple, recorded into the tab-row layer under the
                // tinted tab content so the droplet samples it.
                DuplicateRippleOverlay(
                    rippleProgress = rippleProgress,
                    motion = motion,
                    tabWidthPx = tabWidthPx,
                    layoutDirection = layoutDirection,
                )
                TabRow(
                    tabs = tabs,
                    showLabels = showLabels,
                    tint = accentColor,
                    itemScale = {
                        // Every tab item grows with press, compensating
                        // for the droplet's constant 0.92 content shrink
                        // so icons inside the droplet still read at
                        // ~1.05x. Read inside the graphicsLayer lambda,
                        // in the draw phase.
                        lerp(1f, 1.05f / 0.92f, motion.pressProgress.value)
                    },
                )
            }
        }

        // The bar itself, growing slightly with the press. The press and
        // rubber-band values are read inside the graphicsLayer lambda, in
        // the draw phase, so animation ticks never recompose the bar. The
        // same growth scale is reported to the layerScale provider, so
        // sampling inverts it and the bar's patch stays aligned while the
        // bar grows.
        val barGrowPx = with(density) { 16.dp.toPx() }
        fun barScale(): Float {
            val press = motion.pressProgress.value
            return 1f + (barGrowPx / widthPx) * press
        }
        GlassSurface(
            backdrop = outerBackdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .graphicsLayer {
                    val scale = barScale()
                    scaleX = scale
                    scaleY = scale
                    translationX = barTranslation
                },
            style = glassBarStyle(darkTheme),
            shape = RoundedCornerShape(50),
            layerScale = {
                val scale = barScale()
                Offset(scale, scale)
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.Center)
                    .padding(horizontal = 4.dp),
            ) {
                TabRow(
                    tabs = tabs,
                    showLabels = showLabels,
                    tint = resolvedContentColor,
                    itemScale = {
                        // Every tab item grows slightly with press. Read
                        // inside the graphicsLayer lambda, in the draw
                        // phase, so the press read costs no recomposition.
                        lerp(1f, 1.05f, motion.pressProgress.value)
                    },
                    selectedIndex = if (showDroplet) selected else -1,
                    onTabSemantics = { index ->
                        highlight(index)
                        if (index != currentSelected) {
                            currentOnSelected(index)
                        }
                        motion.moveTo(index.toFloat(), snapValue = !showDroplet)
                    },
                )
            }
        }

        // The droplet, sliding on the animated index, exactly one tab slot
        // wide. CenterStart alignment: the offset below is measured from
        // the bar's left edge, and the default Center alignment would add
        // half the leftover width. The animated value and the rubber-band
        // offset are read in the layout phase, so drag ticks never
        // recompose the bar. The droplet's own press scale and velocity
        // squash live inside BottomBarDroplet, next to the layer-scale
        // provider that inverts them for sampling.
        BottomBarDroplet(
            motion = motion,
            backdrop = dropletBackdrop,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    IntOffset(
                        (dropletX(motion.value.value) + barTranslation).roundToInt(),
                        0,
                    )
                }
                .size(with(density) { tabWidthPx.toDp() }, 56.dp),
            darkTheme = darkTheme,
            // Read in the draw phase, inside the droplet's own layer.
            alphaProvider = { dropletAlpha },
        )

        // The stop handler runs for a normal end AND for a cancelled
        // gesture: a gesture stolen by a parent still snaps instead of
        // leaving the droplet mid-press. Selection callbacks fire
        // synchronously, before the release visuals run, so tab switches
        // feel immediate.
        fun stopGesture() {
            dragging = false
            scope.launch { rippleProgress.animateTo(0f, rippleSpring) }
            if (motion.didDrag) {
                val snap = motion.value.value.roundToInt().coerceIn(0, count - 1)
                highlight(snap)
                if (snap != currentSelected) {
                    currentOnSelected(snap)
                }
                motion.onRelease(snap.toFloat())
            } else {
                val tapIndex = if (layoutDirection == LayoutDirection.Rtl) {
                    ((widthPx - downX - barPaddingPx) / tabWidthPx).toInt()
                } else {
                    ((downX - barPaddingPx) / tabWidthPx).toInt()
                }.coerceIn(0, count - 1)
                highlight(tapIndex)
                if (tapIndex != currentSelected) {
                    currentOnSelected(tapIndex)
                }
                motion.moveTo(tapIndex.toFloat(), snapValue = !showDroplet)
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(tabWidthPx, count, layoutDirection) {
                    detectGlassDrag(
                        onDragStart = { down ->
                            downX = down.x
                            dragValue = motion.value.value
                            rubberBandPx = 0f
                            dragging = true
                            scope.launch { rippleProgress.animateTo(1f, rippleSpring) }
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
                                rubberBandPx += delta.x
                                dragValue = (dragValue + signed / tabWidthPx)
                                    .coerceIn(0f, (count - 1).toFloat())
                                motion.onDragTo(dragValue)
                                highlight(dragValue.roundToInt().coerceIn(0, count - 1))
                            }
                        },
                        onDragEnd = { stopGesture() },
                        onDragCancel = { stopGesture() },
                    )
                },
        )
    }
}

/**
 * The press ripple drawn inside the duplicate tab row's recording, under
 * the tinted tab content and over the row's glass: a flat white wash at
 * 8% x progress plus a radial glow at 15% x progress, radius 1.5x the
 * smallest dimension, both additive, centered on the active tab slot's
 * center (not the touch point) and scaled by the 0.7 press-glow
 * intensity. The progress and the animated tab index are read inside the
 * draw block, so press ticks redraw the ripple without recomposing the
 * bar; nothing is drawn until the press starts. Because it is content of
 * the duplicate row, the ripple is recorded into the tab-row layer and
 * the droplet samples the brightening through its lens.
 */
@Composable
private fun DuplicateRippleOverlay(
    rippleProgress: Animatable<Float, AnimationVector1D>,
    motion: GlassMotion,
    tabWidthPx: Float,
    layoutDirection: LayoutDirection,
) {
    val rippleShader = remember { RuntimeShader(GlassShaders.PRESS_RIPPLE) }
    val rippleBrush = remember { ShaderBrush(rippleShader) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val progress = rippleProgress.value * 0.7f
                if (progress > 0f) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.08f * progress),
                        blendMode = BlendMode.Plus,
                    )
                    val centerX = if (layoutDirection == LayoutDirection.Ltr) {
                        (motion.value.value + 0.5f) * tabWidthPx
                    } else {
                        size.width - (motion.value.value + 0.5f) * tabWidthPx
                    }
                    val touchPoint = Offset(
                        centerX.coerceIn(0f, size.width),
                        (size.height / 2f).coerceIn(0f, size.height),
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
private fun TabRow(
    tabs: List<GlassTab>,
    showLabels: Boolean,
    tint: Color,
    itemScale: () -> Float,
    selectedIndex: Int = -1,
    onTabSemantics: ((Int) -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (onTabSemantics != null) {
                            Modifier.semantics {
                                role = Role.Tab
                                selected = isSelected
                                onClick {
                                    onTabSemantics(index)
                                    true
                                }
                            }
                        } else {
                            Modifier
                        },
                    )
                    .graphicsLayer {
                        // The whole tab item (icon + label) scales
                        // uniformly. The scale lambda is invoked here, in
                        // the draw phase, so press ticks never recompose
                        // the row.
                        val scale = itemScale()
                        scaleX = scale
                        scaleY = scale
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Image(
                    painter = if (isSelected) tab.selectedIcon ?: tab.icon else tab.icon,
                    contentDescription = tab.label,
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.size(27.dp),
                )
                if (showLabels) {
                    BasicText(
                        text = tab.label,
                        style = TextStyle(
                            color = tint,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * The bottom-bar droplet, on its own so the bar never recomposes on
 * animation ticks. While pressed it samples the tab row shrunk toward
 * 0.92x about its center: without the shrink,
 * the droplet's 78/56 growth magnifies the sampled blue tab icon outward
 * from the droplet center, so the icon looks like it flies off its tab
 * while dragging. The shrink applies to the tab-row layer only, through
 * the wrapped source in the droplet's merged backdrop; the outer backdrop
 * is unscaled. Sampling additionally inverts the droplet's own
 * graphicsLayer scale, so the icon under the droplet stays anchored.
 * Drag velocity squashes the droplet - stretched along X, squashed along
 * Y - through the same formula the slider thumb uses, so hold-and-drag
 * morphs the pill instead of just growing a plain circle. The press
 * recipe and the layer scale are all read in the draw phase, so
 * animation ticks never recompose the droplet.
 */
@Composable
private fun BottomBarDroplet(
    motion: GlassMotion,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
    alphaProvider: () -> Float,
) {
    // The droplet's visible scale, velocity squash included. One function
    // feeds both the graphicsLayer below and the layerScale provider, so
    // the inverse sampling transform always matches the visible transform
    // exactly; both read animation state in the draw phase.
    fun dropletScale(): Offset {
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
        // The fade alpha and the press scale share ONE graphicsLayer.
        // A separate outer alpha layer allocates a layout-sized buffer:
        // while fading in, the scaled droplet is drawn into that buffer
        // and clipped to its rectangular bounds, so the droplet reads as
        // a box until the fade finishes. With one layer, the buffer holds
        // the unscaled capsule and the scale applies at composite time,
        // past any finite bounds.
        modifier = modifier.graphicsLayer {
            val scale = dropletScale()
            scaleX = scale.x
            scaleY = scale.y
            alpha = alphaProvider()
        },
        styleProvider = {
            // Evaluated in the draw phase, so press ticks invalidate the
            // draw only instead of recomposing the droplet.
            val press = motion.pressProgress.value
            // Two fills composited: a theme veil (black 10% in light,
            // white 10% in dark) fading out with the press, with a black
            // 3% fill growing in over it. The composite keeps the
            // pressed fill black in both themes.
            val veilColor = if (darkTheme) Color.White else Color.Black
            val surfaceFill = Color.Black.copy(alpha = 0.03f * press)
                .compositeOver(veilColor.copy(alpha = 0.10f * (1f - press)))
            GlassStyle(
                blurRadius = 0.dp,
                refractionHeight = (14f * press).dp,
                refractionAmount = (14f * press).dp,
                saturation = 1f,
                depth = true,
                surfaceColor = surfaceFill,
                chromaticAberration = 0.45f,
                highlight = GlassHighlight(alpha = press),
                shadow = GlassShadow(alpha = press),
                innerShadow = GlassInnerShadow(
                    radius = (8f * lerp(0.75f, 1f, press)).dp,
                    offset = DpOffset(0.dp, (8f * lerp(0.75f, 1f, press)).dp),
                    alpha = lerp(0.5f, 1f, press),
                ),
            )
        },
        shape = RoundedCornerShape(50),
        layerScale = { dropletScale() },
    ) {
        // Vertical glow: white fading 30% to 6%, scaled by the press,
        // drawn with additive blending so it lights the patch instead of
        // washing over it. The press is read inside the graphicsLayer
        // lambda, in the draw phase, so the glow never recomposes the
        // droplet. The glow is drawn as a capsule (half-height corner
        // radius) instead of a rectangle: a rectangle here shows square
        // corners past the droplet shape whenever the press is up.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = motion.pressProgress.value * 0.7f }
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.06f),
                            ),
                        ),
                        cornerRadius = CornerRadius(size.height / 2f),
                        blendMode = BlendMode.Plus,
                    )
                },
        )
    }
}
