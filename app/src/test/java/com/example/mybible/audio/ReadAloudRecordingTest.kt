package com.example.mybible.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading aloud played from the narrated recordings on the phone, with the phone's voice where they've nothing. */
class ReadAloudRecordingTest {

    private class Said(val id: String, val text: String, val telugu: Boolean)

    private class FakeSpeaker(var teluguVoice: Boolean? = true, override var ready: Boolean? = true) : Speaker {
        override var listener: Speaker.Listener? = null
        val said = mutableListOf<Said>()

        override fun hasTelugu() = teluguVoice
        override fun speak(id: String, text: String, telugu: Boolean, rate: Float, voice: String?, pitch: Float) {
            said += Said(id, text, telugu)
        }
        override fun stop() {}
        override fun voices(telugu: Boolean) = emptyList<VoiceOption>()

        fun finish() = listener!!.onDone(said.last().id)
    }

    private class FakePrefs(
        override var language: ReadLanguage = ReadLanguage.ENGLISH,
        var english: Boolean = true,
        var telugu: Boolean = true,
        var englishSpeed: Float = 1f,
        override var continueReading: Boolean = false
    ) : ReadAloudPrefs {
        override fun speed(telugu: Boolean) = if (telugu) 1f else englishSpeed
        override fun voice(telugu: Boolean): String? = null
        override fun recording(telugu: Boolean) = if (telugu) this.telugu else english
    }

    private class Played(val id: Long, val file: RecordingFile, val from: Double, val to: Double, val speed: Float, val marks: List<Double>)

    // Plays nothing: records what it's given, and the test says when a mark is reached or it's ended.
    private class FakeRecorder : Recorder {
        override var listener: Recorder.Listener? = null
        val played = mutableListOf<Played>()
        var paused = false
        var stops = 0
        var at: Double? = null
        var speed = 1f
        var canResume = true

        override fun play(id: Long, file: RecordingFile, from: Double, to: Double, speed: Float, marks: List<Double>) {
            played += Played(id, file, from, to, speed, marks)
            at = from
            paused = false
            this.speed = speed
        }
        override fun pause() { paused = true }
        override fun resume(): Boolean {
            if (!canResume || at == null) return false
            paused = false
            return true
        }
        override fun stop() {
            stops++
            at = null
        }
        override fun speed(speed: Float) { this.speed = speed }
        override val position: Double? get() = at

        fun reach(mark: Int) {
            val last = played.last()
            at = last.marks[mark]
            listener!!.onReached(last.id, last.marks[mark])
        }
        fun end() {
            at = null
            listener!!.onEnded(played.last().id)
        }
        fun fail() = listener!!.onFailed(played.last().id)
    }

    // English verse n of a chapter starts 10n seconds into its chapter's file and ends 8 s later; Telugu
    // the same in its book's file. A chapter's first verse from the heading starts at 0.
    private class FakeRecordings(var english: Boolean = true, var telugu: Boolean = true, val missing: Set<Int> = emptySet()) : RecordedVerses {
        val failed = mutableListOf<RecordingFile>()

        override fun ready(telugu: Boolean) = if (telugu) this.telugu else english
        override suspend fun verse(verse: VerseToRead, telugu: Boolean, heading: Boolean): RecordedVerse? {
            if (!ready(telugu) || verse.verse in missing) return null
            val file = RecordingFile(if (telugu) "te/${verse.book}" else "en/${verse.book} ${verse.chapter}", MediaKind.MP3)
            if (file in failed) return null
            val start = verse.verse * 10.0
            return RecordedVerse(file, if (heading && verse.verse == 1) 0.0 else start - 0.04, start, start + 8)
        }
        override fun failed(file: RecordingFile) { failed += file }
    }

    private class Chapter(val verses: List<VerseToRead>, val from: Int, override val place: ReadingPlace = ReadingPlace.READER) : ReadingSession {
        override val title = "John 3"
        var stopped: Boolean? = null
        var nexts = 0

