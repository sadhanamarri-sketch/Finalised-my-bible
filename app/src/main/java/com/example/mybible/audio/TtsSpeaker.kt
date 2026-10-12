package com.example.mybible.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.io.File
import java.util.Locale

/**
 * The phone's own speech engine (Google's, on most phones), started the first time it's needed rather
 * than with the app. Speaking waits for it to start; everything it reports comes back on the main
 * thread. English is read in the phone's own kind of English when it has one (an Indian English voice
 * on a phone set to English (India)), otherwise in American English.
 */
class TtsSpeaker(context: Context) : Speaker {
    private val appContext = context.applicationContext

    override var listener: Speaker.Listener? = null

    private val main = Handler(Looper.getMainLooper())

    // A connection to the speech engine, started as it's made. The one that records samples into files
    // (see VoiceDepths) is a second, with its own voice, settings and queue, so it never cuts into reading.
    private inner class Connection(val recording: Boolean) {
        var ready: Boolean? = null
            private set

        // What was asked of it before it had started: done once it has, or, if it can't, failed.
        private val waiting = ArrayDeque<Pair<() -> Unit, () -> Unit>>()

        val tts: TextToSpeech = TextToSpeech(appContext) { status -> main.post { started(status) } }

        private fun started(status: Int) {
            ready = status == TextToSpeech.SUCCESS
            if (ready == true) setUp()
            val queued = waiting.toList()
            waiting.clear()
            queued.forEach { (say, fail) -> if (ready == true) say() else fail() }
        }

        private fun setUp() {
            if (!recording) {
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit

                override fun onDone(utteranceId: String) {
                    main.post { if (recording) recordings.remove(utteranceId)?.invoke(true) else listener?.onDone(utteranceId) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) = failed(utteranceId)

                override fun onError(utteranceId: String, errorCode: Int) = failed(utteranceId)

                // Our own stop(): nothing to report.
                override fun onStop(utteranceId: String, interrupted: Boolean) = Unit

                override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                    if (!recording) main.post { listener?.onRange(utteranceId, start) }
                }
            })
        }

        private fun failed(utteranceId: String) {
            main.post { if (recording) recordings.remove(utteranceId)?.invoke(false) else listener?.onError(utteranceId) }
        }

        fun whenReady(say: () -> Unit, fail: () -> Unit) {
            when (ready) {
                true -> say()
                null -> waiting.addLast(say to fail)
                false -> fail()
            }
        }

        fun voices(): Set<Voice> = try {
            tts.voices.orEmpty()
        } catch (e: Exception) {
            // Some engines throw here rather than return nothing.
            emptySet()
        }

        fun stop() {
            waiting.clear()
            if (ready == true) tts.stop()
        }

        fun shutdown() {
            waiting.clear()
            tts.shutdown()
        }
    }

    // The connection read with, and the one recording samples while the voices are measured.
    private var reading: Connection? = null
    private var recorder: Connection? = null
    private val recordings = HashMap<String, (Boolean) -> Unit>()
    private var recordingCount = 0

    // The engine, starting it if it hasn't been yet.
    private fun engine(): Connection = reading ?: Connection(recording = false).also { reading = it }

    /** Whether the phone can speak at all: null while its speech engine is starting. */
    override val ready: Boolean?
        get() = engine().ready

    override fun hasTelugu(): Boolean? = hasVoiceFor(TELUGU)

    override fun speak(id: String, text: String, telugu: Boolean, rate: Float, voice: String?, pitch: Float) {
        val c = engine()
        c.whenReady(say = {
            // The voice chosen for the language, else the language's usual voice.
            val chosen = voice?.let { name -> c.voices().firstOrNull { it.name == name } }
            val set = chosen?.let { c.tts.setVoice(it) }?.takeIf { it == TextToSpeech.SUCCESS }
                ?: c.tts.setLanguage(if (telugu) TELUGU else english())
            if (set < 0) {
                listener?.onError(id)
            } else {
                c.tts.setSpeechRate(rate)
                // Always set: left alone, it's the phone's own speech pitch, which may have been raised.
                c.tts.setPitch(pitch)
                if (c.tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) listener?.onError(id)
            }
        }, fail = { listener?.onError(id) })
    }

    override fun hasVoiceFor(locale: Locale): Boolean? {
        val c = engine()
        if (c.ready != true) return c.ready
        return c.tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
    }

    override fun speakIn(id: String, text: String, locale: Locale, rate: Float, pitch: Float) {
        val c = engine()
        c.whenReady(say = {
            if (c.tts.setLanguage(locale) < 0) {
                listener?.onError(id)
            } else {
                c.tts.setSpeechRate(rate)
                c.tts.setPitch(pitch)
                if (c.tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) listener?.onError(id)
            }
        }, fail = { listener?.onError(id) })
    }

    override fun stop() {
        reading?.stop()
    }

    override fun record(text: String, voice: String, file: File, onSaid: (Boolean) -> Unit) {
        val c = recorder ?: Connection(recording = true).also { recorder = it }
        c.whenReady(say = {
            val chosen = c.voices().firstOrNull { it.name == voice }
            if (chosen == null || c.tts.setVoice(chosen) != TextToSpeech.SUCCESS) {
                onSaid(false)
            } else {
                c.tts.setPitch(1f)
                c.tts.setSpeechRate(1f)
                val id = "sample-${++recordingCount}"
                recordings[id] = onSaid
                if (c.tts.synthesizeToFile(text, Bundle(), file, id) != TextToSpeech.SUCCESS) recordings.remove(id)?.invoke(false)
            }
        }, fail = { onSaid(false) })
    }

    override fun doneRecording() {
        recorder?.shutdown()
        recorder = null
        val unanswered = recordings.values.toList()
        recordings.clear()
        unanswered.forEach { it(false) }
    }

    override fun voices(telugu: Boolean): List<VoiceOption> {
        val c = engine()
        if (c.ready != true) return emptyList()
        val language = if (telugu) TELUGU.language else "en"
        val installed = c.voices()
            .filter { it.locale.language == language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
        // Google's engine lists most voices twice, to use offline and online (en-in-x-ene-local, -network):
        // the same voice, so the online one only when there's no offline one.
        val offline = installed.filterNot { it.isNetworkConnectionRequired }.mapTo(HashSet()) { it.name }
        val found = installed
            .filterNot { it.isNetworkConnectionRequired && it.name.replace("-network", "-local") in offline }
            .sortedWith(compareBy<Voice>({ it.locale.country != english().country }, { it.locale.displayCountry }, { it.isNetworkConnectionRequired }, { it.name }))
        // Google's own names mean little (en-us-x-sfg-local): "United States 1", "India 2". Another
        // phone's engine's are usually names to go by.
        val counts = HashMap<String, Int>()
        return found.map { v ->
            val label = if (googleName.matches(v.name)) {
                val place = v.locale.getDisplayCountry(Locale.ENGLISH).ifBlank { v.locale.getDisplayLanguage(Locale.ENGLISH) }
                val n = (counts[place] ?: 0) + 1
                counts[place] = n
                "$place $n"
            } else {
                v.name.replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.uppercase() }
            }
            VoiceOption(v.name, label, v.isNetworkConnectionRequired)
        }
    }

    private fun english(): Locale = Locale.getDefault().takeIf { it.language == "en" } ?: Locale.US

    private companion object {
        val TELUGU: Locale = Locale("te", "IN")

        // A voice name as Google's engine gives it: en-us-x-sfg-local, te-in-x-tem-network.
        val googleName = Regex("[a-z]{2,3}-[a-z]{2}-x-[a-z0-9]{3,4}-(local|network)")
    }
}
