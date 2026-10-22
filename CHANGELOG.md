# Changelog

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
