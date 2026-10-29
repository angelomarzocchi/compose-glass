package io.github.angelomarzocchi.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max

/*
 * Glass, and the light inside it: a material for surfaces that float over the page — a bar, a FAB, an
 * island at the top, a panel.
 *
 * Not a blur of the page. The glass is a slab with a rounded rim: the page seen through it is bent at
 * the rim, so the edge magnifies, and split a little into its colours there; the room's light catches
 * the rim on one side and leaves the other darker. What the surface holds — icons, labels — is
 * engraved *inside* the slab: it bends at the rim too, a little less, and the rim, a polished wall,
 * can mirror it. And the slab carries light: the finger is a lamp put down on it, whose light spreads
 * through the body and runs out to the rim, and a selection is a light left on inside, the shape of
 * its plate, that the rim catches as it goes by. That is where the 3D shows — at the edge.
 *
 * The page has to be recorded for the glass to see it ([glassBackdrop]), and glass drawn inside its own
 * page would be looking at itself: it sits over it, as a sibling. All of it needs API 33 (AGSL); below
 * that a surface is frosted (31–32) or tinted (24–30), its lights drawn flat.
 */

/**
 * A page recorded once a frame, for the glass laid over it to see — or a still of one, taken every so
 * often ([rememberStillGlassBackdrop]).
 */
@Stable
public class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    /** Where the page is, in the root's pixels. */
    internal var origin by mutableStateOf(Offset.Zero)

    /** Grows when a still is taken: the glass over it draws again, even where nothing else asks for a frame. */
    internal var version by mutableIntStateOf(0)
}

/** A backdrop to record a page into, with [glassBackdrop], for glass laid over it. */
@Composable
public fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/**
 * Records this page into [backdrop] every frame, and draws it as usual. Glass must not be inside it:
 * it would be looking at itself. Put the glass next to the page, over it, in the same parent.
 */
