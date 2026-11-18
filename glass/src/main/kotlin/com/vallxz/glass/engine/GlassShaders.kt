// AGSL shaders for the glass engine, embedded as Kotlin strings:
// refraction, gradient glass, edge highlight, and press ripple.

package com.vallxz.glass.engine

/**
 * AGSL shaders for the glass engine, embedded as Kotlin strings.
 *
 * Shaders are compiled once per glass surface (see [GlassEffectState]) and
 * never loaded from assets: there is no file I/O and no init ordering to
 * get wrong before the first draw.
 *
 * The refraction math is the standard rounded-box signed distance field
 * with an analytic gradient and a circular-arc displacement profile.
 * Corner radii are resolved from *centered* coordinates (negative =
 * left/top), so asymmetric corner shapes refract correctly. The effect
 * layer passes the refraction amount *negated*, so displacement points
 * along the inward gradient.
 *
 * The shaders apply no edge alpha or antialiasing of their own: visible
 * clipping comes from the shape clip applied by the draw code. Pixels
 * outside the shape get full displacement, but are clipped away.
 */
internal object GlassShaders {

    /** Rounded-box SDF + analytic gradient, shared by every shader below. */
    private const val SDF_HELPERS: String = """
        float cornerRadiusAt(float2 centered, float4 cornerRadii) {
            if (centered.x < 0.0) {
                return centered.y < 0.0 ? cornerRadii.x : cornerRadii.w;
            }
            return centered.y < 0.0 ? cornerRadii.y : cornerRadii.z;
        }

        float roundedBoxDistance(float2 centered, float2 halfSize, float radius) {
            float2 q = abs(centered) - halfSize + radius;
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
        }

        float2 roundedBoxGradient(float2 centered, float2 halfSize, float radius) {
            float2 q = abs(centered) - halfSize + radius;
            float2 outside = max(q, 0.0);
            if (outside.x > 0.0 || outside.y > 0.0) {
                return normalize(outside) * sign(centered);
            }
            if (q.x > q.y) {
                return float2(sign(centered.x), 0.0);
            }
            return float2(0.0, sign(centered.y));
        }
    """

    /**
     * Edge refraction over a blurred, color-adjusted backdrop, with
     * optional chromatic dispersion and an optional depth term.
     *
     * Uniforms:
     * - `content`: the backdrop input (bound by the runtime-shader effect).
     * - `glassSize`: glass surface size in pixels (without layer padding).
     * - `cornerRadii`: top-left, top-right, bottom-right, bottom-left, in pixels.
     * - `refractionHeight`: width of the refraction zone inward from the edge.
     * - `refractionAmount`: maximum sample displacement at the edge,
     *   negated by the effect layer.
     * - `chromaticAberration`: dispersion strength, nominally in [0, 1].
     *   Zero disables dispersion.
     * - `depthEnabled`: 1 when the depth term is on, else 0.
     * - `offset`: layer-space origin of the surface, (-padding, -padding).
     *
     * Pixels deeper than [refractionHeight] inside the shape sample
     * unmodified. Inside the band, displacement follows the circular arc
     * `1 - sqrt(1 - t^2)` where `t` runs 1 at the edge to 0 at the inner
     * edge of the band. The gradient is computed with an enlarged radius
     * (radius x 1.5, capped at the smallest half-size) so the bend direction
     * eases around corners; the depth term adds the normalized centered
     * coordinate to the gradient before normalizing.
     *
     * Dispersion is a seven-sample spectral split along the displacement
     * vector, weighted per channel, with intensity scaled by the
     * corner-weighted factor `(x * y) / (halfX * halfY)`, so fringing is
     * strongest toward the corners and absent where refraction does not
     * bend. The depth term adds the normalized centered coordinate (a unit
     * radial vector) to the gradient before normalizing. Sample
     * coordinates are clamped to the layer bounds so the edge never
     * samples transparent pixels outside the layer.
     */
    const val REFRACTION: String = SDF_HELPERS + """
        uniform shader content;
        uniform float2 glassSize;
        uniform float4 cornerRadii;
        uniform float refractionHeight;
        uniform float refractionAmount;
        uniform float chromaticAberration;
        uniform float depthEnabled;
        uniform float2 offset;

        half4 main(float2 fragCoord) {
            float2 surfaceCoord = fragCoord + offset;
            float2 halfSize = glassSize * 0.5;
            float2 centered = surfaceCoord - halfSize;
            float radius = cornerRadiusAt(centered, cornerRadii);
            float dist = roundedBoxDistance(centered, halfSize, radius);
            if (-dist >= refractionHeight) {
                return content.eval(fragCoord);
            }
            float clampedDist = min(dist, 0.0);
            float t = 1.0 + clampedDist / refractionHeight;
            float arc = 1.0 - sqrt(1.0 - t * t);
            float gradientRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 gradient = roundedBoxGradient(centered, halfSize, gradientRadius);
            if (depthEnabled > 0.5) {
                float2 centerDir = centered;
                float centerLength = length(centerDir);
                if (centerLength > 0.000001) {
                    centerDir /= centerLength;
                }
                gradient += centerDir;
                float gradientLength = length(gradient);
                if (gradientLength > 0.000001) {
                    gradient /= gradientLength;
                }
            }
            float2 displacement = gradient * (arc * refractionAmount);
            float2 layerSize = glassSize - 2.0 * offset;
            float2 baseCoord = surfaceCoord + displacement - offset;
            if (chromaticAberration <= 0.0) {
                return content.eval(clamp(baseCoord, float2(0.0), layerSize));
            }
            float intensity = chromaticAberration *
                (centered.x * centered.y) / (halfSize.x * halfSize.y);
            float2 spread = displacement * intensity;
            half4 p1 = content.eval(clamp(baseCoord + spread, float2(0.0), layerSize));
            half4 p2 = content.eval(clamp(baseCoord + spread * (2.0 / 3.0), float2(0.0), layerSize));
            half4 p3 = content.eval(clamp(baseCoord + spread * (1.0 / 3.0), float2(0.0), layerSize));
            half4 p0 = content.eval(clamp(baseCoord, float2(0.0), layerSize));
            half4 m1 = content.eval(clamp(baseCoord - spread * (1.0 / 3.0), float2(0.0), layerSize));
            half4 m2 = content.eval(clamp(baseCoord - spread * (2.0 / 3.0), float2(0.0), layerSize));
            half4 m3 = content.eval(clamp(baseCoord - spread, float2(0.0), layerSize));
            float red = (p1.r + p2.r + p3.r) / 3.5 + m3.r / 7.0;
            float green = p2.g / 7.0 + (p3.g + p0.g + m1.g) / 3.5;
            float blue = (m1.b + m2.b + m3.b) / 3.0;
            float alpha = (p1.a + p2.a + p3.a + p0.a + m1.a + m2.a + m3.a) / 7.0;
            return half4(red, green, blue, alpha);
        }
    """

