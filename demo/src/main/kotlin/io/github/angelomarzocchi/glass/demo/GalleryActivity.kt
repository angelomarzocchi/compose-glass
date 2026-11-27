package io.github.angelomarzocchi.glass.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelomarzocchi.glass.GlassBackdrop
import io.github.angelomarzocchi.glass.GlassMaterial
import io.github.angelomarzocchi.glass.GlassQuality
import io.github.angelomarzocchi.glass.LocalGlassQuality
import androidx.compose.runtime.CompositionLocalProvider
import io.github.angelomarzocchi.glass.GlassSurface
import io.github.angelomarzocchi.glass.rememberGlassBackdrop
import io.github.angelomarzocchi.lumen.FloatingNavBar
import io.github.angelomarzocchi.lumen.FloatingNavItem
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/**
 * Still frames of glass for the README, one per launch: `--ei shot N`.
 *
 * - 0: the spectrum, light — the same glass over the same page, its finish from liquid to matte.
 * - 1: the spectrum, dark.
 * - 2: light inside the glass, from none to full (dark, where light adds up).
 * - 3: the pieces in context — island, tab bar, FAB, panel — dark; 4: the same, light.
 * - 5: compose-lumen's navigation bar in liquid glass, a finger held on a tab, light; 6: the same, dark.
 *
 * `adb shell am start -n io.github.angelomarzocchi.glass.demo/.GalleryActivity --ei shot 0`
 *
 * `--es quality Frosted` (or `Tinted`, `Full`) draws the glass at that quality, as a device that cannot
 * draw it in full would.
 */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val shot = intent.getIntExtra("shot", 0)
        val dark = shot in setOf(1, 2, 3, 6)
        val quality = intent.getStringExtra("quality")?.let { GlassQuality.valueOf(it) }
        setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalGlassQuality provides quality) { Gallery(shot, dark) }
            }
        }
    }
}

