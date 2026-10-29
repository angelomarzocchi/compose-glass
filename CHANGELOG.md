# Changelog

## Unreleased

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
