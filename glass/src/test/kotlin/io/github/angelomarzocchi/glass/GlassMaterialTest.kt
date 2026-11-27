package io.github.angelomarzocchi.glass

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
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
        listOf(
            GlassMaterial.clear(false), GlassMaterial.lens(true), GlassMaterial.frosted(false), GlassMaterial.liquid(true),
            GlassMaterial.liquid(false, finish = 0.5f), GlassMaterial.matte(true),
        ).forEach {
            assertTrue(it.bevel > 0.dp && it.lampReach > 0.dp && it.inkGlow >= 0.dp && it.frost >= 0.dp)
        }
    }

    @Test
    fun liquidGlassHasNoSlabShading() {
        for (dark in listOf(false, true)) {
            for (finish in listOf(0f, 0.5f, 1f)) {
                val glass = GlassMaterial.liquid(dark, finish)
                // No darker band, no light-and-shade across the rim, no highlight over it: that is the acrylic look.
                assertEquals(0f, glass.edge)
                assertEquals(0f, glass.rim)
                assertEquals(0f, glass.specular)
                assertTrue(glass.saturation > 1f && glass.hairline > 0f && glass.shadow > 0f)
            }
        }
    }

    @Test
    fun theFinishFrostsTheGlassAndSettlesItsBend() {
        for (dark in listOf(false, true)) {
            val steps = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { GlassMaterial.liquid(dark, it) }
            steps.zipWithNext().forEach { (a, b) ->
                assertTrue(b.frost > a.frost)
                assertTrue(b.tintAmount > a.tintAmount)
                assertTrue(b.refraction < a.refraction)
                assertTrue(b.saturation < a.saturation)
            }
            assertEquals(0.dp, steps.first().frost)
            assertEquals(GlassMaterial.matte(dark), steps.last())
            // Out of range is the nearest end, not an extrapolation.
            assertEquals(steps.first(), GlassMaterial.liquid(dark, -1f))
            assertEquals(steps.last(), GlassMaterial.liquid(dark, 2f))
        }
    }

    @Test
    fun lerpReachesBothEndsAndEveryPropertyBetween() {
        val a = GlassMaterial.clear(dark = false)
        val b = GlassMaterial.matte(dark = true)
        assertEquals(a, lerp(a, b, 0f))
        assertEquals(b, lerp(a, b, 1f))
        val half = lerp(a, b, 0.5f)
        assertEquals((a.bevel.value + b.bevel.value) / 2f, half.bevel.value, 1e-4f)
        assertEquals((a.saturation + b.saturation) / 2f, half.saturation, 1e-4f)
        assertEquals((a.shadow + b.shadow) / 2f, half.shadow, 1e-4f)
        assertEquals((a.hairlineFocus + b.hairlineFocus) / 2f, half.hairlineFocus, 1e-4f)
    }

    @Test
    fun theAcrylicPresetsLookAsTheyDid() {
        // The new properties are neutral unless a preset asks for them.
        for (glass in listOf(GlassMaterial.clear(false), GlassMaterial.lens(true), GlassMaterial.frosted(false))) {
            assertEquals(1f, glass.saturation)
            assertEquals(0f, glass.rimGlow)
            assertEquals(0f, glass.shadow)
            assertEquals(1f, glass.hairlineFocus)
        }
    }
}
