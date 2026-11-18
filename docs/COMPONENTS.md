# Components

The public API of the `:glass` module: the surface, the style, the
backdrop functions, and every component. All components are controlled:
state lives with the caller, and callbacks report changes. All
components take an optional `backdrop` (null means the ambient backdrop
from `LocalGlassBackdrop`) and an optional `haptic` flag (default on).
A `contentColor` left `Color.Unspecified` resolves to the theme label
color (white in dark mode, black in light mode); pass an explicit color
to override it.
All dimensions, springs, and effect numbers below are the built-in
behavior; style parameters change the glass recipe, not the mechanics.

Import the surface and style from `com.vallxz.glass`, the backdrop API
(`GlassBackdrop`, `rememberGlassBackdrop`,
`rememberGlassMergedBackdrop`, `rememberGlassWrappedBackdrop`,
`glassBackdrop`, `LocalGlassBackdrop`) from
`com.vallxz.glass.engine`, and components from
`com.vallxz.glass.components`. Everything else in the `engine`,
`interaction`, and `shapes` packages, and the internal helpers in
`decoration` and `components`, is not application API.

## GlassSurface

The primitive everything else is built on: a `Box` whose background is
the sampled, effected backdrop clipped to a shape, with content on top.

```kotlin
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop? = null,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Default,
    shape: Shape = RoundedCornerShape(22.dp),
    exportBackdrop: Boolean = false,
    sampleScale: Float = 1f,
    styleProvider: (() -> GlassStyle)? = null,
    sampleScaleProvider: (() -> Float)? = null,
    layerScale: () -> Offset = { Offset(1f, 1f) },
    content: @Composable BoxScope.() -> Unit,
)
```

- `styleProvider` replaces `style` when given, and is evaluated in the
  draw phase: use it when the style reads animation state (press
  progress, velocity), so ticks invalidate the draw only.
- `sampleScaleProvider` replaces `sampleScale` the same way.
- `layerScale` must report exactly the graphicsLayer scale applied to
  the surface's visible output. See the inverse-scale rule in
  [ARCHITECTURE.md](ARCHITECTURE.md).
- `exportBackdrop = true` exports the surface's glass background for
  nested surfaces and provides it as the ambient backdrop for the
  content.
- With no backdrop (explicit or ambient), the surface falls back to its
  tint as a plain fill instead of crashing.

`Modifier.glass(...)` turns any layout into a glass surface with the
same engine and the same parameters (minus `content`, which is the
layout itself). Prefer `GlassSurface` for new containers; use the
modifier when an existing layout needs a glass background without
restructuring.

## GlassStyle

The style recipe. An immutable data class; customize with `copy`. See
[ARCHITECTURE.md](ARCHITECTURE.md) for the field table and the
tint/surface-color distinction.

Presets: `GlassStyle.Default`, `Clear`, `Frosted`, `Vibrant`, `Dark`.

Decoration recipes: `GlassHighlight` (width 0.5 dp, blur 0.25 dp, alpha
1, `GlassHighlightStyle.Angled`, falloff 1), `GlassShadow` (radius
24 dp, offset (0, 4 dp), black 10%), `GlassInnerShadow` (radius 24 dp,
offset (0, 24 dp), black 15%). `GlassGradient` carries the progressive
profile (weights 0/0/1, fade 0..0.8, tint intensity 0.6).

Shared materials: `glassBarStyle(darkTheme)` is the fixed bottom-bar
material, public so detached navigation buttons match the bar exactly;
`glassBarAccentColor(darkTheme)` is the matching accent. The modal
material shared by the dialog and the bottom sheet is internal: modals
always use it.

## Backdrops

```kotlin
@Composable
fun rememberGlassBackdrop(): GlassBackdrop

@Composable
fun rememberGlassMergedBackdrop(primary: GlassBackdrop, secondary: GlassBackdrop): GlassBackdrop

@Composable
fun rememberGlassWrappedBackdrop(inner: GlassBackdrop, scale: () -> Offset): GlassBackdrop

fun Modifier.glassBackdrop(backdrop: GlassBackdrop): Modifier
```

Attach `glassBackdrop` to the background node only; surfaces are
siblings drawn after it. Merged backdrops sample two layers as one.
Wrapped backdrops scale one source about the sampling surface's center.
See [ARCHITECTURE.md](ARCHITECTURE.md) for the recording rules and the
cycle rule.

## Containers

### GlassCard / GlassBox

```kotlin
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    style: GlassStyle = GlassStyle.Default,
    shape: Shape = RoundedCornerShape(22.dp),
    exportBackdrop: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
)

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    style: GlassStyle = GlassStyle.Default,
    shape: Shape = RoundedCornerShape(22.dp),
    exportBackdrop: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
)
```

