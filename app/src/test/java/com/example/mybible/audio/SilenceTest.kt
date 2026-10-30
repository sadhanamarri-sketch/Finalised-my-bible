package com.example.mybible.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.sqrt

/** Backlog 52: the app's silence between verses is a hiss far below hearing, never exact silence. */
class SilenceTest {
    @Test
    fun theHissIsFarBelowHearingButNeverExactSilence() {
        val hiss = Silence.hiss(Silence.CHUNK)
        assertEquals(Silence.CHUNK, hiss.size)
        val rms = sqrt(hiss.sumOf { it.toDouble() * it } / hiss.size)
        val db = 20 * log10(rms / 32767)
        assertEquals(-72.0, db, 1.5)
        assertTrue(hiss.all { kotlin.math.abs(it.toInt()) <= Silence.HISS })
        // No stretch of exact silence an earbud could take for it (not even 10 ms of zeros).
        val longestZeros = hiss.fold(0 to 0) { (run, longest), s -> if (s.toInt() == 0) (run + 1).let { it to maxOf(longest, it) } else 0 to longest }.second
        assertTrue("longest run of zeros $longestZeros", longestZeros < Silence.SAMPLE_RATE / 100)
        // Centred on nothing: no steady push either way.
        assertEquals(0.0, hiss.average(), 1.0)
    }
}
