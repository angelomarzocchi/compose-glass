package io.github.angelomarzocchi.glass

import io.github.angelomarzocchi.glass.GlassQuality.Frosted
import io.github.angelomarzocchi.glass.GlassQuality.Full
import io.github.angelomarzocchi.glass.GlassQuality.Tinted
import org.junit.Assert.assertEquals
import org.junit.Test

/** The quality a device draws glass at: what it can, what it is, what it was asked. */
class GlassQualityTest {

    private fun resolve(asked: GlassQuality? = null, sdk: Int = 35, lowRam: Boolean = false, modest: Boolean = false, powerSave: Boolean = false) =
        resolveGlassQuality(asked, sdk, lowRam, modest, powerSave)

    @Test
    fun aDeviceDrawsAllItsAndroidVersionAllows() {
        assertEquals(Full, resolve(sdk = 33))
        assertEquals(Frosted, resolve(sdk = 31))
        assertEquals(Frosted, resolve(sdk = 32))
        assertEquals(Tinted, resolve(sdk = 30))
        assertEquals(Tinted, resolve(sdk = 24))
    }

    @Test
    fun neverMoreThanTheDeviceCanDraw() {
        assertEquals(Frosted, resolve(asked = Full, sdk = 31))
        assertEquals(Tinted, resolve(asked = Full, sdk = 28))
        assertEquals(Tinted, resolve(asked = Frosted, sdk = 30))
    }

    @Test
    fun aModestDeviceGetsLess() {
        assertEquals(Tinted, resolve(lowRam = true))
        assertEquals(Frosted, resolve(modest = true))
        assertEquals(Tinted, resolve(lowRam = true, modest = true))
        assertEquals(Tinted, resolve(sdk = 31, lowRam = true))
    }

    @Test
    fun batterySaverFrostsTheGlass() {
        assertEquals(Frosted, resolve(powerSave = true))
        assertEquals(Tinted, resolve(sdk = 30, powerSave = true))
    }

    @Test
    fun whatIsAskedForWinsOverTheDevicesOwnChoice() {
        assertEquals(Full, resolve(asked = Full, modest = true, powerSave = true))
        assertEquals(Tinted, resolve(asked = Tinted))
        assertEquals(Frosted, resolve(asked = Frosted))
    }
}