public fun Modifier.glassBackdrop(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * What a glass is made of. Every length is the rim's or the light's; the shape is the surface's own.
 *
 * @param bevel how wide the rounded rim is: inside it the slab is flat.
 * @param refraction how far the rim bends the page: positive draws it in from further inside, so the
 *   edge magnifies; negative pulls in what is outside the edge.
 * @param falloff how close to the very edge the bend is kept: the bend grows as the rim's depth to
 *   this power, so 2 bends across the whole rim and 3 or more only in its last few dp.
 * @param dispersion how much the rim splits the page into its colours, as a share of the bend.
 * @param frost a blur of the page before it is bent: 0 is clear glass.
 * @param tint the body's colour, and [tintAmount] how much of it there is — more in the middle, where
 *   the slab is thickest.
 * @param edge how much denser, and a shade darker, the rim is: seen through at the rim the slab is at
 *   its thickest. The rim's depth without bending the page to show it.
 * @param rim how much the rim catches the room's light; [specular], its highlight.
 * @param inkDepth how deep the engraving sits: the share of the bend it takes.
 * @param inkReflection how much of the engraving the rim mirrors.
 * @param inkGlow how far a lit engraving glows round itself.
 * @param lampReach how far the lamp's light spreads through the body.
 * @param hairline how bright the fine line of light along the very edge is, on the side facing the room's
 *   light: the edge seen, not the rim's depth. As bright as the [rim] unless said otherwise.
 * @param farHairline the same line on the far side, where the light that went into a clear slab catches
 *   the edge on its way out: liquid glass shows both.
 * @param hairlineWidth how wide that line is, inside the edge; it fades out over its inner part.
 */
@Immutable
public data class GlassMaterial(
    val bevel: Dp,
    val refraction: Dp,
    val falloff: Float,
    val dispersion: Float,
    val frost: Dp,
    val tint: Color,
    val tintAmount: Float,
    val edge: Float,
    val rim: Float,
    val specular: Float,
    val inkDepth: Float,
    val inkReflection: Float,
    val inkGlow: Dp,
    val lampReach: Dp,
    val hairline: Float = rim,
    val farHairline: Float = 0f,
    val hairlineWidth: Dp = 0.75.dp,
) {
    public companion object {
        /**
         * Quiet optics, lively light: lit acrylic rather than a lens. A thin rim that bends the page
         * only in its last few dp, barely splitting it, and mirrors nothing; its depth shown by its
         * thickness — denser, a shade darker, a narrow highlight — and by the light inside, which runs
         * out to it. Milky on a light page and smoky on a dark one, with body and frost enough that a
         * label reads over the busiest page there is.
         */
        public fun clear(dark: Boolean): GlassMaterial = GlassMaterial(
            bevel = 10.dp,
            refraction = 8.dp,
            falloff = 3f,
            dispersion = 0.03f,
            frost = 4.dp,
            tint = if (dark) Color(0xFF1C1B20) else Color(0xFFF7F4FB),
            tintAmount = if (dark) 0.62f else 0.52f,
            edge = if (dark) 0.55f else 0.5f,
            rim = if (dark) 0.4f else 0.5f,
            specular = 0.4f,
            inkDepth = 0.15f,
            inkReflection = 0f,
            inkGlow = 3.dp,
            lampReach = 40.dp,
        )

        /**
         * A lens: a wide rim that bends the page all across it, splits it into its colours and
         * mirrors the labels engraved near it. Loud — the page visibly warps at every edge.
         */
        public fun lens(dark: Boolean): GlassMaterial = clear(dark).copy(
            bevel = 16.dp,
            refraction = 18.dp,
            falloff = 2f,
            dispersion = 0.08f,
            edge = 0f,
            rim = if (dark) 0.6f else 0.85f,
            specular = 0.55f,
            inkDepth = 0.5f,
            inkReflection = 0.18f,
            lampReach = 34.dp,
        )

        /**
         * Liquid glass: a lens rather than a slab, with almost no slab to show. The page bends across a
         * wide, soft rim — it magnifies as it nears the edge — but the rim is barely shaded: no darker
         * band, no thick bevel, no highlight across it; only a fine line of light along the edge where
         * the room's light catches it, and a fainter one on the far side. See-through, with frost
         * enough to keep a label readable.
         */
        public fun liquid(dark: Boolean): GlassMaterial = GlassMaterial(
            bevel = 16.dp,
            refraction = 12.dp,
            falloff = 2.4f,
            dispersion = 0.02f,
            frost = 3.dp,
            tint = if (dark) Color(0xFF151518) else Color(0xFFF8F8FA),
            tintAmount = if (dark) 0.44f else 0.32f,
            edge = 0.06f,
            rim = 0.14f,
            specular = 0.05f,
            inkDepth = 0.1f,
            inkReflection = 0f,
            inkGlow = 3.dp,
            lampReach = 44.dp,
            hairline = if (dark) 0.75f else 0.9f,
            farHairline = 0.4f,
        )

        /** [clear] glass frosted, for a surface that holds a lot to read: a panel, a sheet. */
        public fun frosted(dark: Boolean): GlassMaterial =
            clear(dark).copy(frost = 14.dp, tintAmount = if (dark) 0.68f else 0.6f, bevel = 14.dp)
    }
}

/**
 * The lights in a glass this frame, in the surface's own pixels. Filled by whoever draws the glass,
 * from the draw phase, every frame: nothing here is state.
 */
public class GlassLighting internal constructor() {
    /** The lamp under the finger: where, and how much it is on, 0 to 1. */
    public var lamp: Offset = Offset.Zero
    public var lampOn: Float = 0f
    public var lampColor: Color = Color.White

    /** What engraved marks turn under the lamp: an accent on pale glass, white on dark glass. */
    public var lampInk: Color = Color.White

    /** A light left on inside, the shape of [selection] — a capsule of its height. */
    public var selection: Rect = Rect.Zero
    public var selectionOn: Float = 0f
    public var selectionColor: Color = Color.White

    /** What engraved marks turn inside the selection's light. */
    public var selectionInk: Color = Color.White

    /** Where the room's light comes from, on screen: x right, y down. Up and a little left by default. */
    public var keyLight: Offset = DefaultKeyLight

    internal fun reset() {
        lamp = Offset.Zero
        lampOn = 0f
        lampColor = Color.White
        lampInk = Color.White
        selection = Rect.Zero
        selectionOn = 0f
        selectionColor = Color.White
        selectionInk = Color.White
        keyLight = DefaultKeyLight
    }
}

/** The room's light when nothing moves it: up, and a little to the left. */
public val DefaultKeyLight: Offset = Offset(-0.35f, -1f)

/**
 * Draws glass: holds its two programs and the layer the page is bent in. One to a surface.
 *
 * The low-level way in, for a surface that draws its own shape — one whose ends move, say. [drawBody]
 * goes in the surface's draw phase, under what it holds; [inkEffect] is the render effect of the layer
 * that holds it. [GlassSurface] does all of this for a plain rounded surface.
 */
@Stable
public class GlassPainter internal constructor(private val layer: GraphicsLayer, private val page: GraphicsLayer) {
    internal val lighting = GlassLighting()

    // A program that does not compile, or a renderer with no AGSL (Android Studio's preview), leaves
    // the surface on plain glass rather than taking the screen down: [glassShaderError] says why.
    private val programs: Any? =
        if (Build.VERSION.SDK_INT >= 33) runCatching { GlassPrograms() }.getOrNull() else null

    /** Where the surface is, in the root's pixels. */
    internal var origin by mutableStateOf(Offset.Zero)

    /**
     * The glass, over the whole of [shape] — its own pixels, which may reach past the surface's bounds
     * by [overhang] at most — with corners of [radius].
     */
    public fun DrawScope.drawBody(
        backdrop: GlassBackdrop,
        material: GlassMaterial,
        shape: Rect,
        radius: Float,
        dark: Boolean,
        overhang: Float = 0f,
    ) {
        val programs = programs
        if (Build.VERSION.SDK_INT >= 33 && programs is GlassPrograms) {
            with(programs) { drawGlass(layer, page, backdrop, origin, material, lighting, shape, radius, dark, overhang) }
        } else {
            drawPlainGlass(layer, backdrop, origin, material, lighting, shape, radius)
        }
    }

    /** The engraving's render effect, for the layer holding what is engraved: null below API 33. */
    public fun inkEffect(
        density: Density,
        material: GlassMaterial,
        shape: Rect,
        radius: Float,
    ): androidx.compose.ui.graphics.RenderEffect? {
        val programs = programs
        if (Build.VERSION.SDK_INT < 33 || programs !is GlassPrograms) return null
        return programs.ink(density, material, lighting, shape, radius)
    }

    /** Fills the lights for this frame: call before [drawBody] and [inkEffect], in both. */
    public fun light(block: GlassLighting.() -> Unit) {
        lighting.reset()
        lighting.block()
    }
}

/** A painter for one glass surface. */
@Composable
public fun rememberGlassPainter(): GlassPainter {
    val layer = rememberGraphicsLayer()
    val page = rememberGraphicsLayer()
    return remember(layer, page) { GlassPainter(layer, page) }
}

/** Keeps [painter] told where the surface is. Put it where the surface's own bounds are measured. */
public fun Modifier.glassPosition(painter: GlassPainter): Modifier =
    onGloballyPositioned { painter.origin = it.positionInRoot() }

@RequiresApi(33)
private class GlassPrograms {
    val body = Program(GlassBodyShader)
    val ink = Program(GlassInkShader)

    // A frame where nothing about the glass changed draws nothing new. The page under it is recorded once
    // for where the glass is — what the page shows reaches it by itself — and frosted in a layer of its
    // own; the body's program runs over that, in a second layer, and its effect is made again only when
    // its numbers change. A light that moves (the room's, the lamp, a selection) then costs one pass of the
    // program, not the page drawn again and frosted again under it.
    private var pageSize = IntSize.Zero
    private var pageAt = Offset.Unspecified
    private var pageSource: GraphicsLayer? = null
    private var frostRadius = -1f
    private var frostEffect: androidx.compose.ui.graphics.RenderEffect? = null
    private var bodySize = IntSize.Zero

    fun DrawScope.drawGlass(
        layer: GraphicsLayer,
        page: GraphicsLayer,
        backdrop: GlassBackdrop,
        origin: Offset,
        material: GlassMaterial,
        lighting: GlassLighting,
        shape: Rect,
        radius: Float,
        dark: Boolean,
        overhang: Float,
    ) {
        // A still taken of the page ([rememberStillGlassBackdrop]) reaches the glass with a frame of its own.
        backdrop.version
        val bend = material.refraction.toPx()
        val frost = material.frost.toPx()
        // Room round the shape for what the rim bends in from outside it, and for the blur to read.
        val room = ceil(max(-bend, 0f) * (1f + material.dispersion) + frost * 3f + overhang + 2f)
        val size = IntSize(ceil(size.width + 2 * room).toInt(), ceil(size.height + 2 * room).toInt())

        if (frost != frostRadius) {
            frostRadius = frost
            frostEffect = if (frost > 0.5f) RenderEffect.createBlurEffect(frost, frost, Shader.TileMode.CLAMP).asComposeRenderEffect() else null
        }
        page.renderEffect = frostEffect
        val at = origin - backdrop.origin
        if (size != pageSize || at != pageAt || backdrop.layer !== pageSource) {
            page.record(size) { translate(room - at.x, room - at.y) { drawLayer(backdrop.layer) } }
            pageSize = size
            pageAt = at
            pageSource = backdrop.layer
        }

        body.begin()
        body.set("origin", room, room)
        body.set("shape", shape.left, shape.top, shape.right, shape.bottom)
        body.set("radius", radius)
        body.set("bevel", material.bevel.toPx())
        body.set("refraction", bend)
        body.set("falloff", material.falloff)
        body.set("dispersion", material.dispersion)
        body.set("tint", material.tint.red, material.tint.green, material.tint.blue, material.tintAmount)
        body.set("edge", material.edge)
        body.set("rim", material.rim)
        body.set("specular", material.specular)
        body.set("hairline", material.hairline, material.farHairline)
        body.set("lineWidth", material.hairlineWidth.toPx())
        body.set("keyLight", lighting.keyLight.x, lighting.keyLight.y, KeyLightHeight)
        body.set("lamp", lighting.lamp.x, lighting.lamp.y, lighting.lampOn, material.lampReach.toPx())
        body.set("lampColor", lighting.lampColor.red, lighting.lampColor.green, lighting.lampColor.blue)
        body.setSelection(lighting)
        body.set("selColor", lighting.selectionColor.red, lighting.selectionColor.green, lighting.selectionColor.blue)
        body.set("dark", if (dark) 1f else 0f)
        layer.renderEffect = body.effect()
        if (size != bodySize) {
            layer.record(size) { drawLayer(page) }
            bodySize = size
        }
        translate(-room, -room) { drawLayer(layer) }
    }

    fun ink(density: Density, material: GlassMaterial, lighting: GlassLighting, shape: Rect, radius: Float): androidx.compose.ui.graphics.RenderEffect =
        with(density) {
            ink.begin()
            ink.set("shape", shape.left, shape.top, shape.right, shape.bottom)
            ink.set("radius", radius)
            ink.set("bevel", material.bevel.toPx())
            ink.set("refraction", material.refraction.toPx() * material.inkDepth)
            ink.set("reflection", material.inkReflection)
            ink.set("lamp", lighting.lamp.x, lighting.lamp.y, lighting.lampOn, material.lampReach.toPx())
            ink.set("lampColor", lighting.lampColor.red, lighting.lampColor.green, lighting.lampColor.blue)
            ink.set("lampInk", lighting.lampInk.red, lighting.lampInk.green, lighting.lampInk.blue)
            ink.setSelection(lighting)
            ink.set("selInk", lighting.selectionInk.red, lighting.selectionInk.green, lighting.selectionInk.blue)
            ink.set("glow", material.inkGlow.toPx())
            ink.effect()
        }

    private fun Program.setSelection(lighting: GlassLighting) {
        val s = lighting.selection
        set("sel", s.left, s.top, s.right, s.bottom)
        set("selOn", if (s.width > 0f && s.height > 0f) lighting.selectionOn else 0f)
    }
}

/**
 * An AGSL program run as a render effect, and the numbers it was last given: [effect] is a new effect
 * only when one of them changed since the last ([begin], the [set]s, then [effect]). A layer handed the
 * same effect is not drawn again.
 */
@RequiresApi(33)
private class Program(sksl: String) {
    private val shader = RuntimeShader(sksl)
    private val last = FloatArray(MaxValues)
    private var count = 0
    private var changed = true
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null

    fun begin() {
        count = 0
    }

    private fun note(value: Float) {
        if (last[count] != value) {
            last[count] = value
            changed = true
        }
        count++
    }

    fun set(name: String, a: Float) {
        note(a)
        shader.setFloatUniform(name, a)
    }

    fun set(name: String, a: Float, b: Float) {
        note(a)
        note(b)
        shader.setFloatUniform(name, a, b)
    }

    fun set(name: String, a: Float, b: Float, c: Float) {
        note(a)
        note(b)
        note(c)
        shader.setFloatUniform(name, a, b, c)
    }

    fun set(name: String, a: Float, b: Float, c: Float, d: Float) {
        note(a)
        note(b)
        note(c)
        note(d)
        shader.setFloatUniform(name, a, b, c, d)
    }

    fun effect(): androidx.compose.ui.graphics.RenderEffect {
        val made = effect
        if (made != null && !changed) return made
        changed = false
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect().also { effect = it }
    }

    private companion object {
        const val MaxValues = 64
    }
}

/**
 * Glass without AGSL: the page frosted where the platform can blur it (31–32), and the body's tint over
 * it, more of it; the lights drawn on top as plain glows, and a hairline for the rim.
 */
private fun DrawScope.drawPlainGlass(
    layer: GraphicsLayer,
    backdrop: GlassBackdrop,
    origin: Offset,
    material: GlassMaterial,
    lighting: GlassLighting,
    shape: Rect,
    radius: Float,
) {
    val corner = CornerRadius(radius)
    if (Build.VERSION.SDK_INT >= 31) {
        val frost = max(material.frost.toPx(), 12.dp.toPx())
        layer.renderEffect = RenderEffect.createBlurEffect(frost, frost, Shader.TileMode.CLAMP).asComposeRenderEffect()
        val at = origin - backdrop.origin
        layer.record(IntSize(ceil(size.width).toInt().coerceAtLeast(1), ceil(size.height).toInt().coerceAtLeast(1))) {
            translate(-at.x, -at.y) { drawLayer(backdrop.layer) }
        }
        drawLayer(layer)
    }
    val tint = if (Build.VERSION.SDK_INT >= 31) material.tintAmount + 0.2f else 0.92f
    drawRoundRect(material.tint.copy(alpha = tint.coerceIn(0f, 1f)), shape.topLeft, shape.size, corner)
    if (lighting.selectionOn > 0f && lighting.selection.width > 0f) {
        drawRoundRect(
            color = lighting.selectionColor.copy(alpha = 0.45f * lighting.selectionOn),
            topLeft = lighting.selection.topLeft,
            size = lighting.selection.size,
            cornerRadius = CornerRadius(lighting.selection.height / 2f),
        )
    }
    if (lighting.lampOn > 0f && material.lampReach > 0.dp) {
        drawRect(
            Brush.radialGradient(
                0f to lighting.lampColor.copy(alpha = 0.35f * lighting.lampOn),
                1f to Color.Transparent,
                center = lighting.lamp,
                radius = material.lampReach.toPx() * 1.6f,
            ),
            topLeft = shape.topLeft,
            size = shape.size,
        )
    }
    drawRoundRect(
        color = Color.White.copy(alpha = 0.5f * material.hairline.coerceIn(0f, 1f)),
        topLeft = shape.topLeft,
        size = shape.size,
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx()),
    )
}

