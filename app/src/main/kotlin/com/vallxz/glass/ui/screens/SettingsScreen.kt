// SettingsScreen: the control center for every component. Live glass
// tuning, gradient preview, and the temporary gallery backdrop import.

package com.vallxz.glass.ui.screens

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vallxz.glass.components.GlassButton
import com.vallxz.glass.components.GlassGradientPanel
import com.vallxz.glass.components.GlassMenuItem
import com.vallxz.glass.components.glassMenuAnchor
import com.vallxz.glass.effects.GlassGradient
import com.vallxz.glass.engine.GlassBackdrop
import com.vallxz.glass.ui.AppOverlayState
import com.vallxz.glass.ui.GlassSettingsHolder
import com.vallxz.glass.ui.GroupCard
import com.vallxz.glass.ui.HighlightStyleNames
import com.vallxz.glass.ui.RowDivider
import com.vallxz.glass.ui.SectionFooter
import com.vallxz.glass.ui.SectionHeader
import com.vallxz.glass.ui.SliderRow
import com.vallxz.glass.ui.TextRow
import com.vallxz.glass.ui.ToggleRow
import com.vallxz.glass.ui.appDestructiveColor
import com.vallxz.glass.ui.appLabelColor
import com.vallxz.glass.ui.appSecondaryColor
import com.vallxz.glass.ui.appSeparatorColor
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Settings page: the control center for the glass on every page.
 * Every glass parameter is a slider, toggle, or dropdown row inside
 * glass groups, applied live to the grouped cards and preview panels
 * across the app, and persisted on every change so the tuning survives
 * restarts. A Background Preview section imports a gallery image as a
 * temporary backdrop, kept only in memory while the app runs.
 * [topPadding] clears the transparent top bar.
 */