@Composable
private fun Gallery(shot: Int, dark: Boolean) {
    val backdrop = rememberGlassBackdrop()
    Box(Modifier.fillMaxSize()) {
        TravelBackdrop(backdrop, dark)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 150.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (shot) {
                0, 1 -> Spectrum(backdrop, dark)
                2 -> Lights(backdrop, dark)
                5, 6 -> LumenBar(backdrop, dark)
                else -> InContext(backdrop, dark)
            }
        }
        // What the frame shows: a caption over the app, not part of it.
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, top = 12.dp)
                .background(Color.Black.copy(alpha = 0.62f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(Titles[shot].first, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(Titles[shot].second, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        }
    }
}

private val Titles = listOf(
    "From liquid to matte" to "One glass, its finish from 0 to 1",
    "From liquid to matte" to "One glass, its finish from 0 to 1 · dark",
    "Light inside the glass" to "From none to full · dark glass",
    "Glass, in place" to "Island, tab bar, FAB, panel",
    "Glass, in place" to "Island, tab bar, FAB, panel · light",
    "Lumen in glass" to "FloatingNavBar, a finger on Saved · light",
    "Lumen in glass" to "FloatingNavBar, a finger on Saved · dark",
)

/** compose-lumen's bar with its island in liquid glass, a finger held on the third tab. */
@Composable
private fun ColumnScope.LumenBar(backdrop: GlassBackdrop, dark: Boolean) {
    Spacer(Modifier.weight(1f))
    val items = listOf(
        FloatingNavItem("Home", rememberVectorPainter(Icons.Default.Home)),
        FloatingNavItem("Search", rememberVectorPainter(Icons.Default.Search)),
        FloatingNavItem("Saved", rememberVectorPainter(Icons.Default.Favorite)),
        FloatingNavItem("You", rememberVectorPainter(Icons.Default.Person)),
    )
    FloatingNavBar(
        items = items,
        selectedIndex = 0,
        onSelect = {},
        heldAt = 2,
        haptics = false,
        material = rememberGlassIsland(backdrop, GlassMaterial.liquid(dark, finish = 0.3f)),
    )
}

@Composable
private fun Spectrum(backdrop: GlassBackdrop, dark: Boolean) {
    listOf(
        Triple("Liquid", "GlassMaterial.liquid()", 0f),
        Triple("A touch of frost", "liquid(finish = 0.25)", 0.25f),
        Triple("Half way", "liquid(finish = 0.5)", 0.5f),
        Triple("Satin", "liquid(finish = 0.75)", 0.75f),
        Triple("Matte", "GlassMaterial.matte()", 1f),
    ).forEach { (name, detail, finish) ->
        Slab(backdrop, GlassMaterial.liquid(dark, finish), dark, name, detail)
    }
}

@Composable
private fun Lights(backdrop: GlassBackdrop, dark: Boolean) {
    val material = GlassMaterial.liquid(dark, finish = 0.3f)
    val accent = Color(0xFF7C5CFF)
    val warm = Color(0xFFFF8A3D)
    val px = with(LocalDensity.current) { 1.dp.toPx() }
    val lampTint = Color(0xFFC9B8FF)
    Slab(backdrop, material, dark, "No light", "the room's light on the rim only")
    Slab(backdrop, material, dark, "A finger", "lamp, 40dp reach", lighting = { size ->
        lamp = Offset(size.width * 0.3f, size.height * 0.55f)
        lampOn = 1f
        lampColor = lampTint
    })
    Slab(backdrop, material.copy(lampReach = 90.dp), dark, "A brighter finger", "lamp, 90dp reach", lighting = { size ->
        lamp = Offset(size.width * 0.3f, size.height * 0.55f)
        lampOn = 1f
        lampColor = lampTint
    })
    Slab(backdrop, material, dark, "A selection", "a light left on inside", lighting = { size ->
        val inset = 6f * px
        selection = Rect(inset, inset, size.width * 0.42f, size.height - inset)
        selectionOn = 1f
        selectionColor = accent
        selectionInk = Color.White
    })
    Slab(backdrop, material, dark, "Selection and finger", "the rim catches both", lighting = { size ->
        val inset = 6f * px
        selection = Rect(inset, inset, size.width * 0.42f, size.height - inset)
        selectionOn = 1f
        selectionColor = accent
        selectionInk = Color.White
        lamp = Offset(size.width * 0.78f, size.height * 0.5f)
        lampOn = 1f
        lampColor = lampTint
    })
    Slab(backdrop, material, dark, "Full", "selectionOn = 1 over the whole slab", lighting = { size ->
        val inset = 6f * px
        selection = Rect(inset, inset, size.width - inset, size.height - inset)
        selectionOn = 1f
        selectionColor = warm
        selectionInk = Color.White
    })
}

@Composable
private fun ColumnScope.InContext(backdrop: GlassBackdrop, dark: Boolean) {
    val accent = Color(0xFF7C5CFF)
    val bar = GlassMaterial.liquid(dark, finish = 0.3f)
    val ink = if (dark) Color.White else Color(0xFF1C1B20)
    // The island at the top.
    GlassSurface(backdrop, bar, Modifier.height(56.dp), dark = dark, lampInk = if (dark) Color.White else accent) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(accent, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                Text("G", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Week 42", color = ink, fontWeight = FontWeight.SemiBold)
                Text("5 steps to go", color = ink.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            Spacer(Modifier.width(20.dp))
            Icon(Icons.Default.Settings, null, tint = ink)
        }
    }
    // A frosted panel.
    GlassSurface(
        backdrop,
        GlassMaterial.liquid(dark, finish = 0.8f),
        Modifier.fillMaxWidth().height(200.dp),
        cornerRadius = 28.dp,
        dark = dark,
        lampInk = if (dark) Color.White else accent,
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("Matte panel", color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Enough body and frost that text reads over the busiest page, and a rim that still catches the light.",
                color = ink.copy(alpha = 0.8f),
                fontSize = 15.sp,
            )
        }
    }
    Spacer(Modifier.weight(1f))
    // The FAB, half full of light.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        GlassSurface(backdrop, bar, Modifier.size(66.dp), dark = dark, lighting = { size ->
            val inset = size.minDimension * 0.14f
            selection = Rect(inset, inset, size.width - inset, size.height - inset)
            selectionOn = 0.6f
            selectionColor = Color(0xFFFF8A3D)
            selectionInk = Color.White
        }) {
            Icon(Icons.Default.Add, null, tint = ink, modifier = Modifier.align(Alignment.Center).size(28.dp))
        }
    }
    // The tab bar: a selection lit inside, and a finger on another tab.
    val tabs = listOf("Home" to Icons.Default.Home, "Search" to Icons.Default.Search, "Saved" to Icons.Default.Favorite, "You" to Icons.Default.Person)
    val px = with(LocalDensity.current) { 6.dp.toPx() }
    GlassSurface(
        backdrop,
        bar,
        Modifier.fillMaxWidth().height(70.dp),
        dark = dark,
        lampInk = if (dark) Color.White else accent,
        lighting = { size ->
            val cell = size.width / tabs.size
            selection = Rect(px, px, cell - px, size.height - px)
            selectionOn = 1f
            selectionColor = accent
            selectionInk = Color.White
            lamp = Offset(cell * 2.5f, size.height * 0.5f)
            lampOn = 1f
        },
    ) {
        Row(Modifier.fillMaxSize()) {
            tabs.forEach { (label, icon) -> TabCell(label, icon, ink, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TabCell(label: String, icon: ImageVector, ink: Color, modifier: Modifier) {
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = ink)
        Text(label, color = ink, fontSize = 12.sp)
    }
}

/** One capsule of glass, its name and what it is engraved in it. */
@Composable
private fun Slab(
    backdrop: GlassBackdrop,
    material: GlassMaterial,
    dark: Boolean,
    name: String,
    detail: String,
    lighting: io.github.angelomarzocchi.glass.GlassLighting.(androidx.compose.ui.geometry.Size) -> Unit = {},
) {
    val ink = if (dark) Color.White else Color(0xFF1C1B20)
    GlassSurface(
        backdrop = backdrop,
        material = material,
        modifier = Modifier.fillMaxWidth().height(86.dp),
        dark = dark,
        // The accent on pale glass; on dark glass the default, white, which reads in the lamp's light.
        lampInk = if (dark) Color.Unspecified else Color(0xFF7C5CFF),
        lighting = lighting,
    ) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = 30.dp)) {
            Text(name, color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = ink.copy(alpha = 0.75f), fontSize = 13.sp)
        }
    }
}
