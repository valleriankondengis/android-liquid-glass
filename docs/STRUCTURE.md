# Structure

The file and package map of the Liquid Glass template: what lives where,
what each file holds, and where new work goes. For how the engine works,
see [ARCHITECTURE.md](ARCHITECTURE.md). For the component reference, see
[COMPONENTS.md](COMPONENTS.md). For usage patterns and rules, see
[USAGE.md](USAGE.md).

## Repository layout

```text
liquid-glass/
├── Agents.md                  # Instructions for AI agents working in this repo
├── README.md                  # Setup, build, screenshots, links to docs
├── docs/                      # This documentation set
│   ├── ARCHITECTURE.md        # Engine internals and invariants
│   ├── STRUCTURE.md           # This file
│   ├── COMPONENTS.md          # Component reference
│   └── USAGE.md               # Patterns, rules, and troubleshooting
├── screenshot/                # README screenshots (screenshot1.png … screenshot10.png)
├── gradle/
│   ├── libs.versions.toml     # Version catalog: the only dependency list
│   └── wrapper/               # Gradle wrapper (9.8.2)
├── glass/                     # The :glass library module (the engine)
├── app/                       # The :app template app (the demo)
├── build.gradle.kts           # Root build: plugin versions only
└── settings.gradle.kts        # Module list: :glass, :app
```

Sources live under `src/main/kotlin`, following the package name.
Resources live under `src/main/res`. There are no test sources: the
template app on a device is the proving step.

## The `:glass` module

Build namespace `com.vallxz.glass.library`. Kotlin package
`com.vallxz.glass`. The module has no Material3 dependency: components
use `BasicText`, `Image`, and `ColorFilter` only, so the engine stays
usable in any Compose app.

```text
glass/src/main/kotlin/com/vallxz/glass/
├── GlassStyle.kt              # GlassStyle: the style recipe + presets
├── GlassSurface.kt            # GlassSurface, Modifier.glass, LocalGlassBackdrop wiring
├── components/                # The component set (public API)
│   ├── GlassBackButton.kt     # GlassBackButton: content-slot press button
│   ├── GlassBottomBar.kt      # GlassBottomBar + GlassTab: the navigation bar
│   ├── GlassBottomSheet.kt    # GlassBottomSheet: modal sheet
│   ├── GlassButton.kt         # GlassButton, GlassIconButton, press machinery
│   ├── GlassCard.kt           # GlassCard, GlassBox: plain glass containers
│   ├── GlassChip.kt           # GlassChip: selectable chip
│   ├── GlassColors.kt         # Shared system colors, bar + modal materials
│   ├── GlassDialog.kt         # GlassDialog + shared modal overlay helpers
│   ├── GlassGradientPanel.kt  # GlassGradientPanel: progressive glass
│   ├── GlassMenu.kt           # GlassMenu, GlassMenuItem, glassMenuAnchor
│   ├── GlassProgressBar.kt    # GlassProgressBar: determinate + indeterminate
│   ├── GlassRangeSlider.kt    # GlassRangeSlider: two-thumb range slider
│   ├── GlassSearchBar.kt      # GlassSearchBar: text field in glass
│   ├── GlassSegmentedControl.kt # GlassSegmentedControl: sliding droplet
│   ├── GlassSelector.kt       # GlassHorizontalSelector, GlassRulerSelector
│   ├── GlassSlider.kt         # GlassSlider: single-thumb slider
│   ├── GlassStepper.kt         # GlassStepper: hold-to-repeat stepper
│   └── GlassToggle.kt         # GlassToggle: glass switch
├── decoration/                # Edge decoration recipes and drawing
│   ├── GlassDecoration.kt     # Drop shadow + edge highlight drawing
│   ├── GlassHighlight.kt      # GlassHighlight, GlassHighlightStyle
│   └── GlassShadow.kt         # GlassShadow, GlassInnerShadow
├── effects/                   # Effect recipes
│   ├── ColorMatrices.kt       # Brightness/contrast/saturation matrices
│   ├── GlassAdaptive.kt       # GlassAdaptive: luminance-based style retuning
│   └── GlassGradient.kt       # GlassGradient: progressive glass profile
├── engine/                    # The glass engine (internal; see USAGE.md rules)
│   ├── GlassBackdrop.kt       # GlassBackdrop, recording, merged/wrapped backdrops
│   ├── GlassCapabilities.kt   # API floor checks
│   ├── GlassDraw.kt           # The draw pipeline + inner shadow
│   ├── GlassEffectChain.kt    # GPU effect chain building + caching
│   ├── GlassLuminanceProbe.kt # Backdrop luminance sampling
│   ├── GlassRecord.kt         # The single sanctioned layer-recording path
│   └── GlassShaders.kt        # AGSL shaders as Kotlin strings
├── interaction/               # Shared gesture, motion, press, haptics (internal)
│   ├── GlassDrag.kt           # detectGlassDrag: the shared drag detector
│   ├── GlassHaptics.kt        # GlassHaptics: platform haptic constants
│   ├── GlassMotion.kt         # GlassMotion: springs + release sequencing
│   └── GlassPress.kt          # GlassButtonPress: button press + ripple
└── shapes/                    # Shape resolution (internal)
    ├── CornerRadii.kt         # CornerRadii + logical-to-physical mapping
    └── ShapeAdapters.kt       # Shape -> outline path + corner radii
```

