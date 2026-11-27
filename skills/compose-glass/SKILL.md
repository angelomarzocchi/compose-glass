---
name: compose-glass
description: How to use the Glass library (io.github.angelomarzocchi.glass), liquid glass for Jetpack Compose — AGSL refraction at the rim, a fine line of light on the edge, one finish from clear liquid to matte, lights inside the glass, and quality that adapts to the device. Load it before putting a bar, FAB, island, panel, sheet or any floating surface in glass ("liquid glass", "glassmorphism", "frosted", "like iOS"), before writing a GlassSurface lighting block or using GlassPainter directly, before putting compose-lumen's FloatingNavBar, LightTabs, LightActions or LightMenu in glass, and when glass looks wrong (plain, black, grey, bevelled, see-through text, a copy of itself) or costs too much.
---

# Glass

Package `io.github.angelomarzocchi.glass`, from JitPack:

```kotlin
// settings.gradle.kts: repositories { google(); mavenCentral(); maven("https://jitpack.io") }
implementation("com.github.angelomarzocchi:compose-glass:2.0.0")
```

minSdk 24, no Material dependency. Full glass on Android 13+ (AGSL); below that, or on a modest phone,
the same glass is drawn frosted or tinted by itself — never write API-level checks around it.

## Do this, in order

1. **Record the page** under the glass: `val backdrop = rememberGlassBackdrop()` and
   `Modifier.glassBackdrop(backdrop)` on the page's root, **with an opaque background after it** in the
   same chain.
2. **Put glass beside the page, never inside it**: a sibling drawn after the page in the same parent
   (usually a `Box`). Glass inside the recorded page records itself.
3. **Pick the material** with the table below — default `GlassMaterial.liquid(dark, finish = 0.3f)` —
   and pass the same `dark` to the surface.
4. **Add lights only where they mean something**: the finger's lamp on surfaces you touch, a selection
   on a tab bar. No tilt unless asked.
5. **Warm up**: `prepareGlass()` off the main thread at app start.
6. **Check it on a device with Android 13+** (the Studio preview has no AGSL), light and dark.

```kotlin
val dark = isSystemInDarkTheme()
val backdrop = rememberGlassBackdrop()
Box(Modifier.fillMaxSize()) {
    Page(Modifier.fillMaxSize().glassBackdrop(backdrop).background(MaterialTheme.colorScheme.surface))
    GlassSurface(
        backdrop = backdrop,
        material = GlassMaterial.liquid(dark, finish = 0.3f),
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).height(64.dp),
        dark = dark,
    ) { /* engraved content */ }
}
```

## Choosing the glass

Liquid glass with one knob, `finish`, 0 to 1. It is the look users mean by "liquid glass": the page
bends at a soft rim and shows through richer in colour, a fine line of light runs round the edge, a soft
shadow sits under it — no bevel, no side in shade.

| The user asks for | Use |
|---|---|
| liquid glass, "like iOS", clear, see-through, a lens | `GlassMaterial.liquid(dark)` (finish 0) |
| a bar, a FAB, an island over a busy page — **the default** | `GlassMaterial.liquid(dark, finish = 0.3f)` |
| frosted, satin, a bit more privacy | `liquid(dark, finish = 0.5f)` to `0.75f` |
| matte, a panel, a sheet, a dialog with text | `GlassMaterial.matte(dark)` (finish 1) or `liquid(dark, 0.8f)` |
| glass that changes (frosts as the page scrolls under it, a bubble into a sheet) | `lerp(a, b, fraction)`, with the fraction read in draw |
| a bevelled slab, acrylic, a "3D button" — **only if asked** | `clear(dark)`, `lens(dark)`, `frosted(dark)` |

Tuning, with `copy()` on a liquid material: `saturation` (colour through the glass, 1.5 at finish 0),
`tint`/`tintAmount` (body), `hairline`/`farHairline`/`hairlineFocus` (the line of light and its glints),
`rimGlow` (light just inside the edge), `shadow`, `frost`, `bevel`/`refraction`/`falloff` (the bend).

Never on liquid glass, unless asked:
- `edge`, `rim`, `specular` above 0 — that is the acrylic shading, and it brings the bevel back.
- `refraction × falloff` above `bevel` — the page at the rim folds over itself and shows twice.
- `inkReflection` above 0 — labels mirrored into the rim read as a glitch, and cost a pass every frame.
- `inkDepth` above 0 — labels in the flat middle do not bend anyway, and it costs a pass every frame.
- `rememberTiltKeyLight()` — it only moves the glints, and redraws the glass while the phone is in hand.

## Recipes

**Tab bar** — the selected tab is a light left on inside the glass, flowing between tabs:

