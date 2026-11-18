# Usage

Patterns for using the engine, rules for what may and may not be done,
and the protocol for the rare case where engine code must be edited.
For how the engine works, see [ARCHITECTURE.md](ARCHITECTURE.md). For
the file map, see [STRUCTURE.md](STRUCTURE.md). For the component
reference, see [COMPONENTS.md](COMPONENTS.md).

## Quick start

### 1. Add the module

Copy the `glass/` directory into the project, add it in
`settings.gradle.kts`, and depend on it from the app module:

```kotlin
// settings.gradle.kts
include(":glass")

// app/build.gradle.kts
dependencies {
    implementation(project(":glass"))
}
```

Requirements: minSdk 33, compileSdk 37 with minor 1, Kotlin 2.5.0-RC,
AGP 9.6.0-alpha03, Compose BOM 2026.10.01 (artifact
`compose-bom-alpha`), coroutines 1.11.0. The engine needs no other
dependencies and no manifest entries beyond what a normal Compose app
already has.

### 2. Set up backdrops

A glass surface samples a backdrop. The minimal setup is one backdrop
for the background, with glass surfaces as siblings drawn after it:

```kotlin
val backgroundBackdrop = rememberGlassBackdrop()

Box(Modifier.fillMaxSize()) {
    // Background node: records into the backdrop.
    Box(
        Modifier
            .fillMaxSize()
            .glassBackdrop(backgroundBackdrop)
    ) {
        // Background content goes INSIDE the node: children, or draw
        // modifiers placed after glassBackdrop in the chain.
    }

    // Glass surfaces: siblings, drawn after the background node.
    GlassCard(
        backdrop = backgroundBackdrop,
        modifier = Modifier.align(Alignment.Center),
    ) {
        // Content.
    }
}
```

### 3. Use components

```kotlin
var level by remember { mutableFloatStateOf(0.45f) }

GlassSlider(
    value = level,
    onValueChange = { level = it },
    backdrop = backgroundBackdrop,
)
```

Pass the backdrop explicitly, or provide it once with
`LocalGlassBackdrop` and let surfaces resolve it ambiently. An explicit
backdrop always wins over the ambient one.

## Patterns

### Two-layer layout

The template app uses two recording layers: a background layer (the
plain background or an image) and a screen layer (the page content).
In-screen glass samples the background layer; bars and modal overlays
sample a merged backdrop of both, so they refract the page content too:

```kotlin
val backgroundBackdrop = rememberGlassBackdrop()
val screenBackdrop = rememberGlassBackdrop()
val mergedBackdrop = rememberGlassMergedBackdrop(backgroundBackdrop, screenBackdrop)

Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().glassBackdrop(backgroundBackdrop)) { /* background */ }
    Box(Modifier.fillMaxSize().glassBackdrop(screenBackdrop)) { /* page content */ }
    GlassBottomBar(tabs = tabs, selectedIndex = tab, onSelected = { tab = it },
        backdrop = mergedBackdrop, /* ... */)
    AppOverlays(backdrop = mergedBackdrop)
}
```

Keep the recording nodes full-bleed with no padding inside them: padding
belongs on the page content, not on the layer being sampled. Only the
top bar is inset for the status bar.

### Nested glass

When glass sits inside glass (a toggle inside a card, a slider inside a
panel), the outer container exports its glass background so the inner
glass samples real glass instead of a hole:

```kotlin
GlassCard(exportBackdrop = true, backdrop = backgroundBackdrop) {
    GlassToggle(checked = checked, onCheckedChange = { checked = it })
    // No backdrop passed: the toggle resolves the card's exported
    // backdrop ambiently.
}
```

Exporting is opt-in per surface. Do not export a surface whose content
can contain a sampler of the same export: that is the RenderNode cycle
(see [ARCHITECTURE.md](ARCHITECTURE.md)).

### Detached navigation button

When a page lives outside the bar's tabs (a separate Search page, as in
the template app), the app owns two pieces of state: the active page and
the pill's own selection. The droplet hides while the outside page is
active:

