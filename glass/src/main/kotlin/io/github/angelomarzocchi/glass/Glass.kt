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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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
 * @param hairlineFocus how much that line gathers where the edge faces the light, and where it faces
 *   away: 1 spreads it evenly round; 2 or more makes it glint on those two sides and run faint between.
 * @param saturation how rich the page's colours are through the glass: 1 as they are, more to keep a
 *   tinted body from washing them out to grey. Liquid glass shows the page a little richer than it is.
 * @param rimGlow an even light just inside the edge, the same all round: the slab's thickness shown
 *   without a side of it in shade. 0 for none.
 * @param shadow how dark the soft shadow the glass casts under itself is, 0 for none. It is drawn by the
 *   glass, round its shape, and is clipped away where the surface's parent clips.
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
    val hairlineFocus: Float = 1f,
    val saturation: Float = 1f,
    val rimGlow: Float = 0f,
    val shadow: Float = 0f,
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
         * Liquid glass, from all but clear to matte along one [finish], 0 to 1. The default for
         * anything that floats: a bar, a FAB, an island, a panel.
         *
         * A lens rather than a slab, with no slab to show: the page bends across a wide, soft rim — it
         * magnifies as it nears the edge — and is seen a little richer in colour; the rim is not shaded
         * at all, no darker band, no bevel, no highlight across it. Only a fine line of light along the
         * edge, glinting where the room's light meets it and on the far side, a faint glow just inside
         * it, and a soft shadow under the glass.
         *
         * Towards [finish] 1 the glass frosts and fills with body — the page under it a blur of colour, a
         * label over it readable on the busiest page — the bend at the rim settles and the line of light
         * softens. 0 is the clear lens; about 0.3 suits a bar over a busy page; 1 is [matte].
         */
        public fun liquid(dark: Boolean, finish: Float = 0f): GlassMaterial =
            lerp(liquidEnd(dark), matteEnd(dark), finish.coerceIn(0f, 1f))

        /** [liquid] glass at the matte end of its finish: frosted and full-bodied, for a panel or a sheet to read on. */
        public fun matte(dark: Boolean): GlassMaterial = liquid(dark, finish = 1f)

        private fun liquidEnd(dark: Boolean) = GlassMaterial(
            bevel = 18.dp,
            refraction = 14.dp,
            falloff = 2.2f,
            dispersion = 0.025f,
            frost = 0.dp,
            tint = if (dark) Color(0xFF0E0E12) else Color.White,
            tintAmount = if (dark) 0.28f else 0.1f,
            edge = 0f,
            rim = 0f,
            specular = 0f,
            // Labels sit in the flat middle, where liquid glass bends nothing: no bend, and an unlit
            // engraving then costs nothing (see inkEffect).
            inkDepth = 0f,
            inkReflection = 0f,
            inkGlow = 3.dp,
            lampReach = 44.dp,
            hairline = if (dark) 0.7f else 0.85f,
            farHairline = 0.45f,
            hairlineWidth = 1.dp,
            hairlineFocus = 2.5f,
            saturation = 1.5f,
            rimGlow = if (dark) 0.12f else 0.18f,
            shadow = if (dark) 0.3f else 0.12f,
        )

        private fun matteEnd(dark: Boolean) = liquidEnd(dark).copy(
            bevel = 14.dp,
            refraction = 3.dp,
            falloff = 3f,
            dispersion = 0f,
            frost = 20.dp,
            tint = if (dark) Color(0xFF1A1A1F) else Color(0xFFF7F7FA),
            tintAmount = if (dark) 0.62f else 0.55f,
            hairline = if (dark) 0.4f else 0.5f,
            farHairline = 0.2f,
            hairlineFocus = 1.5f,
            saturation = 1.15f,
            rimGlow = 0.05f,
            shadow = if (dark) 0.22f else 0.08f,
        )

        /** [clear] glass frosted, for a surface that holds a lot to read: a panel, a sheet. */
        public fun frosted(dark: Boolean): GlassMaterial =
            clear(dark).copy(frost = 14.dp, tintAmount = if (dark) 0.68f else 0.6f, bevel = 14.dp)
    }
}

/**
 * The glass [fraction] of the way from [start] to [stop]: every property in between. For a glass that
 * changes as something happens — a bar that frosts as the page scrolls under it, a bubble that turns
 * into a sheet.
 */
