// GlassSelector: a horizontal option selector and a ruler selector,
// both with a refracting droplet and snap-to-value scrolling.

package com.vallxz.glass.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.decoration.GlassInnerShadow
import com.vallxz.glass.engine.GlassBackdrop
import com.vallxz.glass.engine.LocalGlassBackdrop
import com.vallxz.glass.engine.glassBackdrop
import com.vallxz.glass.engine.rememberGlassBackdrop
import com.vallxz.glass.engine.rememberGlassMergedBackdrop
import com.vallxz.glass.interaction.detectGlassDrag
import com.vallxz.glass.interaction.rememberGlassHaptics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * A horizontal option selector in the style of a camera-mode picker: a
 * fixed 70 dp strip where labels scroll horizontally underneath a fixed,
 * centered glass droplet. Labels are packed at their measured widths
 * (semibold 15 sp plus 14 dp padding per side) with 6 dp gaps, and the
 * strip is centered with edge spacers of half the viewport minus half
 * the end label, so the first and last labels sit exactly centered with
 * no overscroll into empty space. The scroll position rides the platform
 * scroll state (fling included); releasing snaps to the nearest label
 * (spring damping 0.82 / stiffness 580), and tapping a label
 * snap-selects it.
 *
 * Label alpha lerps 0.38 to 1 and scale 0.9 to 1.0 by distance from the
 * center, normalized by half the viewport width; the focused label goes
 * bold above 88% focus. The droplet width follows the focused label
 * while scrolling and the locked label otherwise (label width + 20 dp,
 * spring damping 0.56 / stiffness 280) with a volume-preserving vertical
 * squash clamped to 0.96-1.04. A haptic tick fires once per completed
 * selection change.
 *
 * The droplet is 55 dp tall, has no blur, a 25/16 dp lens, dispersion
 * (0.3 by default), no drop shadow, an 8 dp inner shadow at full alpha,
 * and a black 3% surface. It samples a merged backdrop - the outer
 * backdrop (the nearest `LocalGlassBackdrop` winning over the explicit
 * argument, as the underlay) plus the labels' own recorded layer - so
 * the focused label refracts through the lens.
 */
