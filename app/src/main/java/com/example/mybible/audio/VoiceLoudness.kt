package com.example.mybible.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * How loud the phone's voices are that the two languages are read in (see Loudness), so each can be
 * evened out against the other language (see Evening): each measured once by having it say a few words
 * into a file as it reads them — its speech engine, and the voice chosen or the language's usual one —
 * and kept, by engine and voice ("<engine>|<voice>", in LUFS). One that couldn't be measured is tried
 * again the next time the app starts.
 */
class VoiceLoudness(
    saved: Map<String, String> = emptyMap(),
    private val save: (Map<String, String>) -> Unit = {},
    // Where the samples are said into; null, and nothing is measured.
    private val workDir: File? = null,
    private val loudnessOf: (File) -> Double? = { file -> SpeakingPitch.readWav(file.readBytes())?.let(Loudness::of) },
    private val compute: CoroutineDispatcher = Dispatchers.Default
) {
    /** A language's voice as it's read: its engine (null, the phone's usual one) and voice (null, the language's usual one). */
    data class Voice(val telugu: Boolean, val engine: String?, val name: String?) {
        val key: String get() = "${engine ?: "usual"}|${name ?: if (telugu) "te" else "en"}"
    }

    private val kept = HashMap(saved)
    private val failed = HashSet<String>()

    /** Whether voices are being measured now. */
    var measuring = false
        private set

    /** How loud [voice] is, in LUFS; null when it hasn't been measured. */
    fun of(voice: Voice): Double? = kept[voice.key]?.toDoubleOrNull()

    /** Whether some of [voices] could be measured now: not yet measured, nor failed this time. */
    fun wanted(voices: List<Voice>): Boolean = workDir != null && voices.any { it.key !in kept && it.key !in failed }

    /** Measures those of [voices] that are [wanted], one after another; then [then]. */
    fun measure(speaker: Speaker, voices: List<Voice>, scope: CoroutineScope, then: () -> Unit = {}) {
        val dir = workDir
        val queue = ArrayDeque(voices.distinctBy { it.key }.filter { it.key !in kept && it.key !in failed })
        if (dir == null || measuring || queue.isEmpty()) return then()
        measuring = true
        var sampleCount = 0

        fun next() {
            val voice = queue.removeFirstOrNull()
            if (voice == null) {
                speaker.doneRecordingAsRead()
                measuring = false
                return then()
            }
            val file = File(dir, "loudness-sample-${++sampleCount}.wav")
            var answered = false
            fun measured(lufs: Double?) {
                file.delete()
                if (lufs != null) {
                    kept[voice.key] = lufs.toString()
                    save(HashMap(kept))
                } else {
                    failed += voice.key
                }
                next()
            }
            // An engine that never answers: on to the next.
            val timeout = scope.launch {
                delay(TIMEOUT_MS)
                if (!answered) {
                    answered = true
                    measured(null)
                }
            }
            val words = if (voice.telugu) VoiceDepths.TELUGU_SAMPLE else VoiceDepths.ENGLISH_SAMPLE
            speaker.recordAsRead(words, voice.telugu, voice.name, file) { ok ->
                if (answered) return@recordAsRead
                answered = true
                timeout.cancel()
                scope.launch { measured(if (ok) withContext(compute) { loudnessOf(file) } else null) }
            }
        }
        next()
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