public fun lerp(start: GlassMaterial, stop: GlassMaterial, fraction: Float): GlassMaterial {
    // The ends exactly: a colour's lerp goes through another colour space and back.
    if (fraction == 0f) return start
    if (fraction == 1f) return stop
    fun mix(a: Float, b: Float) = a + (b - a) * fraction
    fun mix(a: Dp, b: Dp) = androidx.compose.ui.unit.lerp(a, b, fraction)
    return GlassMaterial(
        bevel = mix(start.bevel, stop.bevel),
        refraction = mix(start.refraction, stop.refraction),
        falloff = mix(start.falloff, stop.falloff),
        dispersion = mix(start.dispersion, stop.dispersion),
        frost = mix(start.frost, stop.frost),
        tint = androidx.compose.ui.graphics.lerp(start.tint, stop.tint, fraction),
        tintAmount = mix(start.tintAmount, stop.tintAmount),
        edge = mix(start.edge, stop.edge),
        rim = mix(start.rim, stop.rim),
        specular = mix(start.specular, stop.specular),
        inkDepth = mix(start.inkDepth, stop.inkDepth),
        inkReflection = mix(start.inkReflection, stop.inkReflection),
        inkGlow = mix(start.inkGlow, stop.inkGlow),
        lampReach = mix(start.lampReach, stop.lampReach),
        hairline = mix(start.hairline, stop.hairline),
        farHairline = mix(start.farHairline, stop.farHairline),
        hairlineWidth = mix(start.hairlineWidth, stop.hairlineWidth),
        hairlineFocus = mix(start.hairlineFocus, stop.hairlineFocus),
        saturation = mix(start.saturation, stop.saturation),
        rimGlow = mix(start.rimGlow, stop.rimGlow),
        shadow = mix(start.shadow, stop.shadow),
    )
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
public class GlassPainter internal constructor(
    private val layer: GraphicsLayer,
    private val page: GraphicsLayer,
    plainLayer: GraphicsLayer,
) {
    internal val lighting = GlassLighting()

    /** How much of the glass is drawn ([rememberGlassQuality]): kept by [rememberGlassPainter]. */
    internal var quality by mutableStateOf(GlassQuality.Full)

    // A program that does not compile, or a renderer with no AGSL (Android Studio's preview), leaves
    // the surface on plain glass rather than taking the screen down: [glassShaderError] says why.
    private val programs: Any? by lazy {
        if (Build.VERSION.SDK_INT >= 33) runCatching { GlassPrograms() }.getOrNull() else null
    }

    private val plain = PlainGlass(plainLayer)

    /** The programs, when this surface is drawn in full; null when it is drawn plain. */
    private val fullPrograms: Any?
        get() = if (quality == GlassQuality.Full && Build.VERSION.SDK_INT >= 33) programs else null

    /** Whether this surface is drawn without its programs: its engraving's lights are then drawn flat. */
    internal val drawsPlain: Boolean
        get() = fullPrograms !is GlassPrograms

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
        val programs = fullPrograms
        if (Build.VERSION.SDK_INT >= 33 && programs is GlassPrograms) {
            with(programs) { drawGlass(layer, page, backdrop, origin, material, lighting, shape, radius, dark, overhang) }
        } else {
            // Full glass with no programs to draw it with (a preview, a driver that will not compile them)
            // is the next best thing.
            val drawn = if (quality == GlassQuality.Full) GlassQuality.Frosted else quality
            with(plain) { draw(drawn, backdrop, origin, material, lighting, shape, radius, dark) }
        }
    }

    /**
     * What engraved marks turn in the lights when the surface is drawn plain ([drawsPlain]), over what it
     * holds: the layer that holds it must then be drawn off-screen while a light is on.
     */
    internal fun DrawScope.drawPlainInk(material: GlassMaterial) {
        if (drawsPlain && PlainGlass.lit(lighting)) with(plain) { drawInkLights(material, lighting) }
    }

    /**
     * The engraving's render effect, for the layer holding what is engraved: null below API 33, below
     * [GlassQuality.Full], and while nothing bends, mirrors or lights the engraving.
     */
    public fun inkEffect(
        density: Density,
        material: GlassMaterial,
        shape: Rect,
        radius: Float,
    ): androidx.compose.ui.graphics.RenderEffect? {
        val programs = fullPrograms
        if (Build.VERSION.SDK_INT < 33 || programs !is GlassPrograms) return null
        // An engraving that nothing bends, mirrors or lights looks the same without its program: no effect,
        // and the layer that holds it is not drawn off-screen first. A light coming on brings it back.
        val lit = lighting.lampOn > 0f || (lighting.selectionOn > 0f && lighting.selection.width > 0f && lighting.selection.height > 0f)
        val bend = with(density) { material.refraction.toPx() } * material.inkDepth
        if (!lit && material.inkReflection <= 0f && kotlin.math.abs(bend) < 0.5f) return null
        return programs.ink(density, material, lighting, shape, radius)
    }

    /** Fills the lights for this frame: call before [drawBody] and [inkEffect], in both. */
    public fun light(block: GlassLighting.() -> Unit) {
        lighting.reset()
        lighting.block()
    }
}

