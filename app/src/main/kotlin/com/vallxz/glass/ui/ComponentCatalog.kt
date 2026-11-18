// ComponentCatalog: the catalog of glass components shared by the
// Home and Search pages. Each entry is a live, interactive component
// with its own state, a name, search keywords, and a description.

package com.vallxz.glass.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vallxz.glass.R
import com.vallxz.glass.components.GlassBackButton
import com.vallxz.glass.components.GlassButton
import com.vallxz.glass.components.GlassHorizontalSelector
import com.vallxz.glass.components.GlassMenuItem
import com.vallxz.glass.components.GlassProgressBar
import com.vallxz.glass.components.GlassRangeSlider
import com.vallxz.glass.components.GlassRulerSelector
import com.vallxz.glass.components.GlassSegmentedControl
import com.vallxz.glass.components.GlassStepper
import com.vallxz.glass.components.glassMenuAnchor
import com.vallxz.glass.engine.GlassBackdrop
import kotlin.math.roundToInt

/**
 * Everything a catalog entry needs: the backdrop to sample, the live
 * glass settings, the theme colors, and the overlay host for menus,
 * dialogs, and sheets.
 */
class CatalogContext(
    val backdrop: GlassBackdrop,
    val settings: GlassSettings,
    val darkTheme: Boolean,
    val labelColor: Color,
    val secondaryColor: Color,
    val accentColor: Color,
    val destructiveColor: Color,
    val separatorColor: Color,
    val overlays: AppOverlayState,
    val onResetSettings: () -> Unit,
)

/** One catalog entry: a live component with a name, search keywords, and a description. */
class CatalogEntry(
    val name: String,
    val keywords: List<String>,
    val description: String,
    val content: @Composable (CatalogContext) -> Unit,
) {
    /** True when [query] matches the name or any keyword; a blank query matches nothing. */
    fun matches(query: String): Boolean {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return false
        if (name.lowercase().contains(trimmed)) return true
        return keywords.any { it.contains(trimmed) }
    }
}

