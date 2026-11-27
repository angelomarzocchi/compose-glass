package io.github.angelomarzocchi.glass

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * How much of the glass is drawn. Every surface looks like glass at every quality — the same shape, tint,
 * line of light along the edge and lights inside — and nothing about layout or touch changes; what goes
 * is what costs.
 */
public enum class GlassQuality {
    /**
     * All of it: the page bent at the rim and split into its colours there, the edge's light, the
     * engraving lit from inside. Android 13 (API 33) and up.
     */
    Full,

    /**
     * The page frosted and a little richer in colour under the body's tint, the line of light along the
     * edge, a shadow, and the lights drawn flat; no bend. One blur a surface, and no program. Android 12
     * (API 31) and up.
     */
    Frosted,

    /**
     * A tint all but opaque, the line of light along the edge and the lights drawn flat: nothing is drawn
     * off-screen. Any API level.
     */
    Tinted,
}

/**
 * The quality glass is drawn at below this point, or null — the default — for each device to choose its
 * own (see [rememberGlassQuality]). A device is never asked for more than it can draw: [GlassQuality.Full]
 * is [GlassQuality.Frosted] on Android 12, and [GlassQuality.Tinted] below.
 *
 * ```kotlin
 * CompositionLocalProvider(LocalGlassQuality provides GlassQuality.Frosted) { App() }
 * ```
 */
public val LocalGlassQuality: ProvidableCompositionLocal<GlassQuality?> = staticCompositionLocalOf { null }

/**
 * The quality glass is drawn at here: [LocalGlassQuality] if it is set, within what the device can draw;
 * otherwise the device's own.
 *
 * A device draws all the glass its Android version allows, but for these:
 * - an Android Go device, or one that says its memory is low, is [GlassQuality.Tinted];
 * - a phone with less than about 3 GB of memory — the GPUs that come with it struggle with a page
 *   blurred and bent every frame — is [GlassQuality.Frosted];
 * - with battery saver on, glass is at most [GlassQuality.Frosted], and comes back when it goes off.
 */
@Composable
public fun rememberGlassQuality(): GlassQuality {
    val asked = LocalGlassQuality.current
    val context = LocalContext.current
    val device = remember(context) { DeviceClass.of(context) }
    val saving = remember(context) { PowerSave.watch(context) }
    return resolveGlassQuality(asked, Build.VERSION.SDK_INT, device.lowRam, device.modest, saving.value)
}

/**
 * The quality to draw at: [asked], or the device's own choice when nothing was asked, never more than
 * [sdk] allows. Of two qualities the lower is the later in [GlassQuality].
 */
internal fun resolveGlassQuality(
    asked: GlassQuality?,
    sdk: Int,
    lowRam: Boolean,
    modest: Boolean,
    powerSave: Boolean,
): GlassQuality {
    val most = when {
        sdk >= 33 -> GlassQuality.Full
        sdk >= 31 -> GlassQuality.Frosted
        else -> GlassQuality.Tinted
    }
    val wanted = asked ?: when {
        lowRam -> GlassQuality.Tinted
        modest || powerSave -> GlassQuality.Frosted
        else -> GlassQuality.Full
    }
    return if (wanted.ordinal >= most.ordinal) wanted else most
}

/** What the device is, for the quality it gets: read once, it does not change. */
private class DeviceClass(val lowRam: Boolean, val modest: Boolean) {
    companion object {
        /** Below this much memory a phone is modest: a "3 GB" phone reports about 2.8 GiB. */
        private const val ModestMemory = 3L * 1024 * 1024 * 1024

        fun of(context: Context): DeviceClass {
            val activities = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return DeviceClass(lowRam = false, modest = false)
            val memory = ActivityManager.MemoryInfo().also { activities.getMemoryInfo(it) }
            return DeviceClass(
                lowRam = activities.isLowRamDevice,
                modest = memory.totalMem in 1 until ModestMemory,
            )
        }
    }
}

/** Whether battery saver is on, for the whole app: one receiver, registered the first time it is asked. */
private object PowerSave {
    private val saving = mutableStateOf(false)
    private var watching = false

    fun watch(context: Context): State<Boolean> {
        if (watching) return saving
        watching = true
        val app = context.applicationContext ?: context
        val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return saving
        saving.value = power.isPowerSaveMode
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                saving.value = power.isPowerSaveMode
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(receiver, filter)
            }
        }
        return saving
    }
}
