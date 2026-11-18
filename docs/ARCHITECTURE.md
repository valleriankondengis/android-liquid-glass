# Architecture

How the Liquid Glass engine works: backdrop recording, sampling, the GPU
effect chain, the draw pipeline, decorations, shapes, and interaction.
This document describes mechanisms and invariants. For the file map, see
[STRUCTURE.md](STRUCTURE.md). For the component reference, see
[COMPONENTS.md](COMPONENTS.md). For usage patterns and rules, see
[USAGE.md](USAGE.md).

## Overview

The engine renders real glass: every surface samples the live content
behind it, runs that patch through a GPU effect chain (color, blur,
refraction), and draws the result clipped to its shape, with tint,
content, and edge decorations on top.

It uses only public Compose and Android graphics APIs: `GraphicsLayer`
recording, `RenderEffect` (API 31+), and AGSL `RuntimeShader` (API 33+).
There is no NDK, no native code, no third-party glass library, no bitmap
copies, and no per-frame loop. The minimum SDK is 33, so every API the
engine needs is guaranteed present.

Three principles run through the whole engine:

1. **Draw-driven, never loop-driven.** Backdrops re-record when their
   content draws. Surfaces re-sample when their coordinates or style
   change. Nothing polls, ticks, or re-renders on a clock, so an idle
   screen costs nothing.
2. **Animation state is read in the draw phase.** Press progress,
   velocity, and animated values are read inside draw lambdas and style
   providers, never during composition. An animation tick invalidates a
   draw, not a recomposition.
3. **Sampling is exact.** The sampled patch is aligned to the real
   content pixel-for-pixel, including under graphicsLayer scale, through
   an inverse transform. Approximation here reads as drifting, doubling
   text inside the lens.

## The render pipeline

One draw of one glass surface runs five stages:

