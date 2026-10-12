package com.example.mybible.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VoiceDepthsTest {

    // Says each sample into its file as the voice's name; a voice it can't do fails.
    private class Recorder(val broken: Set<String> = emptySet()) : Speaker {
        override var listener: Speaker.Listener? = null
        override val ready: Boolean? = true
        val recorded = mutableListOf<Pair<String, String>>()
        var done = 0

        override fun hasTelugu() = true
        override fun speak(id: String, text: String, telugu: Boolean, rate: Float, voice: String?, pitch: Float) = Unit
        override fun hasVoiceFor(locale: java.util.Locale) = true
        override fun speakIn(id: String, text: String, locale: java.util.Locale, rate: Float, pitch: Float) = Unit
        override fun stop() = Unit
        override fun voices(telugu: Boolean) = emptyList<VoiceOption>()

        override fun record(text: String, voice: String, file: File, onSaid: (Boolean) -> Unit) {
            recorded += voice to text
            if (voice in broken) return onSaid(false)
            file.writeText(voice)
            onSaid(true)
        }

        override fun doneRecording() {
            done++
        }
    }

    // How deep each voice "is": read back from what was said into its file.
    private val pitches = mapOf("en-in-x-ena-local" to 212f, "en-in-x-end-local" to 112f, "en-us-x-iol-local" to 128f, "te-in-x-tem-local" to 205f)
    private fun pitchOf(file: File): Float? = pitches[file.readText()]

    private val workDir: File = Files.createTempDirectory("voices").toFile().apply { deleteOnExit() }
    private val unconfined = CoroutineScope(Dispatchers.Unconfined)

    private fun depths(saved: Map<String, String> = emptyMap(), save: (Map<String, String>) -> Unit = {}) =
        VoiceDepths(saved, save, workDir, ::pitchOf, Dispatchers.Unconfined)

    private val english = listOf(
        VoiceOption("en-in-x-ena-local", "India 1", online = false),
        VoiceOption("en-in-x-end-local", "India 2", online = false),
        VoiceOption("en-gb-x-rjs-network", "United Kingdom 1", online = true),
        VoiceOption("en-us-x-iol-local", "United States 1", online = false)
    )
    private val telugu = listOf(VoiceOption("te-in-x-tem-local", "India 1", online = false))

    @Test
    fun eachOfflineVoiceIsMeasuredOnceAndTheDeepestReadIn() {
        val recorder = Recorder()
        var saved = emptyMap<String, String>()
        val depths = depths(save = { saved = it })
        assertTrue(depths.unmeasured(english))
        depths.measure(recorder, english, telugu, unconfined) { true }

        // Each offline voice, in its language's words; not the online one.
        assertEquals(listOf("en-in-x-ena-local", "en-in-x-end-local", "en-us-x-iol-local", "te-in-x-tem-local"), recorder.recorded.map { it.first })
        assertEquals(VoiceDepths.TELUGU_SAMPLE, recorder.recorded.last().second)
        assertEquals(1, recorder.done)
        assertFalse(depths.measuring.value)
        assertEquals(4, depths.changes.value)

        // The deepest first, the one not measured last.
        val sorted = depths.sorted(english)
        assertEquals(listOf("India 2", "United States 1", "India 1", "United Kingdom 1"), sorted.map { it.label })
        assertEquals(listOf(112f, 128f, 212f, null), sorted.map { it.depth })
        assertEquals("en-in-x-end-local", depths.deepest(telugu = false))
        assertEquals("te-in-x-tem-local", depths.deepest(telugu = true))
        assertFalse(depths.unmeasured(english + telugu))

        // Kept: measured again from what was saved, nothing is said again.
        val again = depths(saved)
        assertEquals("en-in-x-end-local", again.deepest(telugu = false))
        again.measure(recorder, english, telugu, unconfined) { true }
        assertEquals(4, recorder.recorded.size)
        // No samples left behind.
        assertTrue(workDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aReadingStartingStopsItToGoOnLater() {
        val recorder = Recorder()
        val depths = depths()
        var playing = false
        depths.measure(recorder, english, telugu, unconfined) { !playing.also { playing = true } }
        assertEquals(1, recorder.recorded.size)
        assertFalse(depths.measuring.value)
        assertTrue(depths.unmeasured(english))
        // Later, nothing playing: the rest.
        depths.measure(recorder, english, telugu, unconfined) { true }
        assertEquals(4, recorder.recorded.size)
    }

    @Test
    fun aVoiceThatCantBeMeasuredIsntTriedAgainOrReadIn() {
        val recorder = Recorder(broken = setOf("en-in-x-end-local"))
        val depths = depths()
        depths.measure(recorder, english, telugu, unconfined) { true }
        assertNull(depths.depthOf(english[1]))
        assertEquals("en-us-x-iol-local", depths.deepest(telugu = false))
        // Last, as it was, with those not measured.
        assertEquals(listOf("United States 1", "India 1", "India 2", "United Kingdom 1"), depths.sorted(english).map { it.label })
        depths.measure(recorder, english, telugu, unconfined) { true }
        assertEquals(4, recorder.recorded.size)
    }

    @Test
    fun howDeepAVoiceSounds() {
        assertEquals("Deep", VoiceDepths.describe(112f))
        assertEquals("Medium", VoiceDepths.describe(170f))
        assertEquals("High", VoiceDepths.describe(212f))
        // Nowhere to record: nothing measured.
        val nowhere = VoiceDepths()
        nowhere.measure(Recorder(), english, telugu, unconfined) { true }
        assertFalse(nowhere.canMeasure)
        assertNull(nowhere.deepest(telugu = false))
    }
}