@Composable
fun GlassHorizontalSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    contentColor: Color = Color.Unspecified,
    chromaticAberration: Float = 0.3f,
) {
    require(options.isNotEmpty()) { "GlassHorizontalSelector needs at least one option" }
    val count = options.size
    val selected = selectedIndex.coerceIn(options.indices)
    val darkTheme = isSystemInDarkTheme()
    val labelColor = if (contentColor == Color.Unspecified) {
        glassLabelColor(darkTheme)
    } else {
        contentColor
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberGlassHaptics(haptic)
    val currentSelected by rememberUpdatedState(selected)
    // The selection callback, wrapped with its haptic tick. Read through
    // updated state so the long-lived scroll collectors below always fire
    // the latest callback.
    val onSelectedState = rememberUpdatedState<(Int) -> Unit> { index ->
        haptics.tick()
        onSelected(index)
    }

    val labelsBackdrop = rememberGlassBackdrop()
    val outerBackdrop = LocalGlassBackdrop.current ?: backdrop
    val mergedBackdrop = if (outerBackdrop != null) {
        rememberGlassMergedBackdrop(outerBackdrop, labelsBackdrop)
    } else {
        null
    }
    val dropletBackdrop = mergedBackdrop ?: labelsBackdrop

    // Label widths, measured once each at a fixed semibold weight. The
    // weight must not flip with focus: measuring the rendered (bold /
    // medium) text would shift every item center whenever focus changes.
    val textMeasurer = rememberTextMeasurer()
    val measureStyle = TextStyle(
        color = labelColor,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val itemPaddingPx = with(density) { 14.dp.toPx() }
    val itemWidthsPx = remember(options, measureStyle) {
        options.map { label ->
            textMeasurer.measure(label, style = measureStyle, maxLines = 1)
                .size.width.toFloat() + itemPaddingPx * 2f
        }.toFloatArray()
    }
    val spacingPx = with(density) { 6.dp.toPx() }
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val viewportWidthPx = constraints.maxWidth.toFloat()
        val viewportCenterX = viewportWidthPx / 2f
        val startSpacerPx = viewportWidthPx / 2f - itemWidthsPx.first() / 2f
        val endSpacerPx = viewportWidthPx / 2f - itemWidthsPx.last() / 2f

        // Snap offsets: the scroll position that centers each label. The
        // first offset is 0 and the last equals the scroll range exactly,
        // so the end labels center with no overscroll past them.
        val snapOffsetsPx = remember(itemWidthsPx, spacingPx, startSpacerPx, viewportWidthPx) {
            itemWidthsPx.indices.map { index ->
                val itemCenter =
                    startSpacerPx +
                        itemWidthsPx.take(index).sum() +
                        spacingPx * index +
                        itemWidthsPx[index] / 2f
                (itemCenter - viewportWidthPx / 2f).roundToInt().coerceAtLeast(0)
            }
        }

        fun indexForScroll(scrollPx: Int): Int {
            if (snapOffsetsPx.isEmpty()) return 0
            return snapOffsetsPx.indices.minByOrNull { abs(snapOffsetsPx[it] - scrollPx) } ?: 0
        }

        fun itemCenterInContent(index: Int): Float {
            return startSpacerPx +
                itemWidthsPx.take(index).sum() +
                spacingPx * index +
                itemWidthsPx[index] / 2f
        }

        var focusedIndex by remember(count) { mutableIntStateOf(selected) }
        var lockedIndex by remember(count) { mutableIntStateOf(selected) }
        var isScrolling by remember { mutableIntStateOf(0) }

        suspend fun snapToIndex(index: Int) {
            val targetOffset = snapOffsetsPx.getOrElse(index) { 0 }
            val delta = targetOffset - scrollState.value
            if (abs(delta) > 0.5f) {
                scrollState.animateScrollBy(
                    delta.toFloat(),
                    spring(dampingRatio = 0.82f, stiffness = 580f),
                )
            }
            lockedIndex = index
            focusedIndex = index
            if (index != currentSelected) {
                onSelectedState.value(index)
            }
        }

        // External selection changes snap the strip, and the first run
        // animates in from the strip start when the selection is not the
        // first label.
        LaunchedEffect(selected, snapOffsetsPx) {
            if (isScrolling == 0) {
                snapToIndex(selected)
            }
        }

        // When a scroll (drag or fling) ends, snap to the nearest label.
        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { inProgress ->
                    if (inProgress) {
                        isScrolling = 1
                    } else {
                        scope.launch {
                            snapToIndex(indexForScroll(scrollState.value))
                            isScrolling = 0
                        }
                    }
                }
        }

        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.value }
                .collect { scrollPx ->
                    focusedIndex = indexForScroll(scrollPx)
                }
        }

        val dropletHorizontalInsetPx = with(density) { 10.dp.toPx() } +
            with(density) { 5.dp.toPx() } * 2f
        val dropletWidthIndex = if (isScrolling == 1) focusedIndex else lockedIndex
        val targetDropletWidthPx = itemWidthsPx[dropletWidthIndex] + dropletHorizontalInsetPx
        val dropletWidthAnim = remember(itemWidthsPx, dropletHorizontalInsetPx) {
            Animatable(targetDropletWidthPx)
        }
        LaunchedEffect(targetDropletWidthPx) {
            dropletWidthAnim.animateTo(
                targetDropletWidthPx,
                spring(dampingRatio = 0.56f, stiffness = 280f),
            )
        }
        val dropletWidthPx = dropletWidthAnim.value
        val dropletSquashY =
            (targetDropletWidthPx / dropletWidthAnim.value).coerceIn(0.96f, 1.04f)

        @Composable
        fun SelectorItemsRow(
            interactive: Boolean,
            rowModifier: Modifier,
        ) {
            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(with(density) { startSpacerPx.toDp() }))
                repeat(count) { index ->
                    if (index > 0) {
                        Spacer(Modifier.width(with(density) { spacingPx.toDp() }))
                    }
                    val itemCenterOnScreen =
                        itemCenterInContent(index) - scrollState.value.toFloat()
                    val distanceFromCenter =
                        abs(itemCenterOnScreen - viewportCenterX) / (viewportWidthPx / 2f)
                    val focus = 1f - distanceFromCenter.coerceIn(0f, 1f)
                    val labelScale = lerp(0.9f, 1f, focus)
                    Box(
                        modifier = Modifier
                            .width(with(density) { itemWidthsPx[index].toDp() })
                            .then(
                                if (interactive && enabled) {
                                    Modifier.selectable(
                                        selected = index == selected,
                                        interactionSource = null,
                                        indication = null,
                                        role = Role.Tab,
                                        onClick = { scope.launch { snapToIndex(index) } },
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .graphicsLayer {
                                alpha = lerp(0.38f, 1f, focus)
                                scaleX = labelScale
                                scaleY = labelScale
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = options[index],
                            style = TextStyle(
                                color = labelColor,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                fontWeight = if (focus > 0.88f) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                },
                            ),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(with(density) { endSpacerPx.toDp() }))
            }
        }

        // Labels sit left-to-right regardless of locale, and the droplet
        // and scroll math follow the same coordinates.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.matchParentSize()) {
                // Invisible copy of the label row, recorded for the
                // droplet to sample. It scrolls from the same scroll
                // state with input disabled, mirroring the visible row.
                SelectorItemsRow(
                    interactive = false,
                    rowModifier = Modifier
                        .alpha(0f)
                        .fillMaxWidth()
                        .glassBackdrop(labelsBackdrop)
                        .horizontalScroll(scrollState, enabled = false)
                        .align(Alignment.Center),
                )
                SelectorItemsRow(
                    interactive = true,
                    rowModifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState, enabled = enabled)
                        .align(Alignment.Center),
                )
                // The droplet, fixed at the center. Its vertical squash
                // is a plain layer transform with no sampling inverse.
                GlassSurface(
                    backdrop = dropletBackdrop,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(with(density) { dropletWidthPx.toDp() })
                        .height(55.dp)
                        .graphicsLayer { scaleY = dropletSquashY },
                    style = GlassStyle(
                        blurRadius = 0.dp,
                        refractionHeight = 25.dp,
                        refractionAmount = 16.dp,
                        saturation = 1.5f,
                        depth = false,
                        surfaceColor = Color.Black.copy(alpha = 0.03f),
                        chromaticAberration = chromaticAberration,
                        shadow = null,
                        innerShadow = GlassInnerShadow(
                            radius = 8.dp,
                            offset = DpOffset(0.dp, 8.dp),
                            alpha = 1f,
                        ),
                    ),
                    shape = RoundedCornerShape(50),
                ) { }
            }
        }
    }
}


