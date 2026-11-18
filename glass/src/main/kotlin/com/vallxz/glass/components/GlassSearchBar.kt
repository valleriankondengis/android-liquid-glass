// GlassSearchBar: a glass search field with icon, clear button,
// and state-based text input.

package com.vallxz.glass.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.engine.GlassBackdrop
import com.vallxz.glass.interaction.rememberGlassHaptics
import kotlinx.coroutines.flow.collect

/**
 * A glass search bar: leading search icon, an editable query, and a clear
 * button that appears when the query is non-empty.
 *
 * The query stays hoisted as a plain [String] with [onQueryChange], while
 * the field itself runs on the state-based text API: edits are observed
 * from the field state and reported upward, and external changes to
 * [query] (clear button, programmatic set) are adopted into the field
 * state. The backdrop resolves like [GlassSurface] (the explicit
 * argument, else the nearest `LocalGlassBackdrop`).
 *
 * [shrinkProgress] is the expand/shrink feature: at 0 the field is
 * full size, at 1 it shrinks to 98.5% of its size, with the backdrop
 * sampling following the scale so the glass stays aligned. The caller
 * drives the value - typically an animated float on the field being
 * active (focused or non-empty) - and the feature works with or
 * without an accompanying button beside the field. A button that
 * reveals in the freed space can share the same progress value.
 */
@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    searchIcon: Painter? = null,
    clearIcon: Painter? = null,
    placeholder: String = "Search",
    enabled: Boolean = true,
    haptic: Boolean = true,
    style: GlassStyle = GlassStyle.Default,
    contentColor: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(50),
    height: Dp = 48.dp,
    onSearch: () -> Unit = {},
    layerScale: () -> Offset = { Offset(1f, 1f) },
    shrinkProgress: Float = 0f,
) {
    val haptics = rememberGlassHaptics(haptic)
    val clearInteractionSource = remember { MutableInteractionSource() }
    // System-blue cursor, following the theme.
    val darkTheme = isSystemInDarkTheme()
    val cursorColor = if (darkTheme) Color(0xFF50C0FF) else Color(0xFF0073FF)
    val resolvedContentColor = if (contentColor == Color.Unspecified) {
        glassLabelColor(darkTheme)
    } else {
        contentColor
    }

    val fieldState = rememberTextFieldState(query)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    // Field -> caller: report every edit.
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect { currentOnQueryChange(it) }
    }
    // Caller -> field: adopt external changes; the equality guard keeps the
    // echo of our own reports from resetting the cursor.
    LaunchedEffect(query) {
        if (fieldState.text.toString() != query) {
            fieldState.setTextAndPlaceCursorAtEnd(query)
        }
    }
    val currentOnSearch by rememberUpdatedState(onSearch)
    val shrinkScale = lerp(1f, 0.985f, shrinkProgress)

    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = shrinkScale
                scaleY = shrinkScale
            },
        style = style,
        shape = shape,
        // The shrink scale is composed with the caller's scale so
        // sampling inverts both and the backdrop stays aligned.
        layerScale = {
            val base = layerScale()
            Offset(base.x * shrinkScale, base.y * shrinkScale)
        },
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .align(Alignment.CenterStart)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchIcon != null) {
                Image(
                    painter = searchIcon,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(resolvedContentColor.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 6.dp),
                )
            }
            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = resolvedContentColor,
                    backgroundColor = resolvedContentColor.copy(alpha = 0.3f),
                ),
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (fieldState.text.isEmpty()) {
                        BasicText(
                            text = placeholder,
                            style = TextStyle(
                                color = resolvedContentColor.copy(alpha = 0.6f),
                                fontSize = 17.sp,
                            ),
                        )
                    }
                    BasicTextField(
                        state = fieldState,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        textStyle = TextStyle(color = resolvedContentColor, fontSize = 17.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = { _ -> currentOnSearch() },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        cursorBrush = SolidColor(cursorColor),
                    )
                }
            }
            if (fieldState.text.isNotEmpty() && clearIcon != null) {
                Image(
                    painter = clearIcon,
                    contentDescription = "Clear",
                    colorFilter = ColorFilter.tint(resolvedContentColor.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(18.dp)
                        .clickable(
                            interactionSource = clearInteractionSource,
                            indication = null,
                        ) {
                            if (haptic) haptics.confirm()
                            onQueryChange("")
                        },
                )
            }
        }
    }
}