Dependency direction inside the module: `components` use `engine`,
`decoration`, `effects`, `interaction`, and `shapes`. `engine` uses
`decoration`, `effects`, and `shapes`. Nothing in the engine depends on
`components`. The `internal` files are engine implementation: application
code uses only the public API listed in [COMPONENTS.md](COMPONENTS.md).

## The `:app` module

Build namespace and application ID `com.vallxz.glass`. A plain template
app: fullscreen pages, a transparent top bar, the glass bottom bar with
its separate search button, and a Settings control center. No navigation
library: page state is plain Compose state in `GlassApp`.

```text
app/src/main/kotlin/com/vallxz/glass/
├── MainActivity.kt            # Edge-to-edge setup, sets GlassApp content
└── ui/
    ├── GlassApp.kt            # Root: backdrop layers, pages, top bar, bottom bar, overlays
    ├── AppTopBar.kt           # Top bar: back button (in Settings) + settings button
    ├── AppOverlays.kt         # Dialog/sheet/menu overlay host + requests
    ├── GroupedList.kt         # Settings-style rows, cards, section headers, app colors
    ├── GlassSettings.kt       # GlassSettings data + SharedPreferences holder
    ├── ComponentCatalog.kt    # The component catalog shared by Home and Search
    ├── SearchCancelButton.kt  # The app-level search Cancel button
    ├── screens/
    │   ├── HomeScreen.kt      # Home: the full catalog
    │   ├── AboutScreen.kt     # About: what the engine is + maintainer
    │   ├── SearchScreen.kt    # Search: catalog search + Cancel animation
    │   └── SettingsScreen.kt  # Settings: live glass tuning + background preview
    └── theme/
        └── Theme.kt           # GlassTheme: dark/light + system-bar icon colors
```

```text
app/src/main/res/
├── drawable/                  # Vector icons (home, search, settings, chevron, close, info)
├── mipmap-*/                  # Launcher icons
├── values/                    # Theme, colors, strings
└── xml/                       # Backup + data-extraction rules
```

## Build files

| File | Purpose |
|---|---|
| `gradle/libs.versions.toml` | The only dependency list. AGP, Kotlin, coroutines, androidx core/activity, Compose BOM. |
| `glass/build.gradle.kts` | Library config: namespace, SDK levels, Compose, dependencies. |
| `app/build.gradle.kts` | Application config: namespace, application ID, SDK levels, dependencies. |
| `build.gradle.kts` | Root: plugin versions from the catalog. |
| `settings.gradle.kts` | Module list and repository set (Google, Maven Central, Gradle Plugin Portal). |
| `gradle.properties` | Gradle + AndroidX flags. |
| `gradlew`, `gradlew.bat` | Gradle wrapper scripts. No separate Gradle install needed. |

## Where new work goes

| Work | Location |
|---|---|
| New glass component | A new file in `glass/.../components/`, built on `GlassSurface`, following the conventions in [COMPONENTS.md](COMPONENTS.md). Add a catalog entry in `app/.../ComponentCatalog.kt` to demo it. |
| New style preset | A `GlassStyle` companion entry in `GlassStyle.kt`, or a `copy` at the call site. |
| New page | A new file in `app/.../ui/screens/`, a branch in the `GlassApp` page switch, and a tab entry if it belongs in the bar. |
| New setting | A field in `GlassSettings`, a row in `SettingsScreen`, and a mapping in `toGroupStyle`. |
| New backdrop layer | A `rememberGlassBackdrop()` in `GlassApp`, a recording node in the root `Box`, and a merge entry where surfaces need it. |
| Engine change | Nowhere, unless the forced-edit protocol in [USAGE.md](USAGE.md) applies. |

## Reuse

The `:glass` module is the reusable unit: copy the `glass/` directory
into another project, add it in `settings.gradle.kts`, and depend on it
from the app module. The full procedure, including versions and the
backdrop setup, is in [USAGE.md](USAGE.md) and in `Agents.md`.
