package io.github.angelomarzocchi.glass

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The AGSL programs compile on a real renderer. Only a device or an emulator with API 33 or later can
 * say: the JVM tests cannot compile AGSL. Run with `./gradlew :glass:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class GlassShaderTest {

    @Test
    fun bothProgramsCompile() {
        assumeTrue("AGSL needs API 33", Build.VERSION.SDK_INT >= 33)
        assertNull(glassShaderError())
    }
}
