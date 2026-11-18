// GlassSettings: the tunable glass settings, persisted to
// SharedPreferences and applied live across the app.

package com.vallxz.glass.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.decoration.GlassHighlight
import com.vallxz.glass.decoration.GlassHighlightStyle
import com.vallxz.glass.decoration.GlassInnerShadow
import com.vallxz.glass.decoration.GlassShadow

/**
 * The live-tunable glass recipe, edited on the Settings page and applied
 * to the grouped cards and preview panels across the app. The defaults
 * are the canonical recipe: 8 dp blur, a 16/32 dp lens, vibrancy on, a
 * 0.5 dp specular highlight, a 24 dp drop shadow, and no surface tint -
 * the cards lens the backdrop with nothing veiling it, and the Surface
 * Tint slider adds a wash when wanted.
 */
data class GlassSettings(
    val blurRadius: Float = 8f,
    val refractionHeight: Float = 16f,
    val refractionAmount: Float = 32f,
    val chromaticAberration: Float = 0f,
    val depth: Boolean = false,
    val cornerRadius: Float = 22f,
    val vibrancy: Boolean = true,
    val brightness: Float = 0f,
    val saturation: Float = 1.5f,
    val tintAlpha: Float = 0f,
    val highlightEnabled: Boolean = true,
    /** 0 = Default (angled), 1 = Ambient (split), 2 = Plain (flat). */
    val highlightStyle: Int = 0,
    val highlightAlpha: Float = 1f,
    val highlightWidth: Float = 0.5f,
    val shadowEnabled: Boolean = true,
    val shadowRadius: Float = 24f,
    val shadowAlpha: Float = 1f,
    val insetEnabled: Boolean = false,
    val insetRadius: Float = 8f,
    val insetAlpha: Float = 0.5f,
    val gradientWeight: Float = 1f,
    val blurFadeEnd: Float = 0.8f,
) {
    /** The grouped-card style for these settings in [darkTheme]. */
    fun toGroupStyle(darkTheme: Boolean): GlassStyle = GlassStyle(
        blurRadius = blurRadius.dp,
        refractionHeight = refractionHeight.dp,
        refractionAmount = refractionAmount.dp,
        saturation = if (vibrancy) 1.5f else saturation,
        brightness = brightness,
        depth = depth,
        surfaceColor = if (tintAlpha > 0f) {
            val wash = if (darkTheme) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
            wash.copy(alpha = tintAlpha)
        } else {
            Color.Unspecified
        },
        chromaticAberration = chromaticAberration,
        highlight = if (highlightEnabled) {
            GlassHighlight(
                width = highlightWidth.dp,
                blurRadius = (highlightWidth / 2f).dp,
                alpha = highlightAlpha,
                style = when (highlightStyle) {
                    1 -> GlassHighlightStyle.Split
                    2 -> GlassHighlightStyle.Flat
                    else -> GlassHighlightStyle.Angled
                },
            )
        } else {
            null
        },
        shadow = if (shadowEnabled) {
            GlassShadow(
                radius = shadowRadius.dp,
                offset = DpOffset(0.dp, (shadowRadius / 6f).dp),
                alpha = shadowAlpha,
            )
        } else {
            null
        },
        innerShadow = if (insetEnabled) {
            GlassInnerShadow(
                radius = insetRadius.dp,
                offset = DpOffset(0.dp, insetRadius.dp),
                alpha = insetAlpha,
            )
        } else {
            null
        },
    )
}

/** Highlight style names for the Settings dropdown, in [GlassSettings] order. */
val HighlightStyleNames: List<String> = listOf("Default", "Ambient", "Plain")

/**
 * Holds the glass settings and persists every change to SharedPreferences,
 * so the tuning survives restarts. Reads once on creation; every [update]
 * writes through immediately.
 */
class GlassSettingsHolder(context: Context) {
    private val prefs = context.getSharedPreferences("glass_settings", Context.MODE_PRIVATE)

    var settings by mutableStateOf(load())
        private set

    fun update(transform: (GlassSettings) -> GlassSettings) {
        settings = transform(settings)
        save(settings)
    }

    fun reset() {
        update { GlassSettings() }
    }

    private fun load(): GlassSettings {
        val defaults = GlassSettings()
        return GlassSettings(
            blurRadius = prefs.getFloat("blurRadius", defaults.blurRadius),
            refractionHeight = prefs.getFloat("refractionHeight", defaults.refractionHeight),
            refractionAmount = prefs.getFloat("refractionAmount", defaults.refractionAmount),
            chromaticAberration = prefs.getFloat("chromaticAberration", defaults.chromaticAberration),
            depth = prefs.getBoolean("depth", defaults.depth),
            cornerRadius = prefs.getFloat("cornerRadius", defaults.cornerRadius),
            vibrancy = prefs.getBoolean("vibrancy", defaults.vibrancy),
            brightness = prefs.getFloat("brightness", defaults.brightness),
            saturation = prefs.getFloat("saturation", defaults.saturation),
            tintAlpha = prefs.getFloat("tintAlpha", defaults.tintAlpha),
            highlightEnabled = prefs.getBoolean("highlightEnabled", defaults.highlightEnabled),
            highlightStyle = prefs.getInt("highlightStyle", defaults.highlightStyle),
            highlightAlpha = prefs.getFloat("highlightAlpha", defaults.highlightAlpha),
            highlightWidth = prefs.getFloat("highlightWidth", defaults.highlightWidth),
            shadowEnabled = prefs.getBoolean("shadowEnabled", defaults.shadowEnabled),
            shadowRadius = prefs.getFloat("shadowRadius", defaults.shadowRadius),
            shadowAlpha = prefs.getFloat("shadowAlpha", defaults.shadowAlpha),
            insetEnabled = prefs.getBoolean("insetEnabled", defaults.insetEnabled),
            insetRadius = prefs.getFloat("insetRadius", defaults.insetRadius),
            insetAlpha = prefs.getFloat("insetAlpha", defaults.insetAlpha),
            gradientWeight = prefs.getFloat("gradientWeight", defaults.gradientWeight),
            blurFadeEnd = prefs.getFloat("blurFadeEnd", defaults.blurFadeEnd),
        )
    }

    private fun save(value: GlassSettings) {
        prefs.edit()
            .putFloat("blurRadius", value.blurRadius)
            .putFloat("refractionHeight", value.refractionHeight)
            .putFloat("refractionAmount", value.refractionAmount)
            .putFloat("chromaticAberration", value.chromaticAberration)
            .putBoolean("depth", value.depth)
            .putFloat("cornerRadius", value.cornerRadius)
            .putBoolean("vibrancy", value.vibrancy)
            .putFloat("brightness", value.brightness)
            .putFloat("saturation", value.saturation)
            .putFloat("tintAlpha", value.tintAlpha)
            .putBoolean("highlightEnabled", value.highlightEnabled)
            .putInt("highlightStyle", value.highlightStyle)
            .putFloat("highlightAlpha", value.highlightAlpha)
            .putFloat("highlightWidth", value.highlightWidth)
            .putBoolean("shadowEnabled", value.shadowEnabled)
            .putFloat("shadowRadius", value.shadowRadius)
            .putFloat("shadowAlpha", value.shadowAlpha)
            .putBoolean("insetEnabled", value.insetEnabled)
            .putFloat("insetRadius", value.insetRadius)
            .putFloat("insetAlpha", value.insetAlpha)
            .putFloat("gradientWeight", value.gradientWeight)
            .putFloat("blurFadeEnd", value.blurFadeEnd)
            .apply()
    }
}
