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

    override var ready: Boolean? = null
        private set

    private val main = Handler(Looper.getMainLooper())

    // What was asked to be said before the engine had started: said once it has, or, if it can't, failed.
    private class Waiting(val id: String, val say: () -> Unit)
    private val waiting = ArrayDeque<Waiting>()

    private var engine: TextToSpeech? = null

    private val tts: TextToSpeech get() = start()

    // The engine, starting it if it hasn't been yet.
    private fun start(): TextToSpeech =
        engine ?: TextToSpeech(appContext) { status ->
            main.post {
                ready = status == TextToSpeech.SUCCESS
                if (ready == true) setUp()
                val queued = waiting.toList()
                waiting.clear()
                queued.forEach { if (ready == true) it.say() else listener?.onError(it.id) }
            }
        }.also { engine = it }

    private fun setUp() {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit

            override fun onDone(utteranceId: String) {
                main.post { listener?.onDone(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                main.post { listener?.onError(utteranceId) }
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                main.post { listener?.onError(utteranceId) }
            }

            // Our own stop(): nothing to report.
            override fun onStop(utteranceId: String, interrupted: Boolean) = Unit

            override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                main.post { listener?.onRange(utteranceId, start) }
            }
        })
    }

    private fun whenReady(id: String, say: () -> Unit) {
        when (ready) {
            true -> say()
            null -> {
                waiting.addLast(Waiting(id, say))
                start()
            }
            false -> listener?.onError(id)
        }
    }

    override fun hasTelugu(): Boolean? {
        if (ready != true) {
            start()
            return ready
        }
        return tts.isLanguageAvailable(TELUGU) >= TextToSpeech.LANG_AVAILABLE
    }

    override fun speak(id: String, text: String, telugu: Boolean, rate: Float, voice: String?, pitch: Float) {
        whenReady(id) {
            // The voice chosen for the language, else the language's usual voice.
            val chosen = voice?.let { name -> voicesOrEmpty().firstOrNull { it.name == name } }
            val set = chosen?.let { tts.setVoice(it) }?.takeIf { it == TextToSpeech.SUCCESS }
                ?: tts.setLanguage(if (telugu) TELUGU else english())
            if (set < 0) {
                listener?.onError(id)
                return@whenReady
            }
            tts.setSpeechRate(rate)
            // Always set: left alone, it's the phone's own speech pitch, which may have been raised.
            tts.setPitch(pitch)
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) listener?.onError(id)
        }
    }

    override fun hasVoiceFor(locale: Locale): Boolean? {
        if (ready != true) {
            start()
            return ready
        }
        return tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
    }

    override fun speakIn(id: String, text: String, locale: Locale, rate: Float, pitch: Float) {
        whenReady(id) {
            if (tts.setLanguage(locale) < 0) {
                listener?.onError(id)
                return@whenReady
            }
            tts.setSpeechRate(rate)
            tts.setPitch(pitch)
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) listener?.onError(id)
        }
    }

    override fun stop() {
        waiting.clear()
        if (ready == true) engine?.stop()
    }

    // A second connection to the speech engine, for saying samples into files to measure the voices
    // (see VoiceDepths): with its own voice, settings and queue, it never cuts into what's being read.
    private var recorder: TextToSpeech? = null
    private var recorderReady: Boolean? = null
    private val recorderWaiting = ArrayDeque<() -> Unit>()
    private val recordings = HashMap<String, (Boolean) -> Unit>()
    private var recordingCount = 0

    override fun record(text: String, voice: String, file: File, onSaid: (Boolean) -> Unit) {
        val say: () -> Unit = say@{
            val engine = recorder
            if (recorderReady != true || engine == null) {
                onSaid(false)
                return@say
            }
            val chosen = try {
                engine.voices.orEmpty().firstOrNull { it.name == voice }
            } catch (e: Exception) {
                null
            }
            if (chosen == null || engine.setVoice(chosen) != TextToSpeech.SUCCESS) {
                onSaid(false)
                return@say
            }
            engine.setPitch(1f)
            engine.setSpeechRate(1f)
            val id = "sample-${++recordingCount}"
            recordings[id] = onSaid
            if (engine.synthesizeToFile(text, Bundle(), file, id) != TextToSpeech.SUCCESS) recordings.remove(id)?.invoke(false)
        }
        if (recorder == null) {
            recorder = TextToSpeech(appContext) { status ->
                main.post {
                    if (recorder == null) return@post
                    recorderReady = status == TextToSpeech.SUCCESS
                    if (recorderReady == true) recorder?.setOnUtteranceProgressListener(recordingListener)
                    val queued = recorderWaiting.toList()
                    recorderWaiting.clear()
                    queued.forEach { it() }
                }
            }
        }
        if (recorderReady == null) recorderWaiting.addLast(say) else say()
    }

    private val recordingListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = Unit

        override fun onDone(utteranceId: String) {
            main.post { recordings.remove(utteranceId)?.invoke(true) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            main.post { recordings.remove(utteranceId)?.invoke(false) }
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            main.post { recordings.remove(utteranceId)?.invoke(false) }
        }
    }

    override fun doneRecording() {
        recorder?.shutdown()
        recorder = null
        recorderReady = null
        recorderWaiting.clear()
        val unanswered = recordings.values.toList()
        recordings.clear()
        unanswered.forEach { it(false) }
    }

    override fun voices(telugu: Boolean): List<VoiceOption> {
        if (ready != true) {
            start()
            return emptyList()
        }
        val language = if (telugu) TELUGU.language else "en"
        val installed = voicesOrEmpty()
            .filter { it.locale.language == language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
        // Google's engine lists most voices twice, to use offline and online (en-in-x-ene-local, -network):
        // the same voice, so the online one only when there's no offline one.
        val offline = installed.filterNot { it.isNetworkConnectionRequired }.mapTo(HashSet()) { it.name }
        val found = installed
            .filterNot { it.isNetworkConnectionRequired && it.name.replace("-network", "-local") in offline }
            .sortedWith(compareBy<Voice>({ it.locale.country != english().country }, { it.locale.displayCountry }, { it.isNetworkConnectionRequired }, { it.name }))
        // "United States 1", "India 2": the engine's own names (en-us-x-sfg-local) mean little.
        val counts = HashMap<String, Int>()
        return found.map { v ->
            val place = v.locale.getDisplayCountry(Locale.ENGLISH).ifBlank { v.locale.getDisplayLanguage(Locale.ENGLISH) }
            val n = (counts[place] ?: 0) + 1
            counts[place] = n
            VoiceOption(v.name, "$place $n", v.isNetworkConnectionRequired)
        }
    }

    private fun voicesOrEmpty(): Set<Voice> = try {
        tts.voices.orEmpty()
    } catch (e: Exception) {
        // Some engines throw here rather than return nothing.
        emptySet()
    }

    private fun english(): Locale = Locale.getDefault().takeIf { it.language == "en" } ?: Locale.US

    private companion object {
        val TELUGU: Locale = Locale("te", "IN")
    }
}