```kotlin
val lamp = rememberGlassLamp()
val at = remember { Animatable(0f) }
LaunchedEffect(selected) { at.animateTo(selected.toFloat(), spring(dampingRatio = 0.72f, stiffness = 500f)) }
val inset = with(LocalDensity.current) { 6.dp.toPx() }
GlassSurface(
    backdrop, GlassMaterial.liquid(dark, finish = 0.3f),
    Modifier.fillMaxWidth().height(68.dp).glassLampFollowsFinger(lamp),
    lamp = lamp, dark = dark, lampInk = accent,
    lighting = { size ->
        val cell = size.width / tabs.size
        val left = at.value * cell + inset
        selection = Rect(left, inset, left + cell - 2 * inset, size.height - inset)
        selectionOn = 1f
        selectionColor = accent
        selectionInk = Color.White
    },
) { Row(Modifier.fillMaxSize()) { tabs.forEach { Tab(it, Modifier.weight(1f)) } } }
```

**FAB that fills with light** — `selectionOn` animated 0 → 1 over an inset `selection`.

**Panel or sheet** — `GlassMaterial.matte(dark)` with `cornerRadius = 28.dp`.

**Island at the top** — `liquid(dark, finish = 0.3f)`, `Modifier.statusBarsPadding()`, a lamp.

## GlassSurface

```kotlin
GlassSurface(
    backdrop = backdrop,
    material = GlassMaterial.liquid(dark, finish = 0.3f),
    modifier = Modifier.size(280.dp, 64.dp).glassLampFollowsFinger(lamp),
    cornerRadius = null,     // null = capsule; or e.g. 28.dp
    lamp = lamp,             // the finger's light: the same instance as in glassLampFollowsFinger
    dark = dark,             // light adds up on dark glass, shows as colour on pale glass
    lampInk = accent,        // what marks turn under the finger on pale glass (white on dark glass)
    lighting = { size -> },  // a selection, say
    body = { true },         // false hides the glass and keeps what is engraved
) { content() }
```

- `glassLampFollowsFinger` does **not** consume touches: buttons inside still get their clicks.
- The content is clipped to the glass's shape.
- The lamp lights marks up (a dark label takes its colour, a white letter stays white); the selection
  repaints them in `selectionInk`.

## The lighting block

`lighting: GlassLighting.(Size) -> Unit` runs **every frame, in the draw phase**, in the surface's own
pixels. Read `Animatable.value`s and state there — it redraws when they change; never in composition.
Allocate nothing, launch nothing. Fields, reset before each call:

| Field | Meaning |
|---|---|
| `lamp`, `lampOn`, `lampColor` | The finger's light (filled from `lamp` for you). |
| `lampInk` | What marks turn under the lamp. |
| `selection: Rect`, `selectionOn`, `selectionColor` | A light left on inside, capsule-shaped: the selected tab, a fill. |
| `selectionInk` | What marks turn inside the selection (usually white). |
| `keyLight: Offset` | Where the room's light comes from (x right, y down); `DefaultKeyLight` is up-left. `rememberTiltKeyLight()` follows the phone — only when asked for. |

## Lower level: GlassPainter

For a surface with its own geometry (ends that move, a custom outline). One painter per surface; call
`painter.light { … }` before each of the three, since every call resets the lights:

```kotlin
val painter = rememberGlassPainter()
Box(
    Modifier
        .glassPosition(painter)                                        // where the surface is laid out
        .drawBehind {
            painter.light { lamp = …; lampOn = … }
            with(painter) { drawBody(backdrop, material, shape = myRect, radius = r, dark = dark) }
        }
        .graphicsLayer {
            painter.light { /* the same lights */ }
            renderEffect = painter.inkEffect(this, material, myRect, r)
            compositingStrategy = if (painter.engravingIsolated()) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .drawWithContent {
            painter.light { /* the same lights */ }
            with(painter) { drawEngraving(material) }                  // instead of drawContent()
        },
) { content() }
```

## With compose-lumen (FloatingNavBar, LightTabs, LightActions, LightMenu)

Each takes `material: IslandMaterial`. This adapter puts it in glass; copy it as it is (compose-lumen
1.3.0 or later — on 1.2 leave out the last two overrides):

```kotlin
class GlassIsland(
    private val backdrop: GlassBackdrop,
    private val material: GlassMaterial,
    private val painter: GlassPainter,
    private val keyLight: () -> Offset = { DefaultKeyLight },
) : IslandMaterial {
    override val modifier: Modifier = Modifier.glassPosition(painter)

    private fun GlassLighting.from(lights: IslandLights) {
        lamp = lights.lamp
        lampOn = lights.lampOn
        lampInk = if (lights.dark) Color.White else lights.accent
        selection = lights.selection
        selectionOn = 1f
        selectionColor = lights.accent
        selectionInk = Color.White
        keyLight = this@GlassIsland.keyLight()
    }

    override fun DrawScope.drawIsland(shape: Rect, radius: Float, overhang: Float, lights: IslandLights) {
        painter.light { from(lights) }
        with(painter) { drawBody(backdrop, material, shape, radius, lights.dark, overhang) }
    }

    override fun contentEffect(density: Density, shape: Rect, radius: Float, lights: IslandLights): RenderEffect? {
        painter.light { from(lights) }
        return painter.inkEffect(density, material, shape, radius)
    }

    override fun ContentDrawScope.drawMarks(shape: Rect, radius: Float, lights: IslandLights) {
        painter.light { from(lights) }
        with(painter) { drawEngraving(material) }
    }

    override fun isolatesContent(lights: IslandLights): Boolean {
        painter.light { from(lights) }
        return painter.engravingIsolated()
    }
}

@Composable
fun rememberGlassIsland(backdrop: GlassBackdrop, material: GlassMaterial, keyLight: () -> Offset = { DefaultKeyLight }): GlassIsland {
    val painter = rememberGlassPainter()
    return remember(backdrop, material, painter, keyLight) { GlassIsland(backdrop, material, painter, keyLight) }
}

// FloatingNavBar(items, selected, onSelect, material = rememberGlassIsland(backdrop, GlassMaterial.liquid(dark, finish = 0.3f)))
```