/**
 * A ruler selector on the same 70 dp / 55 dp chassis as
 * [GlassHorizontalSelector]: a scrolling tick strip with a glass droplet
 * showing the selected value. Ticks repeat every 10 dp (minor 14 dp tall,
 * major 24 dp tall every 5th, 1.5 dp rounded stroke), fading from 22% to
 * full alpha toward the center. The value label renders inside the
 * droplet in tabular semibold numerals. The visible strip hides every
 * tick under the droplet, while the recorded strip the droplet samples
 * hides ticks only behind the value label, so ticks continue under the
 * lens and refract and disperse through it. The droplet width fits the
 * widest value label plus 50 dp and padding.
 *
 * The value changes continuously while scrolling (a haptic tick per tick)
 * and snaps on release (spring damping 0.82 / stiffness 580). The droplet
 * has no blur, a 25/16 dp lens, dispersion (0.3 by default), an 8 dp
 * inner shadow at full alpha, and a black 3% surface, and samples the
 * merged backdrop of the outer backdrop plus the tick strip's own
 * recorded layer. The backdrop resolves like [GlassSurface] (explicit
 * argument, else the nearest `LocalGlassBackdrop`).
 */
@Composable
fun GlassRulerSelector(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    valueRange: IntRange = 0..100,
    step: Int = 1,
    unit: String = "",
    enabled: Boolean = true,
    haptic: Boolean = true,
    contentColor: Color = Color.Unspecified,
    chromaticAberration: Float = 0.3f,
) {
    require(step > 0) { "GlassRulerSelector step must be positive" }
    val darkTheme = isSystemInDarkTheme()
    val labelColor = if (contentColor == Color.Unspecified) {
        glassLabelColor(darkTheme)
    } else {
        contentColor
    }
    val density = LocalDensity.current
    val haptics = rememberGlassHaptics(haptic)
    val scope = rememberCoroutineScope()
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    val ticksBackdrop = rememberGlassBackdrop()
    val outerBackdrop = LocalGlassBackdrop.current ?: backdrop
    val mergedBackdrop = if (outerBackdrop != null) {
        rememberGlassMergedBackdrop(outerBackdrop, ticksBackdrop)
    } else {
        null
    }
    val dropletBackdrop = mergedBackdrop ?: ticksBackdrop

    val tickSpacingPx = with(density) { 10.dp.toPx() }
    val tickCount = (valueRange.last - valueRange.first) / step
    fun offsetOf(v: Int): Float =
        ((v - valueRange.first).toFloat() / step).roundToInt() * tickSpacingPx
    fun valueAt(offsetPx: Float): Int {
        val ticks = (offsetPx / tickSpacingPx).roundToInt().coerceIn(0, tickCount)
        return valueRange.first + ticks * step
    }

    val offsetAnim = remember { Animatable(offsetOf(value), Float.VectorConverter) }
    var dragOffset by remember { mutableStateOf<Float?>(null) }
    var snapJob by remember { mutableStateOf<Job?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var lastReported by remember { mutableIntStateOf(value) }

    LaunchedEffect(value) {
        if (!dragging && valueAt(offsetAnim.value) != value) {
            snapJob?.cancel()
            snapJob = scope.launch {
                offsetAnim.animateTo(
                    offsetOf(value),
                    spring(dampingRatio = 0.82f, stiffness = 580f),
                )
            }
        }
    }

    // Widest value label over every reachable value, measured
    // synchronously, sizes the droplet. Measuring all values (not just
    // the ends) keeps the droplet wide enough when a middle value's
    // label is the widest, and the width is correct on the first frame.
    val measureStyle = TextStyle(
        color = labelColor,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
    )
    val textMeasurer = rememberTextMeasurer()
    val widestLabelPx = remember(valueRange, step, unit, measureStyle) {
        var widest = 0
        for (v in valueRange.first..valueRange.last step step) {
            val width = textMeasurer.measure("$v$unit", style = measureStyle, maxLines = 1)
                .size.width
            if (width > widest) widest = width
        }
        widest
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart,
    ) {

        val viewportWidthPx = constraints.maxWidth.toFloat()
        val viewportCenterPx = viewportWidthPx / 2f
        // The displayed value changes only when the scroll crosses a tick
        // boundary; derivedStateOf keeps the droplet label (and the
        // reporting SideEffect below) from recomposing on every animation
        // tick, while the tick strip reads the live offset in its draw
        // block.
        val currentValue by remember(tickSpacingPx, tickCount) {
            derivedStateOf { valueAt(dragOffset ?: offsetAnim.value) }
        }
        val dropletWidthPx = widestLabelPx + with(density) { 60.dp.toPx() }

        SideEffect {
            if (dragging && currentValue != lastReported) {
                lastReported = currentValue
                if (haptic) haptics.scrub()
                currentOnValueChange(currentValue)
            }
        }

        // One tick-drawing pass, shared by the recorded strip and the
        // visible strip. The live scroll offset is read here in the draw
        // phase, so drag and snap ticks redraw the ticks without
        // recomposing the strip. [hiddenHalfWidthPx] is the half-width
        // around the viewport center where ticks are skipped: narrow for
        // the recorded strip (behind the value label only), wide for the
        // visible strip (under the whole droplet).
        fun DrawScope.drawTicks(hiddenHalfWidthPx: Float) {
            val offsetPx = dragOffset ?: offsetAnim.value
            val strokeWidth = 1.5.dp.toPx()
            val minorHeight = 14.dp.toPx()
            val majorHeight = 24.dp.toPx()
            val centerY = size.height / 2f
            val firstTick = ((offsetPx - viewportCenterPx) / tickSpacingPx).toInt() - 1
            val lastTick = ((offsetPx + viewportCenterPx) / tickSpacingPx).toInt() + 1
            for (tick in firstTick..lastTick) {
                if (tick < 0 || tick > tickCount) continue
                val x = viewportCenterPx + tick * tickSpacingPx - offsetPx
                if (abs(x - viewportCenterPx) < hiddenHalfWidthPx) continue
                val distance = (abs(x - viewportCenterPx) / viewportCenterPx)
                    .coerceIn(0f, 1f)
                val alpha = lerp(1f, 0.22f, distance)
                val halfHeight = (if (tick % 5 == 0) majorHeight else minorHeight) / 2f
                drawLine(
                    // Ticks never exceed 55% opacity, scaled by the
                    // distance falloff toward the center.
                    color = labelColor.copy(alpha = 0.55f * alpha),
                    start = Offset(x, centerY - halfHeight),
                    end = Offset(x, centerY + halfHeight),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }

        // The recorded strip: invisible, ticks hidden only behind the
        // value label (half the widest label plus a 4 dp gap), so the
        // droplet samples ticks under its whole lens. The alpha hiding
        // sits outside the recording, so it hides only the visible
        // composite, never the recorded pixels.
        val sampleHiddenHalfWidthPx = widestLabelPx / 2f + with(density) { 4.dp.toPx() }
        Box(
            modifier = Modifier
                .alpha(0f)
                .fillMaxSize()
                .glassBackdrop(ticksBackdrop),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTicks(sampleHiddenHalfWidthPx)
            }
        }

        // The visible strip: ticks hidden under the whole droplet.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawTicks(dropletWidthPx / 2f)
        }

        // The droplet, fixed at the center, showing the current value.
        GlassSurface(
            backdrop = dropletBackdrop,
            modifier = Modifier
                .align(Alignment.Center)
                .size(
                    width = with(density) { dropletWidthPx.toDp() },
                    height = 55.dp,
                ),
            style = GlassStyle(
                blurRadius = 0.dp,
                refractionHeight = 25.dp,
                refractionAmount = 16.dp,
                saturation = 1.5f,
                depth = false,
                surfaceColor = Color.Black.copy(alpha = 0.03f),
                chromaticAberration = chromaticAberration,
                shadow = null,
                innerShadow = GlassInnerShadow(
                    radius = 8.dp,
                    offset = DpOffset(0.dp, 8.dp),
                    alpha = 1f,
                ),
            ),
            shape = RoundedCornerShape(50),
        ) {
            BasicText(
                text = "$currentValue$unit",
                style = measureStyle,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(tickSpacingPx, tickCount) {
                        detectGlassDrag(
                            onDragStart = {
                                snapJob?.cancel()
                                dragOffset = offsetAnim.value
                                dragging = true
                                lastReported = valueAt(offsetAnim.value)
                            },
                            onDrag = { delta ->
                                if (delta.x != 0f) {
                                    val current = dragOffset ?: offsetAnim.value
                                    dragOffset = (current - delta.x)
                                        .coerceIn(0f, tickCount * tickSpacingPx)
                                }
                            },
                            onDragEnd = {
                                dragging = false
                                val fromPx = dragOffset ?: offsetAnim.value
                                dragOffset = null
                                val target = offsetOf(valueAt(fromPx))
                                snapJob?.cancel()
                                snapJob = scope.launch {
                                    offsetAnim.snapTo(fromPx)
                                    offsetAnim.animateTo(
                                        target,
                                        spring(dampingRatio = 0.82f, stiffness = 580f),
                                    )
                                }
                            },
                            onDragCancel = {
                                dragging = false
                                val fromPx = dragOffset ?: offsetAnim.value
                                dragOffset = null
                                val target = offsetOf(valueAt(fromPx))
                                snapJob?.cancel()
                                snapJob = scope.launch {
                                    offsetAnim.snapTo(fromPx)
                                    offsetAnim.animateTo(
                                        target,
                                        spring(dampingRatio = 0.82f, stiffness = 580f),
                                    )
                                }
                            },
                        )
                    },
            )
        }
    }
}
