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

    /** Ten seconds at 50 samples a second of a phone held at [pitch], [roll], trembling as a hand does. */
    private fun heldInHand(pitch: Float, roll: Float, seconds: Int = 10): List<Pair<Float, Float>> {
        val random = java.util.Random(7)
        return List(seconds * 50) { i ->
            val t = i / 50f
            // A physiological tremor of a few tenths of a degree at 8 to 12 Hz, and a little noise.
            val shake = 0.3f * kotlin.math.sin(2f * Math.PI.toFloat() * 9f * t) + 0.1f * random.nextGaussian().toFloat()
            val sway = 0.2f * kotlin.math.sin(2f * Math.PI.toFloat() * 11f * t) + 0.1f * random.nextGaussian().toFloat()
            pitch + shake to roll + sway
        }
    }

    private fun writesHeldStill(gate: TiltGate): Int {
        val filter = TiltFilter()
        var writes = 0
        heldInHand(pitch = -35f, roll = 4f).forEach { (pitch, roll) ->
            filter.update(pitch, roll)
            if (gate.pass(filter.rotationX, filter.rotationY)) writes++
        }
        return writes
    }

    @Test
    fun aHandHoldingThePhoneStillDoesNotKeepTheGlassRedrawing() {
        // Fifty a second would be 500: a tremor smoothed is well inside the dead band.
        val writes = writesHeldStill(TiltGate())
        assertTrue("writes while held still: $writes", writes <= 2)
        // Where a fiftieth of a degree got through, it was most of them.
        val before = writesHeldStill(TiltGate(deadBand = 0.02f))
        assertTrue("writes with the old dead band: $before", before > 100)
    }

    @Test
    fun aTurnOfTheWristStillGetsThrough() {
        val filter = TiltFilter()
        val gate = TiltGate()
        var writes = 0
        heldInHand(pitch = -35f, roll = 0f, seconds = 1).forEach { (p, r) -> filter.update(p, r); gate.pass(filter.rotationX, filter.rotationY) }
        // Half a second rolling 4 degrees to the right.
        repeat(25) { i ->
            filter.update(-35f, 4f * (i + 1) / 25f)
            if (gate.pass(filter.rotationX, filter.rotationY)) writes++
        }
        assertTrue("writes while turning: $writes", writes >= 5)
        assertTrue("the light went right: ${gate.y}", gate.y > 1f)
    }
}