    /**
     * Gradient (progressive) glass: refraction strength follows a vertical
     * profile built from three weights (top, middle, bottom), and the
     * output fades toward the tint color below a blur-fade band.
     *
     * Uniforms: `content`, `glassSize`, `cornerRadii`, `refractionHeight`,
     * `refractionAmount` (positive here, unlike the lens shader, which
     * takes it negated), `offset` as in [REFRACTION], plus:
     * - `topWeight`, `middleWeight`, `bottomWeight`: profile weights,
     *   normalized by their sum. The top term fades out over the top half,
     *   the bottom term fades in over the bottom half, the middle term is a
     *   triangle peaking at the center.
     * - `fadeStart`, `fadeEnd`: blur-fade band as fractions of the height.
     * - `tintColor`: straight (unpremultiplied) tint color whose alpha
     *   the shader scales by the blur mask, so the tint mix target fades
     *   to transparent at the top of the fade band and the panel reaches
     *   full transparency there.
     * - `tintIntensity`: strength of the tint mix.
     *
     * Displacement has two parts: a vertical offset from the profile's
     * gradient (central differences, +/-1 px) times the effective amount
     * times 0.35, plus an edge band inside the effective height (height x
     * profile) with arc-lift displacement times the effective amount times
     * 0.45 along the normalized centered direction. Output color is
     * multiplied by the blur mask, then mixed toward the tint with factor
     * `tintIntensity x (1 - mask) x 0.35`, the tint target's alpha scaled
     * by the mask so the top of the panel fades to fully transparent
     * instead of holding a uniform veil.
     */
    const val GRADIENT: String = SDF_HELPERS + """
        uniform shader content;
        uniform float2 glassSize;
        uniform float4 cornerRadii;
        uniform float refractionHeight;
        uniform float refractionAmount;
        uniform float topWeight;
        uniform float middleWeight;
        uniform float bottomWeight;
        uniform float fadeStart;
        uniform float fadeEnd;
        uniform float4 tintColor;
        uniform float tintIntensity;
        uniform float2 offset;

        float profileAt(float y, float height) {
            float ny = clamp(y / height, 0.0, 1.0);
            float sum = topWeight + middleWeight + bottomWeight;
            if (sum <= 0.0) {
                return 0.0;
            }
            float top = topWeight * (1.0 - smoothstep(0.0, 0.5, ny));
            float bottom = bottomWeight * smoothstep(0.5, 1.0, ny);
            float middle = middleWeight * (1.0 - abs(ny * 2.0 - 1.0));
            return clamp((top + middle + bottom) / sum, 0.0, 1.0);
        }

        half4 main(float2 fragCoord) {
            float2 surfaceCoord = fragCoord + offset;
            float2 halfSize = glassSize * 0.5;
            float2 centered = surfaceCoord - halfSize;
            float height = glassSize.y;
            float profile = profileAt(surfaceCoord.y, height);
            float profileGrad = (profileAt(surfaceCoord.y + 1.0, height) -
                profileAt(surfaceCoord.y - 1.0, height)) * 0.5;
            float effAmount = refractionAmount * profile;
            float effHeight = refractionHeight * profile;
            float radius = cornerRadiusAt(centered, cornerRadii);
            float depth = -roundedBoxDistance(centered, halfSize, radius);
            float2 displacement = float2(0.0, profileGrad * effAmount * 0.35);
            if (depth > 0.0 && depth < effHeight && effHeight > 0.0) {
                float t = 1.0 - depth / effHeight;
                float arc = 1.0 - sqrt(1.0 - t * t);
                float2 dir = centered;
                float dirLength = length(dir);
                if (dirLength > 0.000001) {
                    dir /= dirLength;
                }
                displacement += dir * (arc * effAmount * 0.45);
            }
            float2 layerSize = glassSize - 2.0 * offset;
            float2 sampleCoord = clamp(surfaceCoord + displacement - offset, float2(0.0), layerSize);
            half4 color = content.eval(sampleCoord);
            float mask = smoothstep(fadeStart * height, fadeEnd * height, surfaceCoord.y);
            color *= mask;
            color = mix(color, half4(tintColor.rgb, tintColor.a * mask), tintIntensity * (1.0 - mask) * 0.35);
            return color;
        }
    """

