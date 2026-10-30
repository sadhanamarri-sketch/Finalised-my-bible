package com.example.mybible.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

/** Backlog 46: how loud each recording and voice is, measured once, and English and Telugu evened out. */
class EveningTest {

    // A tone [amplitude] loud, as a WAV file the phone's voice might have said: about 20·log10(amplitude) − 3 LUFS.
    private fun wav(file: File, amplitude: Double, rate: Int = 22_050, seconds: Double = 3.0) {
        val n = (rate * seconds).toInt()
        val data = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + n * 2).put("WAVE".toByteArray())
        data.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        data.put("data".toByteArray()).putInt(n * 2)
        for (i in 0 until n) data.putShort((amplitude * sin(2 * PI * 1000.0 * i / rate) * 32767).toInt().toShort())
        file.writeBytes(data.array())
    }

    private fun lufsOf(amplitude: Double) = 20 * log10(amplitude) - 3.01

    // ---- a recording's sample ----

    // Thirty chapters, three to a file; verse n of each 10n seconds in, 8 seconds long.
    private val timings = RecordingTimings.parse(
        buildString {
            append("""{"files":{""")
            append((1..10).joinToString(",") { "\"f$it.mp3\":600" })
            append("""},"chapters":{""")
            append((1..30).joinToString(",") { c ->
                val verses = (1..5).joinToString(",") { v -> "[${10.0 * v},${10.0 * v + 8}]" }
                "\"Psalms $c\":{\"file\":\"f${(c - 1) / 3 + 1}.mp3\",\"start\":5,\"end\":60,\"verses\":[$verses]}"
            })
            append("}}")
        }
    )

    @Test
    fun aRecordingIsListenedToInADozenPlacesSpreadThroughIt() {
        val all = (1..10).map { "f$it.mp3" }.toSet()
        val stretches = RecordingLoudness.stretches(timings, all)
        assertEquals(12, stretches.size)
        // From the first chapter to the last, each from its second verse (not its heading), 20 seconds or to its last verse.
        assertEquals(RecordingLoudness.Stretch("f1.mp3", 20.0, 40.0), stretches.first())
        assertEquals(RecordingLoudness.Stretch("f10.mp3", 20.0, 40.0), stretches.last())
        assertEquals(RecordingLoudness.Stretch("f1.mp3", 20.0, 30.0), RecordingLoudness.stretches(timings, all, count = 1, seconds = 10.0).single())
        // Only the files there.
        assertEquals(setOf("f2.mp3"), RecordingLoudness.stretches(timings, setOf("f2.mp3")).map { it.file }.toSet())
        assertEquals(3, RecordingLoudness.stretches(timings, setOf("f2.mp3")).size)
        assertTrue(RecordingLoudness.stretches(timings, emptySet()).isEmpty())
    }

    @Test
    fun aRecordingsLoudnessIsWhatItsStretchesSoundLikeTogether() {
        val stretches = RecordingLoudness.stretches(timings, (1..10).map { "f$it.mp3" }.toSet())
        val read = mutableListOf<String>()
        val lufs = RecordingLoudness.measure(stretches) { stretch, onPcm ->
            read += stretch.file
            // One file won't read; the rest are a tone at 0.1 at 44.1 kHz, in two pieces each.
            if (stretch.file == "f5.mp3") return@measure false
            val rate = 44_100
            val tone = FloatArray(rate * 2) { i -> (0.1 * sin(2 * PI * 1000.0 * i / rate)).toFloat() }
            onPcm(tone.copyOfRange(0, rate), rate)
            onPcm(tone.copyOfRange(rate, rate * 2), rate)
            true
        }
        assertEquals(12, read.size)
        assertEquals(lufsOf(0.1), lufs!!, 0.1)
        assertNull(RecordingLoudness.measure(stretches) { _, _ -> false })
    }

    // ---- the phone's voices ----

    // Says each sample into its file as a tone, louder or quieter by voice; a voice it can't do fails.
    private class VoiceRecorder(val amplitudes: Map<String, Double>, val write: (File, Double) -> Unit) : Speaker {
        override var listener: Speaker.Listener? = null
        override val ready: Boolean? = true
        val recorded = mutableListOf<String>()
        var done = 0

        override fun hasTelugu() = true
        override fun speak(id: String, text: String, telugu: Boolean, rate: Float, voice: String?, pitch: Float) = Unit
        override fun stop() = Unit
        override fun voices(telugu: Boolean) = emptyList<VoiceOption>()

        override fun recordAsRead(text: String, telugu: Boolean, voice: String?, file: File, onSaid: (Boolean) -> Unit) {
            val name = voice ?: if (telugu) "te" else "en"
            recorded += name
            val amplitude = amplitudes[name] ?: return onSaid(false)
            write(file, amplitude)
            onSaid(true)
        }

        override fun doneRecordingAsRead() {
            done++
        }
    }

    private val workDir: File = Files.createTempDirectory("loudness").toFile().apply { deleteOnExit() }
    private val unconfined = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun theVoicesReadInAreMeasuredOnceAndKept() {
        val speaker = VoiceRecorder(mapOf("en-us-x-iol-local" to 0.3, "te" to 0.1)) { file, a -> wav(file, a) }
        var saved = emptyMap<String, String>()
        val loudness = VoiceLoudness(save = { saved = it }, workDir = workDir, compute = Dispatchers.Unconfined)
        val english = VoiceLoudness.Voice(telugu = false, engine = null, name = "en-us-x-iol-local")
        val telugu = VoiceLoudness.Voice(telugu = true, engine = "com.example.ai", name = null)
        val broken = VoiceLoudness.Voice(telugu = false, engine = null, name = "en-gb-x-rjs-network")
        assertTrue(loudness.wanted(listOf(english, telugu)))
        var then = 0
        loudness.measure(speaker, listOf(english, telugu, broken, english), unconfined) { then++ }

        // Each once, the one that couldn't be said left unknown; told when done.
        assertEquals(listOf("en-us-x-iol-local", "te", "en-gb-x-rjs-network"), speaker.recorded)
        assertEquals(lufsOf(0.3), loudness.of(english)!!, 0.1)
        assertEquals(lufsOf(0.1), loudness.of(telugu)!!, 0.1)
        assertNull(loudness.of(broken))
        assertEquals(1, speaker.done)
        assertEquals(1, then)
        assertFalse(loudness.measuring)
        assertEquals(setOf("usual|en-us-x-iol-local", "com.example.ai|te"), saved.keys)

        // Not again: kept, and the failed one left until the app starts again.
        assertFalse(loudness.wanted(listOf(english, telugu, broken)))
        loudness.measure(speaker, listOf(english, telugu, broken), unconfined) { then++ }
        assertEquals(3, speaker.recorded.size)
        assertEquals(2, then)
        val again = VoiceLoudness(saved = saved, workDir = workDir, compute = Dispatchers.Unconfined)
        assertEquals(lufsOf(0.3), again.of(english)!!, 0.1)
        assertTrue(again.wanted(listOf(broken)))
    }

    // ---- reading, evened out ----

    private class FakeSpeaker : Speaker {
        override var listener: Speaker.Listener? = null
        override val ready: Boolean? = true
        val said = mutableListOf<Pair<String, Boolean>>()
        val levels = mutableListOf<Double>()
        var write: ((File) -> Unit)? = null

        override fun hasTelugu() = true
        override fun speak(id: String, text: String, telugu: Boolean, rate: Float, voice: String?, pitch: Float) {
            said += id to telugu
        }
        override fun stop() = Unit
        override fun voices(telugu: Boolean) = emptyList<VoiceOption>()
        override fun level(db: Double) {
            levels += db
        }
        override fun recordAsRead(text: String, telugu: Boolean, voice: String?, file: File, onSaid: (Boolean) -> Unit) {
            val w = write ?: return onSaid(false)
            w(file)
            onSaid(true)
        }

        fun finish() = listener!!.onDone(said.last().first)
    }

    private class FakePrefs(var english: Boolean, var telugu: Boolean) : ReadAloudPrefs {
        override val language = ReadLanguage.BOTH
        override val continueReading = false
        override fun speed(telugu: Boolean) = 1f
        override fun voice(telugu: Boolean): String? = null
        override fun recording(telugu: Boolean) = if (telugu) this.telugu else english
    }

    private class FakeRecorder : Recorder {
        override var listener: Recorder.Listener? = null
        val played = mutableListOf<Long>()
        val levels = mutableListOf<Double>()

        override fun play(id: Long, file: RecordingFile, from: Double, to: Double, speed: Float, marks: List<Double>) {
            played += id
        }
        override fun pause() = Unit
        override fun resume() = true
        override fun stop() = Unit
        override fun speed(speed: Float) = Unit
        override val position: Double? = null
        override fun level(db: Double) {
            levels += db
        }

        fun end() = listener!!.onEnded(played.last())
    }

    // Each language's recording, [loud] as measured.
    private class FakeRecordings(val loud: Map<Boolean, Double?>) : RecordedVerses {
        override fun ready(telugu: Boolean) = true
        override suspend fun verse(verse: VerseToRead, telugu: Boolean, heading: Boolean): RecordedVerse =
            RecordedVerse(RecordingFile(if (telugu) "te" else "en", MediaKind.MP3), verse.verse * 10.0, verse.verse * 10.0, verse.verse * 10.0 + 8)
        override fun loudness(telugu: Boolean): Double? = loud[telugu]
    }

    private class Verses : ReadingSession {
        override val place = ReadingPlace.READER
        override val title = "John 3"
        private val verses = (16..18).map { VerseToRead("John", 3, it, "Verse $it", "వచనము $it") }
        override suspend fun next(after: VerseToRead?): VerseToRead? =
            if (after == null) verses.first() else verses.getOrNull(verses.indexOf(after) + 1)
    }

    @Test
    fun twoRecordingsComeOutAlikeTheQuieterRaised() {
        val recorder = FakeRecorder()
        val reader = ReadAloud(FakeSpeaker(), FakePrefs(english = true, telugu = true), unconfined,
            recordings = FakeRecordings(mapOf(false to -22.0, true to -18.0)), recorder = recorder)
        reader.start(Verses())
        // Each verse in English, raised 4 dB, then in Telugu, as recorded.
        recorder.end()
        recorder.end()
        assertEquals(listOf(4.0, 0.0, 4.0), recorder.levels)
    }

    @Test
    fun notKnownYetTheyreAsRecorded() {
        val recorder = FakeRecorder()
        val reader = ReadAloud(FakeSpeaker(), FakePrefs(english = true, telugu = true), unconfined,
            recordings = FakeRecordings(mapOf(false to -22.0, true to null)), recorder = recorder)
        reader.start(Verses())
        recorder.end()
        assertEquals(listOf(0.0, 0.0), recorder.levels)
    }

    @Test
    fun theVoiceIsMeasuredAsTheReadingStartsAndEvenedOutWithTheRecording() {
        // English from its recording, at -24; Telugu in the phone's voice, a tone at 0.224 (-16).
        val speaker = FakeSpeaker().apply { write = { file -> wav(file, 0.224) } }
        val recorder = FakeRecorder()
        val voices = VoiceLoudness(workDir = workDir, compute = Dispatchers.Unconfined)
        val reader = ReadAloud(speaker, FakePrefs(english = true, telugu = false), unconfined,
            recordings = FakeRecordings(mapOf(false to -24.0, true to null)), recorder = recorder, voiceLoudness = voices)
        reader.start(Verses())
        assertEquals(-16.0, voices.of(VoiceLoudness.Voice(telugu = true, engine = null, name = null))!!, 0.1)
        // Eight apart: the recording raised the most it can be, 6, and the voice lowered the other 2.
        recorder.end()
        assertEquals(6.0, recorder.levels.single(), 0.1)
        assertEquals(-2.0, speaker.levels.single(), 0.1)
        assertEquals(true, speaker.said.single().second)
    }

    @Test
    fun aVoiceHeardOnItsOwnIsAsItIs() {
        val speaker = FakeSpeaker()
        val reader = ReadAloud(speaker, FakePrefs(english = false, telugu = false), unconfined,
            recordings = FakeRecordings(mapOf(false to -24.0, true to -16.0)), recorder = FakeRecorder())
        reader.previewVoice("Hello", telugu = false, voice = "en-us-x-iol-local")
        reader.sayWord("ah-GAH-pay")
        assertEquals(listOf(0.0, 0.0), speaker.levels)
    }
}