@Composable
fun SettingsScreen(
    backdrop: GlassBackdrop,
    holder: GlassSettingsHolder,
    overlays: AppOverlayState,
    tempBackground: ImageBitmap?,
    onTempBackgroundChange: (ImageBitmap?) -> Unit,
    topPadding: Dp,
) {
    val darkTheme = isSystemInDarkTheme()
    val labelColor = appLabelColor(darkTheme)
    val secondaryColor = appSecondaryColor(darkTheme)
    val separatorColor = appSeparatorColor(darkTheme)
    val settings = holder.settings
    var styleAnchor by remember { mutableStateOf(IntRect.Zero) }

    // Temporary backdrop import: the system photo picker returns a Uri,
    // decoded off the main thread into a bitmap held only in memory.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnTempBackgroundChange by rememberUpdatedState(onTempBackgroundChange)
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeTempBackground(context, uri)
            }
            currentOnTempBackgroundChange(bitmap)
        }
    }

    fun dpText(value: Float): String = "${"%.1f".format(value)} dp"
    fun percentText(value: Float): String = "${(value * 100).roundToInt()}%"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding,
            bottom = 110.dp,
        ),
    ) {
        item {
            GlassGradientPanel(
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                gradient = GlassGradient(
                    bottomWeight = settings.gradientWeight,
                    fadeStart = 0f,
                    fadeEnd = settings.blurFadeEnd,
                ),
            ) {
                BasicText(
                    text = "Gradient preview",
                    style = TextStyle(color = labelColor, fontSize = 15.sp),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            SectionFooter(
                text = "The gradient panel below the fade band tints toward " +
                    "the surface color. The groups on this screen preview " +
                    "the other settings live.",
                color = secondaryColor,
            )
        }

        item {
            Spacer(modifier = Modifier.height(22.dp))
            SectionHeader(text = "Background Preview", color = secondaryColor)
            GroupCard(settings = settings, darkTheme = darkTheme, backdrop = backdrop) {
                TextRow(
                    label = "Import from Gallery",
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                    chevron = true,
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                )
                if (tempBackground != null) {
                    RowDivider(color = separatorColor)
                    TextRow(
                        label = "Remove Imported Background",
                        labelColor = appDestructiveColor(darkTheme),
                        secondaryColor = secondaryColor,
                        onClick = { onTempBackgroundChange(null) },
                    )
                }
            }
            SectionFooter(
                text = "The imported image is kept only while the app is running. " +
                    "Closing the app restores the plain background.",
                color = secondaryColor,
            )
        }

        item {
            Spacer(modifier = Modifier.height(22.dp))
            SectionHeader(text = "Glass", color = secondaryColor)
            GroupCard(settings = settings, darkTheme = darkTheme, backdrop = backdrop) {
                SliderRow(
                    label = "Blur Radius",
                    value = settings.blurRadius,
                    onValueChange = { value -> holder.update { current -> current.copy(blurRadius = value) } },
                    valueRange = 0f..24f,
                    valueText = dpText(settings.blurRadius),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
                RowDivider(color = separatorColor)
                SliderRow(
                    label = "Refraction Height",
                    value = settings.refractionHeight,
                    onValueChange = { value -> holder.update { current -> current.copy(refractionHeight = value) } },
                    valueRange = 0f..32f,
                    valueText = dpText(settings.refractionHeight),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
                RowDivider(color = separatorColor)
                SliderRow(
                    label = "Refraction Amount",
                    value = settings.refractionAmount,
                    onValueChange = { value -> holder.update { current -> current.copy(refractionAmount = value) } },
                    valueRange = 0f..60f,
                    valueText = dpText(settings.refractionAmount),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
                RowDivider(color = separatorColor)
                SliderRow(
                    label = "Chromatic Aberration",
                    value = settings.chromaticAberration,
                    onValueChange = { value -> holder.update { current -> current.copy(chromaticAberration = value) } },
                    valueRange = 0f..1f,
                    valueText = percentText(settings.chromaticAberration),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
                RowDivider(color = separatorColor)
                ToggleRow(
                    label = "Depth Effect",
                    checked = settings.depth,
                    onCheckedChange = { checked -> holder.update { current -> current.copy(depth = checked) } },
                    labelColor = labelColor,
                )
                RowDivider(color = separatorColor)
                SliderRow(
                    label = "Corner Radius",
                    value = settings.cornerRadius,
                    onValueChange = { value -> holder.update { current -> current.copy(cornerRadius = value) } },
                    valueRange = 0f..48f,
                    valueText = dpText(settings.cornerRadius),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(22.dp))
            SectionHeader(text = "Color", color = secondaryColor)
            GroupCard(settings = settings, darkTheme = darkTheme, backdrop = backdrop) {
                ToggleRow(
                    label = "Vibrancy",
                    checked = settings.vibrancy,
                    onCheckedChange = { checked -> holder.update { current -> current.copy(vibrancy = checked) } },
                    labelColor = labelColor,
                )
                RowDivider(color = separatorColor)
                SliderRow(
                    label = "Brightness",
                    value = settings.brightness,
                    onValueChange = { value -> holder.update { current -> current.copy(brightness = value) } },
                    valueRange = -0.30f..0.50f,
                    valueText = "%.2f".format(settings.brightness),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
                RowDivider(color = separatorColor)
                SliderRow(
                    label = "Saturation",
                    value = settings.saturation,
                    onValueChange = { value -> holder.update { current -> current.copy(saturation = value) } },
                    valueRange = 0.50f..2.50f,
                    valueText = "%.2f".format(settings.saturation),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
                RowDivider(color = separatorColor)
                SliderRow(
                    label = "Surface Tint",
                    value = settings.tintAlpha,
                    onValueChange = { value -> holder.update { current -> current.copy(tintAlpha = value) } },
                    valueRange = 0f..0.60f,
                    valueText = percentText(settings.tintAlpha),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
            }
            SectionFooter(
                text = "Vibrancy pins saturation at 1.5; turn it off to tune " +
                    "saturation by hand.",
                color = secondaryColor,
            )
        }

        item {
            Spacer(modifier = Modifier.height(22.dp))
            SectionHeader(text = "Specular", color = secondaryColor)
            GroupCard(settings = settings, darkTheme = darkTheme, backdrop = backdrop) {
                ToggleRow(
                    label = "Enabled",
                    checked = settings.highlightEnabled,
                    onCheckedChange = { checked -> holder.update { current -> current.copy(highlightEnabled = checked) } },
                    labelColor = labelColor,
                )
                RowDivider(color = separatorColor)
                TextRow(
                    label = "Style",
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                    modifier = Modifier.glassMenuAnchor { styleAnchor = it },
                    detail = HighlightStyleNames[settings.highlightStyle],
                    chevron = true,
                    onClick = {
                        overlays.showMenu(
                            anchorBounds = styleAnchor,
                            items = HighlightStyleNames.mapIndexed { index, name ->
                                GlassMenuItem(
                                    label = name,
                                    selected = index == settings.highlightStyle,
                                ) {
                                    holder.update { it.copy(highlightStyle = index) }
                                }
                            },
                        )
                    },
                )
                RowDivider(color = separatorColor)
                SliderRow(
                    label = "Alpha",
                    value = settings.highlightAlpha,
                    onValueChange = { value -> holder.update { current -> current.copy(highlightAlpha = value) } },
                    valueRange = 0f..1f,
                    valueText = percentText(settings.highlightAlpha),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
                if (settings.highlightStyle != 1) {
                    RowDivider(color = separatorColor)
                    SliderRow(
                        label = "Width",
                        value = settings.highlightWidth,
                        onValueChange = { value -> holder.update { current -> current.copy(highlightWidth = value) } },
                        valueRange = 0.25f..2f,
                        valueText = "%.2f".format(settings.highlightWidth),
                        labelColor = labelColor,
                        secondaryColor = secondaryColor,
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(22.dp))
            SectionHeader(text = "Shadow", color = secondaryColor)
            GroupCard(settings = settings, darkTheme = darkTheme, backdrop = backdrop) {
                ToggleRow(
                    label = "Drop Shadow",
                    checked = settings.shadowEnabled,
                    onCheckedChange = { checked -> holder.update { current -> current.copy(shadowEnabled = checked) } },
                    labelColor = labelColor,
                )
                RowDivider(color = separatorColor)
                SliderRow(
                    label = "Radius",
                    value = settings.shadowRadius,
                    onValueChange = { value -> holder.update { current -> current.copy(shadowRadius = value) } },
                    valueRange = 0f..32f,
                    valueText = dpText(settings.shadowRadius),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
                RowDivider(color = separatorColor)
                SliderRow(
                    label = "Alpha",
                    value = settings.shadowAlpha,
                    onValueChange = { value -> holder.update { current -> current.copy(shadowAlpha = value) } },
                    valueRange = 0f..1f,
                    valueText = percentText(settings.shadowAlpha),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
                RowDivider(color = separatorColor)
                ToggleRow(
                    label = "Depth Inset",
                    checked = settings.insetEnabled,
                    onCheckedChange = { checked -> holder.update { current -> current.copy(insetEnabled = checked) } },
                    labelColor = labelColor,
                )
                RowDivider(color = separatorColor)
                SliderRow(
                    label = "Inset Radius",
                    value = settings.insetRadius,
                    onValueChange = { value -> holder.update { current -> current.copy(insetRadius = value) } },
                    valueRange = 0f..24f,
                    valueText = dpText(settings.insetRadius),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
                RowDivider(color = separatorColor)
                SliderRow(
                    label = "Inset Alpha",
                    value = settings.insetAlpha,
                    onValueChange = { value -> holder.update { current -> current.copy(insetAlpha = value) } },
                    valueRange = 0f..1f,
                    valueText = percentText(settings.insetAlpha),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(22.dp))
            SectionHeader(text = "Gradient", color = secondaryColor)
            GroupCard(settings = settings, darkTheme = darkTheme, backdrop = backdrop) {
                SliderRow(
                    label = "Bottom Weight",
                    value = settings.gradientWeight,
                    onValueChange = { value -> holder.update { current -> current.copy(gradientWeight = value) } },
                    valueRange = 0f..1f,
                    valueText = "%.2f".format(settings.gradientWeight),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
                RowDivider(color = separatorColor)
                SliderRow(
                    label = "Blur Fade End",
                    value = settings.blurFadeEnd,
                    onValueChange = { value -> holder.update { current -> current.copy(blurFadeEnd = value) } },
                    valueRange = 0f..1f,
                    valueText = "%.2f".format(settings.blurFadeEnd),
                    labelColor = labelColor,
                    secondaryColor = secondaryColor,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(22.dp))
            GlassButton(
                onClick = { holder.reset() },
                text = "Reset All Settings",
                backdrop = backdrop,
                contentColor = appDestructiveColor(darkTheme),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionFooter(
                text = "Defaults: blur 8, lens 16/32, saturation 1.5, corner 22, " +
                    "specular on, shadow on, inset off, no surface tint.",
                color = secondaryColor,
            )
        }
    }
}

/**
 * Decodes a gallery image into a bitmap capped at 1920 px on the long
 * edge, so a full-resolution photo does not blow up memory. Returns null
 * when the image cannot be decoded. Decoding runs synchronously: call
 * from Dispatchers.IO.
 */
private fun decodeTempBackground(context: Context, uri: Uri): ImageBitmap? {
    return try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            decoder.setTargetSampleSize((longest / 1920f).toInt().coerceAtLeast(1))
        }.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
