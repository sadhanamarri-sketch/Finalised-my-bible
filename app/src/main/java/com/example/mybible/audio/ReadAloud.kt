package com.example.mybible.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which of the Bible's two texts are read aloud: English, Telugu, or each verse in English then Telugu. */
enum class ReadLanguage(val label: String) {
    ENGLISH("English"),
    TELUGU("Telugu"),
    BOTH("Both");

    /** The language button's next: English, Telugu, Both, round again; English alone when Telugu can't be read. */
    fun next(telugu: Boolean): ReadLanguage = when {
        !telugu -> ENGLISH
        this == ENGLISH -> TELUGU
        this == TELUGU -> BOTH
        else -> ENGLISH
    }

    companion object {
        fun from(name: String?): ReadLanguage = entries.firstOrNull { it.name == name } ?: ENGLISH
    }
}

/**
 * Where a reading in the Reader stops: at the end of its chapter, at the end of its book, or never, on
 * from book to book (to the end of Revelation).
 */
enum class ReadUntil(val label: String) {
    CHAPTER("End of chapter"),
    BOOK("End of book"),
    CONTINUOUS("Continuous");

    companion object {
        fun from(name: String?): ReadUntil = entries.firstOrNull { it.name == name } ?: CHAPTER
    }
}

/** A verse to read aloud: where it is, its King James text and its Telugu (none for a verse it lacks). */
data class VerseToRead(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val english: String,
    val telugu: String?
) {
    val label: String get() = "$book $chapter:$verse"
}

/** Where a reading is: the Reader's chapter, or Verse Scroll's cards. */
enum class ReadingPlace { READER, VERSE_SCROLL }

/**
 * A reading: where its verses come from, one after another, and what it does as it starts, pauses and
 * stops. Every call is on the main thread.
 */
interface ReadingSession {
    val place: ReadingPlace

    /** What the notification calls it: "John 3", "Verse Scroll". */
    val title: String

    /** The verse to read after [after] — the first, given null — or null when there are no more. */
    suspend fun next(after: VerseToRead?): VerseToRead?

    /**
     * Where a headset's "next" goes from [current]: by default the verse after it; Verse Scroll's, the
     * next card. Null when there's nothing after it, which ends the reading.
     */
    suspend fun skip(current: VerseToRead): VerseToRead? = next(current)

    /**
     * Where a headset's "previous" goes from [current]: the verse before it, or null when there's none
     * (then [current] starts again). None by default.
     */
    suspend fun back(current: VerseToRead): VerseToRead? = null

    /** Where touching and holding "previous" goes: the reading's first verse (the Reader's, its chapter's first). None by default. */
    suspend fun first(): VerseToRead? = null

    /** The reading's own verse [verse] of [book] [chapter], for a tap that moves it there; null when it isn't one of its verses. None by default. */
    suspend fun verse(book: String, chapter: Int, verse: Int): VerseToRead? = null

    /**
     * The verses [next] would give one after another from [verse], as far as that's known without moving
     * anything on (the Reader's, the rest of its chapter; a Verse Scroll card's, the rest of its verses):
     * a recording plays on into them without a break. None by default.
     */
    suspend fun following(verse: VerseToRead): List<VerseToRead> = emptyList()

    /**
     * Whether it's waiting to go on to its next verse (Verse Scroll's pause between cards) rather than
     * finding it: a headset's next and previous act at once, rather than being let go. Never, by default.
     */
    val waiting: Boolean get() = false

    /** A headset's "next" while [waiting]: the wait is cut short. */
    fun hurry() {}

    /** It ended: [finished] when it came to its end, otherwise stopped (a tap, another reading starting). */
    fun onStopped(finished: Boolean) {}

    fun onPaused() {}

    fun onResumed() {}
}

/** The reader's settings, as reading needs them: read again for every verse, so changes apply straight away. */
interface ReadAloudPrefs {
    val language: ReadLanguage

    /** How fast a language is read: 1 is the voice's own pace. */
    fun speed(telugu: Boolean): Float

    /** How high a language is read: 1 is the voice's own pitch; lower is deeper. */
    fun pitch(telugu: Boolean): Float = 1f

    /** The voice chosen for a language, or null when none was (then the deepest; see ReadAloud.voiceFor). */
    fun voice(telugu: Boolean): String?

    /** The speech engine a language is read with (its package), or null for the phone's usual one. */
    fun engine(telugu: Boolean): String? = null

    /** Whether a language is played from its recording on the phone, where there's one, rather than read by the phone's voice. */
    fun recording(telugu: Boolean): Boolean = false

    /** Verse Scroll: once a card has been read, move on to the next and read that. */
    val continueReading: Boolean

    /** Verse Scroll, going on by itself: seconds to wait between one card and the next (0, none). */
    val pauseBetweenCards: Int get() = 0
}

