package com.example.mybible.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudTest {

    private class Said(val id: String, val text: String, val telugu: Boolean, val rate: Float, val voice: String?, val pitch: Float = 1f)

    // Says nothing: records what it's given, and the test says when it's done.
    private class FakeSpeaker(var teluguVoice: Boolean? = true, override var ready: Boolean? = true) : Speaker {
        override var listener: Speaker.Listener? = null
        val said = mutableListOf<Said>()
        var stops = 0

        override fun hasTelugu() = teluguVoice
        override fun speak(id: String, text: String, telugu: Boolean, rate: Float, voice: String?, pitch: Float) {
            said += Said(id, text, telugu, rate, voice, pitch)
        }
        val words = mutableListOf<Pair<String, java.util.Locale>>()
        var otherVoices: Boolean? = true
        override fun hasVoiceFor(locale: java.util.Locale) = otherVoices
        override fun speakIn(id: String, text: String, locale: java.util.Locale, rate: Float, pitch: Float) { words += text to locale }
        override fun stop() { stops++ }
        var voices = mapOf(
            false to listOf(VoiceOption("en-in-x-end-local", "India 1", online = false)),
            true to listOf(VoiceOption("te-in-x-tem-local", "India 1", online = false))
        )
        override fun voices(telugu: Boolean) = voices.getValue(telugu)

        fun finish() = listener!!.onDone(said.last().id)
        fun fail() = listener!!.onError(said.last().id)
        fun reachWord(start: Int) = listener!!.onRange(said.last().id, start)
    }

    // English's speed and pitch, and Telugu's when given (else the same).
    private class FakePrefs(
        override var language: ReadLanguage = ReadLanguage.ENGLISH,
        var speed: Float = 1f,
        var teluguVoice: String? = null,
        override var continueReading: Boolean = false,
        var pitch: Float = 1f,
        var teluguSpeed: Float? = null,
        var teluguPitch: Float? = null,
        var englishEngine: String? = null,
        var englishVoice: String? = null
    ) : ReadAloudPrefs {
        override fun speed(telugu: Boolean) = (if (telugu) teluguSpeed else null) ?: speed
        override fun pitch(telugu: Boolean) = (if (telugu) teluguPitch else null) ?: pitch
        override fun voice(telugu: Boolean) = if (telugu) teluguVoice else englishVoice
        override fun engine(telugu: Boolean) = if (telugu) null else englishEngine
    }

    private class Chapter(val verses: List<VerseToRead>, val from: Int = 1) : ReadingSession {
        override val place = ReadingPlace.READER
        override val title = "John 3"
        var stopped: Boolean? = null
        var paused = 0
        var resumed = 0

        override suspend fun next(after: VerseToRead?): VerseToRead? =
            if (after == null) verses.firstOrNull { it.verse == from } else verses.getOrNull(verses.indexOf(after) + 1)

        override suspend fun back(current: VerseToRead): VerseToRead? = verses.getOrNull(verses.indexOf(current) - 1)

        override fun onStopped(finished: Boolean) { stopped = finished }
        override fun onPaused() { paused++ }
        override fun onResumed() { resumed++ }
    }

    private val john3 = listOf(
        VerseToRead("John", 3, 16, "¶ For God so loved the world", "దేవుడు లోకమును ఎంతో ప్రేమించెను."),
        VerseToRead("John", 3, 17, "For God sent not his Son", null),
        VerseToRead("John", 3, 18, "He that believeth on him is not condemned", "ఆయనయందు విశ్వాసముంచువానికి తీర్పు తీర్చబడదు")
    )

    private fun reader(speaker: FakeSpeaker, prefs: FakePrefs = FakePrefs(), depths: VoiceDepths = VoiceDepths(), clock: () -> Long = { 0L }) =
        ReadAloud(speaker, prefs, CoroutineScope(Dispatchers.Unconfined), depths = depths, clock = clock)

    @Test
    fun readInTheVoiceChosenElseTheDeepestAtThePitchChosen() {
        val speaker = FakeSpeaker()
        val prefs = FakePrefs(ReadLanguage.BOTH, pitch = 0.85f)
        // Measured before: the deepest offline voice for each language.
        val depths = VoiceDepths(mapOf("deepest:en" to "en-in-x-end-local", "deepest:te" to "te-in-x-tem-local"))
        val reader = reader(speaker, prefs, depths)
        reader.start(Chapter(john3, from = 16))
        speaker.finish()
        assertEquals(listOf("en-in-x-end-local", "te-in-x-tem-local"), speaker.said.map { it.voice })
        assertTrue(speaker.said.all { it.pitch == 0.85f })
        // One chosen is read in instead: 3:17 has no Telugu, so 3:18's.
        prefs.teluguVoice = "te-in-x-tef-local"
        repeat(3) { speaker.finish() }
        assertEquals("John 3:18", reader.state.value.verse?.label)
        assertEquals(true to "te-in-x-tef-local", speaker.said.last().let { it.telugu to it.voice })
        // Nothing measured, nothing chosen: the phone's usual voice.
        assertNull(reader(FakeSpeaker()).voiceFor(telugu = false))
    }

    @Test
    fun anAiVoiceAppReadsInItsOwnVoiceUnlessOneIsChosen() {
        val depths = VoiceDepths(mapOf("deepest:en" to "en-in-x-end-local", "deepest:te" to "te-in-x-tem-local"))
        val prefs = FakePrefs(ReadLanguage.BOTH, englishEngine = "com.k2fsa.sherpa.onnx.tts.engine")
        val speaker = FakeSpeaker()
        val reader = reader(speaker, prefs, depths)
        // English by the app, in its own voice; Telugu still by the phone's usual engine, in its deepest.
        assertNull(reader.voiceFor(telugu = false))
        assertEquals("te-in-x-tem-local", reader.voiceFor(telugu = true))
        reader.start(Chapter(john3, from = 16))
        speaker.finish()
        assertEquals(listOf(false to null, true to "te-in-x-tem-local"), speaker.said.map { it.telugu to it.voice })
        // One of the app's voices, chosen: read in it.
        prefs.englishVoice = "en_US-lessac-medium"
        assertEquals("en_US-lessac-medium", reader.voiceFor(telugu = false))
        // Only the usual engine's voices are measured: Telugu's here.
        assertTrue(reader.usualVoices(telugu = false).isEmpty())
        assertEquals(listOf("te-in-x-tem-local"), reader.usualVoices(telugu = true).map { it.name })
    }

    @Test
    fun eachLanguageIsReadAtItsOwnSpeedAndPitch() {
        val speaker = FakeSpeaker()
        val prefs = FakePrefs(ReadLanguage.BOTH, speed = 1.25f, pitch = 0.85f, teluguSpeed = 0.9f, teluguPitch = 0.7f)
        val reader = reader(speaker, prefs)
        reader.start(Chapter(john3, from = 16))
        speaker.finish()
        assertEquals(listOf(Triple(false, 1.25f, 0.85f), Triple(true, 0.9f, 0.7f)), speaker.said.map { Triple(it.telugu, it.rate, it.pitch) })
        // A voice heard on its own is at its language's too.
        reader.previewVoice("దేవుడు లోకమును ఎంతో ప్రేమించెను.", telugu = true, voice = "te-in-x-tef-local")
        assertEquals(0.9f to 0.7f, speaker.said.last().let { it.rate to it.pitch })
        reader.stop()
        reader.trySpeaking("For God so loved the world.", telugu = false)
        assertEquals(1.25f to 0.85f, speaker.said.last().let { it.rate to it.pitch })
    }

    @Test
    fun aVoiceIsHeardOnItsOwnPausingWhatsBeingRead() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker, FakePrefs(pitch = 0.75f))
        reader.start(Chapter(john3, from = 16))
        reader.previewVoice("For God so loved the world.", telugu = false, voice = "en-us-x-iom-local")
        assertEquals(ReadAloudState.Status.PAUSED, reader.state.value.status)
        val heard = speaker.said.last()
        assertEquals(Triple("For God so loved the world.", "en-us-x-iom-local", 0.75f), Triple(heard.text, heard.voice, heard.pitch))
        // Its words ending isn't the verse ending.
        speaker.finish()
        assertEquals(ReadAloudState.Status.PAUSED, reader.state.value.status)
        assertEquals("John 3:16", reader.state.value.verse?.label)
    }

    @Test
    fun readsAChapterVerseByVerseAndStopsAtItsEnd() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker)
        val chapter = Chapter(john3, from = 16)
        reader.start(chapter)
        assertEquals(ReadAloudState.Status.PLAYING, reader.state.value.status)
        assertEquals("John 3:16", reader.state.value.verse?.label)
        repeat(2) { speaker.finish() }
        speaker.finish()
        assertEquals(listOf("For God so loved the world", "For God sent not his Son", "He that believeth on him is not condemned"), speaker.said.map { it.text })
        assertEquals(ReadAloudState.Status.IDLE, reader.state.value.status)
        assertEquals(true, chapter.stopped)
        assertNull(reader.session)
    }

    @Test
    fun bothReadsEachVerseInEnglishThenTelugu() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker, FakePrefs(ReadLanguage.BOTH))
        reader.start(Chapter(john3, from = 16))
        assertFalse(reader.state.value.telugu)
        speaker.finish()
        assertTrue(reader.state.value.telugu)
        assertEquals("John 3:16", reader.state.value.verse?.label)
        repeat(3) { speaker.finish() }
        // 3:17 has no Telugu: just its English.
        assertEquals(listOf(false, true, false, false, true), speaker.said.map { it.telugu })
        assertEquals("John 3:18", reader.state.value.verse?.label)
    }

    @Test
    fun teluguAloneLeavesOutAVerseWithoutTeluguAndNeedsATeluguVoice() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker, FakePrefs(ReadLanguage.TELUGU))
        reader.start(Chapter(john3, from = 16))
        speaker.finish()
        assertEquals(listOf("దేవుడు లోకమును ఎంతో ప్రేమించెను.", "ఆయనయందు విశ్వాసముంచువానికి తీర్పు తీర్చబడదు"), speaker.said.map { it.text })
        assertEquals("John 3:18", reader.state.value.verse?.label)
        assertTrue(speaker.said.all { it.telugu })

        val silent = FakeSpeaker(teluguVoice = false)
        val chapter = Chapter(john3, from = 16)
        reader(silent, FakePrefs(ReadLanguage.TELUGU)).also { r ->
            r.start(chapter)
            assertEquals(ReadAloudState.Status.IDLE, r.state.value.status)
            assertTrue(r.state.value.noTeluguVoice)
        }
        assertTrue(silent.said.isEmpty())
        assertEquals(false, chapter.stopped)
    }

    @Test
    fun withoutATeluguVoiceBothReadsTheEnglish() {
        val speaker = FakeSpeaker(teluguVoice = false)
        val reader = reader(speaker, FakePrefs(ReadLanguage.BOTH))
        reader.start(Chapter(john3, from = 16))
        speaker.finish()
        assertEquals(listOf(false, false), speaker.said.map { it.telugu })
        assertTrue(reader.state.value.noTeluguVoice)
    }

    @Test
    fun aTeluguVoiceThatFailsIsLeftOutFromThenOn() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker, FakePrefs(ReadLanguage.BOTH))
        reader.start(Chapter(john3, from = 16))
        speaker.finish()
        speaker.fail()
        // On to 3:17's English, and 3:18's English alone.
        speaker.finish()
        assertEquals(listOf(false, true, false, false), speaker.said.map { it.telugu })
        assertTrue(reader.state.value.noTeluguVoice)
        assertEquals(ReadAloudState.Status.PLAYING, reader.state.value.status)
    }

    @Test
    fun aPausePicksUpAtTheWordItGotTo() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker)
        val chapter = Chapter(john3, from = 16)
        reader.start(chapter)
        speaker.reachWord(4)
        val before = speaker.said.last().id
        reader.pause()
        assertEquals(ReadAloudState.Status.PAUSED, reader.state.value.status)
        assertEquals(1, chapter.paused)
        // What the voice says about the verse it was stopped in counts for nothing.
        speaker.listener!!.onDone(before)
        assertEquals(1, speaker.said.size)
        reader.resume()
        assertEquals("God so loved the world", speaker.said.last().text)
        assertEquals(1, chapter.resumed)
        speaker.reachWord(7)
        reader.pause()
        reader.resume()
        assertEquals("loved the world", speaker.said.last().text)
        speaker.finish()
        assertEquals("For God sent not his Son", speaker.said.last().text)
    }

    @Test
    fun aNewSpeedStartsAgainFromTheWordItGotTo() {
        val speaker = FakeSpeaker()
        val prefs = FakePrefs()
        val reader = reader(speaker, prefs)
        reader.start(Chapter(john3, from = 16))
        speaker.reachWord(8)
        prefs.speed = 1.5f
        reader.refresh()
        assertEquals("so loved the world", speaker.said.last().text)
        assertEquals(1.5f, speaker.said.last().rate)
    }

    @Test
    fun aHeadsetsNextGoesOnToTheNextVerse() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker, FakePrefs(ReadLanguage.BOTH))
        val chapter = Chapter(john3, from = 16)
        reader.start(chapter)
        speaker.reachWord(8)
        reader.skipToNext()
        // 3:17 from its start, the rest of 3:16 and its Telugu left out; what the voice says of 3:16 counts for nothing.
        assertEquals("John 3:17", reader.state.value.verse?.label)
        assertEquals(false to "For God sent not his Son", speaker.said.last().let { it.telugu to it.text })
        speaker.listener!!.onDone(speaker.said[0].id)
        assertEquals("John 3:17", reader.state.value.verse?.label)
        // Paused, it moves on and waits there.
        reader.pause()
        val said = speaker.said.size
        reader.skipToNext()
        assertEquals("John 3:18", reader.state.value.verse?.label)
        assertEquals(ReadAloudState.Status.PAUSED, reader.state.value.status)
        assertEquals(said, speaker.said.size)
        reader.resume()
        assertEquals("He that believeth on him is not condemned", speaker.said.last().text)
        // Past the last verse, the reading ends, as at the chapter's end.
        reader.skipToNext()
        assertEquals(ReadAloudState.Status.IDLE, reader.state.value.status)
        assertEquals(true, chapter.stopped)
    }

    @Test
    fun aHeadsetsPreviousStartsTheVerseAgainOrGoesBack() {
        val speaker = FakeSpeaker()
        var now = 0L
        val reader = reader(speaker, clock = { now })
        reader.start(Chapter(john3, from = 17))
        speaker.reachWord(8)
        // Well into 3:17: it starts again.
        now += 5_000
        reader.skipToPrevious()
        assertEquals("John 3:17", reader.state.value.verse?.label)
        assertEquals("For God sent not his Son", speaker.said.last().text)
        // Pressed again straight away: back to 3:16.
        now += 500
        reader.skipToPrevious()
        assertEquals("John 3:16", reader.state.value.verse?.label)
        assertEquals("For God so loved the world", speaker.said.last().text)
        // Nothing before the first verse: it starts again.
        now += 500
        val said = speaker.said.size
        reader.skipToPrevious()
        assertEquals("John 3:16", reader.state.value.verse?.label)
        assertEquals(said + 1, speaker.said.size)
        assertEquals(ReadAloudState.Status.PLAYING, reader.state.value.status)
    }

    @Test
    fun theReadersChapterGoesBackEvenBeforeTheVerseItStartedFrom() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker)
        reader.start(ChapterReading("John", 3, from = 17) { john3 })
        reader.skipToPrevious()
        assertEquals("John 3:16", reader.state.value.verse?.label)
        repeat(2) { reader.skipToNext() }
        assertEquals("John 3:18", reader.state.value.verse?.label)
        assertEquals("He that believeth on him is not condemned", speaker.said.last().text)
    }

    @Test
    fun holdingPreviousReadsTheChapterFromItsFirstVerse() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker)
        reader.start(ChapterReading("John", 3, from = 18) { john3 })
        reader.skipToStart()
        assertEquals("John 3:16", reader.state.value.verse?.label)
        assertEquals("For God so loved the world", speaker.said.last().text)
        // Paused, it goes there and waits.
        speaker.finish()
        reader.pause()
        reader.skipToStart()
        assertEquals("John 3:16", reader.state.value.verse?.label)
        assertEquals(ReadAloudState.Status.PAUSED, reader.state.value.status)
        // Read in Telugu, a first verse without any: on to the first with it.
        val telugu = FakeSpeaker()
        val inTelugu = reader(telugu, FakePrefs(ReadLanguage.TELUGU))
        inTelugu.start(ChapterReading("John", 3, from = 18) { john3.drop(1) })
        inTelugu.skipToStart()
        assertEquals("John 3:18", inTelugu.state.value.verse?.label)
        assertEquals(ReadAloudState.Status.PLAYING, inTelugu.state.value.status)
    }

    @Test
    fun startingAnotherReadingStopsTheFirst() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker)
        val first = Chapter(john3, from = 16)
        val second = Chapter(john3, from = 18)
        reader.start(first)
        reader.start(second)
        assertEquals(false, first.stopped)
        assertNull(second.stopped)
        assertEquals("He that believeth on him is not condemned", speaker.said.last().text)
        reader.stop()
        assertEquals(false, second.stopped)
        assertEquals(ReadAloudState.Status.IDLE, reader.state.value.status)
    }

    @Test
    fun aPhoneWithNoVoiceSaysSo() {
        val chapter = Chapter(john3)
        val reader = reader(FakeSpeaker(ready = false))
        reader.start(chapter)
        assertTrue(reader.state.value.noVoice)
        assertEquals(false, chapter.stopped)

        val speaker = FakeSpeaker()
        val failing = reader(speaker)
        val again = Chapter(john3, from = 16)
        failing.start(again)
        speaker.fail()
        assertTrue(failing.state.value.noVoice)
        assertEquals(ReadAloudState.Status.IDLE, failing.state.value.status)
        assertEquals(false, again.stopped)
    }

    @Test
    fun theChosenVoiceAndSpeedAreUsed() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker, FakePrefs(ReadLanguage.TELUGU, teluguSpeed = 0.75f, teluguVoice = "te-in-x-tef-local"))
        reader.start(Chapter(john3, from = 16))
        assertEquals("te-in-x-tef-local", speaker.said.last().voice)
        assertEquals(0.75f, speaker.said.last().rate)
    }

    @Test
    fun theReadersChapterGoesFromAVerseToItsEnd() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker)
        var loads = 0
        val chapter = ChapterReading("John", 3, from = 17) { loads++; john3 }
        reader.start(chapter)
        assertEquals("John 3:17", reader.state.value.verse?.label)
        assertEquals("John 3", reader.state.value.title)
        assertEquals(ReadingPlace.READER, reader.state.value.place)
        speaker.finish()
        speaker.finish()
        assertEquals(ReadAloudState.Status.IDLE, reader.state.value.status)
        assertEquals(listOf("For God sent not his Son", "He that believeth on him is not condemned"), speaker.said.map { it.text })
        assertEquals(1, loads)
    }

    @Test
    fun aGreekOrHebrewWordIsSaidInItsOwnVoicePausingAReading() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker)
        val chapter = Chapter(john3, from = 16)
        reader.start(chapter)
        val greek = java.util.Locale("el", "GR")
        assertEquals(true, reader.sayWord("ἀγάπη", greek))
        assertEquals(ReadAloudState.Status.PAUSED, reader.state.value.status)
        assertEquals(1, chapter.paused)
        assertEquals(listOf("ἀγάπη" to greek), speaker.words)
        // No Hebrew voice on the phone: nothing said, and the page is told so.
        speaker.otherVoices = false
        assertEquals(false, reader.sayWord("חֶסֶד", java.util.Locale("he", "IL")))
        assertEquals(1, speaker.words.size)
    }

    @Test
    fun versesAreSaidAsWordsNotMarks() {
        assertEquals("And the Lord said unto Moses", SpeechText.english("¶ And the LORD said unto  Moses"))
        assertEquals("I Am That I Am", SpeechText.english("I AM THAT I AM"))
        assertEquals("O Lord God", SpeechText.english("O LORD GOD"))
        assertEquals("ఇశ్రాయేలీయులందరును ఆర్బా టము చేయుచు", SpeechText.telugu("ఇశ్రాయేలీయులందరును ఆర్బా éటము చేయుచు"))
        assertEquals("వచ్చినషాపాతు", SpeechText.telugu("వచ్చిన1షాపాతు"))
        assertEquals("అతడిట్లనెను, నేను", SpeechText.telugu("అతడిట్లనెను–నేను"))
        assertEquals("సెలవిచ్చెను, నా", SpeechText.telugu("సెలవిచ్చెను - నా"))
    }
}
