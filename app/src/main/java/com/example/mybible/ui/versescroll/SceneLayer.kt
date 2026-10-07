package com.example.mybible.ui.versescroll

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.example.mybible.versescroll.Breathe
import com.example.mybible.versescroll.SceneFx
import com.example.mybible.versescroll.SceneSpec
import com.example.mybible.versescroll.fnv1a
import com.example.mybible.versescroll.sceneEffects
import kotlin.math.floor
import kotlin.math.hypot

// CSS's ease-in-out, which the design preview's motion was tuned with.
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private val VeilDark = Color(0xFF0A0808)
private val VeilLight = Color(0xFFFAF7F0)

/** How far through one run of a repeating animation [t] seconds in is (0..1), honoring its start offset. */
private fun phase(t: Float, period: Float, delay: Float): Float {
    val x = (t - delay) / period
    return x - floor(x)
}

/** The same, for an animation that plays forward then backward ("alternate"); [reverse] starts backward. */
private fun alternating(t: Float, period: Float, delay: Float, reverse: Boolean = false): Float {
    val x = (t - delay) / period
    val n = floor(x).toInt()
    val p = x - n
    val backward = (n % 2 != 0) xor reverse
    return EaseInOut.transform(if (backward) 1 - p else p)
}

/** A keyframe track: value at the start/end, [peak] at [peakAt], eased between (CSS eases each segment). */
private fun peaked(p: Float, low: Float, peak: Float, peakAt: Float): Float =
    if (p < peakAt) low + (peak - low) * EaseInOut.transform(p / peakAt)
    else peak + (low - peak) * EaseInOut.transform((p - peakAt) / (1 - peakAt))

private fun Color.clear() = copy(alpha = 0f)

/**
 * A verse card's painted background: the picture, its slow drift and zoom, the scene's small moving
 * touch, and the veil over it all that keeps the text readable.
 *
 * Time only runs while [running] (the card on screen, with motion on), so cards off screen hold still
 * where they stopped — a card swiped back to carries on from there.
 *
 * @param motion motion is on (the drift, the moving touches, and a little extra dimming to cover the
 *   brighter patches the zoom can bring behind the text).
 * @param effects show the scene's moving touch; off for long verses, which read better without it.
 */
@Composable
internal fun SceneLayer(
    scene: SceneSpec,
    cardKey: String,
    image: ImageBitmap?,
    dark: Boolean,
    motion: Boolean,
    running: Boolean,
    effects: Boolean,
    modifier: Modifier = Modifier
) {
    val breathe = remember(cardKey) { Breathe.forKey(cardKey) }
    val fx = remember(scene.id, cardKey) { sceneEffects(scene, fnv1a(cardKey)) }
    var clock by remember(cardKey) { mutableFloatStateOf(0f) }
    LaunchedEffect(cardKey, running && motion) {
        if (!(running && motion)) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0) clock += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    val shown by animateFloatAsState(if (image != null) 1f else 0f, tween(350), label = "sceneFadeIn")

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = shown
                    if (motion) {
                        val p = alternating(clock, breathe.period, breathe.delay)
                        val s = 1.02f + 0.10f * p
                        scaleX = s
                        scaleY = s
                        translationX = s * p * breathe.dx * size.width
                        translationY = s * p * breathe.dy * size.height
                        transformOrigin = TransformOrigin(breathe.originX, breathe.originY)
                    }
                }
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, 0.2f),
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (motion && effects && fx.isNotEmpty()) {
                Canvas(Modifier.fillMaxSize()) { drawEffects(fx, clock, dark) }
            }
        }
        Canvas(Modifier.fillMaxSize()) { drawVeil(scene, dark, motion) }
    }
}

/**
 * Each scene's measured dimming, plus a fixed gradient that darkens (or lightens) the strips under the
 * top and bottom bars. With motion on, a small margin covers the brighter patches the zoom can bring
 * behind the text.
 */
private fun DrawScope.drawVeil(scene: SceneSpec, dark: Boolean, motion: Boolean) {
    val color = if (dark) VeilDark else VeilLight
    val k = if (dark) scene.kd + (if (motion) 0.05f else 0f) else scene.kl + (if (motion) 0.03f else 0f)
    val stops = if (dark) listOf(0f to 0.45f, 0.14f to 0.08f, 0.8f to 0.08f, 1f to 0.7f)
    else listOf(0f to 0.6f, 0.14f to 0.12f, 0.8f to 0.12f, 1f to 0.8f)
    drawRect(
        brush = Brush.verticalGradient(*stops.map { (at, a) -> at to color.copy(alpha = a) }.toTypedArray())
    )
    drawRect(color.copy(alpha = k.coerceIn(0f, 1f)))
}

