# Agents.md

Instructions for AI agents working in this repository. Read this file
first, then the docs in `docs/` in this order: `ARCHITECTURE.md`,
`STRUCTURE.md`, `COMPONENTS.md`, `USAGE.md`. Do not implement from
assumptions about liquid glass: the engine's mechanisms and invariants
are documented, and guessing breaks them.

## The one rule

**Do not edit engine code.** The `glass/` module's `engine/`,
`interaction/`, and `shapes/` packages, plus `GlassDraw`,
`GlassEffectChain`, `GlassShaders`, `GlassRecord`, and `GlassBackdrop`,
are the glass fundamentals. Changing them breaks sampling, refraction,
or the RenderThread, often only on device. Application work composes
the public API. The only exception is the forced-edit protocol in
`docs/USAGE.md`, and it requires all five of its conditions, including
device verification. A general instruction to "keep going" never
authorizes an engine edit.

## What this project is

A liquid glass UI engine in Kotlin and Jetpack Compose
(the `:glass` module), plus a plain template app (the `:app` module)
that demos it. No third-party glass library, no NDK, no native code.
The maintainer works CLI-first on Windows (sdkmanager, VSCode, Gradle
wrapper) and never uses Android Studio. Code delivered here is
uncompiled: the maintainer builds on Windows and tests on device. Never
claim a build, a delivery, or a device result that did not actually
happen.

## Imports and calls

### Packages

| Package | Contents |
|---|---|
| `com.vallxz.glass` | `GlassSurface`, `GlassStyle`, `Modifier.glass` |
| `com.vallxz.glass.engine` | The backdrop API only: `GlassBackdrop`, `rememberGlassBackdrop`, `rememberGlassMergedBackdrop`, `rememberGlassWrappedBackdrop`, `glassBackdrop`, `LocalGlassBackdrop` |
| `com.vallxz.glass.components` | All components, `GlassTab`, `GlassMenuItem`, `glassMenuAnchor`, `glassBarStyle`, `glassBarAccentColor` |
| `com.vallxz.glass.decoration` | `GlassHighlight`, `GlassHighlightStyle`, `GlassShadow`, `GlassInnerShadow` |
| `com.vallxz.glass.effects` | `GlassGradient`, `GlassAdaptive` |

The backdrop API is the one public exception in the `engine` package.
Do not import anything else from `com.vallxz.glass.engine`, and do not
import from `com.vallxz.glass.interaction` or
`com.vallxz.glass.shapes` in application code. Those are internal
implementation.

### Minimal working setup

```kotlin
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.components.GlassSlider
import com.vallxz.glass.engine.GlassBackdrop
import com.vallxz.glass.engine.glassBackdrop
import com.vallxz.glass.engine.rememberGlassBackdrop
import com.vallxz.glass.engine.rememberGlassMergedBackdrop
```

A screen needs, in order: a backdrop from `rememberGlassBackdrop()`, a
recording node with `Modifier.glassBackdrop(backdrop)` holding the
background content inside it, and glass surfaces as siblings drawn
after the recording node. See `docs/USAGE.md` for the two-layer layout,
nested glass, the detached-button pattern, and modal overlays. Copy
those patterns; do not invent new backdrop wiring.

### Components

Every component takes an optional `backdrop` (null = ambient) and an
optional `haptic` flag, and hoists its state to the caller. Exact
signatures are in `docs/COMPONENTS.md`. Read the signature before
calling a component; do not reconstruct parameter lists from memory.

### Styles

Customize with `GlassStyle.copy`. Use the presets (`Default`, `Clear`,
`Frosted`, `Vibrant`, `Dark`) as bases. Use `glassBarStyle` /
`glassBarAccentColor` for anything that must match the bottom bar. Keep
the modal material fixed: dialogs and sheets always use it.

## Adding the engine to an existing project

1. Copy the `glass/` directory into the project.
2. Add `include(":glass")` in `settings.gradle.kts`.
3. Add `implementation(project(":glass"))` to the app module.
4. Meet the requirements: minSdk 33, compileSdk 37 with minor 1,
   Kotlin 2.5.0-RC, AGP 9.6.0-alpha03, Compose BOM 2026.10.01
   (artifact `compose-bom-alpha`), coroutines 1.11.0.
