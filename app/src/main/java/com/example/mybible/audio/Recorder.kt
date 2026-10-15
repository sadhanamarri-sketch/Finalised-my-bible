package com.example.mybible.audio

/** One of a recording's files on the phone, as the player opens it: where it is ([uri]) and what kind of file. */
data class RecordingFile(val uri: String, val kind: MediaKind)

/**
 * A verse in a recording on the phone: its file, and in seconds where to play it from (a moment before
 * it [start]s, or a chapter's spoken heading), and where it ends.
 */
data class RecordedVerse(val file: RecordingFile, val from: Double, val start: Double, val end: Double)

/** The recordings on the phone, as reading aloud needs them (see Recordings; tests stand in for it). */
interface RecordedVerses {
    /** Whether a language's recording is there to play (some of its files, at least). */
    fun ready(telugu: Boolean): Boolean

    /**
     * [verse] in a language's recording, or null when it isn't there to play (no recording, its file
     * missing, or the verse not in it). With [heading], a chapter's first verse from its spoken heading.
     */
    suspend fun verse(verse: VerseToRead, telugu: Boolean, heading: Boolean): RecordedVerse?

    /** [file] wouldn't play: its verses are read by the phone's voice instead, until the folder's looked over again. */
    fun failed(file: RecordingFile) {}

    /** No recordings: everything is read by the phone's voice. */
    object None : RecordedVerses {
        override fun ready(telugu: Boolean) = false

        override suspend fun verse(verse: VerseToRead, telugu: Boolean, heading: Boolean): RecordedVerse? = null
    }
}

/** Plays stretches of the recordings on the phone (see RecordingPlayer; tests stand in for it). Used on the main thread. */
interface Recorder {
    interface Listener {
        /** The stretch [id] has played to its end. */
        fun onEnded(id: Long)

        /** The stretch [id] couldn't be played: its file gone, or unreadable. */
        fun onFailed(id: Long)

        /** The stretch [id] has reached [seconds], one of the marks it was given. */
        fun onReached(id: Long, seconds: Double)
    }

    var listener: Listener?

    /**
     * Plays [file] from [from] to [to] seconds, and nothing after; at [speed] (1: as recorded; the
     * narrator's pitch is kept either way). [marks] are places in between to be told of as they're
     * reached (where each verse after the first starts). [id] names it in what's reported about it.
     */
    fun play(id: Long, file: RecordingFile, from: Double, to: Double, speed: Float, marks: List<Double> = emptyList())

    fun pause()

    /** Plays on from where it was paused: false when there's nothing to play on (stopped or ended since). */
    fun resume(): Boolean

    fun stop()

    fun speed(speed: Float)

    /** Where in its file it's playing (or paused), in seconds; null when it isn't. */
    val position: Double?

    /** Done with for now: lets the phone's player go, until the next stretch. */
    fun release() {}

    /** Plays nothing: no recordings. */
    object None : Recorder {
        override var listener: Listener? = null

        override fun play(id: Long, file: RecordingFile, from: Double, to: Double, speed: Float, marks: List<Double>) {
            listener?.onFailed(id)
        }

        override fun pause() {}

        override fun resume() = false

        override fun stop() {}

        override fun speed(speed: Float) {}

        override val position: Double? = null
    }
}