        override suspend fun next(after: VerseToRead?): VerseToRead? {
            nexts++
            return if (after == null) verses.firstOrNull { it.verse == from } else verses.getOrNull(verses.indexOf(after) + 1)
        }
        override suspend fun back(current: VerseToRead): VerseToRead? = verses.getOrNull(verses.indexOf(current) - 1)
        override suspend fun verse(book: String, chapter: Int, verse: Int): VerseToRead? =
            verses.firstOrNull { it.book == book && it.chapter == chapter && it.verse == verse }
        override suspend fun following(verse: VerseToRead): List<VerseToRead> = verses.drop(verses.indexOf(verse) + 1)
        override fun onStopped(finished: Boolean) { stopped = finished }
    }

    private val john3 = listOf(
        VerseToRead("John", 3, 15, "That whosoever believeth in him should not perish", "ఆయనయందు విశ్వాసముంచు ప్రతివాడును"),
        VerseToRead("John", 3, 16, "¶ For God so loved the world", "దేవుడు లోకమును ఎంతో ప్రేమించెను."),
        VerseToRead("John", 3, 17, "For God sent not his Son", null),
        VerseToRead("John", 3, 18, "He that believeth on him is not condemned", "ఆయనయందు విశ్వాసముంచువానికి తీర్పు తీర్చబడదు")
    )

    private val genesis1 = (1..3).map { VerseToRead("Genesis", 1, it, "Verse $it", "వచనము $it") }

    private fun reader(speaker: FakeSpeaker, prefs: FakePrefs, recordings: FakeRecordings, recorder: FakeRecorder, clock: () -> Long = { 0L }) =
        ReadAloud(speaker, prefs, CoroutineScope(Dispatchers.Unconfined), clock = clock, recordings = recordings, recorder = recorder)

    @Test
    fun aChapterPlaysOnFromOneVerseToTheNextWithoutABreak() {
        val speaker = FakeSpeaker()
        val recorder = FakeRecorder()
        val reader = reader(speaker, FakePrefs(englishSpeed = 1.25f), FakeRecordings(), recorder)
        val chapter = Chapter(john3, from = 16)
        reader.start(chapter)
        // One stretch, from just before 3:16 to the end of 3:18, told of where 3:17 and 3:18 begin.
        val stretch = recorder.played.single()
        assertEquals("en/John 3", stretch.file.uri)
        assertEquals(159.96, stretch.from, 1e-9)
        assertEquals(188.0, stretch.to, 1e-9)
        assertEquals(listOf(170.0, 180.0), stretch.marks)
        assertEquals(1.25f, stretch.speed)
        assertTrue(reader.state.value.recording)
        assertEquals("John 3:16", reader.state.value.verse?.label)
        // The verse moves on as each begins, the reading kept in step.
        recorder.reach(0)
        assertEquals("John 3:17", reader.state.value.verse?.label)
        recorder.reach(1)
        assertEquals("John 3:18", reader.state.value.verse?.label)
        assertEquals(1, recorder.played.size)
        // Played to its end: the chapter's end, and the reading's.
        recorder.end()
        assertEquals(ReadAloudState.Status.IDLE, reader.state.value.status)
        assertEquals(true, chapter.stopped)
        assertTrue(speaker.said.isEmpty())
    }

