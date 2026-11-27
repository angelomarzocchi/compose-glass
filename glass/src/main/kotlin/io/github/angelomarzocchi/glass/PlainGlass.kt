package io.github.angelomarzocchi.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Glass without its programs ([GlassQuality.Frosted], [GlassQuality.Tinted]): what can be drawn without
 * AGSL, made to look as much like the liquid glass as that allows. The page frosted and a little richer
 * in colour where the platform can blur (API 31+), under the body's tint — more of it, since nothing is
 * bent to show the glass is there — cut to the shape; the line of light along the edge, glinting where the
 * room's light meets it and on the far side; a soft shadow (API 28+); and the lights, drawn flat inside
 * the shape. One to a surface: it keeps what it drew, and draws the page again only when that changes.
 */
internal class PlainGlass(private val layer: GraphicsLayer) {
    private val outline = Path()
    private var shadowPaint: Paint? = null

    private var effectFrost = -1f
    private var effectSaturation = -1f
    private var recordedSize = IntSize.Zero
    private var recordedAt = Offset.Unspecified
    private var recordedSource: GraphicsLayer? = null
    private var recordedFrom = Offset.Unspecified

    fun DrawScope.draw(
        quality: GlassQuality,
        backdrop: GlassBackdrop,
        origin: Offset,
        material: GlassMaterial,
        lighting: GlassLighting,
        shape: Rect,
        radius: Float,
        dark: Boolean,
    ) {
        if (shape.width <= 0f || shape.height <= 0f) return
        val corner = CornerRadius(radius)
        outline.reset()
        outline.addRoundRect(RoundRect(shape, corner))

        drawShadow(material, shape, radius)

        val frosted = quality != GlassQuality.Tinted && Build.VERSION.SDK_INT >= 31
        if (frosted) drawFrostedPage(backdrop, origin, material, shape, radius)

        // More body than the full glass: with nothing bent at the rim, the tint is what says it is glass,
        // and what keeps a label readable over the page.
        val body = if (frosted) material.tintAmount + FrostedBody else TintedBody + material.tintAmount * 0.1f
        drawRoundRect(material.tint.copy(alpha = body.coerceIn(0f, 0.96f)), shape.topLeft, shape.size, corner)

        clipPath(outline) {
            drawSelection(lighting, dark)
            drawLamp(material, lighting, dark, shape)
        }
        drawEdgeLight(material, lighting, shape, radius)
    }

