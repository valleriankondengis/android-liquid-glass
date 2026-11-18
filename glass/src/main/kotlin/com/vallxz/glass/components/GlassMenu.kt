// GlassMenu: a popup menu that grows out of a droplet anchored to a
// row edge, with selectable rows and a dimmed scrim.

package com.vallxz.glass.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.engine.GlassBackdrop
import kotlin.math.roundToInt

/** One row in a [GlassMenu]. */
data class GlassMenuItem(
    val label: String,
    val icon: Painter? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Records the anchor's window bounds into [onBounds] on every layout, for
 * [GlassMenu] placement. Apply it to the composable the menu opens from.
 * The menu does nothing until the bounds are non-zero.
 */
fun Modifier.glassMenuAnchor(onBounds: (IntRect) -> Unit): Modifier =
    this.onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInWindow()
        onBounds(
            IntRect(
                rect.left.roundToInt(),
                rect.top.roundToInt(),
                rect.right.roundToInt(),
                rect.bottom.roundToInt(),
            ),
        )
    }

/**
 * A glass popup menu that grows out of a 30 dp circular "seed" droplet at
 * the anchor's end edge, vertically centered on the anchor, then morphs
 * into the full rounded panel (spring damping 0.72 / stiffness 520, same
 * for open and close). While small the panel is biased toward a square so
 * the seed stays circular; the corner radius lerps from 15 dp to 24 dp,
 * capped at half the smaller side.
 *
 * Placement: below the anchor with a 4 dp gap, flipping above when it
 * would overflow the bottom; horizontally aligned to the anchor's end
 * edge (start edge in RTL) and clamped to 16 dp screen margins. The width
 * follows the anchor width clamped to 180-280 dp (or [menuWidth] when
 * given), also clamped to the screen width minus margins; the height
 * follows the content. The scrim is black at 12% alpha; tapping outside
 * or pressing back dismisses. Rows become clickable once the popup alpha
 * passes 0.5, and nothing inside is clickable while closing.
 *
 * Rows are 44 dp tall with 16 dp horizontal padding, single-line ellipsis
 * text at 17 sp, an optional leading icon with a 12 dp gap, and 0.5 dp
 * hairline dividers between rows. A selected row shows its label in accent
 * blue with a trailing checkmark; a disabled row renders at 30% alpha.
 * Tapping a row runs its action and dismisses the menu.
 *
 * Unlike a dialog-window menu, this menu lives in the caller's
 * composition: place it where it can fill the screen (at the root of the
 * screen composition) and pass the anchor bounds captured with
 * [glassMenuAnchor]. The backdrop is the explicit argument, falling
 * back to the nearest `LocalGlassBackdrop` only when null.
 */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: IntRect,
    items: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    menuWidth: Dp? = null,
    showDividers: Boolean = true,
) {
    var mounted by remember { mutableStateOf(expanded) }
    val morph by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        finishedListener = { value ->
            if (!expanded && value == 0f) mounted = false
        },
        label = "glassMenuMorph",
    )
    LaunchedEffect(expanded) {
        if (expanded) mounted = true
    }
    if (!mounted || items.isEmpty()) return
    // While closing, stop rendering once the morph passes 0.2, where the
    // popup alpha reaches zero; the finished listener unmounts at 0.
    if (!expanded && morph <= 0.2f) return
    if (anchorBounds.width == 0 || anchorBounds.height == 0) return

    val darkTheme = isSystemInDarkTheme()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val labelColor = glassLabelColor(darkTheme)
    val accentColor = glassMenuAccentColor(darkTheme)
    val dividerColor = if (darkTheme) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color.Black.copy(alpha = 0.12f)
    }

    // Popup alpha: ramps in over the first 35% of the morph while opening,
    // and fades over morph 0.2 to 1.0 while closing.
    val popupAlpha = if (expanded) {
        (morph / 0.35f).coerceIn(0f, 1f)
    } else {
        ((morph - 0.2f) / 0.8f).coerceIn(0f, 1f)
    }
    val rowsClickable = expanded && popupAlpha > 0.5f

    BackHandler(enabled = expanded) { onDismissRequest() }

    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.boundsInWindow().topLeft },
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()
        val marginPx = with(density) { 16.dp.toPx() }
        val gapPx = with(density) { 4.dp.toPx() }

        // Anchor bounds in this overlay's coordinates.
        val anchorLeft = anchorBounds.left - overlayOrigin.x
        val anchorTop = anchorBounds.top - overlayOrigin.y
        val anchorRight = anchorBounds.right - overlayOrigin.x
        val anchorBottom = anchorBounds.bottom - overlayOrigin.y
        val anchorWidthPx = anchorRight - anchorLeft
        val anchorCenterY = (anchorTop + anchorBottom) / 2f

        val fullWidthPx = with(density) {
            val wanted = menuWidth?.toPx()
                ?: anchorWidthPx.coerceIn(180.dp.toPx(), 280.dp.toPx())
            wanted.coerceAtMost(screenWidthPx - marginPx * 2f)
        }
        val dividerCount = if (showDividers) items.size - 1 else 0
        val fullHeightPx = with(density) {
            items.size * 44.dp.toPx() + dividerCount * 0.5.dp.toPx()
        }.coerceAtMost(screenHeightPx - marginPx * 2f)

        // Final placement, clamped to the screen margins: opening above
        // a high anchor must not push the panel off the top edge.
        val finalTop = if (anchorBottom + gapPx + fullHeightPx <= screenHeightPx - marginPx) {
            anchorBottom + gapPx
        } else {
            (anchorTop - gapPx - fullHeightPx).coerceAtLeast(marginPx)
        }
        val finalLeft = if (layoutDirection == LayoutDirection.Ltr) {
            (anchorRight - fullWidthPx).coerceIn(marginPx, screenWidthPx - marginPx - fullWidthPx)
        } else {
            anchorLeft.coerceIn(marginPx, screenWidthPx - marginPx - fullWidthPx)
        }

        // Morph: size and position lerp from the 30 dp seed, biased toward
        // a square while small; corner radius lerps 15 dp to 24 dp. The
        // seed's edge sits flush with the anchor's end edge, and the
        // square bias pulls both sides toward the larger side, so the
        // panel grows square instead of shrinking toward the average.
        val seedPx = with(density) { 30.dp.toPx() }
        val seedLeft = if (layoutDirection == LayoutDirection.Ltr) {
            anchorRight - seedPx
        } else {
            anchorLeft
        }
        val seedTop = anchorCenterY - seedPx / 2f
        val squareBias = (1f - morph) * (1f - morph)
        var panelWidth = lerp(seedPx, fullWidthPx, morph)
        var panelHeight = lerp(seedPx, fullHeightPx, morph)
        val maxSide = maxOf(panelWidth, panelHeight)
        panelWidth = lerp(panelWidth, maxSide, squareBias)
        panelHeight = lerp(panelHeight, maxSide, squareBias)
        val panelLeft = lerp(seedLeft, finalLeft, morph)
        val panelTop = lerp(seedTop, finalTop, morph)
        val cornerRadiusPx = with(density) {
            lerp(15.dp.toPx(), 24.dp.toPx(), morph)
        }.coerceAtMost(minOf(panelWidth, panelHeight) / 2f)

        // Scrim.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f * popupAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismissRequest() },
        )

        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .offset { IntOffset(panelLeft.roundToInt(), panelTop.roundToInt()) }
                .size(
                    width = with(density) { panelWidth.toDp() },
                    height = with(density) { panelHeight.toDp() },
                )
                .graphicsLayer(alpha = popupAlpha),
            style = GlassStyle(
                blurRadius = 12.dp,
                refractionHeight = 16.dp,
                refractionAmount = 32.dp,
                saturation = 1.5f,
                depth = false,
                surfaceColor = if (darkTheme) {
                    Color(0xFF1C1C1E).copy(alpha = 0.82f)
                } else {
                    Color.White.copy(alpha = 0.58f)
                },
            ),
            shape = RoundedCornerShape(with(density) { cornerRadiusPx.toDp() }),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                items.forEachIndexed { index, item ->
                    if (index > 0 && showDividers) {
                        GlassHairline(dividerColor)
                    }
                    GlassMenuRow(
                        item = item,
                        labelColor = labelColor,
                        accentColor = accentColor,
                        clickable = rowsClickable,
                        onClick = {
                            item.onClick()
                            onDismissRequest()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassMenuRow(
    item: GlassMenuItem,
    labelColor: Color,
    accentColor: Color,
    clickable: Boolean,
    onClick: () -> Unit,
) {
    val textColor = when {
        !item.enabled -> labelColor.copy(alpha = 0.3f)
        item.selected -> accentColor
        else -> labelColor
    }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .height(44.dp)
            .padding(horizontal = 16.dp)
            .semantics {
                role = Role.Button
                contentDescription = item.label
            }
            .then(
                if (clickable && item.enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { onClick() }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.icon != null) {
            Image(
                painter = item.icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(textColor),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        BasicText(
            text = item.label,
            style = TextStyle(color = textColor, fontSize = 17.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.selected) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val strokeWidth = 2.dp.toPx()
                val w = size.width
                val h = size.height
                drawLine(
                    color = accentColor,
                    start = Offset(w * 0.2f, h * 0.55f),
                    end = Offset(w * 0.4f, h * 0.75f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = accentColor,
                    start = Offset(w * 0.4f, h * 0.75f),
                    end = Offset(w * 0.85f, h * 0.25f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