    @Test
    fun theProgressBarMovesARecordingWithinItsVerse() {
        val recorder = FakeRecorder()
        val reader = reader(FakeSpeaker(), FakePrefs(), FakeRecordings(), recorder)
        reader.start(Chapter(john3, from = 16))
        // 3:16 runs from 160 to 168 seconds into its file: a recording's own length, and how far it's got.
        recorder.at = 162.0
        val progress = reader.progress()!!
        assertEquals(8f, progress.parts.single().seconds, 1e-4f)
        assertEquals(0.25f, progress.at, 1e-4f)
        // Moved three quarters in: played from that moment, on to the chapter's end as before.
        assertTrue(reader.seek(part = 0, at = 0.75f))
        val again = recorder.played.last()
        assertEquals(166.0, again.from, 1e-9)
        assertEquals(188.0, again.to, 1e-9)
        assertEquals(listOf(170.0, 180.0), again.marks)
        // Paused, a move waits there, and it plays on from it.
        reader.pause()
        val played = recorder.played.size
        reader.seek(part = 0, at = 0.5f)
        assertEquals(played, recorder.played.size)
        assertEquals(0.5f, reader.progress()!!.at, 1e-4f)
        reader.resume()
        assertEquals(164.0, recorder.played.last().from, 1e-9)
        assertEquals(ReadAloudState.Status.PLAYING, reader.state.value.status)
    }

    @Test
    fun aTapOnAVersePlaysOnFromItsOwnStartInTheRecording() {
        val recorder = FakeRecorder()
        val reader = reader(FakeSpeaker(), FakePrefs(), FakeRecordings(), recorder)
        val chapter = Chapter(john3, from = 15)
        reader.start(chapter)
        recorder.reach(0)
        assertEquals("John 3:16", reader.state.value.verse?.label)
        // 3:18 tapped: what's playing stops, and 3:18 plays from its own start to the chapter's end.
        val stops = recorder.stops
        reader.moveTo("John", 3, 18)
        assertTrue(recorder.stops > stops)
        assertEquals("John 3:18", reader.state.value.verse?.label)
        assertEquals(179.96, recorder.played.last().from, 1e-9)
        assertEquals(188.0, recorder.played.last().to, 1e-9)
        // Back to 3:16 (Undo): from its start, on into 3:17 and 3:18 without a break.
        reader.moveTo("John", 3, 16)
        assertEquals(159.96, recorder.played.last().from, 1e-9)
        assertEquals(listOf(170.0, 180.0), recorder.played.last().marks)
        // Paused, then a verse tapped: it plays from there.
        reader.pause()
        reader.moveTo("John", 3, 17)
        assertEquals(ReadAloudState.Status.PLAYING, reader.state.value.status)
        assertEquals(169.96, recorder.played.last().from, 1e-9)
        assertNull(chapter.stopped)
    }

    @Test
    fun aChaptersFirstVerseStartsWithItsSpokenHeadingInTheReaderOnly() {
        val recorder = FakeRecorder()
        reader(FakeSpeaker(), FakePrefs(), FakeRecordings(), recorder).start(Chapter(genesis1, from = 1))
        assertEquals(0.0, recorder.played.single().from, 1e-9)
        val cards = FakeRecorder()
        reader(FakeSpeaker(), FakePrefs(), FakeRecordings(), cards).start(Chapter(genesis1, from = 1, place = ReadingPlace.VERSE_SCROLL))
        assertEquals(9.96, cards.played.single().from, 1e-9)
    }

    @Test
    fun theVerseCaughtUpWithIfItsEndComesFirst() {
        val recorder = FakeRecorder()
        val reader = reader(FakeSpeaker(), FakePrefs(), FakeRecordings(), recorder)
        val chapter = Chapter(john3, from = 15)
        reader.start(chapter)
        recorder.reach(0)
        // The stretch ends with marks still to come (a short last verse at speed): on through them all.
        recorder.end()
        assertEquals(true, chapter.stopped)
        // 15 to start, 16, 17 and 18 moved on to, and the end found after 18.
        assertEquals(5, chapter.nexts)
    }

    @Test
    fun teluguAloneStopsItsStretchBeforeAVerseItHasntAndPlaysOnAfter() {
        val recorder = FakeRecorder()
        val reader = reader(FakeSpeaker(), FakePrefs(ReadLanguage.TELUGU), FakeRecordings(), recorder)
        reader.start(Chapter(john3, from = 16))
        // 3:17 has no Telugu: 3:16 alone, then 3:18 from its own start.
        assertEquals(listOf<Double>(), recorder.played.single().marks)
        assertEquals(168.0, recorder.played.single().to, 1e-9)
        recorder.end()
        assertEquals("John 3:18", reader.state.value.verse?.label)
        assertEquals("te/John", recorder.played.last().file.uri)
        assertEquals(179.96, recorder.played.last().from, 1e-9)
    }

