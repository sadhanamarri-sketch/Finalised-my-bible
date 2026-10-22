package com.example.mybible.pictures

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * How much a picture is dimmed under the verse so its text stays readable, measured as
 * tools/verse_scroll/fit_veils.py measured the bundled photos: the picture laid on a card as the card
 * shows it (filling it, a little above centre), the veil over it (a fixed gradient under the bars, plus
 * an even layer k), and the contrast between each text colour and what's behind it checked wherever a
 * verse can sit; the worst 3% of points count. The verse needs 4.5:1, the reference and lighter text
 * 3:1, in the dimmest of the dark themes ([dark], k) and the lightest of the light ones ([light]).
 */
object PictureVeil {

    /** The even layer for the dark themes and for the light ones, 0 to 0.95. */
    data class Veil(val dark: Float, val light: Float)

    private class Family(val veil: DoubleArray, val stops: List<Pair<Double, Double>>, val texts: List<Triple<DoubleArray, Double, Double>>)

    private fun rgb(hex: Long) = doubleArrayOf(((hex shr 16) and 0xFF) / 255.0, ((hex shr 8) and 0xFF) / 255.0, (hex and 0xFF) / 255.0)

    // The veil's colour and gradient, and the text colours (opacity, contrast needed), as the app draws them.
    private val DARK = Family(
        rgb(0x0A0808), listOf(0.0 to 0.45, 0.14 to 0.08, 0.8 to 0.08, 1.0 to 0.7),
        listOf(Triple(rgb(0xE8D8C8), 1.0, 4.5), Triple(rgb(0xE8D8C8), 0.8, 3.0), Triple(rgb(0xE8C27A), 1.0, 3.0))
    )
    private val LIGHT = Family(
        rgb(0xFAF7F0), listOf(0.0 to 0.6, 0.14 to 0.12, 0.8 to 0.12, 1.0 to 0.8),
        listOf(Triple(rgb(0x3D312A), 1.0, 4.5), Triple(rgb(0x3D312A), 0.8, 3.0), Triple(rgb(0x5E4813), 1.0, 3.0))
    )

    // The card the picture is laid on (a phone's, in dp, as the photos were measured), and where text sits.
    const val CARD_W = 78
    const val CARD_H = 169
    private const val BAND_TOP = 0.09
    private const val BAND_BOTTOM = 0.86
    private const val SIDE = 24.0 / 390

    /**
     * The veil for a picture already laid on the card: [pixels] ARGB, [CARD_W] by [CARD_H] (see
     * [cardCrop] for where to cut it from the picture). Measured small, it's given a little more, as fine
     * bright detail (stars, glints) evens out at this size.
     */
    fun measure(pixels: IntArray): Veil =
        Veil((needed(pixels, DARK) + MARGIN).coerceAtMost(0.95f), (needed(pixels, LIGHT) + MARGIN).coerceAtMost(0.95f))

    private const val MARGIN = 0.03f

    /**
     * Where the card cuts a [width] by [height] picture, as the card shows it (filling the card, centred
     * across and a little above centre down): left, top, width, height in the picture's own pixels.
     */
    fun cardCrop(width: Int, height: Int): IntArray {
        val scale = max(CARD_W.toDouble() / width, CARD_H.toDouble() / height)
        val w = (CARD_W / scale).toInt().coerceIn(1, width)
        val h = (CARD_H / scale).toInt().coerceIn(1, height)
        val left = (width - w) / 2
        val top = ((height - h) * 0.6).toInt()
        return intArrayOf(left, top, w, h)
    }

    private fun needed(pixels: IntArray, family: Family): Float {
        if (passes(pixels, family, 0.0)) return 0f
        var lo = 0.0
        var hi = 0.95
        repeat(8) {
            val mid = (lo + hi) / 2
            if (passes(pixels, family, mid)) hi = mid else lo = mid
        }
        return (ceil(hi * 100) / 100).toFloat()
    }

    private fun passes(pixels: IntArray, family: Family, k: Double): Boolean {
        val top = (CARD_H * BAND_TOP).toInt()
        val bottom = (CARD_H * BAND_BOTTOM).toInt()
        val x0 = (CARD_W * SIDE).toInt()
        val x1 = CARD_W - x0
        val n = (bottom - top) * (x1 - x0)
        val behind = DoubleArray(n * 3)
        var i = 0
        for (y in top until bottom) {
            val base = gradient(family.stops, y / (CARD_H - 1.0))
            val a = 1 - (1 - base) * (1 - k)
            for (x in x0 until x1) {
                val p = pixels[y * CARD_W + x]
                behind[i * 3] = ((p shr 16) and 0xFF) / 255.0 * (1 - a) + family.veil[0] * a
                behind[i * 3 + 1] = ((p shr 8) and 0xFF) / 255.0 * (1 - a) + family.veil[1] * a
                behind[i * 3 + 2] = (p and 0xFF) / 255.0 * (1 - a) + family.veil[2] * a
                i++
            }
        }
        val contrasts = DoubleArray(n)
        for ((colour, opacity, needed) in family.texts) {
            for (j in 0 until n) {
                val r = behind[j * 3]
                val g = behind[j * 3 + 1]
                val b = behind[j * 3 + 2]
                // Text at partial opacity mixes with what's behind it.
                val lt = luminance(colour[0] * opacity + r * (1 - opacity), colour[1] * opacity + g * (1 - opacity), colour[2] * opacity + b * (1 - opacity))
                val lb = luminance(r, g, b)
                contrasts[j] = (max(lt, lb) + 0.05) / (min(lt, lb) + 0.05)
            }
            contrasts.sort()
            // The worst 3% count.
            if (contrasts[(n * 0.03).toInt()] < needed) return false
        }
        return true
    }

    private fun gradient(stops: List<Pair<Double, Double>>, at: Double): Double {
        for (s in 1 until stops.size) {
            val (x1, y1) = stops[s]
            if (at <= x1) {
                val (x0, y0) = stops[s - 1]
                return y0 + (y1 - y0) * ((at - x0) / (x1 - x0))
            }
        }
        return stops.last().second
    }

    private fun channel(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(r: Double, g: Double, b: Double): Double =
        0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}
