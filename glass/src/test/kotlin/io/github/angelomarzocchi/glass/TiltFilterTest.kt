package io.github.angelomarzocchi.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The room's light follows the device turning: it answers the wrist, stays within its limit, and
 * settles back when the device is held still at any angle.
 */
class TiltFilterTest {

    private fun TiltFilter.feed(pitch: Float, roll: Float, samples: Int) = repeat(samples) { update(pitch, roll) }

    @Test
    fun theAngleTheDeviceStartsAtIsRest() {
        val filter = TiltFilter()
        filter.feed(pitch = -40f, roll = 12f, samples = 200)
        assertEquals(0f, filter.rotationX, 1e-3f)
        assertEquals(0f, filter.rotationY, 1e-3f)
    }

    @Test
    fun aTurnOfTheWristMovesTheLight() {
        val filter = TiltFilter()
        filter.feed(pitch = -40f, roll = 0f, samples = 10)
        filter.feed(pitch = -37f, roll = 3f, samples = 10)
        assertTrue("rotationX follows the pitch, inverted: ${filter.rotationX}", filter.rotationX < -1f)
        assertTrue("rotationY follows the roll: ${filter.rotationY}", filter.rotationY > 1f)
        val light = keyLightFor(filter.rotationX, filter.rotationY)
        assertTrue("rolled right, the light moves right: $light", light.x > DefaultKeyLight.x)
    }

    @Test
    fun neverPastTheLimit() {
        val filter = TiltFilter()
        filter.feed(pitch = 0f, roll = 0f, samples = 1)
        filter.feed(pitch = -60f, roll = 70f, samples = 30)
        assertTrue(abs(filter.rotationX) <= TiltFilter.LimitDegrees + 1e-3f)
        assertTrue(abs(filter.rotationY) <= TiltFilter.LimitDegrees + 1e-3f)
    }

    @Test
    fun heldStillAtANewAngleItSettlesBack() {
        val filter = TiltFilter()
        filter.feed(pitch = -40f, roll = 0f, samples = 10)
        filter.feed(pitch = -20f, roll = 15f, samples = 600)
        assertEquals(0f, filter.rotationX, .05f)
        assertEquals(0f, filter.rotationY, .05f)
    }

    @Test
    fun aRollAcrossTheHalfTurnIsASmallStep() {
        val filter = TiltFilter()
        filter.feed(pitch = 0f, roll = 179f, samples = 5)
        filter.feed(pitch = 0f, roll = -179f, samples = 10)
        assertTrue("2° of roll, not 358°: ${filter.rotationY}", filter.rotationY in 0f..2f)
    }

    @Test
    fun aSampleThatIsNotANumberIsIgnored() {
        val filter = TiltFilter()
        filter.feed(pitch = 0f, roll = 0f, samples = 5)
        filter.update(Float.NaN, Float.NaN)
        filter.feed(pitch = 0f, roll = 2f, samples = 5)
        assertTrue(filter.rotationX.isFinite() && filter.rotationY.isFinite())
    }

    @Test
    fun atRestTheLightIsTheDefault() {
        assertEquals(DefaultKeyLight, keyLightFor(0f, 0f))
    }
}