    /** The page under the glass, frosted, its colours a little richer, cut to the shape. */
    private fun DrawScope.drawFrostedPage(backdrop: GlassBackdrop, origin: Offset, material: GlassMaterial, shape: Rect, radius: Float) {
        // A still of the page reaches the glass with a frame of its own.
        backdrop.version
        val frost = max(material.frost.toPx(), FallbackFrost.toPx())
        val reduce = if (frost >= HalfSizeFrost.toPx()) 0.5f else 1f
        if (frost != effectFrost || material.saturation != effectSaturation) {
            effectFrost = frost
            effectSaturation = material.saturation
            val blur = RenderEffect.createBlurEffect(frost * reduce, frost * reduce, Shader.TileMode.CLAMP)
            layer.renderEffect = if (material.saturation != 1f) {
                val matrix = ColorMatrix().apply { setSaturation(material.saturation) }
                RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix), blur).asComposeRenderEffect()
            } else {
                blur.asComposeRenderEffect()
            }
        }
        // The shape with room round it for the blur to read, at [reduce] of its size.
        val room = ceil(frost * 2f + 2f)
        val from = Offset(shape.left - room, shape.top - room)
        val size = IntSize(
            ceil((shape.width + 2 * room) * reduce).toInt().coerceAtLeast(1),
            ceil((shape.height + 2 * room) * reduce).toInt().coerceAtLeast(1),
        )
        val at = origin - backdrop.origin
        if (size != recordedSize || at != recordedAt || backdrop.layer !== recordedSource || from != recordedFrom) {
            layer.record(size) {
                scale(reduce, reduce, pivot = Offset.Zero) { translate(-from.x - at.x, -from.y - at.y) { drawLayer(backdrop.layer) } }
            }
            layer.setRoundRectOutline(Offset(room * reduce, room * reduce), Size(shape.width * reduce, shape.height * reduce), radius * reduce)
            layer.clip = true
            recordedSize = size
            recordedAt = at
            recordedSource = backdrop.layer
            recordedFrom = from
        }
        translate(from.x, from.y) { scale(1f / reduce, 1f / reduce, pivot = Offset.Zero) { drawLayer(layer) } }
    }

    /** The glass's soft shadow, round the shape only: the platform draws one for any shape from API 28. */
    private fun DrawScope.drawShadow(material: GlassMaterial, shape: Rect, radius: Float) {
        val shadow = material.shadow.coerceIn(0f, 1f)
        if (shadow <= 0f) return
        if (Build.VERSION.SDK_INT < 28) {
            // No blurred shadow for a shape: rings round it, fainter the further out, a little lower.
            val ring = ShadowSpread.toPx() / ShadowRings
            val drop = ShadowDrop.toPx() / 2f
            for (i in 0 until ShadowRings) {
                val out = ring * (i + 0.5f)
                drawRoundRect(
                    color = Color.Black.copy(alpha = shadow * 0.75f * (1f - i / ShadowRings.toFloat())),
                    topLeft = Offset(shape.left - out, shape.top - out + drop),
                    size = Size(shape.width + 2 * out, shape.height + 2 * out),
                    cornerRadius = CornerRadius(radius + out),
                    style = Stroke(width = ring),
                )
            }
            return
        }
        val paint = shadowPaint ?: Paint(Paint.ANTI_ALIAS_FLAG).also { shadowPaint = it }
        val colour = Color.Black.copy(alpha = (shadow * ShadowStrength).coerceAtMost(1f)).toArgb()
        paint.color = colour
        paint.setShadowLayer(ShadowSpread.toPx() * 0.7f, 0f, ShadowDrop.toPx(), colour)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.clipOutPath(outline.asAndroidPath())
            native.drawRoundRect(shape.left, shape.top, shape.right, shape.bottom, radius, radius, paint)
            native.restore()
        }
    }

    /** The selected plate's light, flat: on pale glass it shows as colour, on dark glass a little less of it. */
    private fun DrawScope.drawSelection(lighting: GlassLighting, dark: Boolean) {
        val on = lighting.selectionOn.coerceIn(0f, 1f)
        val s = lighting.selection
        if (on <= 0f || s.width <= 0f || s.height <= 0f) return
        drawRoundRect(
            color = lighting.selectionColor.copy(alpha = on * if (dark) 0.6f else 0.75f),
            topLeft = s.topLeft,
            size = s.size,
            cornerRadius = CornerRadius(s.height / 2f),
        )
    }

    /** The finger's light, a glow round it. */
    private fun DrawScope.drawLamp(material: GlassMaterial, lighting: GlassLighting, dark: Boolean, shape: Rect) {
        val on = lighting.lampOn.coerceIn(0f, 1f)
        val reach = material.lampReach.toPx()
        if (on <= 0f || reach <= 0f) return
        drawRect(
            Brush.radialGradient(
                0f to lighting.lampColor.copy(alpha = on * if (dark) 0.25f else 0.35f),
                1f to Color.Transparent,
                center = lighting.lamp,
                radius = reach * 1.6f,
            ),
            topLeft = shape.topLeft,
            size = shape.size,
        )
    }

    /**
     * The fine line of light just inside the edge: brightest where the edge faces the room's light, then
     * on the far side, faint between — the full glass's [GlassMaterial.hairline], [farHairline] and
     * [hairlineFocus], laid along the light's direction across the shape.
     */
    private fun DrawScope.drawEdgeLight(material: GlassMaterial, lighting: GlassLighting, shape: Rect, radius: Float) {
        val near = material.hairline.coerceIn(0f, 1.5f)
        val far = material.farHairline.coerceIn(0f, 1.5f)
        if (near <= 0f && far <= 0f) return
        val width = max(material.hairlineWidth.toPx(), 1f)
        var lx = lighting.keyLight.x
        var ly = lighting.keyLight.y
        val length = sqrt(lx * lx + ly * ly)
        if (length < 1e-4f) {
            lx = 0f
            ly = -1f
        } else {
            lx /= length
            ly /= length
        }
        // How far across the shape the light's direction runs, from its centre.
        val reach = abs(lx) * shape.width / 2f + abs(ly) * shape.height / 2f
        val centre = shape.center
        val focus = max(material.hairlineFocus, 0.1f)
        fun at(t: Float): Color {
            val facing = 1f - 2f * t
            val side = (far + (near - far) * (facing * 0.5f + 0.5f)) * (0.3f + 0.7f * abs(facing).pow(focus))
            return Color.White.copy(alpha = (side * EdgeLight).coerceIn(0f, 1f))
        }
        val brush = Brush.linearGradient(
            0f to at(0f), 0.25f to at(0.25f), 0.5f to at(0.5f), 0.75f to at(0.75f), 1f to at(1f),
            start = Offset(centre.x + lx * reach, centre.y + ly * reach),
            end = Offset(centre.x - lx * reach, centre.y - ly * reach),
        )
        val inset = width / 2f
        drawRoundRect(
            brush = brush,
            topLeft = Offset(shape.left + inset, shape.top + inset),
            size = Size(shape.width - width, shape.height - width),
            cornerRadius = CornerRadius(max(radius - inset, 0f)),
            style = Stroke(width = width),
        )
    }

    /**
     * What engraved marks turn in the lights, without the engraving's program, drawn with them: the lamp
     * lights them up (its ink screened over the marks alone), the selection repaints them in its ink. The
     * layer holding them must be drawn off-screen while a light is on ([lit]), for both to keep to the marks.
     */
    fun ContentDrawScope.drawMarksLit(material: GlassMaterial, lighting: GlassLighting) {
        drawContent()
        val lamp = lighting.lampOn.coerceIn(0f, 1f)
        val reach = material.lampReach.toPx()
        if (lamp > 0f && reach > 0f) {
            // The marks again, screened with the lamp's ink, laid over themselves only.
            val canvas = drawContext.canvas
            canvas.saveLayer(Rect(Offset.Zero, size), atop)
            drawContent()
            drawCircle(
                Brush.radialGradient(
                    0f to lighting.lampInk.copy(alpha = lamp),
                    0.45f to lighting.lampInk.copy(alpha = lamp * 0.6f),
                    1f to Color.Transparent,
                    center = lighting.lamp,
                    radius = reach * 1.4f,
                ),
                radius = reach * 1.4f,
                center = lighting.lamp,
                blendMode = BlendMode.Screen,
            )
            canvas.restore()
        }
        val s = lighting.selection
        val selected = lighting.selectionOn.coerceIn(0f, 1f)
        if (selected > 0f && s.width > 0f && s.height > 0f) {
            drawRoundRect(
                color = lighting.selectionInk.copy(alpha = selected),
                topLeft = s.topLeft,
                size = s.size,
                cornerRadius = CornerRadius(s.height / 2f),
                blendMode = BlendMode.SrcAtop,
            )
        }
    }

    private val atop = androidx.compose.ui.graphics.Paint().apply { blendMode = BlendMode.SrcAtop }

    companion object {
        /** Whether any light is on, for the marks to take its colour. */
        fun lit(lighting: GlassLighting): Boolean =
            lighting.lampOn > 0f || (lighting.selectionOn > 0f && lighting.selection.width > 0f && lighting.selection.height > 0f)
    }
}

/** At least this much frost under frosted glass: with no bend, the blur is what says it is glass. */
private val FallbackFrost = 12.dp

/** How much more body frosted glass has than the full glass. */
private const val FrostedBody = 0.15f

/** How much body tinted glass has, before the material's own: all but opaque, with nothing under it frosted. */
private const val TintedBody = 0.92f

/** The edge's line against the full glass's: drawn over the glass rather than added to it. */
private const val EdgeLight = 0.45f

/** How many rings stand for the shadow below API 28. */
private const val ShadowRings = 6

/** The platform's shadow is fainter than the glass's own for the same colour. */
private const val ShadowStrength = 1.6f
