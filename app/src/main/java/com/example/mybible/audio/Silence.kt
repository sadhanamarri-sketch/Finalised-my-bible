package com.example.mybible.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread

/**
 * Silence, played in the app's own name while it reads aloud. The voice is the speech engine's sound —
 * another app's — and Android hands a headset's buttons and earbuds' taps to the app whose sound played
 * last: without this they'd go to some other app, or nowhere. It also keeps a Bluetooth headset's sound
 * open between verses, so a verse's first word isn't clipped as it starts up again.
 */
internal class Silence {
    // The sound being played, and the thread feeding it silence, a chunk at a time.
    @Volatile
    private var track: AudioTrack? = null
    private var writer: Thread? = null

    fun play() {
        if (track != null) return
        val playing = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(CHUNK * 2, AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
                .build()
                .apply { play() }
        } catch (e: Exception) {
            // No sound to be had (rare): reading goes on, the buttons just don't reach it.
            return
        }
        track = playing
        writer = thread(name = "ReadAloudSilence", isDaemon = true) {
            val quiet = ShortArray(CHUNK)
            // Each write waits while the last half second plays.
            while (track === playing) {
                val written = playing.write(quiet, 0, quiet.size)
                if (written < 0) break
                if (written == 0) Thread.sleep(IDLE_MS)
            }
        }
    }

    fun stop() {
        val playing = track ?: return
        track = null
        try {
            // Lets the writer's wait end at once.
            playing.pause()
            playing.flush()
        } catch (e: IllegalStateException) {
            // Already done with.
        }
        writer?.join(JOIN_MS)
        writer = null
        playing.release()
    }

    private companion object {
        const val SAMPLE_RATE = 8_000
        // Half a second of silence a write.
        const val CHUNK = SAMPLE_RATE / 2
        const val IDLE_MS = 100L
        const val JOIN_MS = 1_000L
    }
}
