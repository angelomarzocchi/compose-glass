---
name: compose-glass
description: How to use the Glass library (io.github.angelomarzocchi.glass) — an AGSL glass material for Jetpack Compose with refraction at the rim, engraved content and lights inside the slab. Load it before putting a bar, FAB, island, panel or any floating surface in glass, before writing a GlassSurface lighting block or using GlassPainter directly, before putting compose-lumen's FloatingNavBar in glass, and when glass renders plain, black, transparent, or looks at itself.
---

# Glass

Package `io.github.angelomarzocchi.glass`. Gradle:
`implementation("com.github.angelomarzocchi:compose-glass:<version>")` from `https://jitpack.io`.
minSdk 24; full glass on API 33+ (AGSL), frosted on 31–32, tinted on 24–30. No Material dependency.

## The one structural rule

Glass refracts a **recording of the page under it**. So:

1. Record the page: `Modifier.glassBackdrop(backdrop)` on the page's root (the scrolling content and its
   background), with `val backdrop = rememberGlassBackdrop()`.
2. Put every glass surface **outside** that page, as a sibling drawn after it, in the same parent
   (typically a `Box`). Glass inside the recorded page records itself → feedback, garbage, or nothing.

```kotlin
Box(Modifier.fillMaxSize()) {
    Page(Modifier.fillMaxSize().glassBackdrop(backdrop).background(surface))   // recorded
    GlassSurface(backdrop, GlassMaterial.liquid(dark, finish = 0.3f), Modifier.align(Alignment.BottomCenter)) { … }
}
```

Give the page an opaque background inside the recorded modifier chain (after `glassBackdrop`) —
otherwise the glass sees transparency.

## GlassSurface

```kotlin
val lamp = rememberGlassLamp()
GlassSurface(
    backdrop = backdrop,
    material = GlassMaterial.liquid(dark, finish = 0.3f),   // see "Choosing the glass"
    modifier = Modifier.size(280.dp, 64.dp).glassLampFollowsFinger(lamp),
    cornerRadius = null,                    // null = capsule; or e.g. 28.dp
    lamp = lamp,                            // the finger's light; same instance as in the modifier
    dark = dark,                            // dark glass adds light; pale glass shows it as colour
    lampInk = accent,                       // what engraved content turns under the lamp on pale glass
    lighting = { size -> /* a selection, say; keyLight only if asked for */ },
) { content() }                             // engraved: bends at the rim, lights up under lights
```

- `glassLampFollowsFinger` does **not** consume touches: buttons inside still get clicks.
- Pass `dark` explicitly when the theme can differ from the system setting (it defaults to
  `isSystemInDarkTheme()`), and pick the material with the same flag: `GlassMaterial.liquid(dark, …)`.

## Choosing the glass

Default to **`GlassMaterial.liquid(dark, finish)`**: liquid glass, iOS-style. One knob, `finish`, 0 to 1:

| The user asks for | Use |
|---|---|
| liquid glass, "like iOS", clear, see-through | `liquid(dark)` (finish 0) |
| a bar, a FAB, an island over a busy page (labels must read) | `liquid(dark, finish = 0.3f)` |
| frosted, satin | `liquid(dark, finish = 0.5f)` to `0.75f` |
| matte, a panel or a sheet with text | `matte(dark)` (finish 1) |
| glass that changes (frosts on scroll, morphs) | `lerp(a, b, fraction)` read in draw |
| a bevelled slab, acrylic, "3D button" (only if asked) | `clear(dark)`, `lens(dark)`, `frosted(dark)` |

- Do **not** add `rememberTiltKeyLight()` unless the user asks for the light to follow the phone. On
  liquid glass it only moves the edge's glints, and it keeps the glass redrawing while the phone is in hand.
- Do not turn the acrylic shading (`edge`, `rim`, `specular`) up on liquid glass: that brings back the
  bevel. Tune `saturation`, `hairline`, `rimGlow`, `shadow` with `copy()` instead.
- `inkReflection` mirrors labels into the rim: leave it at 0 unless asked, it reads as a glitch.

## The lighting block

`lighting: GlassLighting.(Size) -> Unit` runs **every frame, in the draw phase**, in the surface's own
pixels. Read `Animatable.value`s and state there — it redraws when they change. Never allocate heavy
objects or launch coroutines in it. Fields (reset to defaults before each call):

