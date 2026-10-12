package com.example.mybible.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudTest {

    private class Said(val id: String, val text: String, val telugu: Boolean, val rate: Float, val voice: String?)

    // Says nothing: records what it's given, and the test says when it's done.
    private class FakeSpeaker(var teluguVoice: Boolean? = true, override var ready: Boolean? = true) : Speaker {
        override var listener: Speaker.Listener? = null
        val said = mutableListOf<Said>()
        var stops = 0

        override fun hasTelugu() = teluguVoice
        override fun speak(id: String, text: String, telugu: Boolean, rate: Float, voice: String?) {
            said += Said(id, text, telugu, rate, voice)
        }
        override fun stop() { stops++ }
        override fun voices(telugu: Boolean) = emptyList<VoiceOption>()

        fun finish() = listener!!.onDone(said.last().id)
        fun fail() = listener!!.onError(said.last().id)
        fun reachWord(start: Int) = listener!!.onRange(said.last().id, start)
    }

    private class FakePrefs(
        override var language: ReadLanguage = ReadLanguage.ENGLISH,
        override var speed: Float = 1f,
        var teluguVoice: String? = null,
        override var continueReading: Boolean = false
    ) : ReadAloudPrefs {
        override fun voice(telugu: Boolean) = if (telugu) teluguVoice else null
    }

    private class Chapter(val verses: List<VerseToRead>, val from: Int = 1) : ReadingSession {
        override val place = ReadingPlace.READER
        override val title = "John 3"
        var stopped: Boolean? = null
        var paused = 0
        var resumed = 0

        override suspend fun next(after: VerseToRead?): VerseToRead? =
            if (after == null) verses.firstOrNull { it.verse == from } else verses.getOrNull(verses.indexOf(after) + 1)

        override fun onStopped(finished: Boolean) { stopped = finished }
        override fun onPaused() { paused++ }
        override fun onResumed() { resumed++ }
    }

    private val john3 = listOf(
        VerseToRead("John", 3, 16, "¶ For God so loved the world", "దేవుడు లోకమును ఎంతో ప్రేమించెను."),
        VerseToRead("John", 3, 17, "For God sent not his Son", null),
        VerseToRead("John", 3, 18, "He that believeth on him is not condemned", "ఆయనయందు విశ్వాసముంచువానికి తీర్పు తీర్చబడదు")
    )

    private fun reader(speaker: FakeSpeaker, prefs: FakePrefs = FakePrefs()) =
        ReadAloud(speaker, prefs, CoroutineScope(Dispatchers.Unconfined))

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
        val reader = reader(speaker, FakePrefs(ReadLanguage.TELUGU, speed = 0.75f, teluguVoice = "te-in-x-tef-local"))
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
