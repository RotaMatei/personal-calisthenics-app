package com.personal.calisthenicsguide.ui.guide

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.personal.calisthenics.core.mesh.HumanMesh
import com.personal.calisthenics.core.mesh.MeshFrames
import com.personal.calisthenics.core.mesh.MeshOptions
import com.personal.calisthenics.core.mesh.MeshRenderer
import com.personal.calisthenics.core.mesh.ViewMap
import com.personal.calisthenics.core.rig.Bounds
import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.ClipPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * The 3D figure is a real, detailed human mesh (MakeHuman, CC0) posed by the same rig as before and drawn uncoloured
 * ("clay") with soft muted hues where the exercise loads the body. The mesh is rasterised on the CPU by :core into an
 * ARGB picture that these composables show as a bitmap. If the mesh cannot be loaded, or drawing it ever fails, every
 * view here falls back to the capsule figure, so a clip is never blank.
 */

/** Loads the body mesh once per process: from the app's assets first, then from the class path. */
object MeshAssets {
    fun load(context: Context): HumanMesh? {
        HumanMesh.shared?.let { return it }
        val fromAssets = runCatching {
            context.applicationContext.assets.open("human.bin").use { HumanMesh.install(it) }
        }.getOrNull()
        return fromAssets ?: runCatching { HumanMesh.sharedOrNull() }.getOrNull()
    }
}

/** The body mesh and whether loading it is over, so "no mesh" can mean "still loading" or "unavailable". */
internal class MeshLoad(val mesh: HumanMesh?, val finished: Boolean)

@Composable
internal fun rememberMeshLoad(): MeshLoad {
    val context = LocalContext.current.applicationContext
    val initial = HumanMesh.shared
    val load by produceState(MeshLoad(initial, initial != null), context) {
        if (!value.finished) {
            val mesh = withContext(Dispatchers.Default) { MeshAssets.load(context) }
            value = MeshLoad(mesh, true)
        }
    }
    return load
}

/** What a live picture draws next: the clip time and the camera. */
internal data class LiveRequest(val timeMs: Long, val camera: Camera)

private const val LIVE_MAX_WIDTH = 720f
private const val LIVE_MAX_PIXELS = 400_000f
private const val LIVE_MIN_SCALE = 0.4f
private const val LIVE_SLOW_MS = 45.0

/** Never start a new picture sooner than this after the previous one (about 35 per second, whatever the display rate). */
private const val LIVE_MIN_INTERVAL_NS = 28_000_000L

