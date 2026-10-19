# Glass

**A glass material for Jetpack Compose, with light that lives inside it.**

Not a blur of the page, and not a lens. Glass is a slab with a rounded rim, drawn with two AGSL
programs:

- **The page bends at the rim.** What is under the glass is refracted only where the rim falls away, so
  the edge magnifies, with a touch of chromatic dispersion.
- **The rim has depth.** Seen through at the rim the slab is thickest: denser, a shade darker, and the
  room's light catches it on one side and leaves the other in shade — a highlight and a hairline that
  move as the phone turns (`rememberTiltKeyLight`).
- **Content is engraved.** Icons and labels sit *inside* the slab: they bend at the rim too, a little
  less, and the polished rim can mirror them.
- **Light lives inside.** The finger is a lamp put down on the glass — its light spreads through the body
  and runs out to the rim. A selection is a light left on inside, the shape of its plate, which the rim
  catches as it passes. Engraved marks light up as the light reaches them.

Full glass needs Android 13 (API 33, AGSL). Below that a surface falls back to frosted (API 31–32,
`RenderEffect` blur) or tinted (24–30) glass with flat lights — it never crashes, and
`glassShaderError()` tells you why.

## Install

Published on [JitPack](https://jitpack.io/#angelomarzocchi/compose-glass).

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.angelomarzocchi:compose-glass:0.1.0")
}
```

minSdk 24, compileSdk 37, Compose BOM `2026.09.00` or later. No Material dependency.

## Quick start

Glass needs to *see* the page under it. Record the page into a backdrop, and put the glass **next to**
the page, over it — never inside it (it would be looking at itself).

```kotlin
val backdrop = rememberGlassBackdrop()
val dark = isSystemInDarkTheme()

Box(Modifier.fillMaxSize()) {
    // The page: recorded every frame for the glass to see.
    LazyColumn(Modifier.fillMaxSize().glassBackdrop(backdrop)) { /* … */ }

    // The glass, over the page, as a sibling.
    val lamp = rememberGlassLamp()
    GlassSurface(
        backdrop = backdrop,
        material = GlassMaterial.clear(dark),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .size(280.dp, 64.dp)
            .glassLampFollowsFinger(lamp),   // the finger carries a light; clicks still pass through
        lamp = lamp,
        dark = dark,
    ) {
        Text("Engraved in glass", Modifier.align(Alignment.Center))
    }
}
```

### Materials

| | |
|---|---|
| `GlassMaterial.clear(dark)` | Lit acrylic: a thin rim that bends the page only in its last few dp, depth shown by thickness and light. The default. |
| `GlassMaterial.lens(dark)` | A wide rim that bends the page across all of it, splits the colours and mirrors the labels. Loud. |
| `GlassMaterial.frosted(dark)` | Clear glass, frosted and denser, for a panel or a sheet that holds a lot to read. |

Every property is a field of the `GlassMaterial` data class — `copy()` it: rim width (`bevel`),
`refraction`, `falloff`, `dispersion`, `frost`, `tint`/`tintAmount`, `edge`, `rim`, `specular`,
`inkDepth`, `inkReflection`, `inkGlow`, `lampReach`.

### Lights

`GlassSurface`'s `lighting` block runs every frame, in the surface's own pixels, from the draw phase:
read animated values there, not in composition.

```kotlin
val roomLight = rememberTiltKeyLight()          // the room's light follows the phone turning
val at = remember { Animatable(0f) }            // where the selection is, in tabs

GlassSurface(
    backdrop, material,
    lighting = { size ->
        keyLight = roomLight()
        val cell = size.width / tabCount
        selection = Rect(at.value * cell + 6f, 6f, (at.value + 1) * cell - 6f, size.height - 6f)
        selectionOn = 1f
        selectionColor = accent
        selectionInk = Color.White              // engraved icons turn white in the selection's light
    },
) { /* tabs */ }
```

A light that fills up is just `selectionOn` animated from 0 to 1 — the demo's "hold to fill" button.

### Your own shapes

`GlassSurface` is a rounded rectangle or a capsule. For a surface that draws its own shape — one whose
ends move — use the painter directly:

```kotlin
val painter = rememberGlassPainter()
Box(
    Modifier
        .glassPosition(painter)
        .drawBehind {
            painter.light { lamp = …; lampOn = … }
            with(painter) { drawBody(backdrop, material, shape = myRect, radius = r, dark = dark) }
        }
        .graphicsLayer {
            painter.light { /* the same lights */ }
            renderEffect = painter.inkEffect(this, material, myRect, r)   // engraving; null below API 33
        },
) { /* content */ }
```

### A navigation bar in glass

[compose-lumen](https://github.com/angelomarzocchi/compose-lumen)'s `FloatingNavBar` takes an
`IslandMaterial`. This adapter puts it in glass — the selected tab becomes a light inside:

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
}

@Composable
fun rememberGlassIsland(backdrop: GlassBackdrop, material: GlassMaterial, keyLight: () -> Offset = { DefaultKeyLight }): GlassIsland {
    val painter = rememberGlassPainter()
    return remember(backdrop, material, painter, keyLight) { GlassIsland(backdrop, material, painter, keyLight) }
}

// FloatingNavBar(items, selected, onSelect, material = rememberGlassIsland(backdrop, GlassMaterial.clear(dark), roomLight))
```

## Performance

Each surface re-records the backdrop through its own layer every frame it is drawn, and runs two
fragment shaders over its own area. Keep glass to the few surfaces that float (a bar, a FAB, an island,
a panel); do not tile a list with it. The tilt sensor is only listened to while the screen is resumed.

## Demo

`demo/` is the glass lab: an island, a tab bar, a FAB and a panel in glass over a busy page, with every
property of the glass on a slider. Run it on a phone with Android 13 or later; Android Studio's preview
has no AGSL, so there the glass is drawn plain.

## Tests

```
./gradlew :glass:testDebugUnitTest              # JVM + Robolectric (API 30 and 31 fallbacks)
./gradlew :glass:connectedDebugAndroidTest      # on a device or emulator with API 33+: the AGSL compiles
```

The JVM tests cover the tilt filter, the materials, the fallbacks below API 33, and that the glass
never takes a click from what it holds. Only a real renderer can compile AGSL, so the shader test is
instrumented.

## Using it with an AI coding assistant

[`skills/compose-glass/SKILL.md`](skills/compose-glass/SKILL.md) teaches an LLM to use Glass correctly.
With Claude Code, copy the folder into your project's `.claude/skills/`.

## License

Apache 2.0 — see [LICENSE](LICENSE).