/**
 * One of the phone's voices: its name for the speech engine, how to show it, and how deep it speaks
 * (its pitch in Hz, once measured; see VoiceDepths).
 */
data class VoiceOption(val name: String, val label: String, val online: Boolean, val depth: Float? = null)

/** A speech engine installed on the phone: its package, its name, and whether it's the phone's usual one. */
data class EngineOption(val name: String, val label: String, val usual: Boolean)

/** The phone's speech engine, behind an interface so tests can stand in for it. */
interface Speaker {
    interface Listener {
        fun onDone(id: String)

        fun onError(id: String)

        /** The word starting at [start] (in what [id] was given) is being said. */
        fun onRange(id: String, start: Int)
    }

    var listener: Listener?

    /** Whether the phone can speak at all: null while its speech engine is starting. */
    val ready: Boolean?

    /** Whether it has a Telugu voice: null while its speech engine is starting. */
    fun hasTelugu(): Boolean?

    /** Says [text] in [voice] (null: the language's usual one), at [rate] and [pitch] (1: the voice's own). */
    fun speak(id: String, text: String, telugu: Boolean, rate: Float, voice: String?, pitch: Float)

    fun stop()

    /** How loud what's said from now on is: 1 as usual, less as a sleep timer runs out. */
    fun volume(level: Float) {}

    fun voices(telugu: Boolean): List<VoiceOption>

    /**
     * Says [text] in the voice named [voice] into [file] rather than aloud, at its own pitch and pace,
     * to measure how deep it is; [onSaid] is told whether it worked, on the main thread.
     */
    fun record(text: String, voice: String, file: java.io.File, onSaid: (Boolean) -> Unit) = onSaid(false)

    /** Done recording for now. */
    fun doneRecording() {}

    /** The speech engines installed on the phone. */
    fun engines(): List<EngineOption> = emptyList()
}

data class ReadAloudState(
    val status: Status = Status.IDLE,
    val place: ReadingPlace? = null,
    /** The reading's name: "John 3", "Verse Scroll". */
    val title: String = "",
    /** The verse being read (or, paused, the one it stopped in). */
    val verse: VerseToRead? = null,
    /** Whether it's the verse's Telugu being read, rather than its English. */
    val telugu: Boolean = false,
    /** Whether it's played from a recording, rather than read by the phone's voice. */
    val recording: Boolean = false,
    /** The phone has no Telugu voice, so Telugu was left out — or, with Telugu alone, nothing could be read. */
    val noTeluguVoice: Boolean = false,
    /** The phone has no voice at all (no speech engine). */
    val noVoice: Boolean = false,
    /** The sleep timer, while one is set: the reading stops when it runs out. */
    val sleep: SleepTimer? = null
) {
    enum class Status { IDLE, PLAYING, PAUSED }

    val active: Boolean get() = status != Status.IDLE
}

/**
 * A sleep timer: the reading stops [at] (milliseconds, on the reader-aloud's sleep clock), set for
 * [minutes] with [minutesLeft] of them still to go; or, [at] null, at the end of the chapter it's in.
 */
data class SleepTimer(val at: Long?, val minutes: Int? = null, val minutesLeft: Int? = null) {
    val atChapterEnd: Boolean get() = at == null
}

/**
 * Reads verses aloud, one at a time: each in English, in Telugu, or in English and then Telugu, as the
 * settings say, in the phone's own voice or played from a narrated recording on the phone. One reading
 * at a time — starting another stops the first. A pause stops the voice mid-verse and picks up again at
 * the word it got to (a recording, where it was). Everything runs on the main thread ([scope]'s).
 *
 * A recording plays each verse from its own stretch of its file, and nothing after it. Read in one
 * language, the verses after it that the reading goes on to (the rest of the Reader's chapter) play on
 * from the same file without a break, its pauses and any music under it as recorded; the verse being
 * read moves on as each one begins.
 */
