package io.github.angelomarzocchi.glass.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import io.github.angelomarzocchi.glass.GlassBackdrop
import io.github.angelomarzocchi.glass.GlassMaterial
import io.github.angelomarzocchi.glass.GlassSurface
import io.github.angelomarzocchi.glass.glassBackdrop
import io.github.angelomarzocchi.glass.glassLampFollowsFinger
import io.github.angelomarzocchi.glass.glassShaderError
import io.github.angelomarzocchi.glass.rememberGlassBackdrop
import io.github.angelomarzocchi.glass.rememberGlassLamp
import io.github.angelomarzocchi.glass.rememberTiltKeyLight
import kotlinx.coroutines.launch

/**
 * The glass lab: an island at the top, a tab bar, a FAB and a panel, in glass, over a busy page that
 * scrolls underneath, with every property of the glass on a slider (the cog in the island).
 *
 * What to try. Scroll: the page bends at every rim and splits into its colours there. Put a finger on
 * the bar and slide it: the lamp's light spreads through the glass and runs out to the rim, and the
 * engraved icons light up as it passes; tap a tab and the selection — a light left on inside — flows
 * there. Hold the +: it fills with light. Tilt the phone: the room's light moves round every rim.
 *
 * Full glass needs Android 13 (API 33) or later. Below that it is frosted or tinted, and the page says so.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { GlassLab() }
    }
}

private enum class Preset(val label: String) {
    Clear("Clear"),
    Lens("Lens"),
    Frosted("Frosted"),
    ;

    fun material(dark: Boolean) = when (this) {
        Clear -> GlassMaterial.clear(dark)
        Lens -> GlassMaterial.lens(dark)
        Frosted -> GlassMaterial.frosted(dark)
    }
}

/** What the panel sets. */
@Stable
private class LabState(dark: Boolean) {
    var dark by mutableStateOf(dark)
    var preset by mutableStateOf(Preset.Clear)
    var bevel by mutableFloatStateOf(0f)
    var refraction by mutableFloatStateOf(0f)
    var dispersion by mutableFloatStateOf(0f)
    var frost by mutableFloatStateOf(0f)
    var tintAmount by mutableFloatStateOf(0f)
    var edge by mutableFloatStateOf(0f)
    var rim by mutableFloatStateOf(0f)
    var specular by mutableFloatStateOf(0f)
    var inkDepth by mutableFloatStateOf(0f)
    var inkReflection by mutableFloatStateOf(0f)
    var lampReach by mutableFloatStateOf(0f)
    var tilt by mutableStateOf(true)
    var panelOpen by mutableStateOf(false)

    init {
        reset()
    }

    fun reset() {
        val m = preset.material(dark)
        bevel = m.bevel.value
        refraction = m.refraction.value
        dispersion = m.dispersion
        frost = m.frost.value
        tintAmount = m.tintAmount
        edge = m.edge
        rim = m.rim
        specular = m.specular
        inkDepth = m.inkDepth
        inkReflection = m.inkReflection
        lampReach = m.lampReach.value
    }

    fun material(): GlassMaterial = preset.material(dark).copy(
        bevel = bevel.dp,
        refraction = refraction.dp,
        dispersion = dispersion,
        frost = frost.dp,
        tintAmount = tintAmount,
        edge = edge,
        rim = rim,
        specular = specular,
        inkDepth = inkDepth,
        inkReflection = inkReflection,
        lampReach = lampReach.dp,
    )
}

@Composable
private fun GlassLab() {
    val systemDark = isSystemInDarkTheme()
    val state = remember { LabState(systemDark) }
    MaterialTheme(colorScheme = if (state.dark) darkColorScheme() else lightColorScheme()) {
        val backdrop = rememberGlassBackdrop()
        val roomLight = rememberTiltKeyLight(enabled = state.tilt)
        val material = state.material()
        val shaderError = remember { glassShaderError() }

        Box(Modifier.fillMaxSize()) {
            // The page: recorded for the glass, and the glass is not inside it.
            Box(
                Modifier
                    .fillMaxSize()
                    .glassBackdrop(backdrop)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(top = 80.dp, bottom = 180.dp),
                ) {
                    LabPage(shaderError)
                }
            }

            TopIsland(
                backdrop = backdrop,
                material = material,
                dark = state.dark,
                roomLight = roomLight,
                onSettings = { state.panelOpen = !state.panelOpen },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
            )

            HoldFab(
                backdrop = backdrop,
                material = material,
                dark = state.dark,
                roomLight = roomLight,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 104.dp),
            )

            TabBar(
                backdrop = backdrop,
                material = material,
                dark = state.dark,
                roomLight = roomLight,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )

            AnimatedVisibility(
                visible = state.panelOpen,
                enter = fadeIn() + scaleIn(initialScale = 0.94f),
                exit = fadeOut() + scaleOut(targetScale = 0.94f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 76.dp),
            ) {
                Panel(state, backdrop, roomLight)
            }
        }
    }
}