    @Test
    fun bothPlaysEachVerseFromEachRecordingInTurn() {
        val recorder = FakeRecorder()
        val reader = reader(FakeSpeaker(), FakePrefs(ReadLanguage.BOTH), FakeRecordings(), recorder)
        reader.start(Chapter(john3, from = 16))
        recorder.end()
        assertTrue(reader.state.value.telugu)
        // 3:17 has no Telugu: its English alone, not played on into 3:18's.
        recorder.end()
        assertEquals(listOf("en/John 3", "te/John", "en/John 3"), recorder.played.map { it.file.uri })
        // Each a verse on its own: nothing played on into.
        assertTrue(recorder.played.all { it.marks.isEmpty() && it.to - it.from < 9 })
        assertEquals("John 3:17", reader.state.value.verse?.label)
    }

    @Test
    fun whatTheRecordingHasntIsReadByThePhonesVoice() {
        val speaker = FakeSpeaker()
        val recorder = FakeRecorder()
        // English from the recording, but 3:17 isn't in it; Telugu by the phone's voice.
        val reader = reader(speaker, FakePrefs(ReadLanguage.BOTH, telugu = false), FakeRecordings(missing = setOf(17)), recorder)
        reader.start(Chapter(john3, from = 16))
        recorder.end()
        assertEquals("దేవుడు లోకమును ఎంతో ప్రేమించెను.", speaker.said.single().text)
        speaker.finish()
        assertEquals("For God sent not his Son", speaker.said.last().text)
        assertFalse(reader.state.value.recording)
        speaker.finish()
        assertTrue(reader.state.value.recording)
        assertEquals(2, recorder.played.size)
    }

    @Test
    fun aFileThatWontPlayIsReadByTheVoiceAndLeftOut() {
        val speaker = FakeSpeaker()
        val recorder = FakeRecorder()
        val recordings = FakeRecordings()
        val reader = reader(speaker, FakePrefs(), recordings, recorder)
        reader.start(Chapter(john3, from = 16))
        recorder.fail()
        assertEquals("For God so loved the world", speaker.said.single().text)
        assertEquals(listOf("en/John 3"), recordings.failed.map { it.uri })
        speaker.finish()
        assertEquals("For God sent not his Son", speaker.said.last().text)
        assertEquals(1, recorder.played.size)
    }

    @Test
    fun aPausePlaysOnFromWhereItWas() {
        val recorder = FakeRecorder()
        val prefs = FakePrefs()
        val reader = reader(FakeSpeaker(), prefs, FakeRecordings(), recorder)
        reader.start(Chapter(john3, from = 16))
        recorder.reach(0)
        reader.pause()
        assertTrue(recorder.paused)
        assertEquals(ReadAloudState.Status.PAUSED, reader.state.value.status)
        // At the speed set meanwhile.
        prefs.englishSpeed = 0.75f
        reader.resume()
        assertFalse(recorder.paused)
        assertEquals(0.75f, recorder.speed)
        assertEquals(1, recorder.played.size)
        // The player let it go meanwhile: played again from where it was paused, to the same end.
        recorder.at = 172.5
        reader.pause()
        recorder.canResume = false
        reader.resume()
        val again = recorder.played.last()
        assertEquals(172.5, again.from, 1e-9)
        assertEquals(188.0, again.to, 1e-9)
        assertEquals(listOf(180.0), again.marks)
        assertEquals("John 3:17", reader.state.value.verse?.label)
    }

