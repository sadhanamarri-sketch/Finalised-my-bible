package com.example.mybible.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Backlog 46: how loud a recording or a voice is, as the ear hears it (ITU-R BS.1770). */
class LoudnessTest {

    private fun tone(hz: Double, amplitude: Double, seconds: Double, rate: Int) =
        FloatArray((seconds * rate).toInt()) { i -> (amplitude * sin(2 * PI * hz * i / rate)).toFloat() }

    private fun silence(seconds: Double, rate: Int) = FloatArray((seconds * rate).toInt())

    private fun lufs(rate: Int, vararg parts: FloatArray): Double? {
        val loudness = Loudness()
        parts.forEach { loudness.add(it, rate) }
        return loudness.lufs()
    }

    @Test
    fun theEarsWeightingIsTheStandardOneAt48kHz() {
        val shelf = Loudness.Biquad.shelf(48_000)
        assertEquals(1.53512485958697, shelf.b0, 1e-9)
        assertEquals(-2.69169618940638, shelf.b1, 1e-9)
        assertEquals(1.19839281085285, shelf.b2, 1e-9)
        assertEquals(-1.69065929318241, shelf.a1, 1e-9)
        assertEquals(0.73248077421585, shelf.a2, 1e-9)
        val highPass = Loudness.Biquad.highPass(48_000)
        assertEquals(-1.99004745483398, highPass.a1, 1e-9)
        assertEquals(0.99007225036621, highPass.a2, 1e-9)
    }

    @Test
    fun aToneAt20DecibelsBelowFullScaleIsMinus23AtEverySampleRate() {
        // The standard's own check: a 1 kHz tone at full scale in one channel is -3.01 LUFS.
        for (rate in listOf(48_000, 44_100, 24_000, 22_050, 16_000)) {
            assertEquals("at $rate", -23.01, lufs(rate, tone(1000.0, 0.1, 5.0, rate))!!, 0.1)
        }
    }

    @Test
    fun twiceAsLoudIsSixMore() {
        val quiet = lufs(22_050, tone(500.0, 0.1, 4.0, 22_050))!!
        val loud = lufs(22_050, tone(500.0, 0.2, 4.0, 22_050))!!
        assertEquals(6.02, loud - quiet, 0.05)
    }

    @Test
    fun silenceAndTheQuietestMomentsDontCount() {
        val rate = 16_000
        val alone = lufs(rate, tone(1000.0, 0.1, 3.0, rate))!!
        // The pauses between verses: left out (but for the moments across the end of the sound, partly each).
        assertEquals(alone, lufs(rate, tone(1000.0, 0.1, 3.0, rate), silence(6.0, rate))!!, 0.3)
        // A murmur 20 dB under the rest: left out too.
        assertEquals(alone, lufs(rate, tone(1000.0, 0.1, 3.0, rate), tone(1000.0, 0.01, 3.0, rate))!!, 0.3)
        // Only silence, or too short for a moment: not known.
        assertNull(lufs(rate, silence(3.0, rate)))
        assertNull(lufs(rate, tone(1000.0, 0.1, 0.3, rate)))
    }

    @Test
    fun stretchesFromDifferentPlacesAddUp() {
        val loudness = Loudness()
        loudness.add(tone(1000.0, 0.1, 3.0, 44_100), 44_100)
        loudness.gap()
        loudness.add(tone(1000.0, 0.1, 3.0, 22_050), 22_050)
        assertEquals(-23.01, loudness.lufs()!!, 0.1)
    }

    @Test
    fun theQuieterIsRaisedToTheLouderAsFarAsItCanBe() {
        // A recording at -22 and a voice at -18: the recording raised 4 dB, the voice as it is.
        val meet = Evening.meet(english = -22.0, telugu = -18.0)
        assertEquals(-18.0, meet!!, 1e-9)
        assertEquals(4.0, Evening.gain(meet, -22.0), 1e-9)
        assertEquals(0.0, Evening.gain(meet, -18.0), 1e-9)
        // Ten apart: the quieter raised 6, the louder lowered 4, both at -20.
        val far = Evening.meet(english = -14.0, telugu = -24.0)
        assertEquals(-18.0, far!!, 1e-9)
        assertEquals(-4.0, Evening.gain(far, -14.0), 1e-9)
        assertEquals(6.0, Evening.gain(far, -24.0), 1e-9)
        // Within a decibel, or one not known: as they are.
        assertNull(Evening.meet(-18.0, -18.5))
        assertNull(Evening.meet(null, -18.0))
        assertEquals(0.0, Evening.gain(null, -22.0), 1e-9)
        assertEquals(0.0, Evening.gain(-18.0, null), 1e-9)
    }
}