/** The island at the top: who, what, and the cog that opens the panel. */
@Composable
private fun TopIsland(
    backdrop: GlassBackdrop,
    material: GlassMaterial,
    dark: Boolean,
    roomLight: () -> Offset,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val lamp = rememberGlassLamp()
    GlassSurface(
        backdrop = backdrop,
        material = material,
        modifier = modifier
            .height(56.dp)
            .glassLampFollowsFinger(lamp),
        lamp = lamp,
        dark = dark,
        lampInk = colors.primary,
        lighting = { keyLight = roomLight() },
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("G", style = MaterialTheme.typography.titleMedium, color = colors.onPrimary)
            }
            Column {
                Text("Glass lab", style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1)
                Text("Scroll, slide, hold", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Tune the glass", tint = colors.onSurface)
            }
        }
    }
}

private val Tabs = listOf(
    "Home" to Icons.Default.Home,
    "Search" to Icons.Default.Search,
    "Saved" to Icons.Default.Favorite,
    "You" to Icons.Default.Person,
)

/**
 * A tab bar in glass: the selected tab is a light left on inside the slab, flowing from tab to tab on a
 * spring; the finger carries a lamp; the icons are engraved and turn white in the selection's light.
 */
@Composable
private fun TabBar(
    backdrop: GlassBackdrop,
    material: GlassMaterial,
    dark: Boolean,
    roomLight: () -> Offset,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val lamp = rememberGlassLamp()
    var selected by remember { mutableIntStateOf(0) }
    // Where the selection's light is, in tabs: it flows rather than jumps.
    val at = remember { Animatable(0f) }
    LaunchedEffect(selected) { at.animateTo(selected.toFloat(), spring(dampingRatio = 0.72f, stiffness = 500f)) }
    val inset = with(LocalDensity.current) { 6.dp.toPx() }
    GlassSurface(
        backdrop = backdrop,
        material = material,
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(68.dp)
            .glassLampFollowsFinger(lamp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    selected = (offset.x / (size.width / Tabs.size)).toInt().coerceIn(0, Tabs.lastIndex)
                }
            },
        lamp = lamp,
        dark = dark,
        lampInk = colors.primary,
        lighting = { size ->
            keyLight = roomLight()
            val cell = size.width / Tabs.size
            val left = at.value * cell + inset
            selection = Rect(left, inset, left + cell - 2 * inset, size.height - inset)
            selectionOn = 1f
            selectionColor = colors.primary
            selectionInk = Color.White
        },
    ) {
        Row(Modifier.fillMaxSize()) {
            Tabs.forEach { (label, icon) ->
                TabCell(label, icon, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TabCell(label: String, icon: ImageVector, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
    }
}

/** Held, the FAB fills with light from inside; let go early and it drains. */
@Composable
private fun HoldFab(
    backdrop: GlassBackdrop,
    material: GlassMaterial,
    dark: Boolean,
    roomLight: () -> Offset,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val lamp = rememberGlassLamp()
    val fill = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    GlassSurface(
        backdrop = backdrop,
        material = material,
        modifier = modifier
            .size(64.dp)
            .graphicsLayer {
                val grown = 1f + 0.07f * pop.value
                scaleX = grown
                scaleY = grown
            }
            .glassLampFollowsFinger(lamp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val charge = scope.launch {
                            fill.animateTo(1f, tween(700, easing = LinearEasing))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            pop.snapTo(1f)
                            pop.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 500f))
                        }
                        tryAwaitRelease()
                        val full = fill.value >= 1f
                        if (!full) charge.cancel()
                        scope.launch { fill.animateTo(0f, tween(if (full) 700 else 260)) }
                    },
                )
            },
        lamp = lamp,
        dark = dark,
        lampInk = colors.primary,
        lighting = { size ->
            keyLight = roomLight()
            val inset = size.minDimension * 0.14f
            selection = Rect(inset, inset, size.width - inset, size.height - inset)
            selectionOn = fill.value
            selectionColor = Color(0xFFFF8A3D)
            selectionInk = Color.White
        },
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "Hold to fill with light",
            tint = colors.onSurface,
            modifier = Modifier
                .align(Alignment.Center)
                .size(28.dp),
        )
    }
}

