package com.example.mybible.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Plays stretches of the recordings on the phone with Media3's player, each from one place in its file
 * to another, exactly: the player is given just that stretch, so nothing after its end is ever heard.
 * (One stopped by a clock lets the sound run on a moment past the stop, longer with Bluetooth, into the
 * next verse's first word.) Other speeds keep the narrator's pitch. Audio focus and headphones coming
 * out are left to the reading's service, as for the phone's voice. Used on the main thread.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class RecordingPlayer(private val context: Context) : Recorder {
    override var listener: Recorder.Listener? = null

    private var player: ExoPlayer? = null

    // The stretch loaded (playing or paused): its id, where it starts in its file, and the marks to be
    // told of. Null once it's ended or stopped.
    private var loaded: Long? = null
    private var from = 0.0
    private val marks = mutableListOf<PlayerMessage>()

    // An MP3 at one bitrate is found in by arithmetic, exactly. One whose bitrate varies is read through
    // to the place: its own table of where things are is too rough to land on a verse.
    private val steady by lazy { DefaultMediaSourceFactory(context, DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)) }
    private val varying by lazy {
        DefaultMediaSourceFactory(
            context,
            DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true).setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
        )
    }

    private val events = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val id = loaded ?: return
            if (playbackState == Player.STATE_ENDED) {
                loaded = null
                listener?.onEnded(id)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = loaded ?: return
            loaded = null
            listener?.onFailed(id)
        }
    }

    // The stretch playing, looked in on in case it plays out without the player saying so (see EndWatch):
    // then it's ended all the same, and the player let go of it.
    private val main = Handler(Looper.getMainLooper())
    private var watch: EndWatch? = null
    private val lookIn = object : Runnable {
        override fun run() {
            val exo = player ?: return
            val id = loaded ?: return
            val playing = exo.playbackState == Player.STATE_READY && exo.playWhenReady
            if (watch?.over(SystemClock.elapsedRealtime(), playing, exo.currentPosition) == true) {
                loaded = null
                clearMarks()
                exo.stop()
                listener?.onEnded(id)
            } else {
                main.postDelayed(this, EndWatch.EVERY_MS)
            }
        }
    }

    private fun player(): ExoPlayer = player ?: ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
            /* handleAudioFocus= */ false
        )
        .setHandleAudioBecomingNoisy(false)
        // Playing on with the screen off.
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()
        .also {
            // A video's sound only (the dramatized KJV's files are videos): its pictures aren't decoded.
            it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
            it.addListener(events)
            player = it
        }

    override fun play(id: Long, file: RecordingFile, from: Double, to: Double, speed: Float, marks: List<Double>) {
        val exo = player()
        clearMarks()
        loaded = id
        this.from = from
        val startMs = (from * 1000).roundToLong().coerceAtLeast(0)
        // A stretch too short to play is over at once.
        val endMs = maxOf((to * 1000).roundToLong(), startMs + 1)
        val item = MediaItem.Builder()
            .setUri(Uri.parse(file.uri))
            .setMimeType(file.kind.mime)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()
            )
            .build()
        exo.setMediaSource((if (file.kind == MediaKind.MP3_VARIABLE) varying else steady).createMediaSource(item))
        exo.playbackParameters = PlaybackParameters(speed)
        for (mark in marks) {
            this.marks += exo.createMessage { _, payload -> if (loaded == id) listener?.onReached(id, payload as Double) }
                .setLooper(Looper.getMainLooper())
                .setPosition(((mark - from) * 1000).roundToLong().coerceAtLeast(0))
                .setPayload(mark)
                .send()
        }
        exo.prepare()
        exo.playWhenReady = true
        watch = EndWatch(lengthMs = endMs - startMs)
        main.removeCallbacks(lookIn)
        main.postDelayed(lookIn, EndWatch.EVERY_MS)
    }

    override fun pause() {
        player?.playWhenReady = false
    }

    override fun resume(): Boolean {
        val exo = player ?: return false
        if (loaded == null || exo.playbackState == Player.STATE_ENDED || exo.playbackState == Player.STATE_IDLE) return false
        exo.playWhenReady = true
        return true
    }

    override fun stop() {
        loaded = null
        main.removeCallbacks(lookIn)
        clearMarks()
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
    }

    override fun speed(speed: Float) {
        player?.playbackParameters = PlaybackParameters(speed)
    }

    override val position: Double?
        get() {
            val exo = player ?: return null
            if (loaded == null) return null
            return from + exo.currentPosition / 1000.0
        }

    override fun release() {
        stop()
        player?.release()
        player = null
    }

    private fun clearMarks() {
        marks.forEach { it.cancel() }
        marks.clear()
    }
}

/**
 * Tells when a stretch has played as far as it ever will without the player saying it's ended: meant
 * to be playing, near its end, and not moving on. Media3 before 1.6 left stretches played slower than
 * 1x just so now and then (its speed changer, at the end, owed less than nothing and never finished),
 * and reading aloud waited there for good; the phone's sound output could yet do the like.
 */
internal class EndWatch(private val lengthMs: Long) {
    // Where the stretch has stayed, and since when; null until it's first looked in on.
    private var stillAtMs: Long? = null
    private var stillSinceMs = 0L

    /**
     * Whether the stretch is over, at [nowMs]: [playing] (ready, not paused) and at [positionMs] in it,
     * having stayed there, near its end, long enough.
     */
    fun over(nowMs: Long, playing: Boolean, positionMs: Long): Boolean {
        val moving = stillAtMs?.let { abs(positionMs - it) > STILL_WITHIN_MS } ?: true
        if (!playing || moving || positionMs < lengthMs - NEAR_END_MS) {
            stillAtMs = positionMs
            stillSinceMs = nowMs
            return false
        }
        return nowMs - stillSinceMs >= STILL_MS
    }

    companion object {
        // How often the stretch playing is looked in on.
        const val EVERY_MS = 250L

        // How near its end it has to be, and how long still there: well past the time the sound output
        // takes to finish what it's been given (a few hundred milliseconds over Bluetooth).
        const val NEAR_END_MS = 1_000L
        const val STILL_MS = 3_000L

        // Less than this either way is staying put.
        const val STILL_WITHIN_MS = 50L
    }
}