    /**
     * Edge highlight, evaluated per pixel along the shape stroke.
     *
     * Uniforms:
     * - `glassSize`, `cornerRadii`: as in [REFRACTION].
     * - `lightDirection`: unit vector pointing toward the light (top-left
     *   at 45 degrees by default).
     * - `falloff`: exponent shaping the rim falloff.
     * - `mode`: 0 = angled (color x intensity), 1 = split (black/white step
     *   by the sign of the dot product, white on the lit side).
     * - `strength`: intensity multiplier for the split mode.
     * - `highlightColor`: straight (unpremultiplied) color for the angled
     *   mode; the shader premultiplies it.
     *
     * Intensity is `|dot(SDF gradient, light direction)|` raised to
     * [falloff]. The gradient is computed with an enlarged radius
     * (radius x 1.5, capped at the smallest half-size) so the rim
     * brightness eases around corners instead of switching abruptly
     * between axes. In split mode the step puts white on the side the
     * light direction points toward (bottom/right for the default 45
     * degree direction) and black on the opposite side. Output is
     * premultiplied.
     */
    const val HIGHLIGHT: String = SDF_HELPERS + """
        uniform float2 glassSize;
        uniform float4 cornerRadii;
        uniform float2 lightDirection;
        uniform float falloff;
        uniform float mode;
        uniform float strength;
        uniform float4 highlightColor;

        half4 main(float2 fragCoord) {
            float2 halfSize = glassSize * 0.5;
            float2 centered = fragCoord - halfSize;
            float radius = cornerRadiusAt(centered, cornerRadii);
            float gradientRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 gradient = roundedBoxGradient(centered, halfSize, gradientRadius);
            float d = dot(gradient, lightDirection);
            float intensity = pow(abs(d), falloff);
            if (mode > 0.5) {
                float stepValue = d >= 0.0 ? 1.0 : 0.0;
                float alpha = intensity * strength;
                float v = stepValue * alpha;
                return half4(v, v, v, alpha);
            }
            float alpha = highlightColor.a * intensity * strength;
            return half4(highlightColor.rgb * alpha, alpha);
        }
    """

    /**
     * Press ripple glow: a radial highlight centered on the touch point,
     * fading out with a smooth S-curve from half the radius to the full
     * radius. The color uniform carries the glow color and alpha; the
     * shader only shapes it.
     */
    const val PRESS_RIPPLE: String = """
        layout(color) uniform half4 color;
        uniform float radius;
        uniform float2 position;

        half4 main(float2 fragCoord) {
            float dist = distance(fragCoord, position);
            float intensity = smoothstep(radius, radius * 0.5, dist);
            return color * intensity;
        }
    """
}