    @Test
    fun aNewSpeedPlaysOnWithoutStartingAgain() {
        val recorder = FakeRecorder()
        val prefs = FakePrefs()
        val reader = reader(FakeSpeaker(), prefs, FakeRecordings(), recorder)
        reader.start(Chapter(john3, from = 16))
        prefs.englishSpeed = 1.5f
        reader.refresh()
        assertEquals(1.5f, recorder.speed)
        assertEquals(1, recorder.played.size)
    }

    @Test
    fun nextAndPreviousStartTheirVerseAfresh() {
        val recorder = FakeRecorder()
        var now = 0L
        val reader = reader(FakeSpeaker(), FakePrefs(), FakeRecordings(), recorder, clock = { now })
        val chapter = Chapter(john3, from = 16)
        reader.start(chapter)
        reader.skipToNext()
        assertEquals("John 3:17", reader.state.value.verse?.label)
        assertEquals(169.96, recorder.played.last().from, 1e-9)
        assertEquals(listOf(180.0), recorder.played.last().marks)
        // What the first stretch reports now counts for nothing.
        recorder.listener!!.onEnded(recorder.played.first().id)
        assertEquals("John 3:17", reader.state.value.verse?.label)
        // Previous, early on: the verse before; later: this one again.
        reader.skipToPrevious()
        assertEquals("John 3:16", reader.state.value.verse?.label)
        now = 10_000
        recorder.reach(0)
        now = 20_000
        reader.skipToPrevious()
        assertEquals("John 3:17", reader.state.value.verse?.label)
        assertEquals(169.96, recorder.played.last().from, 1e-9)
    }

    @Test
    fun noVoiceIsNeededForWhatsAllPlayedFromRecordings() {
        val recorder = FakeRecorder()
        val reader = reader(FakeSpeaker(teluguVoice = false, ready = false), FakePrefs(ReadLanguage.TELUGU), FakeRecordings(), recorder)
        reader.start(Chapter(john3, from = 16))
        assertEquals(ReadAloudState.Status.PLAYING, reader.state.value.status)
        assertFalse(reader.state.value.noTeluguVoice)
        assertEquals("te/John", recorder.played.single().file.uri)
        // Without the recording, Telugu alone can't be read.
        val silent = reader(FakeSpeaker(teluguVoice = false), FakePrefs(ReadLanguage.TELUGU, telugu = false), FakeRecordings(), FakeRecorder())
        silent.start(Chapter(john3, from = 16))
        assertTrue(silent.state.value.noTeluguVoice)
        assertEquals(ReadAloudState.Status.IDLE, silent.state.value.status)
    }

    @Test
    fun switchingToThePhonesVoiceStartsTheVerseAgainInIt() {
        val speaker = FakeSpeaker()
        val recorder = FakeRecorder()
        val prefs = FakePrefs()
        val reader = reader(speaker, prefs, FakeRecordings(), recorder)
        reader.start(Chapter(john3, from = 16))
        recorder.reach(0)
        prefs.english = false
        reader.restartVerse()
        assertEquals("For God sent not his Son", speaker.said.single().text)
        assertFalse(reader.state.value.recording)
        assertNull(recorder.at)
    }

    @Test
    fun theSettingsPlayAVerseOfTheRecordingToHearIt() {
        val speaker = FakeSpeaker()
        val recorder = FakeRecorder()
        val reader = reader(speaker, FakePrefs(), FakeRecordings(), recorder)
        reader.trySpeaking("For God so loved the world.", telugu = false)
        assertEquals(159.96 to 168.0, recorder.played.single().let { it.from to it.to })
        assertTrue(speaker.said.isEmpty())
        // Without one, the phone's voice says the words.
        reader.trySpeaking("దేవుడు లోకమును ఎంతో ప్రేమించెను.", telugu = true)
        assertEquals(2, recorder.played.size)
        val voiceOnly = reader(speaker, FakePrefs(english = false), FakeRecordings(), FakeRecorder())
        voiceOnly.trySpeaking("For God so loved the world.", telugu = false)
        assertEquals("For God so loved the world.", speaker.said.single().text)
    }
}
