package com.example.mybible.audio

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.MainScope

/**
 * The app's one reader-aloud, shared by the screens and the service that keeps it playing with the
 * screen off: it outlives any one screen, so a reading goes on while the app is out of view.
 */
object ReadAloudHub {
    @Volatile
    private var engine: ReadAloud? = null

    /** Tests stand in for the phone's speech engine. */
    @VisibleForTesting
    var speakerFactory: (Context) -> Speaker = ::TtsSpeaker

    fun get(context: Context): ReadAloud = engine ?: synchronized(this) {
        engine ?: context.applicationContext.let { app ->
            val kept = app.getSharedPreferences(VOICE_DEPTHS, Context.MODE_PRIVATE)
            ReadAloud(
                speaker = speakerFactory(app),
                prefs = ReadAloudSettings(app),
                scope = MainScope(),
                onPlaying = { ReadAloudService.start(app) },
                depths = VoiceDepths(
                    saved = kept.all.mapValues { it.value.toString() },
                    save = { all -> kept.edit().clear().apply { all.forEach { (k, v) -> putString(k, v) } }.apply() },
                    workDir = app.cacheDir
                )
            )
        }.also { engine = it }
    }

    // How deep each of the phone's voices is, once measured (see VoiceDepths).
    private const val VOICE_DEPTHS = "voice_depths"

    /** The settings, for the settings sheets. */
    fun settings(context: Context): ReadAloudSettings = get(context).prefs as ReadAloudSettings

    @VisibleForTesting
    fun reset() {
        engine?.stop()
        engine = null
    }
}
