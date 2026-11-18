// GlassBottomSheet: a bottom sheet on the fixed modal glass material,
// with a grabber, slide-up entrance, and squish.

package com.vallxz.glass.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.engine.GlassBackdrop
import kotlin.math.roundToInt

/**
 * A bottom sheet: a full-width glass panel with 34 dp top corners and
 * square bottom corners, using the fixed modal material (12 dp blur,
 * 16/32 dp lens, vibrancy, heavy surface tint).
 *
 * Layout, top to bottom: a centered 36 x 5 dp grabber pill (10 dp top /
 * 8 dp bottom padding), a centered 17 sp semibold title, a 12 dp gap, the
 * caller [content], and 24 dp of bottom padding.
 *
 * The sheet slides up from fully below the screen with a spring (enter
 * damping 0.82, medium-low stiffness; exit damping 0.9, medium stiffness),
 * plus a vertical squish from scaleY 0.88 (spring damping 0.54 /
 * stiffness 420 on enter, 160 ms tween on exit) pivoted at the bottom
 * center, and a 180 ms alpha fade. There is no drag-to-dismiss gesture;
 * taps inside the sheet are swallowed, and dismissal is by scrim tap,
 * back press, or the caller's own rows.
 *
 * The sheet must be placed where it can fill the screen (at the root of
 * the screen composition): it draws its own scrim overlay. The backdrop
 * is the explicit argument, falling back to the nearest
 * `LocalGlassBackdrop` only when null.
 */
@Composable
fun GlassBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val labelColor = glassLabelColor(darkTheme)

    val slide by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = if (visible) {
            spring(dampingRatio = 0.82f, stiffness = 400f)
        } else {
            spring(dampingRatio = 0.9f, stiffness = 700f)
        },
        label = "glassSheetSlide",
    )
    val squishY by animateFloatAsState(
        targetValue = if (visible) 1f else 0.88f,
        animationSpec = if (visible) spring(dampingRatio = 0.54f, stiffness = 420f) else tween(160),
        label = "glassSheetSquish",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(180),
        label = "glassSheetAlpha",
    )
    var sheetHeightPx by remember { mutableIntStateOf(0) }

    GlassModalOverlay(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { sheetHeightPx = it.height }
                .offset { IntOffset(0, (slide * sheetHeightPx).roundToInt()) }
                .graphicsLayer(
                    scaleY = squishY,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    alpha = contentAlpha,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* Swallow taps so they never reach the scrim. */ },
            style = glassModalStyle(darkTheme),
            shape = RoundedCornerShape(
                topStart = 34.dp,
                topEnd = 34.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp,
            ),
            // The vertical squish, reported so sampling inverts it and the
            // backdrop stays aligned while the sheet squishes in. The
            // inverse holds for the bottom-center pivot too.
            layerScale = { Offset(1f, squishY) },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 8.dp)
                        .size(width = 36.dp, height = 5.dp)
                        .background(
                            color = labelColor.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(50),
                        )
                        .align(Alignment.CenterHorizontally),
                )
                BasicText(
                    text = title,
                    style = TextStyle(
                        color = labelColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                content()
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