```kotlin
var tab by remember { mutableIntStateOf(0) }       // 0 = Home, 1 = About, 2 = Search
var pillTab by remember { mutableIntStateOf(0) }   // untouched while Search is active

Row(/* ... */) {
    GlassBottomBar(
        tabs = listOf(homeTab, aboutTab),
        selectedIndex = pillTab,
        onSelected = { pillTab = it; tab = it },
        backdrop = mergedBackdrop,
        showDroplet = tab != 2,
        modifier = Modifier.weight(1f).zIndex(1f), // pill above the button: press growth stays visible
    )
    GlassIconButton(
        onClick = { tab = 2 },
        icon = searchIcon,
        contentDescription = "Search",
        backdrop = mergedBackdrop,
        style = glassBarStyle(darkTheme),          // the bar's own material: no mismatch
        contentColor = if (tab == 2) glassBarAccentColor(darkTheme) else Color.White,
        size = 64.dp,
    )
}
```

Rules for this pattern:

- `showDroplet` means exactly "the active page is one of the bar's
  tabs". While it is false, the bar reports every tap, and a tap makes
  the droplet appear at the tapped tab.
- The pill needs a higher z-order than the side button, or press growth
  slides under the button.
- The side button uses `glassBarStyle` and `glassBarAccentColor` so it
  reads as part of the navbar. A button-styled button next to the bar
  looks mismatched.

### Modal overlays

Dialogs, sheets, and menus render in one overlay host, as siblings after
the screen node, sampling the merged backdrop. Keep the last request
while the exit animation runs, so the overlay animates out instead of
vanishing. The template app's `AppOverlays` shows the pattern:
`DialogRequest` / `MenuRequest` state plus `showDialog` / `dismissDialog`
and friends.

### Settings-driven styles

To let a settings screen retune the glass live, hold one settings object
in state, derive a `GlassStyle` from it (`toGroupStyle` in the template
app), and pass that style to the surfaces. Style providers evaluate in
the draw phase, so a settings change re-styles without recomposing the
page content. Fixed materials (bar, modals) stay fixed: settings tune
cards and panels, not navigation or modal glass.

### Theming

The engine has no theme of its own. Components read
`isSystemInDarkTheme()` where a recipe depends on it, and take
`contentColor` for their content. The template app resolves label,
secondary, accent, and separator colors per theme and passes them down.
Content colors left `Color.Unspecified` resolve to the theme label
color (white in dark mode, black in light mode); pass an explicit color
to override.

## Rules

### Allowed

- Compose new screens, pages, and layouts from the public components.
- Build new components on `GlassSurface` or `Modifier.glass`, following
  the conventions in [COMPONENTS.md](COMPONENTS.md): hoisted state,
  optional `backdrop`, optional `haptic`, draw-phase style providers for
  animation state, `layerScale` reporting for any graphicsLayer scale.
- Customize glass with `GlassStyle.copy` and the presets. Override
  tint, surface color, blur, lens, saturation, decorations, and
  gradients per surface.
- Add backdrop layers, merge them, and wrap sources with draw-phase
  scale providers.
- Add tabs, pages, menus, dialogs, and sheets through the app-level
  patterns above.
- Read backdrop luminance with `rememberGlassLuminance` and retune
  styles with `GlassAdaptive.adapt`.
- Add dependencies to the app module for app features. The `:glass`
  module stays dependency-minimal.

### Not allowed

- **Do not edit engine files.** The `engine/`, `interaction/`, and
  `shapes/` packages, plus `GlassDraw`, `GlassEffectChain`,
  `GlassShaders`, `GlassRecord`, and `GlassBackdrop`, are the glass
  fundamentals. Changing them without understanding every invariant in
  [ARCHITECTURE.md](ARCHITECTURE.md) breaks sampling, refraction, or
  the RenderThread, often only on device. This includes "small" changes
  to padding, offsets, transform order, cache keys, shader uniforms,
  and spring values. See the forced-edit protocol below for the only
  exception.
- **Do not create a recording cycle.** Never record content that can
  contain a surface sampling the same backdrop. Never export a surface
  whose content samples its own export.
- **Do not put Density API calls inside a record block.** No `.toPx()`,
  `.dp`, or `density` on a record block's DrawScope receiver. Resolve
  Dp to pixels in the outer draw scope.
- **Do not add frame loops, polling, or bitmap copies** to keep glass
  fresh. Freshness is draw-driven; a loop janks the whole screen.
- **Do not put a background modifier before `glassBackdrop`.**
  Backgrounds go inside the recorded node.