/** A painter for one glass surface, drawn at the quality [rememberGlassQuality] gives here. */
@Composable
public fun rememberGlassPainter(): GlassPainter {
    val layer = rememberGraphicsLayer()
    val page = rememberGraphicsLayer()
    val plainLayer = rememberGraphicsLayer()
    val quality = rememberGlassQuality()
    val painter = remember(layer, page, plainLayer) { GlassPainter(layer, page, plainLayer).also { it.quality = quality } }
    SideEffect { painter.quality = quality }
    return painter
}

/** Keeps [painter] told where the surface is. Put it where the surface's own bounds are measured. */
public fun Modifier.glassPosition(painter: GlassPainter): Modifier =
    onGloballyPositioned { painter.origin = it.positionInRoot() }

/**
 * The two programs, compiled once for the whole app: compiling them takes a few milliseconds each time,
 * and the first time in a process a good deal more. Every surface sets its own numbers on the same
 * shader and makes its effect from them at once, on the main thread; an effect keeps the numbers it was
 * made with, whatever the shader is given after.
 */
@RequiresApi(33)
private object SharedPrograms {
    val body: RuntimeShader by lazy { RuntimeShader(GlassBodyShader) }
    val ink: RuntimeShader by lazy { RuntimeShader(GlassInkShader) }
}

@RequiresApi(33)
private class GlassPrograms {
    val body = Program(SharedPrograms.body)
    val ink = Program(SharedPrograms.ink)

    // A frame where nothing about the glass changed draws nothing new. The page under it is recorded once
    // for where the glass is — what the page shows reaches it by itself — and frosted in a layer of its
    // own; the body's program runs over that, in a second layer, and its effect is made again only when
    // its numbers change. A light that moves (the room's, the lamp, a selection) then costs one pass of the
    // program, not the page drawn again and frosted again under it.
    private var pageSize = IntSize.Zero
    private var pageAt = Offset.Unspecified
    private var pageSource: GraphicsLayer? = null
    private var pageScale = 0f
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
        val shadow = material.shadow.coerceIn(0f, 1f)
        val shadowSpread = if (shadow > 0f) ShadowSpread.toPx() else 0f
        val shadowDrop = if (shadow > 0f) ShadowDrop.toPx() else 0f
        // Room round the shape for what the rim bends in from outside it, for the blur to read, and for
        // the glass's shadow. A blur of radius r reads about 1.7 r away (its sigma is 0.58 r + 0.5, and
        // it reads three sigmas): 2 r is enough, and every pixel of room is blurred and run through the
        // body's program.
        val room = ceil(max(max(-bend, 0f) * (1f + material.dispersion) + frost * 2f, shadowSpread + shadowDrop) + overhang + 2f)
        val size = IntSize(ceil(size.width + 2 * room).toInt(), ceil(size.height + 2 * room).toInt())