/** The panel, in frosted glass: every property of the glass. */
@Composable
private fun Panel(state: LabState, backdrop: GlassBackdrop, roomLight: () -> Offset) {
    val colors = MaterialTheme.colorScheme
    val lamp = rememberGlassLamp()
    val clear = state.material()
    // Frosted, for all it holds to read; its rim a little wider, as a bigger slab's is.
    val material = clear.copy(
        frost = maxOf(clear.frost, 14.dp),
        tintAmount = maxOf(clear.tintAmount, 0.5f),
        bevel = clear.bevel + 4.dp,
    )
    GlassSurface(
        backdrop = backdrop,
        material = material,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(max = 520.dp)
            .glassLampFollowsFinger(lamp),
        cornerRadius = 28.dp,
        lamp = lamp,
        dark = state.dark,
        lampInk = colors.primary,
        lighting = { keyLight = roomLight() },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("The glass", style = MaterialTheme.typography.titleLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = { state.reset() }) { Text("Reset") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                Preset.entries.forEach { preset ->
                    FilterChip(
                        selected = state.preset == preset,
                        onClick = {
                            state.preset = preset
                            state.reset()
                        },
                        label = { Text(preset.label) },
                    )
                }
            }
            LabSwitch("Dark", state.dark) {
                state.dark = it
                state.reset()
            }
            LabSwitch("The room's light follows the phone", state.tilt) { state.tilt = it }
            LabSlider("Rim width", state.bevel, 4f..36f, "${state.bevel.toInt()} dp") { state.bevel = it }
            LabSlider("Refraction at the rim", state.refraction, -24f..40f, "${state.refraction.toInt()} dp") { state.refraction = it }
            LabSlider("Dispersion", state.dispersion, 0f..0.3f, percent(state.dispersion)) { state.dispersion = it }
            LabSlider("Frost", state.frost, 0f..24f, "${state.frost.toInt()} dp") { state.frost = it }
            LabSlider("Body", state.tintAmount, 0f..0.9f, percent(state.tintAmount)) { state.tintAmount = it }
            LabSlider("Rim thickness", state.edge, 0f..1f, percent(state.edge)) { state.edge = it }
            LabSlider("Light on the rim", state.rim, 0f..1.5f, percent(state.rim)) { state.rim = it }
            LabSlider("Highlight", state.specular, 0f..1.5f, percent(state.specular)) { state.specular = it }
            LabSlider("Engraving depth", state.inkDepth, 0f..1f, percent(state.inkDepth)) { state.inkDepth = it }
            LabSlider("Rim mirror", state.inkReflection, 0f..1f, percent(state.inkReflection)) { state.inkReflection = it }
            LabSlider("Finger light reach", state.lampReach, 12f..90f, "${state.lampReach.toInt()} dp") { state.lampReach = it }
        }
    }
}

@Composable
private fun LabSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LabSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    reading: String,
    onChange: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text(reading, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

private fun percent(value: Float) = "${(value * 100).toInt()}%"

/** The page under the glass: colour, type and stripes, which show a bend best. */
@Composable
private fun LabPage(shaderError: String?) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("GLASS · LAB", style = MaterialTheme.typography.labelLarge, color = colors.primary)
        Text("Light inside the glass", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        if (shaderError != null) {
            Text("Plain glass on this device — $shaderError", style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
        Text(
            "Scroll: the page passes under the glass and bends at the rim. Put a finger on the bar and slide it: " +
                "the light stays inside the glass and runs out to the rim. Hold the +.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
        GradientCard("Sunrise", listOf(Color(0xFFFF8A3D), Color(0xFFE8436F), Color(0xFF7B3FE4)))
        Stripes(horizontal = true)
        Chips()
        GradientCard("Deep sea", listOf(Color(0xFF0B3D91), Color(0xFF1E88E5), Color(0xFF26C6DA)))
        Text(
            "Not a blur and not a lens: a slab with a thin rim. What is under it bends only where the rim " +
                "falls away; the depth shows because the glass is denser there, and the room's light catches it " +
                "on one side. Icons and words are engraved in the slab, and the finger's light runs through it.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
        )
        Stripes(horizontal = false)
        GradientCard("Forest", listOf(Color(0xFF1B5E20), Color(0xFF43A047), Color(0xFFC0CA33)))
        Stripes(horizontal = true)
        GradientCard("Night", listOf(Color(0xFF0D0D1A), Color(0xFF3949AB), Color(0xFFB39DDB)))
    }
}

@Composable
private fun GradientCard(title: String, gradient: List<Color>) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradient)),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(20.dp),
        )
    }
}

/** Thin bands: nothing shows a bend, or the colours splitting, as plainly. */
@Composable
private fun Stripes(horizontal: Boolean) {
    val colors = MaterialTheme.colorScheme
    val bands = listOf(colors.primary, colors.surface, Color(0xFFFF8A3D), colors.surface, colors.onSurface, colors.surface)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        val step = 6.dp.toPx()
        var at = 0f
        var i = 0
        val extent = if (horizontal) size.height else size.width
        while (at < extent) {
            val color = bands[i % bands.size]
            if (horizontal) {
                drawRect(color, topLeft = Offset(0f, at), size = size.copy(height = step))
            } else {
                drawRect(color, topLeft = Offset(at, 0f), size = size.copy(width = step))
            }
            at += step
            i++
        }
    }
}

@Composable
private fun Chips() {
    val chips = listOf(
        "Health" to Color(0xFF7A6DB0),
        "Work" to Color(0xFFFF8A3D),
        "Moon" to Color(0xFFF6E3A8),
        "Home" to Color(0xFF4FA89B),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { (label, color) ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                val light = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue > 0.6f
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (light) Color(0xFF1C1B20) else Color.White)
            }
        }
    }
}