/** How high over the glass the room's light is, against its 1 of reach across the screen. */
private const val KeyLightHeight = 0.9f

/**
 * Why glass is drawn plain on this device, or null when it is drawn in full: below API 33 there is no
 * AGSL, and a program that does not compile says so here, with the compiler's own words.
 */
public fun glassShaderError(): String? {
    if (Build.VERSION.SDK_INT < 33) return "API ${Build.VERSION.SDK_INT}: full glass needs Android 13 (API 33)"
    return runCatching { GlassPrograms() }.exceptionOrNull()?.let { it.message ?: it.toString() }
}

/**
 * A glass surface: the slab, with a rim of [cornerRadius] (a capsule when it is null), over
 * [backdrop], and [content] engraved in it.
 *
 * @param lamp the light a finger carries over it: give the surface [glassLampFollowsFinger] with the
 *   same lamp for the finger to carry it.
 * @param dark whether the glass is dark: on dark glass light adds up, on pale glass it shows as colour.
 * @param lampInk the colour engraved marks turn under the lamp. Unspecified: white on dark glass, and
 *   a deep violet on pale glass — pass your accent colour.
 * @param lighting adds the surface's own lights every frame, in its own pixels, after the lamp's: a
 *   selection, a key light that follows the device ([rememberTiltKeyLight]).
 * @param body whether the slab is drawn, read in draw: false leaves only what is engraved in it — for a
 *   surface whose glass another one takes over for a while, a bubble growing into a sheet.
 */