| Field | Meaning |
|---|---|
| `keyLight: Offset` | Direction of the room's light (x right, y down). `DefaultKeyLight` = up-left. `rememberTiltKeyLight()` follows the phone (only when asked for). |
| `lamp`, `lampOn`, `lampColor` | The finger's light (filled from `lamp` for you). |
| `lampInk` | Colour engraved content turns under the lamp. |
| `selection: Rect`, `selectionOn`, `selectionColor` | A light left on inside, capsule-shaped, e.g. the selected tab. Animate `selection` between tabs, or `selectionOn` 0→1 for a fill. |
| `selectionInk` | Colour engraved content turns inside the selection (usually white). |

## Lower level: GlassPainter

For a surface with its own geometry (ends that move, a custom outline): `rememberGlassPainter()`,
`Modifier.glassPosition(painter)` where the surface is laid out, then in `drawBehind` call
`painter.light { … }` and `with(painter) { drawBody(backdrop, material, shape, radius, dark, overhang) }`,
and in a `graphicsLayer` on the content call `painter.light { … }` again and set
`renderEffect = painter.inkEffect(this, material, shape, radius)`. Call `light {}` before both: each
call resets the lights. One painter per surface.

## With compose-lumen's FloatingNavBar

`FloatingNavBar(material = …)` takes an `IslandMaterial`. The adapter `GlassIsland` (in the README of
both libraries) maps `IslandLights` → `GlassLighting` (lamp, selection as a light inside, accent as
`selectionColor`) and calls `drawBody` / `inkEffect`. Its `modifier` must be `Modifier.glassPosition(painter)`.

## Fallbacks and diagnosis

- `glassShaderError()` returns null when full glass is drawn, otherwise the reason (API level, or the
  AGSL compiler's message). Show it in a debug screen rather than guessing.
- Android Studio's preview has no AGSL: glass is plain there. Judge it on a device with API 33+.
- Quality: `GlassQuality.Full` (API 33), `Frosted` (API 31, blur only), `Tinted` (any API, no
  off-screen pass). Each device picks its own (`rememberGlassQuality()`): Android Go / low memory →
  `Tinted`, under ~3 GB → `Frosted`, battery saver → at most `Frosted`. Override with
  `CompositionLocalProvider(LocalGlassQuality provides GlassQuality.Frosted)`; never more than the API
  allows. Do not write your own API-level checks around glass.
- Below `Full`, `inkEffect` is null: `GlassSurface` then colours its marks in the lights by itself; a
  custom `GlassPainter` surface (or compose-lumen's island) keeps its marks' own colours. Layouts and
  touches are identical at every quality.
- To see a lower quality on a recent phone, provide it through `LocalGlassQuality`.

## Performance

A surface draws again only what changed: the page under it (and its frost) when the page changes, one
program pass when a light moves. Over a page that changes every frame (video, camera) every surface is
drawn again every frame: for glass over a part of it that hardly changes (a bar over a blurred
background), pass `rememberStillGlassBackdrop(backdrop)` instead of `backdrop` — a still of the page
taken every 250 ms at half size, free between stills. Keep the live backdrop for glass over the moving
image itself. Use glass for a handful of floating surfaces, not for list items. `frost` adds blur passes
(halved in size from 4 dp): keep it modest on large panels.

- Call `prepareGlass()` once, off the main thread, at app start (`Dispatchers.Default`): the first AGSL
  compile in a process can take 100 ms or more and would otherwise land on the first frame with glass.
- An unlit surface on liquid glass draws what it holds without an off-screen pass. Keep `inkDepth` at 0
  and `inkReflection` at 0 unless asked: either brings the engraving's pass back on every frame.
- `rememberTiltKeyLight()` costs a sensor and redraws on every visible turn: only when asked for.

## Troubleshooting

| Symptom | Cause |
|---|---|
| Glass shows nothing / only a tint | The page is not recorded (`glassBackdrop` missing) or has no opaque background in its chain. |
| Glass shows a smeared copy of itself | The surface is inside the recorded page. Move it out, as a sibling. |
| Page under glass is offset | The backdrop's page and the surface must be in the same window; `glassPosition` must sit on the surface's measured bounds (GlassSurface does it). |
| Lights don't move | Values read in composition instead of inside `lighting` / `light {}`. |
| Plain glass on a recent phone | Check `glassShaderError()`; a custom `copy()` with a non-finite value can break the shader uniforms. |
