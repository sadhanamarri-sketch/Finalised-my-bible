package com.example.mybible.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * How deep each of the phone's voices is: its speaking pitch (see SpeakingPitch), measured once by
 * having it say a few words into a file, and kept. The settings list the voices from the deepest, and
 * reading aloud is in the deepest voice that works offline unless another was chosen — rather than the
 * phone's usual voice, which is often a high one.
 *
 * Kept as text by name ([saved], handed to [save] after each voice): "depth:<voice>" its pitch in Hz,
 * or -1 when it couldn't be measured; "deepest:en" and "deepest:te" the deepest offline voice for each
 * language.
 */
class VoiceDepths(
    saved: Map<String, String> = emptyMap(),
    private val save: (Map<String, String>) -> Unit = {},
    // Where the samples are said into; null, and nothing is measured.
    private val workDir: File? = null,
    private val pitchOf: (File) -> Float? = SpeakingPitch::of,
    private val compute: CoroutineDispatcher = Dispatchers.Default
) {
    private val kept = HashMap(saved)

    private val _changes = MutableStateFlow(0)

    /** Goes up each time a voice has been measured. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private val _measuring = MutableStateFlow(false)
    val measuring: StateFlow<Boolean> = _measuring.asStateFlow()

    /** The voice's speaking pitch in Hz, or null if it isn't known (an online voice isn't measured). */
    fun depthOf(voice: VoiceOption): Float? = hz(voice.name)

    private fun hz(name: String): Float? = kept[DEPTH + name]?.toFloatOrNull()?.takeIf { it > 0f }

    /** [voices] with their depths, the deepest first; those not measured after, as they were. */
    fun sorted(voices: List<VoiceOption>): List<VoiceOption> =
        voices.map { it.copy(depth = depthOf(it)) }.sortedWith(compareBy(nullsLast()) { it.depth })

    /** The deepest offline voice measured for the language, to read in when none was chosen. */
    fun deepest(telugu: Boolean): String? = kept[if (telugu) DEEPEST_TELUGU else DEEPEST_ENGLISH]

    /** Whether voices can be measured here at all (there's somewhere to record them). */
    val canMeasure: Boolean get() = workDir != null

    /** Whether some of these offline voices haven't been measured yet. */
    fun unmeasured(voices: List<VoiceOption>): Boolean = voices.any { !it.online && DEPTH + it.name !in kept }

    /**
     * Has each offline voice not yet measured say a few words into a file, one at a time, and
     * measures how deep it is — stopping early when [canGoOn] says to (a reading started), to go on
     * another time.
     */
    fun measure(speaker: Speaker, english: List<VoiceOption>, telugu: List<VoiceOption>, scope: CoroutineScope, canGoOn: () -> Boolean) {
        val dir = workDir ?: return
        if (_measuring.value) return
        // The deepest of these, as measured before: a voice may have gone from the phone since.
        val before = HashMap(kept)
        kept.choose(DEEPEST_ENGLISH, english)
        kept.choose(DEEPEST_TELUGU, telugu)
        if (kept != before) {
            save(HashMap(kept))
            _changes.value++
        }
        val queue = ArrayDeque(
            (english.map { it to false } + telugu.map { it to true }).filter { (voice, _) -> !voice.online && DEPTH + voice.name !in kept }
        )
        if (queue.isEmpty()) return
        _measuring.value = true
        var sampleCount = 0

        fun finish() {
            speaker.doneRecording()
            _measuring.value = false
        }

        fun next() {
            val (voice, isTelugu) = queue.removeFirstOrNull() ?: return finish()
            if (!canGoOn()) return finish()
            val file = File(dir, "voice-sample-${++sampleCount}.wav")
            var answered = false
            fun measured(hz: Float?) {
                file.delete()
                kept[DEPTH + voice.name] = (hz ?: FAILED).toString()
                kept.choose(DEEPEST_ENGLISH, english)
                kept.choose(DEEPEST_TELUGU, telugu)
                save(HashMap(kept))
                _changes.value++
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
            speaker.record(if (isTelugu) TELUGU_SAMPLE else ENGLISH_SAMPLE, voice.name, file) { ok ->
                if (answered) return@record
                answered = true
                timeout.cancel()
                scope.launch {
                    val hz = if (ok) withContext(compute) { pitchOf(file) } else null
                    measured(hz)
                }
            }
        }
        next()
    }

    // [key] set to the deepest measured offline voice of [voices], or removed without one.
    private fun HashMap<String, String>.choose(key: String, voices: List<VoiceOption>) {
        val deepest = voices.filter { !it.online }.mapNotNull { v -> hz(v.name)?.let { v.name to it } }.minByOrNull { it.second }?.first
        if (deepest != null) this[key] = deepest else remove(key)
    }

    companion object {
        private const val DEPTH = "depth:"
        private const val DEEPEST_ENGLISH = "deepest:en"
        private const val DEEPEST_TELUGU = "deepest:te"
        private const val FAILED = -1f
        private const val TIMEOUT_MS = 15_000L

        // What each voice says to be measured: a few seconds of speech.
        const val ENGLISH_SAMPLE = "The Lord is my shepherd; I shall not want. He maketh me to lie down in green pastures."
        const val TELUGU_SAMPLE = "యెహోవా నా కాపరి నాకు లేమి కలుగదు. పచ్చికగల చోట్లను ఆయన నన్ను పరుండజేయు చున్నాడు."

        /** How a voice of [hz] sounds, for its label: Deep, Medium or High. */
        fun describe(hz: Float): String = when {
            hz < 155f -> "Deep"
            hz < 185f -> "Medium"
            else -> "High"
        }
    }
}