@Composable
public fun GlassSurface(
    backdrop: GlassBackdrop,
    material: GlassMaterial,
    modifier: Modifier = Modifier,
    cornerRadius: Dp? = null,
    lamp: GlassLamp? = null,
    dark: Boolean = isSystemInDarkTheme(),
    lampInk: Color = Color.Unspecified,
    lighting: GlassLighting.(Size) -> Unit = {},
    body: () -> Boolean = { true },
    content: @Composable BoxScope.() -> Unit,
) {
    val painter = rememberGlassPainter()
    val ink = when {
        lampInk.isSpecified() -> lampInk
        dark -> Color.White
        else -> DefaultLampInk
    }
    fun GlassLighting.all(size: Size) {
        lamp?.let {
            this.lamp = Offset(it.x.value, it.y.value)
            lampOn = it.on.value
        }
        this.lampInk = ink
        lighting(size)
    }
    Box(
        modifier
            .glassPosition(painter)
            .drawBehind {
                if (!body()) return@drawBehind
                val radius = cornerRadius?.toPx() ?: (size.minDimension / 2f)
                painter.light { all(size) }
                with(painter) { drawBody(backdrop, material, Rect(Offset.Zero, size), radius, dark) }
            }
            .graphicsLayer {
                val radius = cornerRadius?.toPx() ?: (size.minDimension / 2f)
                painter.light { all(size) }
                renderEffect = painter.inkEffect(this, material, Rect(Offset.Zero, size), radius)
            },
        content = content,
    )
}

