package com.example.mybible.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * How high or low a voice speaks: the pitch of a recording of it, in Hz — about 85–155 for a man's
 * speaking voice and 165–255 for a woman's. Used to sort the phone's voices from the deepest (see
 * VoiceDepths).
 */
object SpeakingPitch {

    /** A recording's sound, from -1 to 1 in one channel, and how many samples there are a second. */
    class Pcm(val samples: FloatArray, val sampleRate: Int)

    /** The typical pitch of the speech in [file], a WAV recording; null when it can't be told. */
    fun of(file: File): Float? = try {
        readWav(file.readBytes())?.let(::of)
    } catch (e: Exception) {
        null
    }

    /**
     * The typical pitch of the speech in [pcm]: for each moment loud enough to be a vowel, the period
     * its sound repeats at (where it's most like itself a little later), and the middle of those.
     */
    fun of(pcm: Pcm): Float? {
        // To about 11 kHz: plenty for a speaking voice's pitch, and much less to work through.
        val step = maxOf(1, pcm.sampleRate / 11025)
        val rate = pcm.sampleRate.toFloat() / step
        val x = FloatArray(pcm.samples.size / step) { i ->
            var sum = 0f
            for (k in 0 until step) sum += pcm.samples[i * step + k]
            sum / step
        }
        val frame = (rate * FRAME_SECONDS).toInt()
        val hop = maxOf(1, (rate * HOP_SECONDS).toInt())
        val minLag = maxOf(2, (rate / MAX_HZ).toInt())
        val maxLag = (rate / MIN_HZ).toInt()
        val last = x.size - frame - maxLag - 1
        if (last < 0) return null

        val starts = (0..last step hop).toList()
        val energies = DoubleArray(starts.size) { i ->
            var e = 0.0
            for (n in 0 until frame) e += x[starts[i] + n] * x[starts[i] + n]
            e
        }
        val loudest = energies.maxOrNull() ?: return null
        if (loudest <= 0.0) return null

        val pitches = ArrayList<Float>()
        val alike = DoubleArray(maxLag + 2)
        for ((i, start) in starts.withIndex()) {
            // The quiet moments: pauses, and sounds like s and f, which have no pitch.
            if (energies[i] < loudest * MIN_LOUDNESS) continue
            var best = 0.0
            for (lag in minLag..maxLag) {
                var xy = 0.0
                var yy = 0.0
                for (n in 0 until frame) {
                    val later = x[start + n + lag]
                    xy += x[start + n] * later
                    yy += later * later
                }
                alike[lag] = if (yy > 0.0) xy / sqrt(energies[i] * yy) else 0.0
                if (alike[lag] > best) best = alike[lag]
            }
            if (best < MIN_LIKENESS) continue
            // The shortest repeat nearly as alike as the best: the pitch itself, not an octave below.
            val lag = (minLag..maxLag).firstOrNull { lag ->
                alike[lag] >= best * NEAR_BEST &&
                    (lag == minLag || alike[lag] >= alike[lag - 1]) &&
                    (lag == maxLag || alike[lag] >= alike[lag + 1])
            } ?: continue
            pitches += rate / lag
        }
        if (pitches.size < MIN_VOWELS) return null
        pitches.sort()
        return pitches[pitches.size / 2]
    }

    /** A WAV file's sound, its channels mixed into one; null when it isn't 8- or 16-bit PCM or 32-bit float. */
    fun readWav(bytes: ByteArray): Pcm? {
        if (bytes.size < 12 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var format = 0
        var channels = 0
        var sampleRate = 0
        var bits = 0
        var dataStart = -1
        var dataSize = 0
        var at = 12
        while (at + 8 <= bytes.size) {
            val id = String(bytes, at, 4, Charsets.US_ASCII)
            val size = buffer.getInt(at + 4)
            val body = at + 8
            if (id == "fmt " && body + 16 <= bytes.size) {
                format = buffer.getShort(body).toInt() and 0xFFFF
                channels = buffer.getShort(body + 2).toInt()
                sampleRate = buffer.getInt(body + 4)
                bits = buffer.getShort(body + 14).toInt()
                // WAVE_FORMAT_EXTENSIBLE: the format is the start of its sub-format.
                if (format == 0xFFFE && body + 26 <= bytes.size) format = buffer.getShort(body + 24).toInt() and 0xFFFF
            } else if (id == "data") {
                dataStart = body
                // Written as it was spoken, a file can say its sound is 0 long or longer than it is: the rest of it.
                dataSize = if (size <= 0 || body + size > bytes.size) bytes.size - body else size
                break
            }
            if (size < 0 || body + size > bytes.size) break
            at = body + size + (size and 1)
        }
        val bytesPerSample = bits / 8
        if (dataStart < 0 || channels <= 0 || sampleRate <= 0 || bytesPerSample <= 0) return null
        val readable = (format == 1 && (bits == 8 || bits == 16)) || (format == 3 && bits == 32)
        if (!readable) return null
        val frames = dataSize / (channels * bytesPerSample)
        val samples = FloatArray(frames) { i ->
            var sum = 0f
            for (c in 0 until channels) {
                val p = dataStart + (i * channels + c) * bytesPerSample
                sum += when (bits) {
                    8 -> ((bytes[p].toInt() and 0xFF) - 128) / 128f
                    16 -> buffer.getShort(p) / 32768f
                    else -> buffer.getFloat(p)
                }
            }
            sum / channels
        }
        return Pcm(samples, sampleRate)
    }

    private const val MIN_HZ = 60f
    private const val MAX_HZ = 400f
    private const val FRAME_SECONDS = 0.04f
    private const val HOP_SECONDS = 0.01f
    // A vowel's moment is at least this loud, against the loudest.
    private const val MIN_LOUDNESS = 0.1
    // And this like itself one period on (1 is the same).
    private const val MIN_LIKENESS = 0.6
    private const val NEAR_BEST = 0.9
    // Fewer vowel moments than this, and there's too little speech to tell.
    private const val MIN_VOWELS = 8
}
