// System label, accent, and separator colors shared by the
// settings-style components, plus the fixed modal and bottom-bar
// glass materials.

package com.vallxz.glass.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vallxz.glass.GlassStyle

/**
 * System label/accent colors for the settings-style components (dialog,
 * sheet, menu, stepper). They follow the iOS system palette: label black/white, secondary label at 60%, system blue,
 * system red, and the separator gray.
 */
internal fun glassLabelColor(darkTheme: Boolean): Color =
    if (darkTheme) Color.White else Color.Black

internal fun glassSecondaryLabelColor(darkTheme: Boolean): Color =
    glassLabelColor(darkTheme).copy(alpha = 0.6f)

internal fun glassAccentColor(darkTheme: Boolean): Color =
    if (darkTheme) Color(0xFF50C0FF) else Color(0xFF0073FF)

internal fun glassDestructiveColor(darkTheme: Boolean): Color =
    if (darkTheme) Color(0xFFFF453A) else Color(0xFFFF3B30)

internal fun glassSeparatorColor(darkTheme: Boolean): Color =
    if (darkTheme) Color.Gray.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.29f)

/** Accent blue for selected navigation content: the droplet's tint and a detached navigation button's selected icon. */
fun glassBarAccentColor(darkTheme: Boolean): Color =
    if (darkTheme) Color(0xFF409CFF) else Color(0xFF007AFF)

/**
 * The fixed bottom-bar material shared by the bar itself and any
 * detached navigation button beside it: 4 dp blur, a 16/28 dp lens,
 * vibrancy on, and the container tint (light gray 40% in light mode,
 * near-black 40% in dark mode). It is deliberately not tunable:
 * navigation glass looks the same everywhere.
 */
fun glassBarStyle(darkTheme: Boolean): GlassStyle = GlassStyle(
    blurRadius = 4.dp,
    refractionHeight = 16.dp,
    refractionAmount = 28.dp,
    saturation = 1.5f,
    depth = false,
    surfaceColor = if (darkTheme) {
        Color(0xFF121212).copy(alpha = 0.40f)
    } else {
        Color(0xFFFAFAFA).copy(alpha = 0.40f)
    },
    // Highlight and drop shadow keep their defaults, as on the bar.
)

/**
 * The fixed modal material shared by the dialog and the bottom sheet: 12 dp
 * blur, a 16/32 dp lens, vibrancy on, and a heavy flat surface tint (white
 * 58% in light mode, near-black 82% in dark mode). It is deliberately not
 * tunable: modals look the same everywhere.
 */
internal fun glassModalStyle(darkTheme: Boolean): GlassStyle = GlassStyle(
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
    // Highlight and drop shadow keep their defaults: without them the
    // panel reads as a flat tinted rectangle.
)
