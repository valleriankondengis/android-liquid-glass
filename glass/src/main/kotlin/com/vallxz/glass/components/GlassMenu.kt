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
import androidx.compose.foundation.layout.fillMaxWidth
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
 * the seed stays circular; the corner radius lerps from 15 dp to 32 dp,
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
 * The panel has 10 dp top and bottom padding and 16 dp horizontal
 * padding. Rows are 40 dp tall with 6 dp start / 8 dp end padding,
 * single-line ellipsis text at 17 sp with 20 sp line height and -0.43 sp
 * tracking, and an optional leading icon with an 8 dp gap. Dividers are
 * 1 dp lines in 21 dp frames, inset 8 dp on each side. When any row is
 * selected, every row reserves a leading 24 dp checkmark slot; a
 * selected row shows a checkmark there and keeps the primary label
 * color. A disabled row renders in the tertiary label color. Tapping a
 * row runs its action and dismisses the menu.
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
    // Disabled rows use the tertiary label color. The divider is the
    // solid separator gray in light mode; in dark mode it is white at
    // 15%, because the solid value reads too dark on the glass panel.
    val disabledColor = if (darkTheme) Color(0xFF404040) else Color(0xFFBFBFBF)
    val dividerColor = if (darkTheme) Color.White.copy(alpha = 0.15f) else Color(0xFFE6E6E6)
    val hasSelection = items.any { it.selected }

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
            items.size * 40.dp.toPx() + dividerCount * 21.dp.toPx() + 20.dp.toPx()
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
        // a square while small; corner radius lerps 15 dp to 32 dp. The
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
            lerp(15.dp.toPx(), 32.dp.toPx(), morph)
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
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                items.forEachIndexed { index, item ->
                    if (index > 0 && showDividers) {
                        GlassMenuSeparator(dividerColor)
                    }
                    GlassMenuRow(
                        item = item,
                        labelColor = labelColor,
                        disabledColor = disabledColor,
                        hasSelection = hasSelection,
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

/** A 1 dp separator line centered in a 21 dp frame, inset 8 dp on each side. */
@Composable
private fun GlassMenuSeparator(color: Color) {
    Box(
        modifier = Modifier.height(21.dp).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).background(color),
        )
    }
}

@Composable
private fun GlassMenuRow(
    item: GlassMenuItem,
    labelColor: Color,
    disabledColor: Color,
    hasSelection: Boolean,
    clickable: Boolean,
    onClick: () -> Unit,
) {
    val textColor = if (item.enabled) labelColor else disabledColor
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .height(40.dp)
            .padding(start = 6.dp, end = 8.dp)
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
        if (hasSelection) {
            // The slot is always reserved so labels stay aligned; the
            // checkmark itself draws only on the selected row.
            Canvas(modifier = Modifier.size(width = 24.dp, height = 22.dp)) {
                if (item.selected) {
                    val strokeWidth = 2.dp.toPx()
                    val w = size.width
                    val h = size.height
                    drawLine(
                        color = textColor,
                        start = Offset(w * 0.2f, h * 0.55f),
                        end = Offset(w * 0.4f, h * 0.75f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = textColor,
                        start = Offset(w * 0.4f, h * 0.75f),
                        end = Offset(w * 0.85f, h * 0.25f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (item.icon != null) {
            Image(
                painter = item.icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(textColor),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        BasicText(
            text = item.label,
            style = TextStyle(
                color = textColor,
                fontSize = 17.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.43).sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