- **Do not give one surface two graphicsLayers where an outer alpha
  layer wraps an inner scale.** The alpha layer's layout-sized buffer
  clips the scaled surface to a rectangle while fading. Fade alpha and
  press scale share one graphicsLayer.
- **Do not bypass the shared gesture and motion systems** with ad-hoc
  pointer input or animation code in a component. Shared systems keep
  press, drag, release, and haptics identical everywhere.
- **Do not change shader math, uniform meanings, or the effect-chain
  order** from application code. Shaders are engine implementation.
- **Do not add custom fonts, CDNs, remote images, or network code to
  the template.** The template stays plain: system typeface, local
  vector drawables, standard Google Maven dependencies only, runnable
  offline after the first Gradle sync.
- **Do not name other glass libraries or repositories** in code,
  comments, or docs. The engine is an original implementation; its
  documentation describes mechanisms, not provenance.

### Forced edits to engine files

Editing a file under `glass/` is permitted only when all of these hold:

1. Adapting to an existing project provably requires it: the public API
   cannot express the needed behavior, and the limitation is
   demonstrated, not assumed.
2. The change is minimal and isolated: one file, one mechanism, with a
   comment stating why the public API could not do it.
3. Every invariant in [ARCHITECTURE.md](ARCHITECTURE.md) is re-checked
   against the change, especially the cycle rule, the Density rule, and
   the inverse-scale rule.
4. The change is verified on a device: the affected surfaces, scrolling,
   press/drag/release, show/hide transitions, and nested glass, in both
   themes.
5. The edit is recorded as forced adaptation, to be revisited when the
   public API can cover it.

If any condition fails, the edit does not happen: find a composition
that uses the public API instead, or leave the behavior out.

## Troubleshooting

| Symptom | Usual cause |
|---|---|
| Surface shows a transparent patch | A `background` or `drawBehind` sits before `glassBackdrop`, so the recording is empty. Move backgrounds inside the node. |
| Surface shows a plain tint, no refraction | No backdrop resolved (explicit or ambient). Check the backdrop wiring and the sibling order. |
| Droplet or thumb turns into a rectangle while fading | Fade alpha and press scale in separate graphicsLayers. Merge them into one layer. |
| Text inside the lens drifts or doubles while pressed | A graphicsLayer scale is not reported through `layerScale`. Report exactly the scale the layer sets. |
| Glass looks right at rest but smears while scrolling | Coordinates are stale. Surfaces must read coordinates with the never-equal policy (built into `GlassSurface`); custom surfaces must do the same. |
| Native crash in the RenderThread | A recording cycle: a backdrop records content containing a sampler of itself. Restructure so samplers are siblings of the recorded node, and exports hold background only. |
| Blur or refraction invisible over a plain background | Expected: refraction is mathematically invisible over a uniform patch. Judge glass over varied content (the template's Settings background preview exists for this). |
| Build errors about unresolved Compose APIs | The Compose BOM moves APIs between releases. Check the exact API form against the BOM in use (see Toolchain notes below) instead of assuming an older form. |

## Toolchain notes

Proven API facts for Compose BOM 2026.10.01. Do not repeat older forms:

- `Color.toArgb()` is an extension in `androidx.compose.ui.graphics`
  and needs an explicit import.
- `matchParentSize()` and `align()` are `BoxScope` member extensions:
  never import them; they resolve through the `BoxScope` receiver.
- `WindowInsets.statusBars` and `PaddingValues.calculateTopPadding()`
  do not resolve. Read the status-bar inset with
  `ViewCompat.getRootWindowInsets(view)` +
  `WindowInsetsCompat.Type.statusBars()`, with a 24 dp fallback.
- Float `lerp` lives in `androidx.compose.ui.util.lerp`.
  `awaitPointerEvent` is a member of `AwaitPointerEventScope` (no
  import). `changedToUp()` / `changedToUpOrCanceled()` do not exist;
  use `previousPressed && !pressed`.
- `drawRoundRect` is a `DrawScope` member (no import), unlike
  `translate`, `clipPath`, and `scale` (extensions, need imports).
- `RectangleShape` lives in `androidx.compose.ui.graphics`.
  `InfiniteTransition.animateFloat` is an extension and needs an
  import. `setTextAndPlaceCursorAtEnd` is an extension on
  `TextFieldState`.