        // A page frosted enough is frosted at half its size, and drawn back at its own: a quarter of the
        // pixels to draw and blur, and a blur that wide shows no difference.
        val reduce = if (frost >= HalfSizeFrost.toPx()) 0.5f else 1f
        if (frost != frostRadius) {
            frostRadius = frost
            val radius = frost * reduce
            frostEffect = if (radius > 0.5f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect() else null
        }
        page.renderEffect = frostEffect
        val at = origin - backdrop.origin
        if (size != pageSize || at != pageAt || backdrop.layer !== pageSource || reduce != pageScale) {
            val reduced = IntSize(ceil(size.width * reduce).toInt(), ceil(size.height * reduce).toInt())
            page.record(reduced) {
                scale(reduce, reduce, pivot = Offset.Zero) { translate(room - at.x, room - at.y) { drawLayer(backdrop.layer) } }
            }
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
        body.set("hairline", material.hairline, material.farHairline, material.hairlineFocus)
        body.set("lineWidth", material.hairlineWidth.toPx())
        body.set("keyLight", lighting.keyLight.x, lighting.keyLight.y, KeyLightHeight)
        body.set("lamp", lighting.lamp.x, lighting.lamp.y, lighting.lampOn, material.lampReach.toPx())
        body.set("lampColor", lighting.lampColor.red, lighting.lampColor.green, lighting.lampColor.blue)
        body.setSelection(lighting)
        body.set("selColor", lighting.selectionColor.red, lighting.selectionColor.green, lighting.selectionColor.blue)
        body.set("dark", if (dark) 1f else 0f)
        body.set("saturation", material.saturation)
        body.set("rimGlow", material.rimGlow)
        body.set("shadow", shadow, shadowSpread, shadowDrop)
        layer.renderEffect = body.effect()
        if (size != bodySize || reduce != pageScale) {
            layer.record(size) { scale(1f / reduce, 1f / reduce, pivot = Offset.Zero) { drawLayer(page) } }
            bodySize = size
            pageScale = reduce
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
 * An AGSL program run as a render effect for one surface, and the numbers that surface last gave it:
 * [effect] is a new effect only when one of them changed since the last ([begin], the [set]s, then
 * [effect]). A layer handed the same effect is not drawn again.
 *
 * The [shader] is shared by every surface ([SharedPrograms]): all the numbers are set again before every
 * [effect], and an effect is made from them straight away.
 */
@RequiresApi(33)
private class Program(private val shader: RuntimeShader) {
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

/** How high over the glass the room's light is, against its 1 of reach across the screen. */
private const val KeyLightHeight = 0.9f

/** From how much frost on the page under the glass is blurred at half its size. */
internal val HalfSizeFrost = 4.dp

/** How far the glass's shadow spreads round it, and how far down it falls. */
internal val ShadowSpread = 14.dp
internal val ShadowDrop = 4.dp

/**
 * Why glass is drawn plain on this device, or null when it is drawn in full: below API 33 there is no
 * AGSL, and a program that does not compile says so here, with the compiler's own words.
 */
public fun glassShaderError(): String? {
    if (Build.VERSION.SDK_INT < 33) return "API ${Build.VERSION.SDK_INT}: full glass needs Android 13 (API 33)"
    return runCatching { GlassPrograms() }.exceptionOrNull()?.let { it.message ?: it.toString() }
}

/**
 * Compiles glass's programs now, on the calling thread, instead of on the main thread when the first
 * surface is drawn: the first compile in a process can take more than a hundred milliseconds. Call it off
 * the main thread early on, at app start: `scope.launch(Dispatchers.Default) { prepareGlass() }`. Safe to
 * call more than once, and from any thread; does nothing below API 33 or where AGSL does not compile.
 */
public fun prepareGlass() {
    if (Build.VERSION.SDK_INT < 33) return
    runCatching {
        SharedPrograms.body
        SharedPrograms.ink
    }
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
    // What is engraved stays inside the glass, lit or not: the engraving's program cuts it to the shape,
    // and the layer's clip does when there is no program to run.
    val outline = remember(cornerRadius) { if (cornerRadius == null) RoundedCornerShape(percent = 50) else RoundedCornerShape(cornerRadius) }
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
                shape = outline
                clip = true
                // Drawn plain, the marks take the lights' colours over themselves alone: off-screen, while lit.
                compositingStrategy = if (painter.drawsPlain && PlainGlass.lit(painter.lighting)) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawWithContent {
                drawContent()
                if (painter.drawsPlain) {
                    painter.light { all(size) }
                    with(painter) { drawPlainInk(material) }
                }
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
