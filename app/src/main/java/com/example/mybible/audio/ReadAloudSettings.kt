package com.example.mybible.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Reading aloud's settings, kept between visits: the language, the speed, the pitch, a voice for each language. */
class ReadAloudSettings(context: Context) : ReadAloudPrefs {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var language by mutableStateOf(ReadLanguage.from(prefs.getString(KEY_LANGUAGE, null)))
        private set

    override var speed by mutableFloatStateOf(prefs.getFloat(KEY_SPEED, 1f).coerceIn(MIN_SPEED, MAX_SPEED))
        private set

    override var pitch by mutableFloatStateOf(prefs.getFloat(KEY_PITCH, DEFAULT_PITCH).coerceIn(MIN_PITCH, 1f))
        private set

    var englishVoice by mutableStateOf(prefs.getString(KEY_ENGLISH_VOICE, null))
        private set

    var teluguVoice by mutableStateOf(prefs.getString(KEY_TELUGU_VOICE, null))
        private set

    override var continueReading by mutableStateOf(prefs.getBoolean(KEY_CONTINUE, false))
        private set

    override fun voice(telugu: Boolean): String? = if (telugu) teluguVoice else englishVoice

    fun updateLanguage(value: ReadLanguage) {
        language = value
        prefs.edit().putString(KEY_LANGUAGE, value.name).apply()
    }

    fun updateSpeed(value: Float) {
        speed = value.coerceIn(MIN_SPEED, MAX_SPEED)
        prefs.edit().putFloat(KEY_SPEED, speed).apply()
    }

    fun updatePitch(value: Float) {
        pitch = value.coerceIn(MIN_PITCH, 1f)
        prefs.edit().putFloat(KEY_PITCH, pitch).apply()
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
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2f

        /** The speeds offered. */
        val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f)

        /** The pitches offered, the deepest first: the voice's own is 1. */
        val PITCHES = listOf(0.75f to "Deep", 0.85f to "Lower", 1f to "Usual")

        // Lower than the voice's own: the phone's usual voices tend to sound shrill reading at length.
        const val DEFAULT_PITCH = 0.85f
        const val MIN_PITCH = 0.5f

        private const val KEY_LANGUAGE = "language"
        private const val KEY_SPEED = "speed"
        private const val KEY_PITCH = "pitch"
        private const val KEY_ENGLISH_VOICE = "english_voice"
        private const val KEY_TELUGU_VOICE = "telugu_voice"
        private const val KEY_CONTINUE = "continue_reading"
    }
}
