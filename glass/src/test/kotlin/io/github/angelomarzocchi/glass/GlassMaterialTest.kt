package io.github.angelomarzocchi.glass

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

/** The presets are what their names say, in both themes. */
class GlassMaterialTest {

    @Test
    fun theLensBendsMoreAndOverMoreOfTheRimThanClearGlass() {
        for (dark in listOf(false, true)) {
            val clear = GlassMaterial.clear(dark)
            val lens = GlassMaterial.lens(dark)
            assertTrue(lens.refraction > clear.refraction)
            assertTrue(lens.bevel > clear.bevel)
            // A lower falloff spreads the bend across the rim instead of its last few dp.
            assertTrue(lens.falloff < clear.falloff)
            assertTrue(lens.dispersion > clear.dispersion)
        }
    }

    @Test
    fun frostedGlassIsClearGlassFrostedAndDenser() {
        for (dark in listOf(false, true)) {
            val clear = GlassMaterial.clear(dark)
            val frosted = GlassMaterial.frosted(dark)
            assertTrue(frosted.frost > clear.frost)
            assertTrue(frosted.tintAmount > clear.tintAmount)
            assertTrue(frosted.copy(frost = clear.frost, tintAmount = clear.tintAmount, bevel = clear.bevel) == clear)
        }
    }

    @Test
    fun liquidGlassBendsAcrossAWiderRimButBarelyShadesIt() {
        for (dark in listOf(false, true)) {
            val clear = GlassMaterial.clear(dark)
            val liquid = GlassMaterial.liquid(dark)
            assertTrue(liquid.bevel > clear.bevel)
            assertTrue(liquid.refraction > clear.refraction)
            assertTrue(liquid.falloff < clear.falloff)
            // The depth is in the bend, not in the shading: no dark band, little rim light, a fine edge.
            assertTrue(liquid.edge < clear.edge && liquid.rim < clear.rim && liquid.specular < clear.specular)
            assertTrue(liquid.hairline > liquid.rim && liquid.farHairline > 0f)
            assertTrue(liquid.tintAmount < clear.tintAmount)
        }
    }

    @Test
    fun theEdgeLineIsTheRimsUnlessSaidOtherwise() {
        val clear = GlassMaterial.clear(dark = true)
        assertTrue(clear.hairline == clear.rim && clear.farHairline == 0f)
        assertTrue(clear.copy(rim = 0.9f).hairline == clear.hairline)
    }

    @Test
    fun darkGlassIsSmokyAndLightGlassMilky() {
        val dark = GlassMaterial.clear(dark = true).tint
        val light = GlassMaterial.clear(dark = false).tint
        assertTrue(dark.red + dark.green + dark.blue < 0.5f)
        assertTrue(light.red + light.green + light.blue > 2.5f)
    }

    @Test
    fun everyLengthIsPositive() {
        listOf(GlassMaterial.clear(false), GlassMaterial.lens(true), GlassMaterial.frosted(false), GlassMaterial.liquid(true)).forEach {
            assertTrue(it.bevel > 0.dp && it.lampReach > 0.dp && it.inkGlow >= 0.dp && it.frost >= 0.dp)
        }
    }
}