Plain glass containers. `GlassCard` arranges content in a column;
`GlassBox` in a box. Set `exportBackdrop = true` when glass nests
inside, so the nested glass samples this card's real glass background.

### GlassGradientPanel

```kotlin
@Composable
fun GlassGradientPanel(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    shape: Shape = RectangleShape,
    gradient: GlassGradient = GlassGradient(),
    content: @Composable BoxScope.() -> Unit,
)
```

Progressive glass: refraction follows the gradient profile and fades
toward the tint below the fade band. Default shape is a rectangle and
the default profile is flat at the top, fully refractive at the bottom.
Gradient styles skip the tint and surface fill; the tint lives only
inside the shader's fade mix.

### GlassChip

```kotlin
@Composable
fun GlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    icon: Painter? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    style: GlassStyle = GlassStyle.Default,
    contentColor: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
)
```

Selectable chip. The selected state tints the style (white tint at
0.35). Confirm haptic on tap.

## Buttons

Buttons share one press system: press growth, position-aware ripple
glow, and drag translation, with a confirm haptic. The default button
style is 8 dp blur with a 12/24 dp lens.

### GlassButton

```kotlin
@Composable
fun GlassButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    icon: Painter? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    style: GlassStyle = buttonDefaultStyle,
    contentColor: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
)
```

48 dp tall pill button with an optional leading icon. The ripple is a
flat white wash plus a radial shader glow at the touch point.

### GlassIconButton

```kotlin
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    icon: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    style: GlassStyle = buttonDefaultStyle,
    contentColor: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(50),
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
)
```

Square icon button with the same press behavior. Used for the top-bar
actions and the detached navigation button.

### GlassBackButton

```kotlin
@Composable
fun GlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    style: GlassStyle = buttonDefaultStyle,
    shape: Shape = RoundedCornerShape(50),
    size: Dp = 48.dp,
    content: @Composable BoxScope.() -> Unit,
)
```

A press button with a content slot: icon, text, or both. Nothing is
hardcoded. Content is placed in a full-size box with default (top-start)
alignment, so callers align it themselves (for example,
`Modifier.align(Alignment.Center)`).

## Navigation

### GlassBottomBar

```kotlin
@Composable
fun GlassBottomBar(
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    showLabels: Boolean = true,
    haptic: Boolean = true,
    contentColor: Color = Color.Unspecified,
    showDroplet: Boolean = true,
)

data class GlassTab(
    val label: String,
    val icon: Painter,
    val selectedIcon: Painter? = null,
)
```

The navigation bar: 64 dp tall, 56 dp droplet, 4 dp bar padding.
Dragging slides the droplet with velocity squash and rubber-band drift
at the ends; tapping jumps it. The droplet samples a merged backdrop
(the outer backdrop plus the tab row wrapped to shrink on press) and
carries the depth term, dispersion, and a press glow. The bar grows
while pressed and the tab content shrinks to 0.92.

`showDroplet` hides the droplet while the active page is not one of the
bar's tabs (for example, a detached Search page). While hidden, the bar
has no current selection: every tap and snap reports, including taps on
the tab the droplet last sat on, and a tap makes the droplet appear at
the tapped tab instead of sliding in from its stale position. See the
detached-button pattern in [USAGE.md](USAGE.md).

### GlassSegmentedControl

```kotlin
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    contentColor: Color = Color.Unspecified,
)
```

48 dp segmented control with a sliding droplet. The track magnifies at
rest (1.3x) and the droplet stretches with drag velocity. Labels are
15 sp semibold, fading to 0.75 alpha when unselected. `contentColor`
`Unspecified` resolves to the theme label color.

## Sliders, selectors, stepper

All share the motion engine: press growth, damped drag chase, snap on
release, haptics on steps and ends.

### GlassSlider

```kotlin
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    haptic: Boolean = true,
    trackHeight: Dp = 6.dp,
    thumbWidth: Dp = 40.dp,
    thumbHeight: Dp = 24.dp,
    visibilityThreshold: Float = 0.01f,
    onValueChangeFinished: () -> Unit = {},
)
```

6 dp track, 40x24 dp thumb growing to 1.5x on press. Haptic scrub every
5% of the range. The thumb samples the track wrapped to collapse
vertically on press.

### GlassRangeSlider

```kotlin
@Composable
fun GlassRangeSlider(
    start: Float,
    end: Float,
    onRangeChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    minGap: Float = 0f,
    enabled: Boolean = true,
    haptic: Boolean = true,
    thumbWidth: Dp = 40.dp,
    thumbHeight: Dp = 24.dp,
    visibilityThreshold: Float = 0.01f,
    onRangeChangeFinished: () -> Unit = {},
)
```