private fun DrawScope.drawEffects(fx: List<SceneFx>, t: Float, dark: Boolean) {
    val w = size.width
    val h = size.height
    for (e in fx) when (e) {
        is SceneFx.Twinkle -> {
            val p = phase(t, e.period, e.delay)
            val opacity = peaked(p, 0.08f, 0.95f, 0.5f)
            val s = peaked(p, 0.7f, 1.15f, 0.5f)
            val c = Offset(e.x * w, e.y * h)
            val glow = 7.2f * density * s
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xBFDCE2FF), 0.3f to Color(0x99DCE2FF), 1f to Color(0x00DCE2FF),
                    center = c, radius = glow
                ),
                radius = glow, center = c, alpha = opacity
            )
            drawCircle(Color.White, radius = 1.2f * density * s, center = c, alpha = opacity)
        }
        is SceneFx.Glint -> {
            val p = phase(t, e.period, e.delay)
            val opacity = peaked(p, 0f, 0.9f, 0.45f)
            val sx = peaked(p, 0.55f, 1f, 0.45f)
            val ry = 0.0025f * h
            val rx = e.width * w / 2 * sx
            val c = Offset(e.x * w + e.width * w / 2, e.y * h + ry)
            val color = Color(e.color)
            scale(scaleX = rx / ry, scaleY = 1f, pivot = c) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(color, color.clear()), center = c, radius = ry),
                    radius = ry, center = c, alpha = opacity
                )
            }
        }
        is SceneFx.Rays -> {
            val p = alternating(t, 16f, 0f)
            val angle = -4.5f + 9f * p
            val opacity = 0.3f + 0.45f * p
            val src = Offset(e.x * w, e.y * h)
            val reach = maxOf(
                hypot(src.x, src.y), hypot(w - src.x, src.y), hypot(src.x, h - src.y), hypot(w - src.x, h - src.y)
            )
            val color = Color(e.color)
            // A soft ray every 13 degrees: clear, full at 3, clear again by 7.
            val stops = mutableListOf<Pair<Float, Color>>()
            var deg = 0f
            while (deg < 360f) {
                stops += deg / 360f to color.clear()
                if (deg + 3f <= 360f) stops += (deg + 3f) / 360f to color
                if (deg + 7f <= 360f) stops += (deg + 7f) / 360f to color.clear()
                deg += 13f
            }
            stops += 1f to color.clear()
            val layer = Paint().apply { alpha = opacity }
            drawContext.canvas.saveLayer(Rect(0f, 0f, w, h), layer)
            rotate(angle, pivot = src) {
                drawRect(brush = Brush.sweepGradient(*stops.toTypedArray(), center = src))
            }
            // Fades the rays out with distance from the source.
            drawRect(
                brush = Brush.radialGradient(
                    0f to Color.Black, 1f to Color.Black.clear(), center = src, radius = 0.58f * reach
                ),
                blendMode = BlendMode.DstIn
            )
            drawContext.canvas.restore()
        }
        is SceneFx.Blob -> {
            val forward = alternating(t, e.period, e.delay)
            val size = 0.75f * w
            val dx = (-0.13f + 0.26f * forward) * size
            val dy = (-0.09f + 0.19f * forward) * size
            val c = Offset(e.x * w + size / 2 + dx, e.y * h + size / 2 + dy)
            val color = Color(e.color)
            val r = 0.35f * w
            drawCircle(
                brush = Brush.radialGradient(listOf(color, color.clear()), center = c, radius = r),
                radius = r, center = c, alpha = if (dark) 0.22f else 0.3f, blendMode = BlendMode.Screen
            )
        }
        is SceneFx.Flake -> {
            val p = phase(t, e.period, e.delay)
            val s = e.sizeDp * density
            val c = Offset(e.x * w + e.swayDp * density * p + s / 2, h * p - s / 2)
            val glow = s / 2 + 3f * density
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0x99FFFFFF), Color(0x00FFFFFF)), center = c, radius = glow),
                radius = glow, center = c
            )
            drawCircle(Color(0xE6FFFFFF), radius = s / 2, center = c)
        }
        is SceneFx.Ripple -> {
            val p = phase(t, e.period, e.delay)
            val opacity = peaked(p, 0f, 0.75f, 0.5f)
            val sx = peaked(p, 0.6f, 1f, 0.5f)
            val len = e.width * w * sx
            val left = e.x * w + (e.width * w - len) / 2
            val color = Color(e.color)
            drawRect(
                brush = Brush.horizontalGradient(listOf(color.clear(), color, color.clear()), startX = left, endX = left + len),
                topLeft = Offset(left, e.y * h),
                size = Size(len, 2f * density),
                alpha = opacity
            )
        }
        is SceneFx.Breeze -> {
            val p = EaseInOut.transform(phase(t, e.period, e.delay))
            val bandW = 0.7f * w
            val tx = (-1.1f + 2.7f * p) * bandW
            val rx = 0.337f * w
            val ry = 0.48f * e.height * h
            val c = Offset(bandW / 2 + tx, (e.y + e.height / 2) * h)
            val color = Color(e.color)
            scale(scaleX = rx / ry, scaleY = 1f, pivot = c) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(color, color.clear()), center = c, radius = ry),
                    radius = ry, center = c, alpha = 0.5f, blendMode = BlendMode.Screen
                )
            }
        }
        is SceneFx.Mist -> {
            val p = alternating(t, e.period, e.delay, e.reverse)
            val tx = (-0.288f + 0.576f * p) * w
            val rx = 0.84f * w
            val ry = 0.0653f * h
            val c = Offset(0.5f * w + tx, e.y * h + 0.07f * h)
            val color = Color(e.color)
            scale(scaleX = rx / ry, scaleY = 1f, pivot = c) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(color, color.clear()), center = c, radius = ry),
                    radius = ry, center = c, alpha = e.opacity * (if (dark) 0.7f else 1f)
                )
            }
        }
    }
}
