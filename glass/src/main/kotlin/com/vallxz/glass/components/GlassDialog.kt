// GlassDialog: a centered modal dialog with title, message, and
// stacked action buttons, on the fixed modal glass material.

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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

/** A 0.5 dp full-width hairline divider in [color]. */
@Composable
internal fun GlassHairline(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(color),
    )
}

/**
 * A generic modal dialog: a fixed 270 dp wide glass panel with a centered
 * 17 sp semibold title and a 15 sp message, 8 dp apart, inside 16 dp
 * horizontal / 20 dp vertical padding, then full-width 44 dp buttons
 * stacked vertically and separated by hairlines. The design stays the
 * same for every use; the texts and the button colors are the caller's
 * to change.
 *
 * The cancel button (regular weight) comes first, the confirm button
 * (semibold) second; with no cancel text there is a single semibold
 * button. Tapping a button runs its action and dismisses. The panel
 * enters with a "squish" (scale X 1.1 / Y 0.72 to 1/1, spring damping
 * 0.54 / stiffness 420) and exits with a 150 ms tween; content alpha
 * fades over 180 ms. The glass is the fixed modal material (12 dp blur,
 * 16/32 dp lens, vibrancy, heavy surface tint). There is no icon and no
 * close button; dismissal is by button tap, scrim tap, or back press.
 *
 * Button colors: [confirmColor] and [cancelColor] override the button
 * label colors when set; when unspecified, both buttons use the system
 * accent, except a destructive dialog, whose confirm label uses the
 * system red. The dialog must be placed where it can fill the screen
 * (at the root of the screen composition): it draws its own scrim
 * overlay. The backdrop is the explicit argument, falling back to the
 * nearest `LocalGlassBackdrop` only when null.
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
    val secondaryColor = glassSecondaryLabelColor(darkTheme)
    val accentColor = glassAccentColor(darkTheme)
    val separatorColor = glassSeparatorColor(darkTheme)

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
    val panelShape = RoundedCornerShape(35.dp)
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
                .width(270.dp)
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
            // Clip the content to the panel shape so the button press
            // wash follows the rounded corners instead of drawing square.
            Column(modifier = Modifier.fillMaxWidth().clip(panelShape)) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                ) {
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
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicText(
                        text = message,
                        style = TextStyle(
                            color = secondaryColor,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                GlassHairline(separatorColor)
                if (cancelText != null) {
                    GlassDialogButton(
                        text = cancelText,
                        color = if (cancelColor != Color.Unspecified) cancelColor else accentColor,
                        bold = false,
                        onClick = {
                            onCancel?.invoke()
                            onDismissRequest()
                        },
                    )
                    GlassHairline(separatorColor)
                }
                GlassDialogButton(
                    text = confirmText,
                    color = when {
                        confirmColor != Color.Unspecified -> confirmColor
                        destructive -> glassDestructiveColor(darkTheme)
                        else -> accentColor
                    },
                    bold = true,
                    onClick = {
                        onConfirm()
                        onDismissRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun GlassDialogButton(
    text: String,
    color: Color,
    bold: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(
                if (pressed) Color(0xFF8E8E93).copy(alpha = 0.18f) else Color.Transparent,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = color,
                fontSize = 17.sp,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}
