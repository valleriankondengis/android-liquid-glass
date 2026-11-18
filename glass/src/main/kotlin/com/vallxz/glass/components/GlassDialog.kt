// GlassDialog: a centered modal dialog with title, message, and
// pill action buttons, on the fixed modal glass material.

package com.vallxz.glass.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.engine.GlassBackdrop

/**
 * Full-screen modal host shared by the dialog and the bottom sheet: a black
 * scrim fading in over 220 ms (out over 180 ms) to 42% alpha, tap-outside
 * to dismiss, back press to dismiss, and unmounting once hidden and fully
 * faded. The host renders nothing while unmounted, so callers can keep it
 * composed unconditionally and drive it with [visible].
 *
 * The modal content is drawn above the scrim inside [content]; content
 * containers must swallow their own taps (an empty clickable) so taps on
 * the modal itself do not reach the scrim.
 */
@Composable
internal fun GlassModalOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrimTargetAlpha: Float = 0.42f,
    content: @Composable BoxScope.() -> Unit,
) {
    var mounted by remember { mutableStateOf(visible) }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) scrimTargetAlpha else 0f,
        animationSpec = tween(durationMillis = if (visible) 220 else 180),
        finishedListener = { value ->
            if (!visible && value == 0f) mounted = false
        },
        label = "glassModalScrim",
    )
    LaunchedEffect(visible) {
        if (visible) mounted = true
    }
    if (!mounted) return
    BackHandler { onDismissRequest() }
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismissRequest() },
        )
        content()
    }
}

/**
 * A generic modal dialog: a fixed 300 dp wide glass panel with a
 * left-aligned 17 sp semibold title and a 17 sp regular message, both
 * in the primary label color with 22 sp line height and -0.43 sp
 * tracking, 10 dp apart, inside 14 dp panel padding plus 8 dp
 * horizontal / 8 dp top / 24 dp bottom text padding, then 48 dp pill
 * action buttons. There are no dividers. The design stays the same
 * for every use; the texts and the button label colors are the
 * caller's to change.
 *
 * With two actions the buttons sit side by side with a 16 dp gap: the
 * cancel button (secondary fill, primary label) on the left, the
 * confirm button (blue fill, white label) on the right. With one
 * action there is a single full-width pill. A destructive dialog
 * keeps the secondary fill for the confirm pill and colors its label
 * system red. Button labels are 17 sp medium. Tapping a button runs
 * its action and dismisses. The panel enters with a "squish" (scale
 * X 1.1 / Y 0.72 to 1/1, spring damping 0.54 / stiffness 420) and
 * exits with a 150 ms tween; content alpha fades over 180 ms. The
 * glass is the fixed modal material (12 dp blur, 16/32 dp lens,
 * vibrancy, heavy surface tint). There is no icon and no close
 * button; dismissal is by button tap, scrim tap, or back press.
 *
 * Button colors: [confirmColor] and [cancelColor] override the button
 * label colors when set; when unspecified, the labels use the colors
 * above. The fills never change. The dialog must be placed where it
 * can fill the screen (at the root of the screen composition): it
 * draws its own scrim overlay. The backdrop is the explicit argument,
 * falling back to the nearest `LocalGlassBackdrop` only when null.
 */
@Composable
fun GlassDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    cancelText: String? = null,
    onCancel: (() -> Unit)? = null,
    destructive: Boolean = false,
    confirmColor: Color = Color.Unspecified,
    cancelColor: Color = Color.Unspecified,
) {
    val darkTheme = isSystemInDarkTheme()
    val labelColor = glassLabelColor(darkTheme)
    // Pill fills: the accent blue, and the secondary fill gray. The
    // light secondary alpha is the design value; the dark alpha
    // follows the system fill behavior and still needs a device check.
    val primaryFill = Color(0xFF0088FF)
    val secondaryFill = Color(0xFF787880).copy(alpha = if (darkTheme) 0.32f else 0.16f)

    val scaleX by animateFloatAsState(
        targetValue = if (visible) 1f else 1.1f,
        animationSpec = if (visible) spring(dampingRatio = 0.54f, stiffness = 420f) else tween(150),
        label = "glassDialogScaleX",
    )
    val scaleY by animateFloatAsState(
        targetValue = if (visible) 1f else 0.72f,
        animationSpec = if (visible) spring(dampingRatio = 0.54f, stiffness = 420f) else tween(150),
        label = "glassDialogScaleY",
    )
    val panelShape = RoundedCornerShape(34.dp)
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(180),
        label = "glassDialogAlpha",
    )

    GlassModalOverlay(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.Center)
                .width(300.dp)
                .graphicsLayer(
                    scaleX = scaleX,
                    scaleY = scaleY,
                    alpha = contentAlpha,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* Swallow taps so they never reach the scrim. */ },
            style = glassModalStyle(darkTheme),
            shape = panelShape,
            // The squish scale, reported so sampling inverts it and the
            // backdrop stays aligned while the panel squishes in.
            layerScale = { Offset(scaleX, scaleY) },
        ) {
            // Clip the content to the panel shape so nothing draws
            // past the rounded corners.
            Column(
                modifier = Modifier.fillMaxWidth().clip(panelShape).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicText(
                        text = title,
                        style = TextStyle(
                            color = labelColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 22.sp,
                            letterSpacing = (-0.43).sp,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BasicText(
                        text = message,
                        style = TextStyle(
                            color = labelColor,
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            letterSpacing = (-0.43).sp,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val confirmLabelColor = when {
                    confirmColor != Color.Unspecified -> confirmColor
                    destructive -> glassDestructiveColor(darkTheme)
                    else -> Color.White
                }
                val confirmFill = if (destructive) secondaryFill else primaryFill
                if (cancelText != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        GlassDialogPillButton(
                            text = cancelText,
                            fill = secondaryFill,
                            labelColor = if (cancelColor != Color.Unspecified) cancelColor else labelColor,
                            onClick = {
                                onCancel?.invoke()
                                onDismissRequest()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        GlassDialogPillButton(
                            text = confirmText,
                            fill = confirmFill,
                            labelColor = confirmLabelColor,
                            onClick = {
                                onConfirm()
                                onDismissRequest()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    GlassDialogPillButton(
                        text = confirmText,
                        fill = confirmFill,
                        labelColor = confirmLabelColor,
                        onClick = {
                            onConfirm()
                            onDismissRequest()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** A 48 dp pill button: a solid fill with a centered 17 sp medium label. A press dims the fill. */
@Composable
private fun GlassDialogPillButton(
    text: String,
    fill: Color,
    labelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(fill)
            .background(if (pressed) Color.Black.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = labelColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp,
                letterSpacing = (-0.43).sp,
                textAlign = TextAlign.Center,
            ),
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
