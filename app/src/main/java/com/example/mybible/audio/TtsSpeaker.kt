package com.example.mybible.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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

    override fun speak(id: String, text: String, telugu: Boolean, rate: Float, voice: String?) {
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
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) listener?.onError(id)
        }
    }

    override fun stop() {
        waiting.clear()
        if (ready == true) engine?.stop()
    }

    override fun voices(telugu: Boolean): List<VoiceOption> {
        if (ready != true) {
            start()
            return emptyList()
        }
        val language = if (telugu) TELUGU.language else "en"
        val found = voicesOrEmpty()
            .filter { it.locale.language == language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .sortedWith(compareBy<Voice>({ it.locale.country != english().country }, { it.locale.displayCountry }, { it.isNetworkConnectionRequired }, { it.name }))
        // "United States 1", "India 2 · online": the engine's own names (en-us-x-sfg-local) mean little.
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
