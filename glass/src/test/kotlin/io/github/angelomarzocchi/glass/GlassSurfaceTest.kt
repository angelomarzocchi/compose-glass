package io.github.angelomarzocchi.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Glass over a page, on the API levels where it cannot be drawn in full: it must still draw — tinted
 * below 31, frosted from 31 — and never take a touch from what it holds.
 *
 * The full glass, on API 33 and up, is drawn by AGSL programs that only a real renderer compiles: see
 * the instrumented `GlassShaderTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 31])
class GlassSurfaceTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun belowAndroid13TheReasonIsGiven() {
        val reason = glassShaderError()
        assertNotNull(reason)
        assertTrue(reason!!.contains("API ${Build.VERSION.SDK_INT}"))
    }

    @Test
    fun aButtonEngravedInTheGlassStillGetsItsClick() {
        var clicks = 0
        rule.setContent {
            val backdrop = rememberGlassBackdrop()
            val lamp = rememberGlassLamp()
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .glassBackdrop(backdrop)
                        .background(Color.Magenta),
                )
                GlassSurface(
                    backdrop = backdrop,
                    material = GlassMaterial.clear(dark = false),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(200.dp, 64.dp)
                        .glassLampFollowsFinger(lamp)
                        .testTag("glass"),
                    lamp = lamp,
                    lighting = { size ->
                        selection = Rect(0f, 0f, size.width / 2f, size.height)
                        selectionOn = 1f
                    },
                ) {
                    BasicText(
                        "Tap me",
                        Modifier
                            .align(Alignment.Center)
                            .clickable { clicks++ },
                    )
                }
            }
        }
        rule.onNodeWithText("Tap me").performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun theLampComesOnUnderTheFingerAndGoesOutAfter() {
        lateinit var lamp: GlassLamp
        rule.setContent {
            val backdrop = rememberGlassBackdrop()
            lamp = rememberGlassLamp()
            Box(Modifier.fillMaxSize().glassBackdrop(backdrop)) {
                GlassSurface(
                    backdrop = backdrop,
                    material = GlassMaterial.frosted(dark = true),
                    modifier = Modifier
                        .size(120.dp)
                        .glassLampFollowsFinger(lamp)
                        .testTag("glass"),
                    cornerRadius = 24.dp,
                    lamp = lamp,
                ) {}
            }
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("glass").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(500)
        rule.runOnIdle {
            assertEquals(1f, lamp.on.value, 0.01f)
            assertTrue("under the finger: ${lamp.x.value}", lamp.x.value > 0f)
        }
        rule.onNodeWithTag("glass").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.runOnIdle { assertEquals(0f, lamp.on.value, 0.01f) }
    }

    @Test
    fun belowAndroid13ThereIsNoEngravingEffect() {
        var effect: Any? = Unit
        rule.setContent {
            val painter = rememberGlassPainter()
            val density = androidx.compose.ui.platform.LocalDensity.current
            effect = painter.inkEffect(density, GlassMaterial.clear(false), Rect(0f, 0f, 10f, 10f), 5f)
        }
        rule.runOnIdle { assertNull(effect) }
    }
}
