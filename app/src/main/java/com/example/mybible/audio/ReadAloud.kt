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

    companion object {
        fun from(name: String?): ReadLanguage = entries.firstOrNull { it.name == name } ?: ENGLISH
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

    /** Verse Scroll: once a card has been read, move on to the next and read that. */
    val continueReading: Boolean
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
    /** The phone has no Telugu voice, so Telugu was left out — or, with Telugu alone, nothing could be read. */
    val noTeluguVoice: Boolean = false,
    /** The phone has no voice at all (no speech engine). */
    val noVoice: Boolean = false
) {
    enum class Status { IDLE, PLAYING, PAUSED }

    val active: Boolean get() = status != Status.IDLE
}

/**
 * Reads verses aloud in the phone's own voice, one at a time: each in English, in Telugu, or in English
 * and then Telugu, as the settings say. One reading at a time — starting another stops the first. A
 * pause stops the voice mid-verse and picks up again at the word it got to. Everything runs on the main
 * thread ([scope]'s).
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
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }
) : Speaker.Listener {

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

    // The Telugu voice failed during this reading (say, an online voice with no connection).
    private var teluguFailed = false

    private data class Part(val telugu: Boolean, val text: String)

    init {
        speaker.listener = this
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
        utterances++
        speaker.speak("try-$utterances", text, telugu, prefs.speed(telugu), voice, prefs.pitch(telugu))
    }

    /** Whether the phone has a Telugu voice (null: not known yet). */
    fun hasTeluguVoice(): Boolean? = if (teluguFailed) false else speaker.hasTelugu()

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
        utterances++
        speaker.speak("word-$utterances", spoken, telugu = false, rate = WORD_RATE, voice = voiceFor(telugu = false), pitch = prefs.pitch(telugu = false))
        return ready
    }

    /** A few words in the language's voice, speed and pitch, to hear them: only while nothing is being read. */
    fun trySpeaking(text: String, telugu: Boolean) {
        if (_state.value.active) return
        utterances++
        speaker.speak("try-$utterances", text, telugu, prefs.speed(telugu), voiceFor(telugu), prefs.pitch(telugu))
    }

    /** Starts reading [next] from its first verse, stopping whatever was being read. */
    fun start(next: ReadingSession) {
        val previous = session
        halt()
        session = null
        if (previous != null && previous !== next) previous.onStopped(finished = false)
        if (speaker.ready == false) {
            _state.value = ReadAloudState(noVoice = true)
            next.onStopped(finished = false)
            return
        }
        if (prefs.language == ReadLanguage.TELUGU && speaker.hasTelugu() == false) {
            _state.value = ReadAloudState(noTeluguVoice = true)
            next.onStopped(finished = false)
            return
        }
        session = next
        teluguFailed = false
        _state.value = ReadAloudState(status = ReadAloudState.Status.PLAYING, place = next.place, title = next.title)
        onPlaying()
        advance(after = null)
    }

    fun pause() {
        val current = session ?: return
        if (_state.value.status != ReadAloudState.Status.PLAYING) return
        utterances++
        speaker.stop()
        _state.update { it.copy(status = ReadAloudState.Status.PAUSED) }
        current.onPaused()
    }

    fun resume() {
        val current = session ?: return
        if (_state.value.status != ReadAloudState.Status.PAUSED) return
        _state.update { it.copy(status = ReadAloudState.Status.PLAYING) }
        onPlaying()
        current.onResumed()
        when {
            // Still finding the next verse: it's read as soon as it's found.
            advancing?.isActive == true -> Unit
            parts.isNotEmpty() -> speakPart()
            else -> advance(after = _state.value.verse)
        }
    }

    fun stop() {
        val current = session
        halt()
        session = null
        _state.value = ReadAloudState()
        current?.onStopped(finished = false)
        measureSoon()
    }

    /**
     * A headset's "next" (a double tap on most earbuds), or the notification's: on to the next verse —
     * in Verse Scroll, the next card — from its start. Paused, it waits there to be played.
     */
    fun skipToNext() {
        val verse = skippableVerse() ?: return
        advance(after = verse) { reading, from -> from?.let { reading.skip(it) } }
    }

    /**
     * A headset's "previous" (a triple tap on most earbuds), or the notification's: the verse being read
     * again from its start — or, in its first few seconds, the verse before it (in Verse Scroll, the
     * card before), as a music player's "previous" goes back a song.
     */
    fun skipToPrevious() {
        val verse = skippableVerse() ?: return
        if (clock() - verseStartedAt > BACK_WITHIN_MS) {
            partIndex = 0
            resumeAt = 0
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
        return verse
    }

    /** A setting changed (the speed, a voice): what's being said starts again, from the word it got to. */
    fun refresh() {
        if (_state.value.status != ReadAloudState.Status.PLAYING || parts.isEmpty() || advancing?.isActive == true) return
        utterances++
        speaker.stop()
        speakPart()
    }

    private fun halt() {
        advancing?.cancel()
        advancing = null
        utterances++
        speaker.stop()
        parts = emptyList()
        partIndex = 0
        resumeAt = 0
    }

    // Reads the verse after [after] in the reading (its first, without one), or ends the reading. [step]
    // finds it: the reading's next verse, or for a headset's next or previous, the one to skip to.
    private fun advance(
        after: VerseToRead?,
        step: suspend (ReadingSession, VerseToRead?) -> VerseToRead? = { reading, from -> reading.next(from) }
    ) {
        val current = session ?: return
        parts = emptyList()
        partIndex = 0
        resumeAt = 0
        advancing = scope.launch {
            var previous = after
            var skipped = 0
            while (true) {
                val verse = step(current, previous)
                if (session !== current) return@launch
                if (verse == null) {
                    end(current, finished = true)
                    return@launch
                }
                val read = partsOf(verse)
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
                parts = read
                verseStartedAt = clock()
                _state.update { it.copy(verse = verse, telugu = read[0].telugu) }
                break
            }
            if (_state.value.status == ReadAloudState.Status.PLAYING) speakPart()
        }
    }

    private fun end(current: ReadingSession, finished: Boolean, noTeluguVoice: Boolean = false) {
        halt()
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

    // The verse's text in each language it's read in, in order; null when it's Telugu alone and there's no
    // Telugu voice.
    private fun partsOf(verse: VerseToRead): List<Part>? {
        val language = prefs.language
        val english = SpeechText.english(verse.english).takeIf { it.isNotEmpty() }?.let { Part(false, it) }
        val teluguVoice = !teluguFailed && speaker.hasTelugu() != false
        if (language != ReadLanguage.ENGLISH && !teluguVoice) _state.update { it.copy(noTeluguVoice = true) }
        val telugu = verse.telugu?.let(SpeechText::telugu)?.takeIf { it.isNotEmpty() && teluguVoice }?.let { Part(true, it) }
        return when (language) {
            ReadLanguage.ENGLISH -> listOfNotNull(english)
            ReadLanguage.TELUGU -> if (teluguVoice) listOfNotNull(telugu) else null
            ReadLanguage.BOTH -> listOfNotNull(english, telugu)
        }
    }

    private fun speakPart() {
        val part = parts.getOrNull(partIndex) ?: return
        val from = resumeAt.coerceIn(0, part.text.length)
        if (partIndex == 0 && from == 0) verseStartedAt = clock()
        spokenFrom = from
        utterances++
        _state.update { it.copy(telugu = part.telugu) }
        speaker.speak(currentId, part.text.substring(from), part.telugu, prefs.speed(part.telugu), voiceFor(part.telugu), prefs.pitch(part.telugu))
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
            // No Telugu voice after all: Telugu is left out from here on (and with Telugu alone, it stops).
            teluguFailed = true
            _state.update { it.copy(noTeluguVoice = true) }
            if (prefs.language == ReadLanguage.TELUGU) {
                session?.let { end(it, finished = false, noTeluguVoice = true) }
            } else {
                onDone(id)
            }
        } else {
            val current = session
            halt()
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

        // A word said on its own goes a little slower, to be heard clearly.
        const val WORD_RATE = 0.8f
    }
}