Two-thumb range slider with a minimum gap. Tapping the track moves the
nearest thumb. Fill spans the selected range.

### GlassToggle

```kotlin
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    thumbIcon: Painter? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
)
```

64x28 dp track, 40x24 dp thumb, 20 dp travel. Snaps at the midpoint.
Accent green. Programmatic changes animate through the same motion as
gestures. An optional thumb icon rides inside the thumb.

### GlassStepper

```kotlin
@Composable
fun GlassStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    valueRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
    step: Int = 1,
    enabled: Boolean = true,
    haptic: Boolean = true,
    contentColor: Color = Color.Unspecified,
)
```

Minus/plus stepper. Holding repeats: first repeat after 400 ms, then
every 80 ms. A tap steps on release, only if no repeat fired. The press
binds to the half where the gesture started.

### GlassHorizontalSelector

```kotlin
@Composable
fun GlassHorizontalSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    contentColor: Color = Color.Unspecified,
    chromaticAberration: Float = 0.3f,
)
```

Horizontally scrolling option selector with a glass droplet over the
focused option. Options snap to center with fling support; labels scale
and fade with focus distance. The droplet width follows the focused
option's label width.

### GlassRulerSelector

```kotlin
@Composable
fun GlassRulerSelector(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    valueRange: IntRange = 0..100,
    step: Int = 1,
    unit: String = "",
    enabled: Boolean = true,
    haptic: Boolean = true,
    contentColor: Color = Color.Unspecified,
    chromaticAberration: Float = 0.3f,
)
```

Tick ruler with a glass droplet showing the current value and unit.
Drag the ticks; the value reports while dragging. The droplet width fits
the widest label in the range. `step` must be positive.

## Menus, dialogs, sheets

Modal components share a fixed material (12 dp blur, 16/32 dp lens,
heavy flat surface tint) and one overlay pattern: last-request-kept for
exit animations, scrim dismiss, back-handler dismiss.

### GlassMenu

```kotlin
data class GlassMenuItem(
    val label: String,
    val icon: Painter? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: IntRect,
    items: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    menuWidth: Dp? = null,
    showDividers: Boolean = true,
)

fun Modifier.glassMenuAnchor(onBounds: (IntRect) -> Unit): Modifier
```

Popup menu that grows out of a droplet at the anchor's edge. Apply
`glassMenuAnchor` to the composable the menu opens from; the menu does
nothing until the bounds are non-zero. Width clamps to 180..280 dp.
Rows are 44 dp with optional dividers. The menu renders in the overlay
host, not in a dialog window.

### GlassDialog

```kotlin
@Composable
fun GlassDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    cancelText: String? = null,
    onCancel: (() -> Unit)? = null,
    destructive: Boolean = false,
    confirmColor: Color = Color.Unspecified,
    cancelColor: Color = Color.Unspecified,
)
```

270 dp modal dialog with stacked 44 dp buttons and hairline dividers.
Cancel first (regular), confirm second (semibold). `destructive` colors
the confirm action system red. `confirmColor` / `cancelColor` override
the button text colors; `Unspecified` keeps the default behavior. Squish
enter animation, scrim at 42% black.

### GlassBottomSheet

```kotlin
@Composable
fun GlassBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    content: @Composable ColumnScope.() -> Unit,
)
```

Full-width sheet with 34 dp top corners, square bottom corners, a
grabber, and a centered title. Slides up with a squish pivot at the
bottom center. No drag-to-dismiss: dismiss on scrim tap or back press.

## Progress and search

### GlassProgressBar

```kotlin
@Composable
fun GlassProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    indeterminate: Boolean = false,
)
```

8 dp capsule. Determinate reads `progress` directly with no animation.
Indeterminate runs a 35% segment on a 1400 ms linear loop. Track gray,
fill accent at 85%.

### GlassSearchBar

```kotlin
@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    searchIcon: Painter? = null,
    clearIcon: Painter? = null,
    placeholder: String = "Search",
    enabled: Boolean = true,
    haptic: Boolean = true,
    style: GlassStyle = GlassStyle.Default,
    contentColor: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(50),
    height: Dp = 48.dp,
    onSearch: () -> Unit = {},
    layerScale: () -> Offset = { Offset(1f, 1f) },
    shrinkProgress: Float = 0f,
)
```

State-based text field in glass. Icons render only when non-null; the
clear icon only when text is non-empty. `shrinkProgress` (0..1) shrinks
the bar slightly and composes into the sampling scale, so a caller can
drive the expand/shrink that accompanies a Cancel button. The Cancel
button itself is app chrome, not engine API: the template app's
`SearchCancelButton` shows the pattern.
