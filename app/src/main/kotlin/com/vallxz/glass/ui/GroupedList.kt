// GroupedList: grouped-list building blocks for the template screens -
// section headers, rows, dividers, and glass cards.

package com.vallxz.glass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.components.GlassSlider
import com.vallxz.glass.components.GlassToggle
import com.vallxz.glass.engine.GlassBackdrop

/** Label black/white, following the system theme. */
fun appLabelColor(darkTheme: Boolean): Color =
    if (darkTheme) Color.White else Color.Black

/** Secondary label at 60% opacity. */
fun appSecondaryColor(darkTheme: Boolean): Color =
    appLabelColor(darkTheme).copy(alpha = 0.6f)

/** System blue. */
fun appAccentColor(darkTheme: Boolean): Color =
    if (darkTheme) Color(0xFF50C0FF) else Color(0xFF0073FF)

/** System red, for destructive actions. */
fun appDestructiveColor(darkTheme: Boolean): Color =
    if (darkTheme) Color(0xFFFF453A) else Color(0xFFFF3B30)

/** Separator gray: black 29% in light mode, gray 60% in dark mode. */
fun appSeparatorColor(darkTheme: Boolean): Color =
    if (darkTheme) Color.Gray.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.29f)

/** Uppercase 13 sp section header above a group. */
@Composable
fun SectionHeader(text: String, color: Color) {
    BasicText(
        text = text.uppercase(),
        style = TextStyle(color = color, fontSize = 13.sp),
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** 13 sp footer note below a group. */
@Composable
fun SectionFooter(text: String, color: Color) {
    BasicText(
        text = text,
        style = TextStyle(color = color, fontSize = 13.sp),
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * A glass grouped card: the style comes from the live glass settings,
 * the corner radius is the tunable one (22 dp by default), and the card
 * exports its own backdrop so glass components inside its rows refract
 * the card plus the backdrop behind it.
 */
@Composable
fun GroupCard(
    settings: GlassSettings,
    darkTheme: Boolean,
    backdrop: GlassBackdrop? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardShape = RoundedCornerShape(settings.cornerRadius.dp)
    GlassSurface(
        backdrop = backdrop,
        style = settings.toGroupStyle(darkTheme),
        shape = cardShape,
        exportBackdrop = true,
    ) {
        // Clip the rows to the card shape so the row press wash
        // follows the rounded corners instead of drawing square.
        Column(modifier = Modifier.fillMaxWidth().clip(cardShape)) {
            content()
        }
    }
}

/** A 0.5 dp divider between group rows, with a 20 dp leading inset unless [showLeadingInset] is false. */
@Composable
fun RowDivider(color: Color, showLeadingInset: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (showLeadingInset) 20.dp else 0.dp)
            .height(0.5.dp)
            .background(color),
    )
}

/** A right-pointing chevron, drawn as two rounded lines. */
@Composable
fun Chevron(color: Color) {
    Canvas(modifier = Modifier.size(width = 8.dp, height = 13.dp)) {
        val strokeWidth = 2.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(
            color = color,
            start = Offset(strokeWidth / 2f, strokeWidth / 2f),
            end = Offset(w - strokeWidth / 2f, h / 2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w - strokeWidth / 2f, h / 2f),
            end = Offset(strokeWidth / 2f, h - strokeWidth / 2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * A group row: min height 50 dp, 20 dp horizontal / 14 dp vertical
 * padding, a 17 sp label with an optional 13 sp secondary subtitle 2 dp
 * below it, an optional 17 sp secondary detail followed by a 6 dp gap,
 * and an optional chevron. Tapping runs [onClick] when given, with a gray
 * pressed wash.
 */
@Composable
fun TextRow(
    label: String,
    labelColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    detailColor: Color = secondaryColor,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .background(
                if (pressed && onClick != null) {
                    Color(0xFF8E8E93).copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { onClick() }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BasicText(
                    text = label,
                    style = TextStyle(color = labelColor, fontSize = 17.sp),
                )
                if (subtitle != null) {
                    BasicText(
                        text = subtitle,
                        style = TextStyle(color = secondaryColor, fontSize = 13.sp),
                    )
                }
            }
            if (detail != null) {
                BasicText(
                    text = detail,
                    style = TextStyle(color = detailColor, fontSize = 17.sp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (chevron) {
                Chevron(color = secondaryColor)
            }
        }
    }
}

/** A group row with a label, an optional subtitle, and a glass toggle. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    labelColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(
                text = label,
                style = TextStyle(color = labelColor, fontSize = 17.sp),
            )
            if (subtitle != null) {
                BasicText(
                    text = subtitle,
                    style = TextStyle(color = labelColor.copy(alpha = 0.6f), fontSize = 13.sp),
                )
            }
        }
        GlassToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A group row with a label, a value readout, and a glass slider
 * underneath: 20 dp horizontal padding, 14 dp vertical padding, and a
 * 10 dp gap above the slider.
 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    labelColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = label,
                style = TextStyle(color = labelColor, fontSize = 17.sp),
                modifier = Modifier.weight(1f),
            )
            BasicText(
                text = valueText,
                style = TextStyle(color = secondaryColor, fontSize = 17.sp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        GlassSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}
