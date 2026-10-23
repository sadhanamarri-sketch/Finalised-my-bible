package com.example.mybible.audio

import kotlinx.coroutines.CompletableDeferred
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

        override suspend fun verse(book: String, chapter: Int, verse: Int): VerseToRead? =
            verses.firstOrNull { it.book == book && it.chapter == chapter && it.verse == verse }

        override fun onStopped(finished: Boolean) { stopped = finished }
        override fun onPaused() { paused++ }
        override fun onResumed() { resumed++ }
    }

    private val john3 = listOf(
        VerseToRead("John", 3, 16, "¶ For God so loved the world", "దేవుడు లోకమును ఎంతో ప్రేమించెను."),
        VerseToRead("John", 3, 17, "For God sent not his Son", null),
        VerseToRead("John", 3, 18, "He that believeth on him is not condemned", "ఆయనయందు విశ్వాసముంచువానికి తీర్పు తీర్చబడదు")
    )

    // A Bible of three chapters: John 3 (16 to 18) and 4, then Acts 1, the last.
    private val john4 = listOf(
        VerseToRead("John", 4, 1, "When therefore the Lord knew", null),
        VerseToRead("John", 4, 2, "Though Jesus himself baptized not", null)
    )
    private val acts1 = listOf(VerseToRead("Acts", 1, 1, "The former treatise have I made", null))
    private val bible by lazy { mapOf(("John" to 3) to john3, ("John" to 4) to john4, ("Acts" to 1) to acts1) }

    private fun chapterAfter(book: String, chapter: Int): Pair<String, Int>? = when (book to chapter) {
        "John" to 3 -> "John" to 4
        "John" to 4 -> "Acts" to 1
        else -> null
    }

    private fun reading(from: Int, until: ReadUntil, onEnd: (Boolean) -> Unit = {}) =
        ChapterReading("John", 3, from, until = { until }, chapterAfter = ::chapterAfter, onEnd = onEnd) { b, c -> bible.getValue(b to c) }

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
    fun waitingToGoOnNextGoesOnAtOnceAndPreviousReadsTheLastVerseAgain() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker)
        val cards = Pausing(john3)
        reader.start(cards)
        speaker.finish()
        // 3:16 read, and a wait before 3:17 (Verse Scroll's pause between cards): nothing more said yet.
        assertEquals(1, speaker.said.size)
        assertTrue(cards.waiting)
        // Next goes on at once, to the verse it was waiting for, not past it.
        reader.skipToNext()
        assertFalse(cards.waiting)
        assertEquals("John 3:17", reader.state.value.verse?.label)
        assertEquals("For God sent not his Son", speaker.said.last().text)
        // Previous while it waits: the verse just read, again.
        speaker.finish()
        assertTrue(cards.waiting)
        reader.skipToPrevious()
        assertFalse(cards.waiting)
        assertEquals(3, speaker.said.size)
        assertEquals("For God sent not his Son", speaker.said.last().text)
        // Being read, Next skips to the next at once, as Verse Scroll's goes a card on.
        reader.skipToNext()
        assertFalse(cards.waiting)
        assertEquals("John 3:18", reader.state.value.verse?.label)
        assertEquals("He that believeth on him is not condemned", speaker.said.last().text)
    }

    @Test
    fun aVerseSaidToBeDoneTwiceMovesTheReadingOnOnce() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker)
        val cards = Pausing(john3)
        reader.start(cards)
        val first = speaker.said.last().id
        speaker.listener!!.onDone(first)
        // The voice saying so again, while the reading waits to go on to the next card: nothing more.
        speaker.listener!!.onDone(first)
        assertEquals(1, cards.waits)
        cards.hurry()
        assertEquals(listOf("For God so loved the world", "For God sent not his Son"), speaker.said.map { it.text })
        assertEquals("John 3:17", reader.state.value.verse?.label)
    }

    @Test
    fun aPauseInAVoiceThatDoesntSayItsWordsPicksUpAboutWhereItGotTo() {
        var now = 0L
        val speaker = FakeSpeaker()
        val reader = reader(speaker, clock = { now })
        val verse = VerseToRead("John", 1, 1, "In the beginning was the Word, and the Word was with God, and the Word was God.", null)
        reader.start(Chapter(listOf(verse), from = 1))
        val whole = speaker.said.last().text
        // Four seconds in, at about 15 letters a second: some 60 letters said, none of them told.
        now = 4_000
        reader.pause()
        reader.resume()
        val again = speaker.said.last().text
        assertTrue(again.length < whole.length)
        assertTrue(whole.endsWith(again))
        // From a word a moment before where it had got to.
        val from = whole.length - again.length
        assertTrue("picked up at $from", from in 30..40)
        assertTrue(from == 0 || whole[from - 1] == ' ')
    }

    // Waits before each verse after its first, as Verse Scroll's pause between cards, until hurried.
    private class Pausing(val verses: List<VerseToRead>) : ReadingSession {
        override val place = ReadingPlace.VERSE_SCROLL
        override val title = "Verse Scroll"
        private var wait: CompletableDeferred<Unit>? = null
        override val waiting: Boolean get() = wait != null
        // How many times it's waited to go on.
        var waits = 0

        override suspend fun next(after: VerseToRead?): VerseToRead? {
            if (after == null) return verses.first()
            waits++
            try {
                CompletableDeferred<Unit>().also { wait = it }.await()
            } finally {
                wait = null
            }
            return verses.getOrNull(verses.indexOf(after) + 1)
        }

        override suspend fun skip(current: VerseToRead): VerseToRead? = verses.getOrNull(verses.indexOf(current) + 1)

        override fun hurry() {
            wait?.complete(Unit)
        }
    }

    @Test
    fun aReadingGoesOnPastItsChapterAsFarAsReadUntilSays() {
        fun read(until: ReadUntil): Pair<List<String>, Boolean?> {
            val speaker = FakeSpeaker()
            val reader = reader(speaker)
            var ended: Boolean? = null
            reader.start(reading(from = 18, until = until) { ended = it })
            val read = mutableListOf<String>()
            repeat(10) {
                if (reader.state.value.active) {
                    read += reader.state.value.verse!!.label
                    speaker.finish()
                }
            }
            return read to ended
        }
        assertEquals(listOf("John 3:18") to true, read(ReadUntil.CHAPTER))
        assertEquals(listOf("John 3:18", "John 4:1", "John 4:2") to true, read(ReadUntil.BOOK))
        // On into the next book, and to the end of the last.
        assertEquals(listOf("John 3:18", "John 4:1", "John 4:2", "Acts 1:1") to true, read(ReadUntil.CONTINUOUS))
    }

    @Test
    fun goingOnIntoTheNextChapterItsNameFollowsAndPreviousGoesBack() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker)
        reader.start(reading(from = 18, until = ReadUntil.CONTINUOUS))
        assertEquals("John 3", reader.state.value.title)
        speaker.finish()
        assertEquals("John 4:1", reader.state.value.verse?.label)
        assertEquals("John 4", reader.state.value.title)
        // Previous early in John 4:1: back to the end of the chapter it came from.
        reader.skipToPrevious()
        assertEquals("John 3:18", reader.state.value.verse?.label)
        assertEquals("John 3", reader.state.value.title)
        repeat(2) { speaker.finish() }
        assertEquals("John 4:2", reader.state.value.verse?.label)
        // Holding previous: the first verse of the chapter it's in now.
        reader.skipToStart()
        assertEquals("John 4:1", reader.state.value.verse?.label)
    }

    @Test
    fun aSleepTimerForTheChaptersEndStopsTheReadingThere() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker)
        var ended: Boolean? = null
        reader.start(reading(from = 17, until = ReadUntil.CONTINUOUS) { ended = it })
        reader.sleepAtChapterEnd()
        assertEquals(true, reader.state.value.sleep?.atChapterEnd)
        repeat(2) { speaker.finish() }
        // John 3 read to its end, and not on into John 4.
        assertEquals(ReadAloudState.Status.IDLE, reader.state.value.status)
        assertNull(reader.state.value.sleep)
        assertEquals(false, ended)
        assertEquals(listOf("For God sent not his Son", "He that believeth on him is not condemned"), speaker.said.map { it.text })
        // Next pressed at the chapter's last verse goes on all the same (whoever pressed it is awake).
        reader.start(reading(from = 18, until = ReadUntil.CONTINUOUS))
        reader.sleepAtChapterEnd()
        reader.skipToNext()
        assertEquals("John 4:1", reader.state.value.verse?.label)
        assertEquals(ReadAloudState.Status.PLAYING, reader.state.value.status)
    }

    @Test
    fun aSleepTimerGoesOnWithTheNextReadingAndGoesWithTheReading() {
        val now = 1_000_000L
        val reader = ReadAloud(FakeSpeaker(), FakePrefs(), CoroutineScope(Dispatchers.Unconfined), clock = { 0L }, sleepClock = { now })
        // Nothing being read: no timer.
        reader.sleepIn(30)
        assertNull(reader.state.value.sleep)
        reader.start(Chapter(john3, from = 16))
        reader.sleepIn(30)
        assertEquals(SleepTimer(now + 30 * 60_000L, minutes = 30, minutesLeft = 30), reader.state.value.sleep)
        assertEquals(30 * 60_000L, reader.sleepLeftMs())
        // Another reading started before it runs out: the timer goes on with it.
        reader.start(Chapter(john3, from = 17))
        assertEquals(30, reader.state.value.sleep?.minutes)
        reader.cancelSleep()
        assertNull(reader.state.value.sleep)
        // One for the chapter's end replaces one for so many minutes, and goes with a Verse Scroll reading.
        reader.sleepIn(15)
        reader.sleepAtChapterEnd()
        assertEquals(SleepTimer(at = null), reader.state.value.sleep)
        assertNull(reader.sleepLeftMs())
        reader.start(Pausing(john3))
        assertNull(reader.state.value.sleep)
        // Stopped, the reading takes its timer with it.
        reader.sleepIn(45)
        assertEquals(45, reader.state.value.sleep?.minutes)
        reader.stop()
        assertNull(reader.state.value.sleep)
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
        reader.start(ChapterReading("John", 3, from = 17) { _, _ -> john3 })
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
        reader.start(ChapterReading("John", 3, from = 18) { _, _ -> john3 })
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
        inTelugu.start(ChapterReading("John", 3, from = 18) { _, _ -> john3.drop(1) })
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
        val chapter = ChapterReading("John", 3, from = 17) { _, _ -> loads++; john3 }
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
    fun aTapOnAVerseReadsFromItAtOnceAndOnFromThere() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker, FakePrefs(ReadLanguage.BOTH))
        var ended: Boolean? = null
        reader.start(ChapterReading("John", 3, from = 16, onEnd = { ended = it }) { _, _ -> john3 })
        speaker.reachWord(4)
        // 3:18 tapped part way through 3:16: its English from the start, at once.
        val stops = speaker.stops
        reader.moveTo("John", 3, 18)
        assertTrue(speaker.stops > stops)
        assertEquals("John 3:18", reader.state.value.verse?.label)
        assertEquals(false to "He that believeth on him is not condemned", speaker.said.last().let { it.telugu to it.text })
        // Back to 3:16 (the bar's Undo): from its start, then on as the reading goes, in both languages.
        reader.moveTo("John", 3, 16)
        assertEquals("For God so loved the world", speaker.said.last().text)
        speaker.finish()
        assertTrue(speaker.said.last().telugu)
        speaker.finish()
        assertEquals("John 3:17", reader.state.value.verse?.label)
        // The verse being read, tapped: again from its start.
        speaker.reachWord(8)
        reader.moveTo("John", 3, 17)
        assertEquals("For God sent not his Son", speaker.said.last().text)
        // Not one of the reading's verses: nothing changes.
        val said = speaker.said.size
        reader.moveTo("John", 4, 1)
        assertEquals(said, speaker.said.size)
        assertEquals("John 3:17", reader.state.value.verse?.label)
        assertEquals(ReadAloudState.Status.PLAYING, reader.state.value.status)
        assertNull(ended)
        // On to the chapter's end: finished.
        reader.moveTo("John", 3, 18)
        repeat(2) { speaker.finish() }
        assertEquals(true, ended)
    }

    @Test
    fun pausedATapOnAVersePlaysFromIt() {
        val speaker = FakeSpeaker()
        var playing = 0
        val reader = ReadAloud(speaker, FakePrefs(), CoroutineScope(Dispatchers.Unconfined), onPlaying = { playing++ }, clock = { 0L })
        val chapter = Chapter(john3, from = 16)
        reader.start(chapter)
        reader.pause()
        reader.moveTo("John", 3, 18)
        assertEquals(ReadAloudState.Status.PLAYING, reader.state.value.status)
        assertEquals("John 3:18", reader.state.value.verse?.label)
        assertEquals("He that believeth on him is not condemned", speaker.said.last().text)
        assertEquals(1, chapter.resumed)
        assertEquals(2, playing)
    }

    @Test
    fun aGreekOrHebrewWordIsSaidInTheEnglishVoicePausingAReading() {
        val speaker = FakeSpeaker()
        val reader = reader(speaker, FakePrefs(pitch = 0.85f, speed = 1.4f, englishVoice = "en-us-x-iol-local"))
        val chapter = Chapter(john3, from = 16)
        reader.start(chapter)
        assertEquals(true, reader.sayWord("ah gah-pay"))
        assertEquals(ReadAloudState.Status.PAUSED, reader.state.value.status)
        assertEquals(1, chapter.paused)
        // What its sounds-like spelling shows, in the English voice and pitch, a little slower than usual.
        val word = speaker.said.last()
        assertEquals("ah gah-pay", word.text)
        assertFalse(word.telugu)
        assertEquals("en-us-x-iol-local", word.voice)
        assertEquals(0.85f, word.pitch)
        assertEquals(0.8f, word.rate)
        // Its end isn't the verse's: the reading stays paused.
        speaker.finish()
        assertEquals(ReadAloudState.Status.PAUSED, reader.state.value.status)
        // No English voice on the phone: nothing said, and the page is told so.
        val silent = FakeSpeaker(ready = false)
        assertEquals(false, reader(silent).sayWord("KHEH-sed"))
        assertTrue(silent.said.isEmpty())
    }

    @Test
    fun theProgressBarShowsHowFarTheVerseHasGotAndMovesIt() {
        val speaker = FakeSpeaker()
        var now = 0L
        val reader = reader(speaker, FakePrefs(ReadLanguage.BOTH), clock = { now })
        assertFalse(reader.seek(part = 0, at = 0.5f))
        reader.start(Chapter(john3, from = 16))
        // Its English, then its Telugu, each as long as its text takes; at the start.
        val start = reader.progress()!!
        assertEquals("John 3:16", start.verse.label)
        assertEquals(listOf(false, true), start.parts.map { it.telugu })
        assertEquals(0 to 0f, start.part to start.at)
        // A word said: that far into the verse's English.
        val english = speaker.said.last().text
        assertEquals("For God so loved the world", english)
        speaker.reachWord(english.indexOf("the"))
        assertEquals(english.indexOf("the").toFloat() / english.length, reader.progress()!!.at, 1e-6f)
        // Moved halfway: on from the word there.
        assertTrue(reader.seek(part = 0, at = 0.5f))
        assertEquals("loved the world", speaker.said.last().text)
        // To the dot: its Telugu, from the start.
        reader.seek(part = 1, at = 0f)
        assertEquals(true to "దేవుడు లోకమును ఎంతో ప్రేమించెను.", speaker.said.last().let { it.telugu to it.text })
        assertTrue(reader.state.value.telugu)
        assertEquals(1, reader.progress()!!.part)
        // Paused, a move waits there, and it plays on from it.
        reader.pause()
        val said = speaker.said.size
        reader.seek(part = 0, at = 0f)
        assertEquals(said, speaker.said.size)
        assertEquals(0 to 0f, reader.progress()!!.let { it.part to it.at })
        reader.resume()
        assertEquals("For God so loved the world", speaker.said.last().text)
        // A voice that never says which word it's on: as far as it would have got at its pace (15 a second).
        now += 1_000
        assertEquals(15f / english.length, reader.progress()!!.at, 1e-6f)
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
