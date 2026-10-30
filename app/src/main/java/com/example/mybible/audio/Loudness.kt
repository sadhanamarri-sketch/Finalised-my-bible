package com.example.mybible.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/**
 * How loud sound is to the ear, in LUFS (loudness units, full scale), measured as broadcasters measure
 * it (ITU-R BS.1770): weighed as hearing weighs it (the lowest sounds count for less), over 400 ms
 * moments, leaving out silence and the moments far quieter than the rest, so the pauses between verses
 * don't count. Spoken word on a phone is usually around -16 to -20; each LU is a decibel.
 *
 * Fed sound in one channel (from -1 to 1), a stretch at a time ([add]), with [gap] between stretches
 * from different places; [lufs] is the loudness of everything heard so far.
 */
class Loudness {
    private var rate = 0
    private var shelf = Biquad.NONE
    private var highPass = Biquad.NONE

    // The weighted sound's squares, summed over each 100 ms ([inQuarter] of them so far), and the last
    // three quarters' means: with the next, a 400 ms moment (the moments overlap by three quarters).
    private var sum = 0.0
    private var inQuarter = 0
    private val recent = ArrayDeque<Double>()

    // Each moment's mean square.
    private val moments = ArrayList<Double>()

    /** [count] samples of [samples] from [from], [rate] a second; a new rate starts a new stretch. */
    fun add(samples: FloatArray, rate: Int, from: Int = 0, count: Int = samples.size - from) {
        if (rate <= 0) return
        if (rate != this.rate) start(rate)
        val quarter = rate / 10
        for (i in from until from + count) {
            val weighed = highPass.next(shelf.next(samples[i].toDouble()))
            sum += weighed * weighed
            if (++inQuarter == quarter) {
                recent.addLast(sum / quarter)
                sum = 0.0
                inQuarter = 0
                if (recent.size == 4) {
                    moments += recent.average()
                    recent.removeFirst()
                }
            }
        }
    }

    /** What's added next is from somewhere else: no moment spans the two. */
    fun gap() {
        shelf.reset()
        highPass.reset()
        sum = 0.0
        inQuarter = 0
        recent.clear()
    }

    /** The loudness of what's been heard, in LUFS; null when there's been nothing but silence. */
    fun lufs(): Double? {
        val heard = moments.filter { lufsOf(it) > ABSOLUTE_GATE }
        if (heard.isEmpty()) return null
        val gate = lufsOf(heard.average()) - RELATIVE_GATE
        return lufsOf(heard.filter { lufsOf(it) > gate }.average())
    }

    private fun start(rate: Int) {
        this.rate = rate
        shelf = Biquad.shelf(rate)
        highPass = Biquad.highPass(rate)
        gap()
    }

    /** A second-order filter: the two the ear's weighting is made of, worked out for any sample rate. */
    internal class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun next(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = if (abs(y) < 1e-30) 0.0 else y
            return y1
        }

        fun reset() {
            x1 = 0.0
            x2 = 0.0
            y1 = 0.0
            y2 = 0.0
        }

        companion object {
            val NONE get() = Biquad(1.0, 0.0, 0.0, 0.0, 0.0)

            // The head's effect: sound above about 1.5 kHz counts for about 4 dB more.
            fun shelf(rate: Int): Biquad {
                val k = tan(PI * 1681.974450955533 / rate)
                val q = 0.7071752369554196
                val vh = 10.0.pow(3.999843853973347 / 20)
                val vb = vh.pow(0.4996667741545416)
                val a0 = 1 + k / q + k * k
                return Biquad(
                    (vh + vb * k / q + k * k) / a0,
                    2 * (k * k - vh) / a0,
                    (vh - vb * k / q + k * k) / a0,
                    2 * (k * k - 1) / a0,
                    (1 - k / q + k * k) / a0
                )
            }

            // Sound below about 60 Hz counts for little.
            fun highPass(rate: Int): Biquad {
                val k = tan(PI * 38.13547087602444 / rate)
                val q = 0.5003270373238773
                val a0 = 1 + k / q + k * k
                return Biquad(1.0, -2.0, 1.0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0)
            }
        }
    }

    companion object {
        // Moments quieter than this are silence; and quieter by this than the rest, left out too.
        private const val ABSOLUTE_GATE = -70.0
        private const val RELATIVE_GATE = 10.0

        private fun lufsOf(meanSquare: Double): Double = -0.691 + 10 * log10(meanSquare)

        /** The loudness of [pcm], a recording in one channel; null when it's silent or too short. */
        fun of(pcm: SpeakingPitch.Pcm): Double? = Loudness().apply { add(pcm.samples, pcm.sampleRate) }.lufs()
    }
}

/**
 * How much to raise (+) or lower (−) the sound of English and of Telugu, in dB, so they sound alike
 * (backlog 46): given how loud each is ([english], [telugu], in LUFS; null not known), the level they
 * meet at — the louder's, unless the quieter would be raised more than [MAX_RAISE], when the louder is
 * lowered for the rest. Null when there's nothing to even out: one not known, or the two within a
 * decibel of each other.
 */
object Evening {
    const val MAX_RAISE = 6.0
    private const val CLOSE = 1.0

    fun meet(english: Double?, telugu: Double?): Double? {
        if (english == null || telugu == null || abs(english - telugu) < CLOSE) return null
        return minOf(maxOf(english, telugu), minOf(english, telugu) + MAX_RAISE)
    }

    /** How much to raise or lower sound [loudness] loud, to come out at [meet]: never raised more than [MAX_RAISE]. */
    fun gain(meet: Double?, loudness: Double?): Double =
        if (meet == null || loudness == null) 0.0 else (meet - loudness).coerceAtMost(MAX_RAISE)
}