private fun Color.isSpecified(): Boolean = this != Color.Unspecified

private val DefaultLampInk = Color(0xFF4F378B)

/** A lamp a finger carries over a glass surface: where it is, in the surface's pixels, and how much it is on. */
@Stable
public class GlassLamp internal constructor() {
    public val x: Animatable<Float, AnimationVector1D> = Animatable(0f)
    public val y: Animatable<Float, AnimationVector1D> = Animatable(0f)
    public val on: Animatable<Float, AnimationVector1D> = Animatable(0f)

    /** Puts the lamp down at [at], gliding there if it was still on, and turns it on. */
    public fun press(at: Offset, scope: CoroutineScope) {
        val glide = on.value > 0f
        scope.launch { if (glide) x.animateTo(at.x, LampFollow) else x.snapTo(at.x) }
        scope.launch { if (glide) y.animateTo(at.y, LampFollow) else y.snapTo(at.y) }
        scope.launch { on.animateTo(1f, LampIn) }
    }

    /** Moves the lamp after the finger, on a quick spring. */
    public fun move(at: Offset, scope: CoroutineScope) {
        scope.launch { x.animateTo(at.x, LampFollow) }
        scope.launch { y.animateTo(at.y, LampFollow) }
    }

    /** The finger is gone: the lamp fades out where it is. */
    public fun release(scope: CoroutineScope) {
        scope.launch { on.animateTo(0f, LampOut) }
    }
}

/** A lamp for one surface. */
@Composable
public fun rememberGlassLamp(): GlassLamp = remember { GlassLamp() }

/**
 * The finger carries [lamp] over the surface, without taking the touch from anything under it: a
 * button still gets its click.
 */
@Composable
public fun Modifier.glassLampFollowsFinger(lamp: GlassLamp): Modifier {
    val scope = rememberCoroutineScope()
    return pointerInput(lamp) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            lamp.press(down.position, scope)
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                lamp.move(change.position, scope)
            }
            lamp.release(scope)
        }
    }
}

private val LampFollow = spring<Float>(dampingRatio = 0.9f, stiffness = 900f)
private val LampIn = tween<Float>(durationMillis = 90)
private val LampOut = tween<Float>(durationMillis = 420)
