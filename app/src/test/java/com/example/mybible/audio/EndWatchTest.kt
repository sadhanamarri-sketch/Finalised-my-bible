package com.example.mybible.audio

import org.junit.Assert.assertTrue
import org.junit.Test

/** A stretch that's played out without the player saying so is ended all the same; one still playing never is. */
class EndWatchTest {

    // Looks in every quarter second, from [startMs] to [untilMs], at the position [at] gives for each time.
    private fun watch(watch: EndWatch, startMs: Long, untilMs: Long, playing: (Long) -> Boolean = { true }, at: (Long) -> Long): Long? {
        var now = startMs
        while (now <= untilMs) {
            if (watch.over(now, playing(now), at(now))) return now
            now += EndWatch.EVERY_MS
        }
        return null
    }

    @Test
    fun aStretchPlayingToItsEndIsLeftToThePlayer() {
        // 8 s at 0.85x: it moves on all the way to its end.
        val w = EndWatch(lengthMs = 8_000)
        assertTrue(watch(w, 0, 9_000) { (it * 0.85).toLong() } == null)
    }

    @Test
    fun aStretchStuckJustShortOfItsEndIsOverAfterThreeSeconds() {
        // It reaches 7,980 ms of 8,000 at 9,388 ms, then goes no further.
        val w = EndWatch(lengthMs = 8_000)
        val over = watch(w, 0, 20_000) { minOf((it * 0.85).toLong(), 7_980) }
        assertTrue(over != null && over >= 9_388 + EndWatch.STILL_MS && over <= 9_388 + EndWatch.STILL_MS + 2 * EndWatch.EVERY_MS)
    }

    @Test
    fun aStretchStillFarFromItsEndIsNeverEndedForIt() {
        // Stuck halfway (a stalled sound output, say): not taken for its end.
        val w = EndWatch(lengthMs = 8_000)
        assertTrue(watch(w, 0, 30_000) { minOf(it, 4_000) } == null)
    }

    @Test
    fun pausedAtItsEndItWaits() {
        val w = EndWatch(lengthMs = 8_000)
        // Paused from 8 s to 20 s, right at its end: never over while paused.
        assertTrue(watch(w, 0, 20_000, playing = { it < 8_000 }) { minOf(it, 7_990) } == null)
        // Played again from 20 s, it's three seconds more.
        assertTrue(watch(w, 20_250, 22_750) { 7_990 } == null)
        assertTrue(w.over(23_000, true, 7_990))
    }

    @Test
    fun aLittleJitterIsStayingPut() {
        val w = EndWatch(lengthMs = 8_000)
        val over = watch(w, 0, 20_000) { if (it < 8_000) it else 7_970 + (it / 250) % 3 * 10 }
        assertTrue(over != null && over <= 8_000 + EndWatch.STILL_MS + 2 * EndWatch.EVERY_MS)
    }

    @Test
    fun aVeryShortStretchStuckAtItsEnd() {
        // Half a second long, stuck at 480 ms.
        val w = EndWatch(lengthMs = 500)
        val over = watch(w, 0, 10_000) { minOf(it, 480) }
        assertTrue(over != null && over >= 480 + EndWatch.STILL_MS)
    }
}
