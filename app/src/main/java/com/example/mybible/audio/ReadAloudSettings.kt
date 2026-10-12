package com.example.mybible.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Reading aloud's settings, kept between visits: the language, the speed, the pitch, and for each
 * language the speech engine and the voice.
 */
class ReadAloudSettings(context: Context) : ReadAloudPrefs {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var language by mutableStateOf(ReadLanguage.from(prefs.getString(KEY_LANGUAGE, null)))
        private set

    override var speed by mutableFloatStateOf(prefs.getFloat(KEY_SPEED, DEFAULT_SPEED).coerceIn(MIN_SPEED, MAX_SPEED))
        private set

    override var pitch by mutableFloatStateOf(prefs.getFloat(KEY_PITCH, DEFAULT_PITCH).coerceIn(MIN_PITCH, MAX_PITCH))
        private set

    // The speech engine for each language (its package): null, the phone's usual one.
    var englishEngine by mutableStateOf(prefs.getString(KEY_ENGLISH_ENGINE, null))
        private set

    var teluguEngine by mutableStateOf(prefs.getString(KEY_TELUGU_ENGINE, null))
        private set

    var englishVoice by mutableStateOf(prefs.getString(KEY_ENGLISH_VOICE, null))
        private set

    var teluguVoice by mutableStateOf(prefs.getString(KEY_TELUGU_VOICE, null))
        private set

    override var continueReading by mutableStateOf(prefs.getBoolean(KEY_CONTINUE, false))
        private set

    override fun voice(telugu: Boolean): String? = if (telugu) teluguVoice else englishVoice

    override fun engine(telugu: Boolean): String? = if (telugu) teluguEngine else englishEngine

    fun updateLanguage(value: ReadLanguage) {
        language = value
        prefs.edit().putString(KEY_LANGUAGE, value.name).apply()
    }

    fun updateSpeed(value: Float) {
        speed = value.coerceIn(MIN_SPEED, MAX_SPEED)
        prefs.edit().putFloat(KEY_SPEED, speed).apply()
    }

    fun updatePitch(value: Float) {
        pitch = value.coerceIn(MIN_PITCH, MAX_PITCH)
        prefs.edit().putFloat(KEY_PITCH, pitch).apply()
    }

    /**
     * The speech engine a language is read with ([engine] its package, null the phone's usual one). A
     * voice chosen before was the other engine's, so it goes.
     */
    fun updateEngine(telugu: Boolean, engine: String?) {
        if (telugu) teluguEngine = engine else englishEngine = engine
        prefs.edit().putString(if (telugu) KEY_TELUGU_ENGINE else KEY_ENGLISH_ENGINE, engine).apply()
        updateVoice(telugu, null)
    }

    /** [name] null: the deepest of the phone's voices for the language (see VoiceDepths), or its usual one. */
    fun updateVoice(telugu: Boolean, name: String?) {
        if (telugu) teluguVoice = name else englishVoice = name
        prefs.edit().putString(if (telugu) KEY_TELUGU_VOICE else KEY_ENGLISH_VOICE, name).apply()
    }

    fun updateContinueReading(on: Boolean) {
        continueReading = on
        prefs.edit().putBoolean(KEY_CONTINUE, on).apply()
    }

    companion object {
        const val PREFS_NAME = "read_aloud"

        // Speed and pitch: 1 is the voice's own; the sliders go in steps of 0.05.
        const val DEFAULT_SPEED = 1f
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3f
        // Lower than the voice's own: the phone's usual voices tend to sound shrill reading at length.
        const val DEFAULT_PITCH = 0.85f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 1.5f

        private const val KEY_LANGUAGE = "language"
        private const val KEY_SPEED = "speed"
        private const val KEY_PITCH = "pitch"
        private const val KEY_ENGLISH_ENGINE = "english_engine"
        private const val KEY_TELUGU_ENGINE = "telugu_engine"
        private const val KEY_ENGLISH_VOICE = "english_voice"
        private const val KEY_TELUGU_VOICE = "telugu_voice"
        private const val KEY_CONTINUE = "continue_reading"
    }
}