The bar is drawn over the recorded page like any glass (a sibling of the page, not inside it). The
island's `dark` comes from lumen's island colour: keep `LumenColors` and the material in the same theme.
`RenderEffect` here is `androidx.compose.ui.graphics.RenderEffect`.

## Quality: older and modest devices

Every surface is drawn at a `GlassQuality`; each looks like the same glass, lays out and touches the same.

| | Drawn | Needs |
|---|---|---|
| `Full` | everything: the bend at the rim, colours split there, marks lit from inside | API 33 |
| `Frosted` | page frosted and richer in colour under more tint, line of light, shadow, flat lights; no bend | API 31 |
| `Tinted` | a tint all but opaque, line of light, shadow, flat lights; nothing off-screen | any |

Each device chooses its own (`rememberGlassQuality()` says which): all its API allows, but Android Go /
low memory → `Tinted`, under ~3 GB → `Frosted`, battery saver on → at most `Frosted`. To force one —
a setting, a screenshot test, checking how an old phone sees it on a new one:

```kotlin
CompositionLocalProvider(LocalGlassQuality provides GlassQuality.Frosted) { App() }
```

A device is never asked for more than its API allows.

## Performance

Glass costs about one page's worth of drawing on top of the page, for a handful of surfaces.

- **A handful of floating surfaces** (bar, FAB, island, panel). Never glass on list items or cards.
- `prepareGlass()` once, off the main thread, at app start: the first AGSL compile in a process can take
  100 ms or more and would otherwise land on the first frame with glass.
  `lifecycleScope.launch(Dispatchers.Default) { prepareGlass() }`
- Over a page that changes every frame (video, camera): glass over the moving image keeps the live
  backdrop; glass over a part that hardly changes takes `rememberStillGlassBackdrop(backdrop)` — a still
  every 250 ms at half size, free in between.
- Unlit liquid glass draws its content without an off-screen pass; a light on brings one in.
  `inkDepth`/`inkReflection` above 0 bring it in for good.
- `frost` is a blur (at half size from 4 dp): fine on panels, but large frosted surfaces over a page
  that scrolls are the costliest thing here.
- No `rememberTiltKeyLight()` unless asked.

## What the user should see (check before saying it is done)

- On Android 13+, `glassShaderError()` is null and `rememberGlassQuality()` is `Full` (or the device's
  reason for less).
- The page under the glass is visible, bent near the rim, its colours not washed grey.
- No darker band or bevel inside the edge; a thin line of light round it, brighter top-left and
  bottom-right; a soft shadow under it.
- Labels read over the busiest part of the page (raise `finish` if not, rather than `tintAmount` alone).
- The glass does not show itself, and does not move or flicker when nothing touches it.
- Light and dark theme both checked, with the material's `dark` matching the theme.

## Troubleshooting

| Symptom | Cause |
|---|---|
| Glass shows nothing, or only a tint | The page is not recorded (`glassBackdrop` missing) or has no opaque background in its chain. |
| A smeared copy of the glass inside itself | The surface is inside the recorded page. Move it out, as a sibling. |
| The page under the glass is offset | The page and the glass must be in the same window; `glassPosition` must sit on the surface's measured bounds (`GlassSurface` does it). |
| Bevelled, plastic, "3D button" look | An acrylic preset (`clear`, `lens`, `frosted`) or `edge`/`rim`/`specular` above 0. Use `liquid`. |
| Grey, washed-out glass | `tintAmount` too high for the finish; use a higher `finish` for readability instead, or raise `saturation`. |
| A band of the page shows twice at the rim | `refraction × falloff` above `bevel`. |
| Frosted, no bend, on a recent phone | `rememberGlassQuality()`: battery saver, a phone under ~3 GB, `LocalGlassQuality`, or `glassShaderError()` not null. |
| Lights do not move | Values read in composition instead of in `lighting` / `light {}`. |
| The selected label of a lumen bar stays dark below Android 13 | compose-lumen older than 1.3.0, or the adapter without `drawMarks`/`isolatesContent`. |
| Janky first frame with glass | `prepareGlass()` not called at start. |
