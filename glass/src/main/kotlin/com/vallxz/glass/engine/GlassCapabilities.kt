// Capability checks for the platform graphics features the glass
// engine needs.

package com.vallxz.glass.engine

import android.os.Build

/**
 * The single place that knows which platform graphics capabilities exist.
 *
 * The glass engine must never check [Build.VERSION.SDK_INT] anywhere else:
 * keeping every capability gate here is what makes lowering `minSdk` later a
 * contained change instead of a hunt through the engine.
 *
 * With the current `minSdk` of 33 both capabilities are always available, so
 * these checks cost nothing in practice today. They exist so the engine can
 * degrade honestly (skip an effect) instead of crashing if the floor ever moves.
 */
internal object GlassCapabilities {

    /** `RenderEffect` (backdrop blur, color filters) needs API 31+. */
    val renderEffectSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** AGSL runtime shaders (lens refraction, dispersion) need API 33+. */
    val runtimeShaderSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
