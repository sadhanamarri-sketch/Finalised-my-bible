package com.example.mybible.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Reading aloud's settings, kept between visits: the language, and for English and for Telugu each its
 * own speech engine, speed, pitch and voice, and whether it's played from its recording on the phone.
 */
class ReadAloudSettings(context: Context) : ReadAloudPrefs {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var language by mutableStateOf(ReadLanguage.from(prefs.getString(KEY_LANGUAGE, null)))
        private set

    // One speed and pitch for both, as kept before they were each language's, starts each one's.
    var englishSpeed by mutableFloatStateOf(kept(KEY_ENGLISH_SPEED, KEY_SPEED, DEFAULT_SPEED).coerceIn(MIN_SPEED, MAX_SPEED))
        private set

    var teluguSpeed by mutableFloatStateOf(kept(KEY_TELUGU_SPEED, KEY_SPEED, DEFAULT_SPEED).coerceIn(MIN_SPEED, MAX_SPEED))
        private set

    var englishPitch by mutableFloatStateOf(kept(KEY_ENGLISH_PITCH, KEY_PITCH, DEFAULT_PITCH).coerceIn(MIN_PITCH, MAX_PITCH))
        private set

    var teluguPitch by mutableFloatStateOf(kept(KEY_TELUGU_PITCH, KEY_PITCH, DEFAULT_PITCH).coerceIn(MIN_PITCH, MAX_PITCH))
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

    // Each language played from its recording on the phone (once there's one), rather than read by the phone's voice.
    var englishRecording by mutableStateOf(prefs.getBoolean(KEY_ENGLISH_RECORDING, false))
        private set

    var teluguRecording by mutableStateOf(prefs.getBoolean(KEY_TELUGU_RECORDING, false))
        private set

    private fun kept(key: String, before: String, default: Float): Float = prefs.getFloat(key, prefs.getFloat(before, default))

    override fun speed(telugu: Boolean): Float = if (telugu) teluguSpeed else englishSpeed

    override fun pitch(telugu: Boolean): Float = if (telugu) teluguPitch else englishPitch

    override fun voice(telugu: Boolean): String? = if (telugu) teluguVoice else englishVoice

    override fun engine(telugu: Boolean): String? = if (telugu) teluguEngine else englishEngine

    override fun recording(telugu: Boolean): Boolean = if (telugu) teluguRecording else englishRecording

    fun updateLanguage(value: ReadLanguage) {
        language = value
        prefs.edit().putString(KEY_LANGUAGE, value.name).apply()
    }

    fun updateSpeed(telugu: Boolean, value: Float) {
        val speed = value.coerceIn(MIN_SPEED, MAX_SPEED)
        if (telugu) teluguSpeed = speed else englishSpeed = speed
        prefs.edit().putFloat(if (telugu) KEY_TELUGU_SPEED else KEY_ENGLISH_SPEED, speed).apply()
    }

    fun updatePitch(telugu: Boolean, value: Float) {
        val pitch = value.coerceIn(MIN_PITCH, MAX_PITCH)
        if (telugu) teluguPitch = pitch else englishPitch = pitch
        prefs.edit().putFloat(if (telugu) KEY_TELUGU_PITCH else KEY_ENGLISH_PITCH, pitch).apply()
    }

    /**
     * The speech engine a language is read with ([engine] its package, null the phone's usual one). A
     * voice chosen before was the other engine's, so it goes.
     */
    fun updateEngine(telugu: Boolean, engine: String?) {
        if (engine == engine(telugu)) return
        if (telugu) teluguEngine = engine else englishEngine = engine
        prefs.edit().putString(if (telugu) KEY_TELUGU_ENGINE else KEY_ENGLISH_ENGINE, engine).apply()
        updateVoice(telugu, null)
    }

    /** [name] null: the deepest of the phone's voices for the language (see VoiceDepths), or its usual one. */
    fun updateVoice(telugu: Boolean, name: String?) {
        if (telugu) teluguVoice = name else englishVoice = name
        prefs.edit().putString(if (telugu) KEY_TELUGU_VOICE else KEY_ENGLISH_VOICE, name).apply()
    }

    fun updateRecording(telugu: Boolean, on: Boolean) {
        if (telugu) teluguRecording = on else englishRecording = on
        prefs.edit().putBoolean(if (telugu) KEY_TELUGU_RECORDING else KEY_ENGLISH_RECORDING, on).apply()
    }

    fun updateContinueReading(on: Boolean) {
        continueReading = on
        prefs.edit().putBoolean(KEY_CONTINUE, on).apply()
    }

    /** The Reader's one-line hint over its reading bar ("Tap a verse to hear it"), shown once. */
    var tapHintShown by mutableStateOf(prefs.getBoolean(KEY_TAP_HINT, false))
        private set

    fun markTapHintShown() {
        tapHintShown = true
        prefs.edit().putBoolean(KEY_TAP_HINT, true).apply()
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
        private const val KEY_ENGLISH_SPEED = "english_speed"
        private const val KEY_TELUGU_SPEED = "telugu_speed"
        private const val KEY_ENGLISH_PITCH = "english_pitch"
        private const val KEY_TELUGU_PITCH = "telugu_pitch"
        // Kept before each language had its own.
        private const val KEY_SPEED = "speed"
        private const val KEY_PITCH = "pitch"
        private const val KEY_ENGLISH_VOICE = "english_voice"
        private const val KEY_TELUGU_VOICE = "telugu_voice"
        private const val KEY_CONTINUE = "continue_reading"
        private const val KEY_ENGLISH_ENGINE = "english_engine"
        private const val KEY_TELUGU_ENGINE = "telugu_engine"
        private const val KEY_ENGLISH_RECORDING = "english_recording"
        private const val KEY_TELUGU_RECORDING = "telugu_recording"
        private const val KEY_TAP_HINT = "tap_hint_shown"
    }
}