1. **Backdrop recording.** Each backdrop is a `GraphicsLayer` whose
   content is recorded by a `glassBackdrop` modifier on the background
   node, on every draw of that node. See
   [Backdrops](#backdrops) below.
2. **Sampling.** The surface records its effected layer: every sampled
   backdrop layer is drawn into the surface's glass layer, translated by
   the negative offset of the surface origin inside that backdrop, with
   the inverse layer scale applied. See
   [Sampling math](#sampling-math) below.
3. **Effect chain.** The glass layer carries a `RenderEffect` built from
   the style: color adjustment, blur, then the refraction or gradient
   shader. The effect applies to the recorded patch only, never to the
   surface's content. See [Effect chain](#effect-chain) below.
4. **Draw.** The effected layer is drawn clipped to the shape, then the
   tint and surface fill, then the content (also clipped), then the edge
   highlight and inner shadow above the content. The drop shadow is drawn
   first, underneath everything, clipped to outside the shape.
5. **Compositing.** The surface's own graphicsLayer transforms (scale,
   alpha, translation) apply at composite time. Sampling already accounts
   for scale through the inverse transform; translations need no
   accounting because the offset math uses transformed coordinates.

Draw stacking, bottom to top: drop shadow, effected backdrop layer,
surface tint and solid fill, content, edge highlight, inner shadow.

Until every sampled backdrop has recorded its first frame, only the tint
and decorations are drawn, so a surface never flashes empty content.

## Backdrops

A `GlassBackdrop` is a recorded layer of background content plus the
coordinates that locate it. Surfaces sample backdrops; they never sample
each other directly.

| API | Purpose |
|---|---|
| `rememberGlassBackdrop()` | Creates a backdrop. One per recorded layer. |
| `Modifier.glassBackdrop(backdrop)` | Records everything the node draws into the backdrop's layer, on every draw, then draws the node normally. |
| `rememberGlassMergedBackdrop(primary, secondary)` | Samples two layers as one: the secondary is drawn over the primary. Records nothing itself. |
| `rememberGlassWrappedBackdrop(inner, scale)` | Samples one layer scaled about the sampling surface's center. Records nothing itself. |
| `LocalGlassBackdrop` | Ambient backdrop. A surface with no explicit backdrop samples the ambient one. |
| `GlassSurface(exportBackdrop = true)` | Exports the surface's glass background (effected patch + tint only) as a backdrop for nested surfaces, and provides it as the ambient backdrop for its content. |

### Recording rules

- **Apply `glassBackdrop` to background content only.** Glass surfaces
  sampling a backdrop must be siblings drawn after the backdrop node in
  the same parent, never children of it.
- **Only content drawn inside the modifier is recorded**: children of
  the node, and draw modifiers placed after `glassBackdrop` in the chain.
  A `background` or `drawBehind` placed before it draws outside the
  recording, and every surface sampling that backdrop shows a transparent
  patch. Put backgrounds inside the node, as child content or as an inner
  modifier.
- **A backdrop layer must never record content that can contain a
  surface sampling it.** That is a RenderNode cycle: the export layer
  would contain itself, transitively, and the RenderThread dies in
  native code. This is why an exporting surface records only its glass
  background into the export layer, never its content, and never its
  decorations.

### Merged and wrapped sources

A merged backdrop samples each source with its own offset, so layers at
different positions compose correctly. The template app merges a
background layer and a screen layer so bars and modal overlays sample
both.

A wrapped backdrop scales one source about the sampling surface's
center, independently of the other sources: a slider track collapsing
vertically as the thumb is pressed, a segmented track shrinking as it
is pressed, a tab row shrinking inside the bottom-bar droplet. The wrap
scale
is a draw-phase provider, so animation state inside it invalidates the
draw only.

### Nested glass

An exporting surface (`GlassCard` with `exportBackdrop = true`, for
example) lets glass nested inside it sample the outer surface's real
glass background instead of the root backdrop, so glass-on-glass looks
like glass instead of a hole. The export layer holds the effected patch
and tint only. Content, shadow, highlight, and inner shadow stay out:
content may hold nested samplers (cycle rule, above), and decorations
are per-surface.

## Sampling math

Each backdrop layer is recorded in its own coordinates. To sample it,
the surface computes the offset of its own origin inside the backdrop
with `LayoutCoordinates.localPositionOf`, falling back to unclipped
window bounds when the coordinate transform fails. The source layer is
drawn translated by the negative offset.

Coordinates are snapshot state with a never-equal policy: the
coordinates object is the same instance across placements, so the
default policy would swallow every move and the surface would keep
sampling a stale offset while scrolling. Never-equal invalidates the
draw on every placement.

### Inverse layer scale

When a surface is scaled by a graphicsLayer, its coordinates already
include the transform, so the offset is the transformed origin. The
recording applies the inverse scale (1/scale) about the recording
origin, outside the offset translation, so the sampled patch cancels the
visible scale exactly: without it, the sampled content would scale along
with the node while the real content outside does not, and text inside
the lens would drift and double while pressed.

The inverse is pivot-agnostic: it holds for any graphicsLayer transform
origin. Only scale is inverted. Translations need no inverse, and
rotation is not supported. Any surface that scales itself must report
exactly the scale its graphicsLayer sets through the `layerScale`
provider (1,1 when unscaled). A transform that is not reported is
invisible to sampling.

Wrapped sources apply their own sampling scale around the surface
center, wrapping the inverse scale and offset translation for that
source only.

### Sample scale

`sampleScale` magnifies the sampled content around the surface center
(1 = no magnification): sources are drawn scaled by the value about the
center when the glass layer is recorded, so each surface pixel samples a
smaller backdrop region.

## Effect chain

`GlassEffectState` builds one cached GPU effect per surface. Chain
order, innermost first: color adjustment, then blur, then the refraction
shader (or the gradient shader when the style carries a gradient
profile). Effects whose parameters are neutral are skipped: a style with
no blur and no color change yields only the shader effect.

- **Color.** A color-matrix filter combining brightness, contrast, and
  saturation. Saturation uses the luminance weights R 0.213, G 0.715,
  B 0.072.
- **Blur.** A blur effect with Clamp tiling.
- **Lens.** A runtime shader (see [Shaders](#shaders) below). The
  refraction amount is passed negated, so displacement points along the
  inward gradient. The gradient shader receives it positive.

### Padding

The glass layer is recorded larger than the surface by the effect
padding on all sides, and drawn shifted back by the padding, so blur
samples at the edge read real pixels instead of clamped ones. Padding
grows to the blur radius only when a color effect precedes the blur in
the chain (a blur-first chain with Clamp tiling needs no margin), and
the lens shrinks it by the refraction height, clamped at zero, because
refraction samples inward. Gradient glass always keeps padding zero.
The shader's `offset` uniform (-padding, -padding) keeps its centered
coordinates aligned with the surface inside the enlarged layer.

### Caching

The chain is rebuilt only when the style, size, corner radii, blur, or
refraction values change (the effect key). The compiled `RuntimeShader`
instances are reused across rebuilds; uniforms are refreshed on every
rebuild. The built effect is assigned to the glass layer only when the
instance actually changes, so assigning the same effect on every draw
does not mark the layer dirty for no reason.

## Shaders

All shaders are AGSL embedded as Kotlin strings and compiled once per
surface. There is no asset loading and no init ordering to get wrong.
AGSL requires declarations before use; helpers are concatenated before
the shader body and take values as parameters.

The refraction math is the standard rounded-box signed distance field
with an analytic gradient and a circular-arc displacement profile
(`1 - sqrt(1 - t^2)`), where `t` runs 1 at the edge to 0 at the inner
edge of the refraction band. Pixels deeper than the refraction height
inside the shape sample unmodified. Corner radii resolve from centered
coordinates (negative = left/top), so asymmetric corner shapes refract
correctly. The gradient is computed with an enlarged radius (radius
x 1.5, capped at the smallest half-size) so the bend direction eases
around corners.

- **Refraction.** Edge refraction with optional chromatic dispersion and
  an optional depth term. Dispersion is a seven-sample spectral split
  along the displacement vector (+1, +2/3, +1/3, 0, -1/3, -2/3, -1),
  weighted per channel, with intensity scaled by a corner-weighted
  factor so fringing is strongest toward the corners and absent where
  refraction does not bend. The depth term adds the normalized centered
  coordinate to the gradient before normalizing. Sample coordinates are
  clamped to the layer bounds so the edge never samples transparent
  pixels. The shader applies no edge alpha or antialiasing of its own:
  visible clipping comes from the shape clip in the draw code.
- **Gradient.** Progressive glass: refraction strength follows a
  vertical profile built from three weights (top, middle, bottom), and
  the output fades toward the tint color below a blur-fade band.
  Displacement has a vertical profile-gradient part and an edge-band
  part along the normalized centered direction.
- **Highlight.** Evaluated per pixel along the shape stroke: brightness
  follows the dot product between the shape's edge normal and the light
  direction, raised to the falloff. See
  [Decorations](#decorations) below.
- **Press ripple.** A radial highlight centered on the touch point,
  fading out with a smooth S-curve from half the radius to the full
  radius. The color uniform carries the glow color and alpha; the shader
  only shapes it.

## Styles

`GlassStyle` is an immutable data class. Every field has a default, and
styles are customized with `copy`. The canonical defaults are: 8 dp
blur, 16/32 dp lens, saturation 1.5, no depth term, tint alpha 0.28,
highlight and drop shadow on, no inner shadow, no gradient.

| Field | Meaning |
|---|---|
| `blurRadius` | Backdrop blur radius. |
| `refractionHeight` | Width of the refraction band inward from the edge. |
| `refractionAmount` | Maximum sample displacement at the edge. |
| `saturation`, `contrast`, `brightness` | Color adjustment of the sampled patch. |
| `depth` | Depth term in the refraction shader. Default false; the depth effect belongs to droplets and thumbs, not to bars and panels. |
| `tint`, `tintAlpha` | Colored tint: a hue blend at full strength plus a solid overlay at `tintAlpha`. A colored tint re-hues the backdrop instead of veiling it. |
| `surfaceColor` | Plain solid fill over the glass. Independent of `tint`: use one or the other for a given surface. |
| `chromaticAberration` | Dispersion strength, 0..1. Zero disables dispersion. |
| `highlight`, `shadow`, `innerShadow` | Decorations, or null to disable. |
| `gradient` | Gradient profile, or null for uniform refraction. Gradient styles draw neither the tint nor the surface fill: the tint lives only inside the gradient shader's fade mix. |

Presets: `Default` (balanced), `Clear` (almost no blur, strong lens),
`Frosted` (heavy blur, milky tint), `Vibrant` (oversaturated),
`Dark` (smoked tint). Two fixed materials live in the components
package: `glassBarStyle` (the bottom-bar material, shared with detached
navigation buttons) and the modal material shared by the dialog and the
bottom sheet. Fixed materials are deliberately not tunable: navigation
glass and modal glass look the same everywhere.

## Decorations

Decorations are data classes (`GlassHighlight`, `GlassShadow`,
`GlassInnerShadow`) plus draw functions. Callers resolve every Dp to
pixels in the outer draw scope: decoration code may run inside a layer
record block, where any Density access on the record receiver recurses
forever and crashes.

- **Edge highlight.** A single stroke along the shape path, twice the
  highlight width, drawn inside the shape clip so only its inner half is
  visible. The stroke carries a true blur mask filter, so the rim
  softens evenly. Three styles: `Angled` (brightness from the dot
  product with a 45-degree light, white at 50%, additive), `Split`
  (black/white step from the sign of the dot product, 0.38 intensity),
  `Flat` (solid white stroke, no shader).
- **Drop shadow.** A blurred fill of the shape path, clipped to the area
  outside the path (Difference clip), so the shadow never darkens the
  glass interior. Defaults: 24 dp radius, offset (0, 4 dp), black 10%.
- **Inner shadow.** A sharp fill of the shape, punched out by a sharp
  clear-blend copy translated by the shadow offset, recorded into an
  offscreen layer, with the blur applied to the whole layer as a blur
  effect, so both edges of the crescent fade smoothly. Blurring only the
  fill and punching sharp would leave a hard line at the punch boundary.
  The layer carries the shadow alpha, so alpha-only changes never
  re-record. Defaults: 24 dp radius, offset (0, radius), black 15%.

## Shapes

The lens supports only rounded-rectangle shapes: the shader's signed
distance field assumes circular corner arcs. `CornerBasedShape`
(including `RoundedCornerShape`), `CircleShape`, and `RectangleShape`
resolve to four corner radii in pixels, clamped to half the smallest
dimension; anything else fails loudly instead of refracting the wrong
shape. The same resolution feeds the shader uniforms and the clip path,
so the refraction and the visible outline always agree.

The shape outline (clip path + corner radii) is cached on the surface
state, keyed by shape, size, layout direction, density, and font scale,
and rebuilt only when the key changes. The path is only ever read, never
mutated, so reuse across draws is safe.

## Interaction

Two shared systems drive every interactive component: the gesture
detector and the motion engine. Components share them so press, drag,
release, and haptics feel identical everywhere.

### Gesture detection

`detectGlassDrag` is the single drag detector. It awaits the pointer
down twice (Initial pass, then Main, both with unconsumed changes
allowed), fires `onDragStart` with the down position, then fires one
zero-delta `onDrag` before tracking movement. It transfers to another
pressed pointer when the tracked pointer goes up, and it never consumes
changes. A cancelled gesture runs the same stop handler as a normal end:
there is no separate cancel path, so a gesture stolen by a parent still
snaps instead of leaving the component mid-press.

### Motion engine

`GlassMotion` holds the animated value, press progress, press scales,
and velocity as animation holders, driven by springs:

- Press: stiffness 1.0, damping 1000. Scale X: 0.6/250. Scale Y:
  0.7/250. Value: 1.0/1000. Velocity smoothing: 0.5/300.
- Drag retargets are fire-and-forget: each callback launches a fresh
  animation toward the new target, and superseding is delegated to the
  animation primitive.
- Release sequencing: the stop handler runs first (snapping decisions
  and state callbacks fire synchronously, before the release visuals),
  then the release waits one frame, waits until the value is within 2.5%
  of the value-range span of its target, and only then fires the
  press/scale animations back to rest, without waiting for them.
- `moveTo` (tap-to-jump) runs the same press and release sequence, but
  the value animation is only launched, never awaited, and carries no
  velocity feed, so no squash appears during a tap jump. With value
  snapping, the value jumps instantly and only the press pulse animates
  in place: for a thumb that was hidden and must appear at the tapped
  position.
- `syncTo` follows external state changes while idle, and returns early
  while pressed, so a programmatic change never fights an active
  gesture.
- Tap-vs-drag classification uses a small movement dead-zone (2 dp).
  The dead-zone gates only the classification, never the drag response:
  the value follows every delta immediately. There is no fling; velocity
  feeds squash only.

### Press and haptics

Buttons share a press system (`GlassButtonPress`) with position-aware
ripple glow and drag translation. Haptics go through one helper with
platform constants, guarded by API level, and failures are swallowed:
haptics must never break a gesture.

## Invalidation model

- Backdrop re-recordings do not invalidate surface draws. At API 33+,
  the renderer picks up a re-recorded layer's new content through the
  render-node reference graph, so a fixed surface shows fresh backdrop
  pixels without re-recording its effected layer. Reading a recording
  version in the draw would force every surface to re-record on every
  backdrop recording and jank scrolling.
- Style, sample scale, and layer scale arrive as providers evaluated
  inside the draw block. Effect rebuilds are gated on the effect key.
  Outline rebuilds are gated on the outline key. Inner-shadow
  re-recordings are gated on radius, offset, color, and outline, with
  alpha riding the layer.
- The backdrop node re-records unconditionally on every draw of that
  node. No version counter is needed at API 33+: freshness is the
  RenderNode reference graph.

## Capabilities and adaptive luminance

`GlassCapabilities` centralizes the API floor: render effects need
API 31+, runtime shaders need API 33+. The engine degrades honestly
(skips an effect) instead of crashing if the floor ever moves.

`GlassAdaptive.adapt` retunes a style for a backdrop luminance (0 =
black, 1 = white): brightness, contrast, and blur shift with a
signed-square remap so the response stays gentle near mid-gray.
`rememberGlassLuminance` samples a backdrop's mean luminance. Both are
public API; the template app does not wire them into Settings, so they
are available for projects that want backdrop-aware styles.

## Invariants

The rules the engine depends on. Breaking one breaks the glass, often
in native code or in ways that only show on device:

1. An export/backdrop layer must never record content that can contain
   a surface sampling it (RenderNode cycle).
2. No Density API (`.toPx()`, `.dp`, `density`) on a record block's
   DrawScope receiver (StackOverflowError). `recordGlassLayer` is the
   single sanctioned recording path, and callers resolve Dp outside it.
3. Backdrop recording is draw-driven only. No frame-clock loop, no
   polling.
4. Press scales are inverted in sampling through the reported layer
   scale. A scale that is not reported is invisible to sampling.
5. Style, sample-scale, and layer-scale providers are evaluated in the
   draw phase, not during composition.
6. A background modifier before `glassBackdrop` draws outside the
   recording. Backgrounds go inside the node.
7. AGSL requires declarations before use; shader helpers take values as
   parameters.
8. A surface's fade alpha and press scale must share one graphicsLayer.
   A separate outer alpha layer allocates a layout-sized buffer that
   clips the scaled surface to a rectangle while fading.
9. Shaders apply no edge alpha or antialiasing. All visible shaping
   comes from the shape clip in the draw code.

The usage rules built on these invariants, including what may and may
not be changed, are in [USAGE.md](USAGE.md).

## Known simplifications

Behavior choices kept honest, so nobody mistakes them for bugs:

- Continuous-curvature (squircle) corners are not implemented; capsules
  use circular arcs.
- The segmented droplet's speed stretch derives from the motion
  engine's smoothed velocity instead of a dedicated second-order
  integrator. Its overdrag is active, with exponential resistance past
  the ends.
- Slider, range slider, and toggle respond to presses anywhere in the
  component area, and a tap on the thumb jumps the value. The
  containers are taller (36 dp) so the grown thumb stays inside.
- The ruler selector has no fling and no appear animation, and reports
  its value only while dragging.
- The popup menu lives in the caller's composition as a full-screen
  overlay (it needs the anchor bounds and a root-level placement),
  instead of a separate dialog window.
- The bottom sheet has no drag-to-dismiss.
- The default background is plain white in light mode and black in dark
  mode, with the grouped cards and rows providing the detail the lens
  refracts. The template's gallery import is a temporary, session-only
  backdrop preview; there is no persisted wallpaper. The text magnifier
  is not included.
- The luminance probe is best-effort: a failed probe keeps the last
  value. Nested glass composes, but each level costs a layer.
