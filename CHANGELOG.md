# Changelog

## 2.0.0

Liquid glass, faster, and on every device. Upgrading from 1.x:

- Use `GlassMaterial.liquid(dark, finish = 0.3f)` where you had `clear(dark)`, and `matte(dark)` where
  you had `frosted(dark)`. The acrylic presets are still there, and look as they did.
- Drop `rememberTiltKeyLight()` unless you want the glints to follow the phone.
- Rebuild: `GlassMaterial` has new fields, so its constructor and `copy()` changed.
- Call `prepareGlass()` off the main thread at start.
- A `GlassPainter` surface: add `compositingStrategy = …engravingIsolated()…` and `drawEngraving` (see
  the README). With compose-lumen, update to 1.3.0 and add the adapter's two new overrides.
- Older and modest devices now draw frosted or tinted liquid glass by themselves (`GlassQuality`).

- **Liquid glass is the glass.** `GlassMaterial.liquid(dark, finish)` runs from the clear lens (`finish`
  0) to matte (1) along one knob, and `GlassMaterial.matte(dark)` is its far end. No bevel and no side in
  shade: the page bends at a wide, soft rim and shows through richer in colour, a fine line of light runs
  round the edge glinting where the light meets it, a faint glow sits just inside it, and the glass casts
  a soft shadow of its own. `liquid(dark)` looks different from 0.2.0's: that one was a lightly shaded slab.
- New `GlassMaterial` properties, neutral by default so `clear`, `lens` and `frosted` look as they did:
  `saturation`, `rimGlow`, `shadow` and `hairlineFocus`.
- `lerp(start, stop, fraction)` between two glasses.
- New fields in the `GlassMaterial` data class change its constructor and `copy()` signatures: rebuild
  against this version.
- Faster: an engraving with no light on it is not drawn off-screen (liquid glass bends no labels:
  `inkDepth` 0), a page frosted by 4 dp or more is frosted at half its size, the body reads the page once
  where the rim does not split its colours, and the glow round a lit mark is only worked out where a
  light is on. In the demo's lab the median frame while scrolling went from about 55 ms to about 30 ms on
  an emulator, the page alone taking about 28 ms.
- The two AGSL programs are compiled once for the app instead of once a surface (about 4 ms each time on
  an emulator), and `prepareGlass()` compiles them ahead, off the main thread.
- `rememberTiltKeyLight` lets a turn through only past a quarter of a degree: a hand holding the phone
  still no longer redraws every glass fifty times a second.
- `GlassSurface` clips what it holds to its shape, lit or not.
- `GlassQuality` — `Full`, `Frosted`, `Tinted` — with `LocalGlassQuality` to ask for one and
  `rememberGlassQuality()` to read it. By default a device draws all its Android version allows, but an
  Android Go or low-memory device is `Tinted`, a phone under about 3 GB is `Frosted`, and battery saver
  holds glass at `Frosted` while it is on.
- Plain glass rewritten to look like the liquid glass: the page frosted at half size and a little
  richer in colour, cut to the shape (it used to show a frosted rectangle past a capsule's ends), the
  line of light along the edge glinting where the light meets it, the glass's shadow (rings below API
  28), and the lights clipped to the shape. It no longer makes a new blur and records the page again on
  every frame. `GlassSurface` colours its marks in the lights without the engraving's program too.
- The finger's lamp is softer: less of its light carried round the rim, a light under the finger rather
  than a fog over dark glass. It lights the engraved marks up instead of repainting them (its ink screens
  what is there): a dark label turns the lamp's colour, a white letter on a coloured disc stays white.
- `liquid()` at finish 0 has a wider rim and a gentler bend (24 dp, 10 dp): the page at the rim no longer
  folds over itself and shows twice.
- `GlassPainter.drawEngraving` and `engravingIsolated`, public: a surface drawn with the painter colours
  its marks in the lights below full quality too. compose-lumen 1.3.0's `IslandMaterial.drawMarks` and
  `isolatesContent` take them: the README's `GlassIsland` adapter shows how.
- Demo: built against compose-lumen's sources when they sit next to this repository.
- The skill (`skills/compose-glass`) rewritten for liquid glass: a workflow, recipes, the full compose-lumen
  adapter, qualities, a performance budget, what the user should see, and the usual mistakes.
- Demo: the lab has the qualities on chips; the gallery takes `--es quality Frosted`.
- Demo: the lab opens on liquid glass with a finish slider, the room's light off; the gallery shows the
  finish from 0 to 1 and compose-lumen's bar in glass (`--ei shot 5`, `6`).

## 1.1.1

- JitPack builds the library again: use this version. 1.1.0 was tagged but never built, so it cannot be installed.

## 1.1.0

- `GlassSurface(body = { … })`: whether the slab is drawn, read in draw. False leaves only what is
  engraved in it, for a surface whose glass another takes over for a while — a bubble growing into a
  sheet drawn with a `GlassPainter` of its own.

## 1.0.0

- `rememberStillGlassBackdrop(source, every, scale)`: a still of the page, taken every `every` (250 ms)
  at `scale` (half) of its size, for glass over a part of a changing page that hardly changes — a bar
  over a blurred background. Glass over it is drawn again only when a still is taken. The stills come
  from a renderer of their own, made once, that nothing waits on.
- Glass draws again only what changed: the page under a surface, frosted, is a layer of its own and the
  body's program another; each effect is made again only when its numbers change. A light that moves
  (the room's, the lamp, a selection) costs one program pass instead of the page, its frost and the
  program; the engraving no longer draws again when only the room's light moves.

## 0.2.0

- `GlassMaterial.liquid(dark)`: liquid glass, a lens with almost no slab. The page bends across a wide,
  soft rim, but the rim is barely shaded — no darker band, no thick bevel, no highlight across it — and a
  fine line of light runs along the edge, fainter on the far side.
- `GlassMaterial.hairline` and `farHairline`: the fine line of light along the very edge, on the side
  facing the room's light and on the far side, set apart from the `rim`'s shading. `hairline` is the
  `rim` unless set, so the other presets look as they did.

## 0.1.0

- First release: `GlassSurface`, `GlassPainter`, `GlassMaterial` (`clear`, `lens`, `frosted`),
  `rememberTiltKeyLight`, `glassLampFollowsFinger`.