class ReadAloud(
    private val speaker: Speaker,
    val prefs: ReadAloudPrefs,
    private val scope: CoroutineScope,
    /** It started or went on playing: the notification and audio focus go with it. */
    private val onPlaying: () -> Unit = {},
    /** How deep each of the phone's voices is, to read in the deepest unless one was chosen. */
    private val depths: VoiceDepths = VoiceDepths(),
    /** The time in milliseconds, to tell a headset's "previous" early in a verse from later on. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    /** The narrated recordings on the phone, played instead of the phone's voice where the settings say. */
    val recordings: RecordedVerses = RecordedVerses.None,
    /** Plays them. */
    private val recorder: Recorder = Recorder.None,
    /** The time for a sleep timer, in milliseconds, going on while the phone sleeps. */
    private val sleepClock: () -> Long = { android.os.SystemClock.elapsedRealtime() }
) : Speaker.Listener, Recorder.Listener {

    private val _state = MutableStateFlow(ReadAloudState())
    val state: StateFlow<ReadAloudState> = _state.asStateFlow()

    /** The reading going on (playing or paused), if any. */
    var session: ReadingSession? = null
        private set

    // The verse being read, a part per language, and which part is being said.
    private var parts: List<Part> = emptyList()
    private var partIndex = 0

    // Where in the part to start from: 0, or after a pause or a change of speed, the word it got to.
    private var resumeAt = 0

    // Where in the part the text last given to the speaker starts, to place the words it reports.
    private var spokenFrom = 0

    // When the verse being read came up, or was last said from its beginning (see skipToPrevious).
    private var verseStartedAt = 0L

    // Each thing said gets a new id; anything the speaker reports about an older one is ignored.
    private var utterances = 0L
    private val currentId: String get() = "read-$utterances"

    private var advancing: Job? = null

    // The sleep timer counting down, while one is set for so many minutes (see sleepIn).
    private var sleeping: Job? = null

    // How loud the reading is: 1 as usual, lowered as a sleep timer runs out.
    private var loudness = 1f

    // The Telugu voice failed during this reading (say, an online voice with no connection).
    private var teluguFailed = false

    // Each stretch of a recording played (or a recording stopped) gets a new id; anything the player
    // reports about an older one is ignored.
    private var plays = 0L

    // Where in its file a recording was paused, to play on from there should the player have let it go.
    private var recordedResume: Double? = null

    // How far the recording playing has got (the last of its marks reached, or past its end), and the
    // verse moving on with it.
    private var reached = 0.0
    private var crossing: Job? = null

    /** A verse's text in a language, to be read by the phone's voice or played from a recording. */
    private sealed interface Part {
        val telugu: Boolean
        val text: String
    }

    private data class Spoken(override val telugu: Boolean, override val text: String) : Part

    // A verse in a recording, and the verses after it to play on into from the same file ([ahead]).
    private data class Recorded(
        override val telugu: Boolean,
        override val text: String,
        val at: RecordedVerse,
        val ahead: List<Ahead> = emptyList()
    ) : Part

    private data class Ahead(val verse: VerseToRead, val text: String, val at: RecordedVerse)

    init {
        speaker.listener = this
        recorder.listener = this
    }

    // Whether a language is played from its recording (chosen, and there to play).
    private fun reads(telugu: Boolean): Boolean = prefs.recording(telugu) && recordings.ready(telugu)

    // Whether everything the language setting reads is played from recordings: no voice needed.
    private fun recordingsOnly(): Boolean = when (prefs.language) {
        ReadLanguage.ENGLISH -> reads(telugu = false)
        ReadLanguage.TELUGU -> reads(telugu = true)
        ReadLanguage.BOTH -> reads(telugu = false) && reads(telugu = true)
    }

    /** The phone's voices for a language, for the settings: the deepest first, once they're measured. */
    fun voices(telugu: Boolean): List<VoiceOption> = depths.sorted(speaker.voices(telugu))

    /** Goes up each time one of the phone's voices has been measured (see VoiceDepths). */
    val voiceDepthChanges: StateFlow<Int> get() = depths.changes

    /** Whether the phone's voices are being measured. */
    val measuringVoices: StateFlow<Boolean> get() = depths.measuring

    /**
     * The voice a language is read in: the one chosen, else — with the phone's usual speech engine —
     * the deepest offline one, else the engine's own (null).
     */
    fun voiceFor(telugu: Boolean): String? = prefs.voice(telugu) ?: depths.deepest(telugu).takeIf { prefs.engine(telugu) == null }

    /** The speech engines installed on the phone, for the settings. */
    fun engines(): List<EngineOption> = speaker.engines()

    /**
     * Measures how deep each of the phone's voices is that hasn't been yet, for listing them from the
     * deepest and reading in the deepest. Only the phone's usual engine's: an AI voice app's voices
     * have names to go by, and take long to measure. Not while something is being said: it stops if a
     * reading plays, to go on another time.
     */
    fun measureVoices() {
        if (_state.value.status == ReadAloudState.Status.PLAYING || speaker.ready != true) return
        depths.measure(speaker, usualVoices(telugu = false), usualVoices(telugu = true), scope) {
            _state.value.status != ReadAloudState.Status.PLAYING
        }
    }

    /** A language's voices when it's read by the phone's usual engine, the ones measured; else none. */
    fun usualVoices(telugu: Boolean): List<VoiceOption> = if (prefs.engine(telugu) == null) speaker.voices(telugu) else emptyList()

    /** Whether some of [voices] (the phone's, from [voices]) haven't been measured yet. */
    fun voicesUnmeasured(voices: List<VoiceOption>): Boolean = depths.unmeasured(voices)

    /** A few words in [voice], at the language's speed and pitch, to hear it: pausing a reading that's playing. */
    fun previewVoice(text: String, telugu: Boolean, voice: String) {
        if (_state.value.status == ReadAloudState.Status.PLAYING) pause()
        stopSample()
        utterances++
        speaker.speak("try-$utterances", text, telugu, prefs.speed(telugu), voice, prefs.pitch(telugu))
    }

    /** Whether the phone has a Telugu voice (null: not known yet). */
    fun hasTeluguVoice(): Boolean? = if (teluguFailed) false else speaker.hasTelugu()

    /** Whether Telugu can be read: from its recording, or by a Telugu voice (unless the phone surely has none). */
    fun canReadTelugu(): Boolean = reads(telugu = true) || hasTeluguVoice() != false

    /** Whether English's speech engine has started (null: not yet; false: it couldn't). */
    fun englishReady(): Boolean? = speaker.ready

    /**
     * Says how a Greek or Hebrew word sounds — [spoken], its sounds-like spelling as an English voice
     * should be given it (see Pronunciation.spoken) — in the English voice, a little slower, pausing
     * anything being read. False when there's no English voice (null: not known yet, and it's said if
     * it can be).
     */
    fun sayWord(spoken: String): Boolean? {
        val ready = speaker.ready
        if (ready == false) return false
        if (_state.value.status == ReadAloudState.Status.PLAYING) pause()
        stopSample()
        utterances++
        speaker.speak("word-$utterances", spoken, telugu = false, rate = WORD_RATE, voice = voiceFor(telugu = false), pitch = prefs.pitch(telugu = false))
        return ready
    }

    /**
     * A few words in the language's voice, speed and pitch, to hear them: only while nothing is being read.
     * Played from its recording, a verse of that (John 3:16) at the language's speed instead.
     */
    fun trySpeaking(text: String, telugu: Boolean) {
        if (_state.value.active) return
        stopSample()
        utterances++
        if (!reads(telugu)) {
            speaker.speak("try-$utterances", text, telugu, prefs.speed(telugu), voiceFor(telugu), prefs.pitch(telugu))
            return
        }
        speaker.stop()
        val said = utterances
        val id = plays
        scope.launch {
            val sample = recordings.verse(SAMPLE_VERSE, telugu, heading = false)
            if (id != plays || said != utterances || _state.value.active) return@launch
            if (sample != null) {
                recorder.play(id, sample.file, sample.from, sample.end, prefs.speed(telugu))
            } else {
                speaker.speak("try-$utterances", text, telugu, prefs.speed(telugu), voiceFor(telugu), prefs.pitch(telugu))
            }
        }
    }

    // A recording heard on its own (trySpeaking's), stopped for something else.
    private fun stopSample() {
        if (_state.value.active) return
        plays++
        recorder.stop()
    }

    /** Starts reading [next] from its first verse, stopping whatever was being read. */
    fun start(next: ReadingSession) {
        val previous = session
        halt()
        session = null
        if (previous != null && previous !== next) previous.onStopped(finished = false)
        if (speaker.ready == false && !recordingsOnly()) {
            endSleep()
            _state.value = ReadAloudState(noVoice = true)
            next.onStopped(finished = false)
            return
        }
        if (prefs.language == ReadLanguage.TELUGU && speaker.hasTelugu() == false && !reads(telugu = true)) {
            endSleep()
            _state.value = ReadAloudState(noTeluguVoice = true)
            next.onStopped(finished = false)
            return
        }
        session = next
        teluguFailed = false
        // A sleep timer goes on with the reading started next (one for a chapter's end, in the Reader only).
        val sleep = _state.value.sleep?.takeUnless { it.atChapterEnd && next.place != ReadingPlace.READER }
        _state.value = ReadAloudState(status = ReadAloudState.Status.PLAYING, place = next.place, title = next.title, sleep = sleep)
        onPlaying()
        advance(after = null)
    }

    fun pause() {
        val current = session ?: return
        if (_state.value.status != ReadAloudState.Status.PLAYING) return
        utterances++
        speaker.stop()
        if (parts.getOrNull(partIndex) is Recorded) {
            recordedResume = recorder.position
            recorder.pause()
        }
        _state.update { it.copy(status = ReadAloudState.Status.PAUSED) }
        current.onPaused()
    }

    fun resume() {
        val current = session ?: return
        if (_state.value.status != ReadAloudState.Status.PAUSED) return
        _state.update { it.copy(status = ReadAloudState.Status.PLAYING) }
        onPlaying()
        current.onResumed()
        val part = parts.getOrNull(partIndex)
        when {
            // Still finding the next verse: it's read as soon as it's found.
            advancing?.isActive == true -> Unit
            // A recording paused: on from where it was, at the speed now set.
            part is Recorded -> {
                recorder.speed(prefs.speed(part.telugu))
                if (recorder.resume()) recordedResume = null else speakPart()
            }
            part != null -> speakPart()
            else -> advance(after = _state.value.verse)
        }
    }

    fun stop() {
        val current = session
        halt()
        endSleep()
        recorder.release()
        session = null
        _state.value = ReadAloudState()
        current?.onStopped(finished = false)
        measureSoon()
    }

    /**
     * A tap on one of the reading's verses ([ReadingSession.verse]): on from that verse at once, from its
     * start (the one being read starts again), in the same language and speed, the reading going on from
     * there as usual. Paused, it plays. A verse that isn't the reading's is left alone.
     */
    fun moveTo(book: String, chapter: Int, verse: Int) {
        val current = session ?: return
        scope.launch {
            val there = current.verse(book, chapter, verse) ?: return@launch
            if (session !== current) return@launch
            advancing?.cancel()
            utterances++
            speaker.stop()
            stopRecording()
            if (_state.value.status == ReadAloudState.Status.PAUSED) {
                _state.update { it.copy(status = ReadAloudState.Status.PLAYING) }
                onPlaying()
                current.onResumed()
            }
            readFrom(there)
        }
    }

    /**
     * The language, or whether a language is played from its recording, changed: the verse being read
     * starts again as now set (a recording otherwise plays on in the old way to the chapter's end).
     */
    fun restartVerse() {
        val verse = skippableVerse() ?: return
        readFrom(verse)
    }

    /**
     * A sleep timer for the reading going on: it stops [minutes] from now, fading out over its last few
     * seconds. A sleep timer, not a pause: paused meanwhile, it still runs out, and the reading stops.
     * It goes on with a reading started before then, and goes with the reading. Replaces any set before.
     */
    fun sleepIn(minutes: Int) {
        if (session == null) return
        endSleep()
        val at = sleepClock() + minutes * 60_000L
        _state.update { it.copy(sleep = SleepTimer(at, minutes, minutesLeft(at - sleepClock()))) }
        sleeping = scope.launch { countDown(at) }
    }

    /** A sleep timer for the Reader's reading: it stops at the end of the chapter it's in. */
    fun sleepAtChapterEnd() {
        if (session?.place != ReadingPlace.READER) return
        endSleep()
        _state.update { it.copy(sleep = SleepTimer(at = null)) }
    }

    /** The sleep timer off: the reading goes on, as loud as usual. */
    fun cancelSleep() {
        endSleep()
        _state.update { it.copy(sleep = null) }
    }

    /** How long until the sleep timer stops the reading, in milliseconds; null with none set, or one for a chapter's end. */
    fun sleepLeftMs(): Long? = _state.value.sleep?.at?.let { (it - sleepClock()).coerceAtLeast(0) }

    // The sleep timer counting down to [at]: the minutes left kept up to date (for the notification), the
    // sound fading out over its last seconds, then the reading stops.
    private suspend fun countDown(at: Long) {
        while (true) {
            val left = at - sleepClock()
            if (left <= 0) break
            if (left <= FADE_MS) {
                volume(left.toFloat() / FADE_MS)
                delay(minOf(left, FADE_STEP_MS))
            } else {
                val minutes = minutesLeft(left)
                _state.update { s -> s.sleep?.takeIf { it.at == at }?.let { s.copy(sleep = it.copy(minutesLeft = minutes)) } ?: s }
                // Until a minute fewer are left, or the fading begins.
                delay(minOf(left - FADE_MS, (left - 1) % 60_000L + 1))
            }
        }
        sleeping = null
        stop()
    }

    private fun minutesLeft(ms: Long): Int = ((ms + 59_999) / 60_000).toInt()

    // No sleep timer (the reading over, or another set): its countdown stops, and the sound is as loud as usual.
    private fun endSleep() {
        sleeping?.cancel()
        sleeping = null
        if (loudness < 1f) volume(1f)
    }

    private fun volume(level: Float) {
        loudness = level.coerceIn(0f, 1f)
        speaker.volume(loudness)
        recorder.volume(loudness)
    }

    /**
     * A headset's "next" (a double tap on most earbuds), or the notification's: on to the next verse —
     * in Verse Scroll, the next card — from its start. Paused, it waits there to be played. Waiting to
     * go on (Verse Scroll's pause between cards), it goes on at once.
     */
    fun skipToNext() {
        if (advancing?.isActive == true && session?.waiting == true) {
            session?.hurry()
            return
        }
        val verse = skippableVerse() ?: return
        advance(after = verse) { reading, from -> from?.let { reading.skip(it) } }
    }

    /**
     * A headset's "previous" (a triple tap on most earbuds), or the notification's: the verse being read
     * again from its start — or, in its first few seconds, the verse before it (in Verse Scroll, the
     * card before), as a music player's "previous" goes back a song. Waiting to go on (Verse Scroll's
     * pause between cards), the verse just read, again.
     */
    fun skipToPrevious() {
        if (advancing?.isActive == true && session?.waiting == true) {
            val verse = _state.value.verse ?: return
            advancing?.cancel()
            utterances++
            speaker.stop()
            stopRecording()
            readFrom(verse)
            return
        }
        val verse = skippableVerse() ?: return
        if (clock() - verseStartedAt > BACK_WITHIN_MS) {
            partIndex = 0
            resumeAt = 0
            recordedResume = null
            if (_state.value.status == ReadAloudState.Status.PLAYING) speakPart()
            return
        }
        // Nothing before it: this one again.
        advance(after = verse) { reading, from -> from?.let { reading.back(it) ?: it } }
    }

    /**
     * Touching and holding "previous": back to the reading's first verse — in the Reader, the chapter's
     * first — from its start. Paused, it waits there to be played.
     */
    fun skipToStart() {
        val verse = skippableVerse() ?: return
        // There, then on as usual should that verse have nothing to read (no Telugu, read in Telugu).
        var there = false
        advance(after = verse) { reading, from ->
            if (there) {
                reading.next(from)
            } else {
                there = true
                reading.first() ?: from
            }
        }
    }

    // The verse being read, stopped where it is for a skip; null when there's nothing to skip from
    // (no reading, or still finding the next verse).
    private fun skippableVerse(): VerseToRead? {
        if (session == null || advancing?.isActive == true) return null
        val verse = _state.value.verse ?: return null
        utterances++
        speaker.stop()
        stopRecording()
        return verse
    }

    /**
     * A setting changed (the speed, a voice): what's being said starts again, from the word it got to. A
     * recording just plays on at the new speed.
     */
    fun refresh() {
        if (_state.value.status != ReadAloudState.Status.PLAYING || parts.isEmpty() || advancing?.isActive == true) return
        val part = parts.getOrNull(partIndex)
        if (part is Recorded) {
            recorder.speed(prefs.speed(part.telugu))
            return
        }
        utterances++
        speaker.stop()
        speakPart()
    }

    private fun halt() {
        advancing?.cancel()
        advancing = null
        utterances++
        speaker.stop()
        stopRecording()
        parts = emptyList()
        partIndex = 0
        resumeAt = 0
    }

    // Whatever recording is playing stops, and anything it reports from now on is ignored.
    private fun stopRecording() {
        crossing?.cancel()
        crossing = null
        plays++
        recorder.stop()
        recordedResume = null
        reached = 0.0
    }

    // Reads [verse] (just come to in the reading), then on from it as usual.
    private fun readFrom(verse: VerseToRead) {
        var given = false
        advance(after = null) { reading, from ->
            if (given) {
                reading.next(from)
            } else {
                given = true
                verse
            }
        }
    }

    // Reads the verse after [after] in the reading (its first, without one), or ends the reading. [step]
    // finds it: without one, the reading's next verse (going on by itself); for a headset's next or
    // previous, the one to skip to. Going on by itself into another chapter, with the sleep timer set for
    // the end of this one, the reading stops.
    private fun advance(
        after: VerseToRead?,
        step: (suspend (ReadingSession, VerseToRead?) -> VerseToRead?)? = null
    ) {
        val current = session ?: return
        parts = emptyList()
        partIndex = 0
        resumeAt = 0
        recordedResume = null
        advancing = scope.launch {
            var previous = after
            var skipped = 0
            while (true) {
                val verse = if (step != null) step(current, previous) else current.next(previous)
                if (session !== current) return@launch
                if (verse == null) {
                    end(current, finished = true)
                    return@launch
                }
                if (step == null && previous != null && _state.value.sleep?.atChapterEnd == true &&
                    (verse.book != previous.book || verse.chapter != previous.chapter)
                ) {
                    end(current, finished = false)
                    return@launch
                }
                val read = partsOf(verse, current)
                if (session !== current) return@launch
                if (read == null) {
                    // Telugu alone, and no Telugu voice: nothing can be read.
                    end(current, finished = false, noTeluguVoice = true)
                    return@launch
                }
                // Nothing in the chosen language for this verse (no Telugu for it): on to the next.
                if (read.isEmpty() && ++skipped < MAX_SKIPPED) {
                    previous = verse
                    continue
                }
                if (read.isEmpty()) {
                    end(current, finished = false)
                    return@launch
                }
                // A recording read in one language plays on into the verses after it. (In both, each verse
                // is its English and then its Telugu, though some have no Telugu.)
                val alone = read.singleOrNull().takeIf { prefs.language != ReadLanguage.BOTH }
                parts = if (alone is Recorded) listOf(alone.copy(ahead = aheadOf(current, verse, alone))) else read
                if (session !== current) return@launch
                verseStartedAt = clock()
                // The reading's name follows it (the Reader's, from chapter to chapter).
                _state.update { it.copy(verse = verse, telugu = read[0].telugu, recording = read[0] is Recorded, title = current.title) }
                break
            }
            if (_state.value.status == ReadAloudState.Status.PLAYING) speakPart()
        }
    }

    private fun end(current: ReadingSession, finished: Boolean, noTeluguVoice: Boolean = false) {
        halt()
        endSleep()
        recorder.release()
        session = null
        _state.value = ReadAloudState(noTeluguVoice = noTeluguVoice || _state.value.noTeluguVoice)
        current.onStopped(finished)
        measureSoon()
    }

    // A reading over: the voices not yet measured are, a moment later, so the next is in the deepest.
    private fun measureSoon() {
        if (!depths.canMeasure) return
        scope.launch {
            delay(MEASURE_AFTER_MS)
            if (!_state.value.active) measureVoices()
        }
    }

    // The verse's text in each language it's read in, in order, each played from its recording where it's
    // there to play, else read by the phone's voice; null when it's Telugu alone and there's neither a
    // Telugu recording nor a Telugu voice. A chapter's first verse, in the Reader, from its spoken heading.
    private suspend fun partsOf(verse: VerseToRead, current: ReadingSession): List<Part>? {
        val language = prefs.language
        val heading = current.place == ReadingPlace.READER
        val english = if (language == ReadLanguage.TELUGU) null else SpeechText.english(verse.english).takeIf { it.isNotEmpty() }?.let { text ->
            recorded(verse, telugu = false, text, heading) ?: Spoken(false, text)
        }
        val teluguRecording = reads(telugu = true)
        val teluguVoice = !teluguFailed && speaker.hasTelugu() != false
        if (language != ReadLanguage.ENGLISH && !teluguVoice && !teluguRecording) _state.update { it.copy(noTeluguVoice = true) }
        val telugu = if (language == ReadLanguage.ENGLISH) null else verse.telugu?.let(SpeechText::telugu)?.takeIf { it.isNotEmpty() }?.let { text ->
            recorded(verse, telugu = true, text, heading) ?: Spoken(true, text).takeIf { teluguVoice }
        }
        return when (language) {
            ReadLanguage.ENGLISH -> listOfNotNull(english)
            ReadLanguage.TELUGU -> if (teluguVoice || teluguRecording) listOfNotNull(telugu) else null
            ReadLanguage.BOTH -> listOfNotNull(english, telugu)
        }
    }

    // [verse] in a language's recording, when it's played from that and the verse is there to play.
    private suspend fun recorded(verse: VerseToRead, telugu: Boolean, text: String, heading: Boolean): Recorded? {
        if (!reads(telugu)) return null
        val at = recordings.verse(verse, telugu, heading) ?: return null
        return Recorded(telugu, text, at)
    }

    // The verses the reading goes on to after [verse], each the next in its chapter, that its recording
    // [part] can play on into: in the same file, each read in the same way.
    private suspend fun aheadOf(current: ReadingSession, verse: VerseToRead, part: Recorded): List<Ahead> {
        val ahead = mutableListOf<Ahead>()
        var last = verse
        var lastAt = part.at
        for (next in current.following(verse)) {
            if (next.book != last.book || next.chapter != last.chapter || next.verse != last.verse + 1) break
            val text = (if (part.telugu) next.telugu?.let(SpeechText::telugu) else SpeechText.english(next.english))
                ?.takeIf { it.isNotEmpty() } ?: break
            val at = recordings.verse(next, part.telugu, heading = false) ?: break
            if (at.file != lastAt.file || at.start < lastAt.start) break
            ahead += Ahead(next, text, at)
            last = next
            lastAt = at
        }
        return ahead
    }

    private fun speakPart() {
        when (val part = parts.getOrNull(partIndex) ?: return) {
            is Spoken -> {
                val from = resumeAt.coerceIn(0, part.text.length)
                if (partIndex == 0 && from == 0) verseStartedAt = clock()
                spokenFrom = from
                utterances++
                _state.update { it.copy(telugu = part.telugu, recording = false) }
                speaker.speak(currentId, part.text.substring(from), part.telugu, prefs.speed(part.telugu), voiceFor(part.telugu), prefs.pitch(part.telugu))
            }
            is Recorded -> {
                // From where it was paused, should the player have let it go; else from the verse's start.
                val from = recordedResume ?: part.at.from
                if (partIndex == 0 && recordedResume == null) verseStartedAt = clock()
                recordedResume = null
                crossing?.cancel()
                plays++
                reached = from
                _state.update { it.copy(telugu = part.telugu, recording = true) }
                val to = (part.ahead.lastOrNull()?.at ?: part.at).end
                recorder.play(plays, part.at.file, from, to, prefs.speed(part.telugu), part.ahead.map { it.at.start })
            }
        }
    }

    // ---- what the recording's player reports ----

    override fun onReached(id: Long, seconds: Double) {
        if (id != plays || session == null) return
        reached = maxOf(reached, seconds)
        if (crossing?.isActive != true) crossing = scope.launch { moveOn(id) }
    }

    override fun onEnded(id: Long) {
        if (id != plays) return
        // A recording heard in the settings, over: the player's let go.
        if (session == null) {
            recorder.release()
            return
        }
        reached = Double.POSITIVE_INFINITY
        val before = crossing
        crossing = scope.launch {
            before?.join()
            if (!moveOn(id)) return@launch
            // Played to its end: the verse's next part (its Telugu, after its English), or the next verse.
            recordedResume = null
            if (partIndex + 1 < parts.size) {
                partIndex++
                if (_state.value.status == ReadAloudState.Status.PLAYING) speakPart()
            } else {
                advance(after = _state.value.verse)
            }
        }
    }

    override fun onFailed(id: Long) {
        if (id != plays || session == null) return
        val part = parts.getOrNull(partIndex) as? Recorded ?: return
        // Its file wouldn't play (moved or deleted since it was found): the verse is read by the phone's
        // voice instead, and the verses after it find the file left out.
        recordings.failed(part.at.file)
        parts = parts.toMutableList().also { it[partIndex] = Spoken(part.telugu, part.text) }
        recordedResume = null
        resumeAt = 0
        if (_state.value.status == ReadAloudState.Status.PLAYING) speakPart()
    }

    // The verse moves on through those the recording has played into so far, the reading kept in step
    // (its next verse each time). False when the reading went elsewhere, or stopped, on the way.
    private suspend fun moveOn(id: Long): Boolean {
        while (true) {
            if (id != plays) return false
            val current = session ?: return false
            val part = parts.getOrNull(partIndex) as? Recorded ?: return true
            val next = part.ahead.firstOrNull() ?: return true
            if (next.at.start > reached + MARK_SLACK) return true
            val verse = _state.value.verse ?: return false
            val found = current.next(verse)
            if (id != plays || session !== current) return false
            if (found == null || found.book != next.verse.book || found.chapter != next.verse.chapter || found.verse != next.verse.verse) {
                // Somewhere else after all: what's playing stops, and that's read instead.
                stopRecording()
                if (found == null) end(current, finished = true) else readFrom(found)
                return false
            }
            parts = listOf(Recorded(part.telugu, next.text, next.at, part.ahead.drop(1)))
            partIndex = 0
            verseStartedAt = clock()
            _state.update { it.copy(verse = found) }
        }
    }

    override fun onRange(id: String, start: Int) {
        if (id == currentId) resumeAt = spokenFrom + start
    }

    override fun onDone(id: String) {
        if (id != currentId || _state.value.status != ReadAloudState.Status.PLAYING) return
        resumeAt = 0
        if (partIndex + 1 < parts.size) {
            partIndex++
            speakPart()
        } else {
            advance(after = _state.value.verse)
        }
    }

    override fun onError(id: String) {
        if (id != currentId) return
        val part = parts.getOrNull(partIndex) ?: return
        if (part.telugu) {
            // No Telugu voice after all: Telugu is left out from here on (and with Telugu alone, it stops,
            // unless it's played from its recording, this verse just missing from it).
            teluguFailed = true
            _state.update { it.copy(noTeluguVoice = true) }
            if (prefs.language == ReadLanguage.TELUGU && !reads(telugu = true)) {
                session?.let { end(it, finished = false, noTeluguVoice = true) }
            } else {
                onDone(id)
            }
        } else {
            val current = session
            halt()
            endSleep()
            session = null
            _state.value = ReadAloudState(noVoice = true)
            current?.onStopped(finished = false)
        }
    }

    private companion object {
        const val MEASURE_AFTER_MS = 2_000L

        // A headset's "previous" this soon after a verse began goes back to the one before; later, it
        // starts the verse again.
        const val BACK_WITHIN_MS = 3_000L

        // Verses in a row with nothing to read before giving up (a list of verses with no Telugu at all).
        const val MAX_SKIPPED = 50

        // A sleep timer fades the reading out over this long, in steps this far apart.
        const val FADE_MS = 8_000L
        const val FADE_STEP_MS = 250L

        // A word said on its own goes a little slower, to be heard clearly.
        const val WORD_RATE = 0.8f

        // A mark reached counts for a verse starting this close after it (seconds), however it's rounded.
        const val MARK_SLACK = 0.01

        // The verse a recording is heard by in the settings.
        val SAMPLE_VERSE = VerseToRead("John", 3, 16, "", null)
    }
}
