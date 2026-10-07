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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.example.mybible.versescroll.Breathe
import com.example.mybible.versescroll.SceneSpec
import kotlin.math.floor

// CSS's ease-in-out, which the design preview's motion was tuned with.
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private val VeilDark = Color(0xFF0A0808)
private val VeilLight = Color(0xFFFAF7F0)

/** How far through an animation that plays forward then backward ("alternate") [t] seconds in is, eased. */
private fun alternating(t: Float, period: Float, delay: Float): Float {
    val x = (t - delay) / period
    val n = floor(x).toInt()
    val p = x - n
    return EaseInOut.transform(if (n % 2 != 0) 1 - p else p)
}

/**
 * A verse card's background photo: the picture, its slow drift and zoom, and the veil over it that
 * keeps the text readable.
 *
 * Time only runs while [running] (the card on screen, with motion on), so cards off screen hold still
 * where they stopped — a card swiped back to carries on from there.
 *
 * @param motion motion is on (the drift, and a little extra dimming to cover the brighter patches the
 *   zoom can bring behind the text).
 */
@Composable
internal fun SceneLayer(
    scene: SceneSpec,
    cardKey: String,
    image: ImageBitmap?,
    dark: Boolean,
    motion: Boolean,
    running: Boolean,
    modifier: Modifier = Modifier
) {
    val breathe = remember(cardKey) { Breathe.forKey(cardKey) }
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
