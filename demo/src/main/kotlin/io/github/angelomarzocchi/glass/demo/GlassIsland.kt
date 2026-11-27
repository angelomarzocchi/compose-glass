package io.github.angelomarzocchi.glass.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import io.github.angelomarzocchi.glass.DefaultKeyLight
import io.github.angelomarzocchi.glass.GlassBackdrop
import io.github.angelomarzocchi.glass.GlassLighting
import io.github.angelomarzocchi.glass.GlassMaterial
import io.github.angelomarzocchi.glass.GlassPainter
import io.github.angelomarzocchi.glass.glassPosition
import io.github.angelomarzocchi.glass.rememberGlassPainter
import io.github.angelomarzocchi.lumen.IslandLights
import io.github.angelomarzocchi.lumen.IslandMaterial

/**
 * compose-lumen's island in glass: the README's adapter, as the demo uses it to check that the bar's
 * lights — the finger's lamp, the selected tab as a light inside — still read in the glass.
 */
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
fun rememberGlassIsland(
    backdrop: GlassBackdrop,
    material: GlassMaterial,
    keyLight: () -> Offset = { DefaultKeyLight },
): GlassIsland {
    val painter = rememberGlassPainter()
    return remember(backdrop, material, painter, keyLight) { GlassIsland(backdrop, material, painter, keyLight) }
}