5. Set up backdrops per `docs/USAGE.md`: recording nodes first, glass
   surfaces as siblings after them. In an existing project, the
   recording node usually wraps the existing background or the existing
   content root; do not restructure the app's navigation to fit the
   engine.
6. Keep the app's theme and navigation. The engine has no theme and no
   navigation library. Page state stays in the app.
7. Verify on device: every glass surface, scrolling, press/drag/release,
   show/hide transitions, nested glass, both themes. If the existing
   project provably needs behavior the public API cannot express, stop
   and use the forced-edit protocol in `docs/USAGE.md` instead of
   editing engine files directly.

## Allowed

- Compose screens, pages, and layouts from the public components.
- Build new components on `GlassSurface` or `Modifier.glass`, following
  the conventions in `docs/COMPONENTS.md`: hoisted state, optional
  `backdrop`, optional `haptic`, draw-phase style providers for
  animation state, `layerScale` reporting for any graphicsLayer scale.
- Customize glass with `GlassStyle.copy` and presets.
- Add backdrop layers, merge them, wrap sources with draw-phase scale
  providers.
- Add tabs, pages, menus, dialogs, sheets, settings, and catalog
  entries through the app-level patterns.
- Read the current file before patching it. Diagnose from the actual
  code (and from the maintainer's build log, screenshot, or video)
  before stacking another patch. Keep changes narrow.

## Not allowed

- Editing engine files, except under the forced-edit protocol. This
  includes padding, offsets, transform order, cache keys, shader
  uniforms, spring values, and shader math.
- Recording cycles, Density API calls inside record blocks, frame
  loops, polling, bitmap copies, backgrounds before `glassBackdrop`,
  two graphicsLayers with an outer alpha layer wrapping an inner
  scale, ad-hoc gesture/animation code replacing the shared systems.
  (Each is explained in `docs/ARCHITECTURE.md` and `docs/USAGE.md`.)
- Custom fonts, CDNs, remote images, network code, or extra
  dependencies in the template. The template stays plain: system
  typeface, local vector drawables, standard Google Maven dependencies
  only, runnable offline after the first Gradle sync.
- Naming other glass libraries or repositories anywhere in code,
  comments, docs, or messages about this project.
- Change-history comments. Comments are simple, clean, to the point,
  professional, and feature-focused. No history, no provenance, no
  "previously / was / reverted / renamed" narration. Explain what the
  code does and why a constraint exists, in the present tense.
- Suppressing the Gradle native-access warnings, or changing
  `gradle.properties` / wrapper scripts to hide them. They are not a
  project problem.
- Claiming a fix is proven before the maintainer confirms it on device.
  State plainly what is uncompiled here.

## Verification checklist

Before delivering, check all of these:

1. Braces and parentheses balance in every edited file; no unused
   imports; no unresolved references to renamed or deleted symbols.
2. Zero occurrences of other glass libraries' or repositories' names
   anywhere in the tree (code, comments, docs, resources).
3. Comments comply with the rule above.
4. The change follows the documented patterns; no engine file was
   touched unless the forced-edit protocol was satisfied and recorded.
5. The delivery is a zip, never single files, named
   `<project>-YYYYMMDD-HHMMSS.zip` with the timestamp complete to the
   second in Asia/Jakarta, no colons or spaces. Every rebuild gets a
   fresh timestamp, even if only the name changed. The zip holds the
   project folder at its root, excluding `.gradle/`, `**/build/`,
   `.idea/`, `local.properties`, and `.git/`.
6. The delivery message tells the maintainer to extract to a clean,
   empty folder (extracting over an old folder leaves stale renamed or
   deleted files behind and causes conflicting-overload and
   unresolved-reference build failures), and states plainly that the
   code was not compiled or tested here and that their build on device
   is the proving step.
7. Memory is updated with the delivery (zip name, hash, what changed)
   so the next session starts from the right facts.
