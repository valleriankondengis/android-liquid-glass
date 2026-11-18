// GlassEffectChain: builds the cached render effect for a style -
// color adjustment, blur, then the lens or gradient shader.

package com.vallxz.glass.engine

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.effects.ColorMatrices
import com.vallxz.glass.shapes.CornerRadii

/**
 * The built GPU effect for one glass surface, plus the layer padding the
 * draw code must apply around the recording.
 */
internal data class BuiltGlassEffect(
    val effect: ComposeRenderEffect?,
    val paddingPx: Float,
)

/**
 * Builds the GPU effect chain for one glass surface and caches its compiled
 * shaders.
 *
 * Chain order, innermost first: color adjustment, then blur, then the
 * refraction shader (or the gradient shader when the style carries a
 * gradient profile). Effects whose parameters are neutral are skipped, so a
 * style with no blur and no color change yields only the shader effect,
 * and a style with no refraction yields only blur/color.
 *
 * One instance is remembered per glass surface. The [RuntimeShader]s are
 * compiled once; the chain itself is rebuilt only when the style, size, or
 * corner radii change. Uniforms are refreshed on every rebuild. The
 * refraction amount is passed to the lens shader *negated*, so
 * displacement points along the inward gradient; the gradient shader
 * receives it positive.
 *
 * Layer padding: the glass layer is recorded larger than the surface by
 * [BuiltGlassEffect.paddingPx] on all sides, because blur samples beyond
 * the edge. Padding grows to the blur radius only when a color effect
 * precedes the blur in the chain (a blur-first chain with Clamp tiling
 * needs no margin), and the lens shrinks it by the refraction height
 * (clamped at zero), because refraction samples inward. Gradient glass
 * always keeps padding zero: its blur runs first with no prior effect.
 * The shader's offset uniform (-padding, -padding) keeps its centered
 * coordinates aligned with the surface inside the enlarged layer.
 */
internal class GlassEffectState {

    private var refractionShader: RuntimeShader? = null
    private var gradientShader: RuntimeShader? = null
    private var cachedKey: EffectKey? = null
    private var cachedEffect: BuiltGlassEffect? = null

    fun buildEffect(
        style: GlassStyle,
        widthPx: Float,
        heightPx: Float,
        radii: CornerRadii,
        blurPx: Float,
        refractionHeightPx: Float,
        refractionAmountPx: Float,
    ): BuiltGlassEffect {
        val key = EffectKey(style, widthPx, heightPx, radii, blurPx, refractionHeightPx, refractionAmountPx)
        cachedEffect?.let { if (key == cachedKey) return it }

        val built = buildChain(style, widthPx, heightPx, radii, blurPx, refractionHeightPx, refractionAmountPx)
        cachedKey = key
        cachedEffect = built
        return built
    }

