package io.github.angelomarzocchi.glass

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A still of [source]: the page as it was a moment ago, taken again every [every]. For glass over a part
 * of the page that hardly changes from one frame to the next — a bar over a blurred background, over a
 * page that only moves under other glass — while the page under it keeps changing (a video, a camera).
 *
 * Glass over a live page draws again whenever the page does: the page under it, its frost and its program,
 * every frame. Over a still it draws again only when a new still is taken, or when its own lights move;
 * in between it costs nothing. What it shows is at most [every] old, and the still is taken at [scale] of
 * the page's size: the page under glass is bent and frosted, and a half-size still is not told apart.
 *
 * The stills are drawn by a renderer of their own, made once and kept: the GPU draws one when it can, and
 * nothing waits for it. Until the first still, and below Android 10, the glass sees the live page. Use it
 * as any backdrop, in [GlassSurface] or [GlassPainter.drawBody]; the page is still recorded with
 * [glassBackdrop] on [source].
 */
@Composable
public fun rememberStillGlassBackdrop(source: GlassBackdrop, every: Duration = 250.milliseconds, scale: Float = 0.5f): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    val still = remember(layer) { GlassBackdrop(layer) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    LaunchedEffect(source, still) { snapshotFlow { source.origin }.collect { still.origin = it } }
    LaunchedEffect(source, still, every, scale, density, direction) {
        var shown = IntSize.Zero
        var stills: Any? = null
        try {
            while (true) {
                // Only while the page is being drawn: no stills of a screen nobody sees.
                withFrameNanos { }
                val size = source.layer.size
                if (size.width <= 0 || size.height <= 0) continue
                if (shown != size) {
                    // Not a still yet, or the page changed size: the live page meanwhile.
                    layer.record(density, direction, size) { drawLayer(source.layer) }
                    shown = size
                    still.version++
                }
                if (Build.VERSION.SDK_INT < 29) return@LaunchedEffect
                val reduced = IntSize((size.width * scale).roundToInt().coerceAtLeast(1), (size.height * scale).roundToInt().coerceAtLeast(1))
                val camera = (stills as? Stills)?.takeIf { it.size == reduced } ?: run {
                    (stills as? Stills)?.close()
                    Stills(reduced).also { stills = it }
                }
                val image = camera.take(density, direction) { scale(scale, scale, pivot = Offset.Zero) { drawLayer(source.layer) } }
                if (image != null && source.layer.size == size) {
                    layer.record(density, direction, size) {
                        drawImage(image, srcOffset = IntOffset.Zero, srcSize = reduced, dstOffset = IntOffset.Zero, dstSize = size, filterQuality = FilterQuality.Low)
                    }
                    still.version++
                }
                delay(every)
            }
        } finally {
            (stills as? Stills)?.close()
        }
    }
    return still
}

/**
 * Takes stills of a drawing, [size] pixels: a renderer and the surface it draws into, made once. A still
 * is asked for and not waited on — the render thread goes on with the window's frames — and comes back
 * as a bitmap on the GPU's own memory, which stays good until two stills later.
 */
@RequiresApi(29)
private class Stills(val size: IntSize) : AutoCloseable {
    private val reader =
        ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, MaxImages, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
    private val node = RenderNode("glass still").apply { setPosition(0, 0, size.width, size.height) }
    private val renderer = HardwareRenderer().apply {
        setSurface(reader.surface)
        setContentRoot(node)
    }
    private val main = Handler(Looper.getMainLooper())

    // The still on show and the one before it: a buffer is drawn into again only once neither shows it.
    private var current: Image? = null
    private var previous: Image? = null

    /** A still of [block], or null when one could not be had this time. */
    suspend fun take(density: Density, direction: LayoutDirection, block: DrawScope.() -> Unit): ImageBitmap? {
        // A still that came after its wait was over: out of the queue, or the renderer would run out of buffers
        // and wait for one on the render thread.
        while (true) (reader.acquireNextImage() ?: break).close()
        val canvas = node.beginRecording(size.width, size.height)
        try {
            CanvasDrawScope().draw(density, direction, androidx.compose.ui.graphics.Canvas(canvas), Size(size.width.toFloat(), size.height.toFloat()), block)
        } finally {
            node.endRecording()
        }
        renderer.createRenderRequest().setWaitForPresent(false).syncAndDraw()
        val image = withTimeoutOrNull(StillTimeoutMs) { nextImage() } ?: return null
        val buffer = image.hardwareBuffer
        val bitmap = buffer?.let { Bitmap.wrapHardwareBuffer(it, ColorSpace.get(ColorSpace.Named.SRGB)) }
        buffer?.close()
        if (bitmap == null) {
            image.close()
            return null
        }
        previous?.close()
        previous = current
        current = image
        return bitmap.asImageBitmap()
    }

    private suspend fun nextImage(): Image = suspendCancellableCoroutine { done ->
        reader.setOnImageAvailableListener({ r ->
            r.setOnImageAvailableListener(null, null)
            val image = r.acquireNextImage()
            if (image != null && done.isActive) done.resume(image) else image?.close()
        }, main)
        done.invokeOnCancellation { reader.setOnImageAvailableListener(null, null) }
    }

    override fun close() {
        renderer.destroy()
        current?.close()
        previous?.close()
        reader.close()
    }

    private companion object {
        const val MaxImages = 3
        const val StillTimeoutMs = 500L
    }
}
