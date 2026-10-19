package io.github.angelomarzocchi.glass.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelomarzocchi.glass.GlassBackdrop
import io.github.angelomarzocchi.glass.glassBackdrop
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The page under the glass in the gallery: an ordinary travel app — a header, category chips and a
 * feed of places with their photos — so the glass is judged over the kind of content it will float on.
 * The photos are painted, not bundled, so the repository carries no third-party images.
 */
@Composable
internal fun TravelBackdrop(backdrop: GlassBackdrop, dark: Boolean) {
    val base = if (dark) Color(0xFF111316) else Color(0xFFF7F7F5)
    val ink = if (dark) Color(0xFFF2F2F2) else Color(0xFF1B1C1E)
    val muted = ink.copy(alpha = 0.6f)
    val places = if (dark) NightPlaces else DayPlaces
    Box(
        Modifier
            .fillMaxSize()
            .glassBackdrop(backdrop)
            .background(base),
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 70.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Discover", color = ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Mountains", "Coast", "Cities", "Desert").forEachIndexed { i, label ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (i == 0) ink else ink.copy(alpha = 0.08f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(label, color = if (i == 0) base else ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            places.forEach { place ->
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(22.dp)),
                ) { place.photo(this) }
                Column {
                    Text(place.title, color = ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(place.meta, color = muted, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(place.blurb, color = ink.copy(alpha = 0.8f), fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
        }
    }
}

private class Place(val title: String, val meta: String, val blurb: String, val photo: DrawScope.() -> Unit)

private val DayPlaces = listOf(
    Place(
        "Lago di Braies, Dolomites",
        "★ 4.9 · 3,218 reviews · 2h from Venice",
        "A green lake under limestone walls, best at sunrise before the boats go out.",
    ) { mountains() },
    Place(
        "Positano, Amalfi Coast",
        "★ 4.8 · 5,904 reviews · ferries from Naples",
        "Houses stacked down the cliff to a pebble beach; take the path of the gods at dawn.",
    ) { coast() },
    Place(
        "Erg Chebbi, Morocco",
        "★ 4.7 · 1,402 reviews · camel trek",
        "Dunes up to 150 metres high; stay for the stars.",
    ) { dunes() },
)

private val NightPlaces = listOf(
    Place(
        "Shinjuku, Tokyo",
        "★ 4.8 · 12,511 reviews · open all night",
        "Neon side streets, tiny bars and the view from the 45th floor for free.",
    ) { city() },
    Place(
        "Tromsø, Norway",
        "★ 4.9 · 2,877 reviews · September to March",
        "The northern lights over the fjord, twenty minutes out of town.",
    ) { aurora() },
    Place(
        "Lago di Braies, Dolomites",
        "★ 4.9 · 3,218 reviews · 2h from Venice",
        "A green lake under limestone walls, best at sunrise before the boats go out.",
    ) { mountains() },
)

private fun DrawScope.sky(vararg stops: Pair<Float, Color>) {
    drawRect(Brush.verticalGradient(*stops))
}

/** A jagged ridge across the frame, its top at [top] of the height, varying by [roughness]. */
private fun DrawScope.ridge(seed: Int, top: Float, roughness: Float, color: Brush, until: Float = 1f) {
    val random = Random(seed)
    val path = Path().apply {
        moveTo(0f, size.height * until)
        var x = 0f
        var y = size.height * top
        lineTo(0f, y)
        while (x < size.width) {
            x += size.width * (0.03f + random.nextFloat() * 0.06f)
            y = (size.height * top + (random.nextFloat() - 0.5f) * size.height * roughness)
            lineTo(x, y)
        }
        lineTo(size.width, size.height * until)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.mountains() {
    sky(0f to Color(0xFF8EC5FF), 0.55f to Color(0xFFFFD6B0), 1f to Color(0xFFFFE9D2))
    drawCircle(Color(0xFFFFF4D6), radius = size.height * 0.09f, center = Offset(size.width * 0.72f, size.height * 0.32f))
    ridge(1, 0.38f, 0.32f, Brush.verticalGradient(listOf(Color(0xFFB9C6D8), Color(0xFF8C9BB3))), until = 0.62f)
    ridge(2, 0.48f, 0.22f, Brush.verticalGradient(listOf(Color(0xFF6E7F99), Color(0xFF4C5C74))), until = 0.62f)
    // Pines on the far shore.
    val random = Random(7)
    var x = 0f
    while (x < size.width) {
        val h = size.height * (0.06f + random.nextFloat() * 0.06f)
        val w = h * 0.45f
        drawPath(Path().apply { moveTo(x, size.height * 0.62f); lineTo(x + w / 2f, size.height * 0.62f - h); lineTo(x + w, size.height * 0.62f); close() }, Color(0xFF1F3B2E))
        x += w * 0.7f
    }
    // The lake, and the mountains in it.
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF2E8C7E), Color(0xFF17574F)), startY = size.height * 0.62f),
        topLeft = Offset(0f, size.height * 0.62f),
        size = Size(size.width, size.height * 0.38f),
    )
    for (i in 0 until 14) {
        val y = size.height * (0.65f + i * 0.025f)
        drawLine(Color.White.copy(alpha = 0.12f), Offset(size.width * (0.1f + (i % 3) * 0.2f), y), Offset(size.width * (0.45f + (i % 4) * 0.12f), y), strokeWidth = 2f)
    }
}

private fun DrawScope.coast() {
    sky(0f to Color(0xFF5FA8F5), 1f to Color(0xFFCFE6FF))
    // The sea.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1D6FB8), Color(0xFF0C4A84)), startY = size.height * 0.5f), Offset(0f, size.height * 0.5f), Size(size.width, size.height * 0.5f))
    for (i in 0 until 18) {
        val y = size.height * (0.55f + i * 0.025f)
        drawLine(Color.White.copy(alpha = 0.18f), Offset(size.width * ((i * 37 % 60) / 100f), y), Offset(size.width * ((i * 37 % 60) / 100f + 0.18f), y), strokeWidth = 2f)
    }
    // The cliff.
    val cliff = Path().apply {
        moveTo(size.width * 0.35f, size.height)
        cubicTo(size.width * 0.42f, size.height * 0.55f, size.width * 0.55f, size.height * 0.2f, size.width * 0.75f, size.height * 0.08f)
        lineTo(size.width, size.height * 0.05f)
        lineTo(size.width, size.height)
        close()
    }
    drawPath(cliff, Brush.verticalGradient(listOf(Color(0xFF6F8B4E), Color(0xFF4F6638))))
    // Houses down the cliff.
    val colors = listOf(Color(0xFFF5D6A4), Color(0xFFF2B8A0), Color(0xFFFFF1DB), Color(0xFFE8C06A), Color(0xFFF7E3C2), Color(0xFFD9826B))
    val random = Random(3)
    for (i in 0 until 46) {
        val t = random.nextFloat()
        val x = size.width * (0.45f + t * 0.5f + random.nextFloat() * 0.05f)
        val y = size.height * (0.85f - t * 0.65f + random.nextFloat() * 0.12f)
        val w = size.width * (0.03f + random.nextFloat() * 0.03f)
        drawRect(colors[i % colors.size], Offset(x, y), Size(w, w * 0.8f))
        drawRect(Color(0xFF3A3A3A).copy(alpha = 0.5f), Offset(x + w * 0.35f, y + w * 0.3f), Size(w * 0.25f, w * 0.3f))
    }
}

private fun DrawScope.dunes() {
    sky(0f to Color(0xFF7FB3E8), 1f to Color(0xFFF4E1C1))
    fun dune(top: Float, phase: Float, light: Color, dark: Color) {
        val path = Path().apply {
            moveTo(0f, size.height)
            var x = 0f
            while (x <= size.width) {
                val y = size.height * (top + 0.06f * sin((x / size.width * 2.2f + phase) * PI.toFloat()))
                lineTo(x, y)
                x += 8f
            }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, Brush.horizontalGradient(listOf(light, dark)))
    }
    dune(0.5f, 0.1f, Color(0xFFF0B97A), Color(0xFFC77E3F))
    dune(0.66f, 0.8f, Color(0xFFE9A35E), Color(0xFFB36A2F))
    dune(0.82f, 0.3f, Color(0xFFDB8E48), Color(0xFF9C5824))
}

private fun DrawScope.city() {
    sky(0f to Color(0xFF0B0E26), 1f to Color(0xFF2B1840))
    val random = Random(11)
    var x = 0f
    while (x < size.width) {
        val w = size.width * (0.06f + random.nextFloat() * 0.08f)
        val h = size.height * (0.35f + random.nextFloat() * 0.55f)
        val top = size.height - h
        drawRect(lerp(Color(0xFF14172B), Color(0xFF242845), random.nextFloat()), Offset(x, top), Size(w, h))
        // Lit windows.
        var wy = top + 8f
        while (wy < size.height - 6f) {
            var wx = x + 5f
            while (wx < x + w - 8f) {
                if (random.nextFloat() < 0.45f) {
                    val c = if (random.nextFloat() < 0.8f) Color(0xFFFFD27A) else Color(0xFF8FD3FF)
                    drawRect(c.copy(alpha = 0.55f + random.nextFloat() * 0.45f), Offset(wx, wy), Size(5f, 7f))
                }
                wx += 10f
            }
            wy += 13f
        }
        x += w + 3f
    }
    // A neon sign.
    drawRect(Color(0xFFFF3D7F), Offset(size.width * 0.18f, size.height * 0.55f), Size(size.width * 0.05f, size.height * 0.3f))
    drawRect(Color(0xFF3DE7FF), Offset(size.width * 0.66f, size.height * 0.48f), Size(size.width * 0.04f, size.height * 0.26f))
}

private fun DrawScope.aurora() {
    sky(0f to Color(0xFF050A1A), 1f to Color(0xFF0D1F2E))
    val random = Random(5)
    repeat(120) {
        drawCircle(Color.White.copy(alpha = 0.3f + random.nextFloat() * 0.7f), radius = 1f + random.nextFloat() * 1.6f, center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height * 0.6f))
    }
    for (band in 0 until 3) {
        val path = Path().apply {
            var x = 0f
            moveTo(0f, size.height * (0.3f + band * 0.06f))
            while (x <= size.width) {
                lineTo(x, size.height * (0.28f + band * 0.06f + 0.08f * sin((x / size.width * 1.6f + band * 0.4f) * PI.toFloat())))
                x += 6f
            }
            lineTo(size.width, 0f)
            lineTo(0f, 0f)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF3DFFB0).copy(alpha = 0.35f - band * 0.08f), Color.Transparent), startY = 0f, endY = size.height * 0.55f))
    }
    ridge(9, 0.72f, 0.12f, Brush.verticalGradient(listOf(Color(0xFF0B1620), Color(0xFF05090D))))
    drawRect(Color(0xFF0E2433), Offset(0f, size.height * 0.84f), Size(size.width, size.height * 0.16f))
}