/** The full component catalog, in Home page order. */
val componentCatalog: List<CatalogEntry> = listOf(
    CatalogEntry(
        name = "Button",
        keywords = listOf("button", "back", "tinted", "press", "ripple"),
        description = "Buttons follow the Settings lens. The back buttons show " +
            "the same press behavior with an icon or a text label.",
        content = { ctx ->
            // Buttons follow the Settings lens (16/32), with no surface
            // wash: the surface tint slider veils cards, not buttons.
            // The tinted button hue-blends its pale yellow tint over
            // the backdrop.
            val buttonStyle =
                ctx.settings.toGroupStyle(ctx.darkTheme).copy(surfaceColor = Color.Unspecified)
            GroupCard(settings = ctx.settings, darkTheme = ctx.darkTheme, backdrop = ctx.backdrop) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(
                            onClick = { },
                            text = "Button 1",
                            backdrop = ctx.backdrop,
                            style = buttonStyle,
                            contentColor = ctx.labelColor,
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton(
                            onClick = { },
                            text = "Button 2 Tint",
                            backdrop = ctx.backdrop,
                            style = buttonStyle.copy(
                                tint = Color(0xFFFEF082),
                                tintAlpha = 0.28f,
                            ),
                            contentColor = ctx.labelColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassBackButton(
                            onClick = { },
                            backdrop = ctx.backdrop,
                            style = buttonStyle,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_chevron_left),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(ctx.labelColor),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(22.dp),
                            )
                        }
                        GlassBackButton(
                            onClick = { },
                            backdrop = ctx.backdrop,
                            style = buttonStyle,
                        ) {
                            BasicText(
                                text = "Back",
                                style = TextStyle(
                                    color = ctx.labelColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
            }
        },
    ),
    CatalogEntry(
        name = "Switch",
        keywords = listOf("switch", "toggle", "on", "off"),
        description = "Glass toggles keep their built-in styling.",
        content = { ctx ->
            var checked by remember { mutableStateOf(true) }
            GroupCard(settings = ctx.settings, darkTheme = ctx.darkTheme, backdrop = ctx.backdrop) {
                ToggleRow(
                    label = "Switch",
                    checked = checked,
                    onCheckedChange = { checked = it },
                    labelColor = ctx.labelColor,
                    subtitle = "A glass toggle",
                )
            }
        },
    ),
    CatalogEntry(
        name = "Stepper",
        keywords = listOf("stepper", "quantity", "plus", "minus", "count"),
        description = "Hold − or + to keep stepping.",
        content = { ctx ->
            var quantity by remember { mutableIntStateOf(2) }
            GroupCard(settings = ctx.settings, darkTheme = ctx.darkTheme, backdrop = ctx.backdrop) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = "Count",
                        style = TextStyle(color = ctx.labelColor, fontSize = 17.sp),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        text = "$quantity",
                        style = TextStyle(color = ctx.secondaryColor, fontSize = 17.sp),
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    GlassStepper(
                        value = quantity,
                        onValueChange = { quantity = it },
                        valueRange = 0..20,
                    )
                }
            }
        },
    ),
    CatalogEntry(
        name = "Menu",
        keywords = listOf("menu", "popup", "sort", "dropdown", "options"),
        description = "The menu grows out of a droplet at the row's edge.",
        content = { ctx ->
            var selectedMenu by remember { mutableStateOf("Menu 1") }
            var anchor by remember { mutableStateOf(IntRect.Zero) }
            GroupCard(settings = ctx.settings, darkTheme = ctx.darkTheme, backdrop = ctx.backdrop) {
                TextRow(
                    label = "Menu",
                    labelColor = ctx.labelColor,
                    secondaryColor = ctx.secondaryColor,
                    modifier = Modifier.glassMenuAnchor { anchor = it },
                    detail = selectedMenu,
                    chevron = true,
                    onClick = {
                        val options = listOf("Menu 1", "Menu 2", "Menu 3")
                        ctx.overlays.showMenu(
                            anchorBounds = anchor,
                            items = options.map { option ->
                                GlassMenuItem(
                                    label = option,
                                    selected = option == selectedMenu,
                                ) {
                                    selectedMenu = option
                                }
                            },
                        )
                    },
                )
            }
        },
    ),
    CatalogEntry(
        name = "Dialog",
        keywords = listOf("dialog", "alert", "modal", "popup", "confirm"),
        description = "Modals use a fixed glass preset for consistent visibility.",
        content = { ctx ->
            GroupCard(settings = ctx.settings, darkTheme = ctx.darkTheme, backdrop = ctx.backdrop) {
                TextRow(
                    label = "Show Dialog",
                    labelColor = ctx.labelColor,
                    secondaryColor = ctx.secondaryColor,
                    chevron = true,
                    onClick = {
                        ctx.overlays.showDialog(
                            DialogRequest(
                                title = "Lorem ipsum",
                                message = "Lorem ipsum dolor sit amet, consectetur " +
                                    "adipiscing elit, sed do eiusmod tempor incididunt " +
                                    "ut labore et dolore magna aliqua.",
                                confirmText = "Yes",
                                onConfirm = { },
                                cancelText = "No",
                            ),
                        )
                    },
                )
            }
        },
    ),
    CatalogEntry(
        name = "Bottom Sheet",
        keywords = listOf("bottom", "sheet", "share", "modal", "slide"),
        description = "The sheet slides up with a squish and dismisses on scrim tap.",
        content = { ctx ->
            GroupCard(settings = ctx.settings, darkTheme = ctx.darkTheme, backdrop = ctx.backdrop) {
                TextRow(
                    label = "Show Bottom Sheet",
                    labelColor = ctx.labelColor,
                    secondaryColor = ctx.secondaryColor,
                    chevron = true,
                    onClick = { ctx.overlays.sheetVisible = true },
                )
            }
        },
    ),
    CatalogEntry(
        name = "Slider",
        keywords = listOf("slider", "level", "value"),
        description = "Drag the thumb or tap the track.",
        content = { ctx ->
            var level by remember { mutableFloatStateOf(0.45f) }
            GroupCard(settings = ctx.settings, darkTheme = ctx.darkTheme, backdrop = ctx.backdrop) {
                SliderRow(
                    label = "Level",
                    value = level,
                    onValueChange = { level = it },
                    valueRange = 0f..1f,
                    valueText = "${(level * 100).roundToInt()}%",
                    labelColor = ctx.labelColor,
                    secondaryColor = ctx.secondaryColor,
                )
            }
        },
    ),
    CatalogEntry(
        name = "Double Slider",
        keywords = listOf("double", "range", "slider", "value", "numbers"),
        description = "Drag either thumb, or tap the track to move the nearest one.",
        content = { ctx ->
            var start by remember { mutableFloatStateOf(20f) }
            var end by remember { mutableFloatStateOf(65f) }
            GroupCard(settings = ctx.settings, darkTheme = ctx.darkTheme, backdrop = ctx.backdrop) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = "Range",
                            style = TextStyle(color = ctx.labelColor, fontSize = 17.sp),
                            modifier = Modifier.weight(1f),
                        )
                        BasicText(
                            text = "${start.roundToInt()} - ${end.roundToInt()}",
                            style = TextStyle(color = ctx.secondaryColor, fontSize = 17.sp),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    GlassRangeSlider(
                        start = start,
                        end = end,
                        onRangeChange = { newStart, newEnd ->
                            start = newStart
                            end = newEnd
                        },
                        valueRange = 0f..100f,
                        minGap = 5f,
                    )
                }
            }
        },
    ),
    CatalogEntry(
        name = "Horizontal Selector",
        keywords = listOf("horizontal", "selector", "camera", "mode", "options"),
        description = "Swipe horizontally to switch between options.",
        content = { ctx ->
            var selected by remember { mutableIntStateOf(1) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            ) {
                GlassHorizontalSelector(
                    options = listOf(
                        "Mode 1", "Mode 2", "Mode 3", "Super Mode 4", "Super Mode 5", "OFF",
                    ),
                    selectedIndex = selected,
                    onSelected = { selected = it },
                    backdrop = ctx.backdrop,
                    contentColor = ctx.labelColor,
                    chromaticAberration = 0.8f,
                )
            }
        },
    ),
    CatalogEntry(
        name = "Range Slider",
        keywords = listOf("ticks", "value", "mm", "measurement", "focal", "zoom"),
        description = "Focal length in millimeters. Swipe the ticks to zoom.",
        content = { ctx ->
            var value by remember { mutableIntStateOf(35) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            ) {
                GlassRulerSelector(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 14..200,
                    unit = "mm",
                    backdrop = ctx.backdrop,
                    contentColor = ctx.labelColor,
                    chromaticAberration = 0.8f,
                )
            }
        },
    ),
    CatalogEntry(
        name = "Segmented",
        keywords = listOf("segment", "segmented", "control", "pill", "tabs"),
        description = "Tap a segment or drag the droplet between segments.",
        content = { ctx ->
            var selected by remember { mutableIntStateOf(1) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            ) {
                GlassSegmentedControl(
                    options = listOf("Tab 1", "Tab 2", "Tab 3", "Tab 4", "Tab 5"),
                    selectedIndex = selected,
                    onSelected = { selected = it },
                    backdrop = ctx.backdrop,
                    contentColor = ctx.labelColor,
                )
            }
        },
    ),
    CatalogEntry(
        name = "Progress Bar",
        keywords = listOf("progress", "bar", "download", "loading", "indeterminate"),
        description = "Indeterminate progress runs on its own.",
        content = { ctx ->
            GroupCard(settings = ctx.settings, darkTheme = ctx.darkTheme, backdrop = ctx.backdrop) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    GlassProgressBar(
                        progress = 0f,
                        indeterminate = true,
                    )
                }
            }
        },
    ),

)

/** Adds [entries] to a lazy list: a header, the live component, and its description each. */
fun LazyListScope.catalogItems(
    entries: List<CatalogEntry>,
    context: CatalogContext,
) {
    items(entries, key = { it.name }) { entry ->
        Column {
            SectionHeader(text = entry.name, color = context.secondaryColor)
            entry.content(context)
            SectionFooter(text = entry.description, color = context.secondaryColor)
        }
    }
}