    private fun buildChain(
        style: GlassStyle,
        widthPx: Float,
        heightPx: Float,
        radii: CornerRadii,
        blurPx: Float,
        refractionHeightPx: Float,
        refractionAmountPx: Float,
    ): BuiltGlassEffect {
        val refractionActive = GlassCapabilities.runtimeShaderSupported &&
            refractionHeightPx > 0f && refractionAmountPx > 0f
        val hasColorEffect = style.saturation != 1f || style.contrast != 1f ||
            style.brightness != 0f
        // Padding grows to the blur radius only when a color effect
        // precedes the blur: a blur-first chain with Clamp tiling needs no
        // margin, and the lens shrinks whatever padding exists by the
        // refraction height. Gradient glass always keeps padding zero:
        // its blur runs first with no prior effect and Clamp mode.
        val lensShrinkPx = if (refractionActive) refractionHeightPx else 0f
        val paddingPx = if (style.gradient != null || !hasColorEffect) {
            0f
        } else {
            (blurPx - lensShrinkPx).coerceAtLeast(0f)
        }
        if (!GlassCapabilities.renderEffectSupported) {
            return BuiltGlassEffect(effect = null, paddingPx = paddingPx)
        }

        var effect: RenderEffect? = null

        if (hasColorEffect) {
            val matrix = ColorMatrix(
                ColorMatrices.colorControls(
                    brightness = style.brightness,
                    contrast = style.contrast,
                    saturation = style.saturation,
                ),
            )
            effect = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
        }

        if (blurPx > 0f) {
            val blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            effect = if (effect == null) blur else RenderEffect.createChainEffect(blur, effect)
        }

        if (refractionActive) {
            val gradient = style.gradient
            val shaderEffect = if (gradient != null) {
                val runtimeShader = gradientShader
                    ?: RuntimeShader(GlassShaders.GRADIENT).also { gradientShader = it }
                runtimeShader.setFloatUniform("glassSize", widthPx, heightPx)
                runtimeShader.setFloatUniform(
                    "cornerRadii",
                    radii.topLeft,
                    radii.topRight,
                    radii.bottomRight,
                    radii.bottomLeft,
                )
                runtimeShader.setFloatUniform("refractionHeight", refractionHeightPx)
                // Gradient glass takes the amount positive, unlike the
                // lens shader below, which takes it negated.
                runtimeShader.setFloatUniform("refractionAmount", refractionAmountPx)
                runtimeShader.setFloatUniform("topWeight", gradient.topWeight)
                runtimeShader.setFloatUniform("middleWeight", gradient.middleWeight)
                runtimeShader.setFloatUniform("bottomWeight", gradient.bottomWeight)
                runtimeShader.setFloatUniform("fadeStart", gradient.fadeStart)
                // A zero-width fade band makes smoothstep undefined.
                runtimeShader.setFloatUniform(
                    "fadeEnd",
                    gradient.fadeEnd.coerceAtLeast(gradient.fadeStart + 0.01f),
                )
                val tint = style.tint
                if (tint != Color.Unspecified) {
                    // Straight (unpremultiplied): the shader scales the
                    // tint target's alpha by the blur mask itself.
                    val alpha = tint.alpha * style.tintAlpha
                    runtimeShader.setFloatUniform(
                        "tintColor",
                        tint.red,
                        tint.green,
                        tint.blue,
                        alpha,
                    )
                } else {
                    runtimeShader.setFloatUniform("tintColor", 0f, 0f, 0f, 0f)
                }
                runtimeShader.setFloatUniform("tintIntensity", gradient.tintIntensity)
                runtimeShader.setFloatUniform("offset", -paddingPx, -paddingPx)
                RenderEffect.createRuntimeShaderEffect(runtimeShader, "content")
            } else {
                val runtimeShader = refractionShader
                    ?: RuntimeShader(GlassShaders.REFRACTION).also { refractionShader = it }
                runtimeShader.setFloatUniform("glassSize", widthPx, heightPx)
                runtimeShader.setFloatUniform(
                    "cornerRadii",
                    radii.topLeft,
                    radii.topRight,
                    radii.bottomRight,
                    radii.bottomLeft,
                )
                runtimeShader.setFloatUniform("refractionHeight", refractionHeightPx)
                runtimeShader.setFloatUniform("refractionAmount", -refractionAmountPx)
                runtimeShader.setFloatUniform(
                    "chromaticAberration",
                    style.chromaticAberration.coerceIn(0f, 1f),
                )
                runtimeShader.setFloatUniform("depthEnabled", if (style.depth) 1f else 0f)
                runtimeShader.setFloatUniform("offset", -paddingPx, -paddingPx)
                RenderEffect.createRuntimeShaderEffect(runtimeShader, "content")
            }
            effect = if (effect == null) {
                shaderEffect
            } else {
                RenderEffect.createChainEffect(shaderEffect, effect)
            }
        }

        return BuiltGlassEffect(effect = effect?.asComposeRenderEffect(), paddingPx = paddingPx)
    }

    private data class EffectKey(
        val style: GlassStyle,
        val widthPx: Float,
        val heightPx: Float,
        val radii: CornerRadii,
        val blurPx: Float,
        val refractionHeightPx: Float,
        val refractionAmountPx: Float,
    )
}