/** Three bitmaps in rotation: one is shown, one is being filled, one may still be queued for the screen. */
private class LiveBuffers(val w: Int, val h: Int) {
    val pixels = IntArray(w * h)
    val bitmaps = Array(3) { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
    val images: Array<ImageBitmap> = Array(3) { bitmaps[it].asImageBitmap() }
    var next = 0
}

/**
 * A looping clip as a live picture: the mesh is rendered on a background thread for whatever [request] asks for at the
 * start of a display frame, at a size that keeps phones at a comfortable frame rate, and drawn scaled to fill the
 * canvas. [bounds] gives the framing of the clip for the mesh (see MeshFraming; it runs off the main thread);
 * [boundsKey] must change whenever [bounds] would answer differently. [pad] is in canvas pixels.
 */
@Composable
internal fun LivePicture(
    player: ClipPlayer,
    boundsKey: Any,
    bounds: (ClipPlayer) -> Bounds,
    pad: Float,
    modifier: Modifier = Modifier,
    request: () -> LiveRequest,
) {
    val load = rememberMeshLoad()
    val mesh = load.mesh
    var broken by remember(player) { mutableStateOf(false) }
    if (broken || (mesh == null && load.finished)) {
        // Capsule figure: the mesh is unavailable.
        val framing = remember(player, boundsKey) { bounds(player) }
        Canvas(modifier) {
            val req = request()
            drawPrims(player.frame(req.timeMs, camera = req.camera).prims, ViewFit(framing, size.width, size.height, pad))
        }
        return
    }
    if (mesh == null) {
        // Still loading: just the backdrop for a moment.
        Canvas(modifier) {}
        return
    }

    var box by remember { mutableStateOf(IntSize.Zero) }
    var shown by remember(mesh, player) { mutableStateOf<ImageBitmap?>(null) }
    val currentRequest by rememberUpdatedState(request)
    val currentBounds by rememberUpdatedState(bounds)

    LaunchedEffect(mesh, player, boundsKey, pad, box) {
        val canvasW = box.width
        val canvasH = box.height
        if (canvasW < 8 || canvasH < 8) return@LaunchedEffect
        val renderer = MeshRenderer(mesh)
        val measure = currentBounds
        val framing = withContext(Dispatchers.Default) { measure(player) }
        var scale = min(1f, min(LIVE_MAX_WIDTH / canvasW, sqrt(LIVE_MAX_PIXELS / (canvasW.toFloat() * canvasH))))
        var buffers = LiveBuffers((canvasW * scale).roundToInt().coerceAtLeast(8), (canvasH * scale).roundToInt().coerceAtLeast(8))
        var last: LiveRequest? = null
        var lastStartNs = 0L
        var averageMs = 0.0
        var frames = 0
        while (true) {
            // Look at the request once per display frame; only draw when it changed and the last picture is old enough.
            val frame = withFrameNanos { nanos -> if (nanos - lastStartNs < LIVE_MIN_INTERVAL_NS) null else currentRequest() }
            if (frame == null || frame == last) continue
            val req: LiveRequest = frame
            last = req
            lastStartNs = System.nanoTime()
            val target = buffers
            val targetScale = scale
            val started = System.nanoTime()
            val slot = try {
                withContext(Dispatchers.Default) {
                    val view = ViewMap(framing, target.w.toFloat(), target.h.toFloat(), pad * targetScale)
                    MeshFrames.clip(renderer, player, req.timeMs, req.camera, view, target.pixels, MeshOptions(supersample = 1))
                    val s = target.next
                    target.bitmaps[s].setPixels(target.pixels, 0, target.w, 0, 0, target.w, target.h)
                    target.next = (s + 1) % target.bitmaps.size
                    s
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                broken = true
                return@LaunchedEffect
            }
            shown = target.images[slot]

            // Too slow for this phone: render smaller (the picture is scaled up on screen).
            val tookMs = (System.nanoTime() - started) / 1e6
            averageMs = if (frames == 0) tookMs else averageMs * 0.85 + tookMs * 0.15
            frames++
            if (frames >= 12 && averageMs > LIVE_SLOW_MS && scale > LIVE_MIN_SCALE) {
                scale = (scale * 0.8f).coerceAtLeast(LIVE_MIN_SCALE)
                buffers = LiveBuffers((canvasW * scale).roundToInt().coerceAtLeast(8), (canvasH * scale).roundToInt().coerceAtLeast(8))
                averageMs = 0.0
                frames = 0
                last = null
            }
        }
    }

    Canvas(modifier.onSizeChanged { box = it }) {
        val image = shown ?: return@Canvas
        drawImage(
            image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}

/** Renders one still picture at a time with one shared renderer, so a list of thumbnails does not allocate one each. */
private object StillRendering {
    private val lock = Mutex()
    private var renderer: MeshRenderer? = null
    private var owner: HumanMesh? = null

    suspend fun <T> render(mesh: HumanMesh, block: (MeshRenderer) -> T): T = lock.withLock {
        withContext(Dispatchers.Default) {
            val r = renderer?.takeIf { owner === mesh } ?: MeshRenderer(mesh).also { renderer = it; owner = mesh }
            block(r)
        }
    }
}

/** Finished still pictures, so scrolling a list back and forth does not render them again. */
private object StillCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
}

/**
 * A still picture of the mesh, rendered once (off the main thread) at the size of the canvas and cached under
 * [cacheKey], which must name everything that changes the picture except its size. [draw] puts the figure into the
 * given buffer through the given [ViewMap]; [overlay] is painted on top (labels); [fallback] paints the capsule figure
 * while the mesh is unavailable or if drawing it fails.
 */
@Composable
internal fun StaticMeshPicture(
    cacheKey: String,
    bounds: Bounds,
    pad: Float,
    modifier: Modifier = Modifier,
    overlay: DrawScope.() -> Unit = {},
    fallback: DrawScope.() -> Unit,
    draw: (MeshRenderer, ViewMap, IntArray, MeshOptions) -> Boolean,
) {
    val load = rememberMeshLoad()
    val mesh = load.mesh
    var box by remember { mutableStateOf(IntSize.Zero) }
    var failed by remember(cacheKey) { mutableStateOf(false) }
    val currentDraw by rememberUpdatedState(draw)
    val image by produceState<ImageBitmap?>(null, mesh, cacheKey, box, bounds) {
        val m = mesh
        val w = box.width
        val h = box.height
        if (m == null || w < 8 || h < 8) return@produceState
        val key = "$cacheKey|${w}x$h|$bounds|$pad"
        val cached = StillCache.get(key)
        if (cached != null) {
            value = cached.asImageBitmap()
            return@produceState
        }
        try {
            val bitmap = StillRendering.render(m) { renderer ->
                // Double-size rendering for small pictures; big ones rely on the renderer's edge smoothing instead.
                val options = MeshOptions(supersample = if (w * h <= 330_000) 2 else 1)
                val pixels = IntArray(w * h)
                if (currentDraw(renderer, ViewMap(bounds, w.toFloat(), h.toFloat(), pad), pixels, options)) {
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(pixels, 0, w, 0, 0, w, h) }
                } else {
                    null
                }
            }
            if (bitmap != null) {
                StillCache.put(key, bitmap)
                value = bitmap.asImageBitmap()
            } else {
                failed = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            failed = true
        }
    }

    Canvas(modifier.onSizeChanged { box = it }) {
        val shown = image
        if (shown != null) {
            drawImage(
                shown,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        } else if ((mesh == null && load.finished) || failed) {
            fallback()
        }
        overlay()
    }
}
