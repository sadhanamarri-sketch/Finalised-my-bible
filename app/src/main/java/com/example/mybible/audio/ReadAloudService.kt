package com.example.mybible.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.mybible.MainActivity
import com.example.mybible.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps a reading aloud going with the screen off or the app out of view: a notification (and the lock
 * screen's media controls, and a headset's buttons) to pause, play and stop. It runs while something is
 * being read and stops itself once nothing is. It also gives way to other sound: a call or another
 * app's audio pauses the reading (picking up after a short interruption), as do headphones coming out.
 */
class ReadAloudService : Service() {
    private val engine by lazy { ReadAloudHub.get(this) }
    private val scope = MainScope()
    private lateinit var media: MediaSession
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    private var focus: AudioFocusRequest? = null

    // Keeps the phone from dozing off between verses with the screen off (the voice itself is another app's).
    private val awake by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyBible:ReadAloud")
            .apply { setReferenceCounted(false) }
    }

    // Paused because another sound took over for a moment (a call, a navigation prompt): plays on after it.
    private var pausedForFocus = false
    private var listeningForNoisy = false

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) engine.pause()
        }
    }

    private val focusChange = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> if (pausedForFocus) {
                pausedForFocus = false
                engine.resume()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                if (engine.state.value.status == ReadAloudState.Status.PLAYING) {
                    pausedForFocus = true
                    engine.pause()
                }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedForFocus = false
                engine.pause()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        media = MediaSession(this, "ReadAloud").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = engine.resume()
                override fun onPause() = engine.pause()
                override fun onStop() = engine.stop()
            })
            setSessionActivity(openApp())
            isActive = true
        }
        scope.launch { engine.state.collect { render(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started to play (startForegroundService), it has to be in the foreground at once, even to stop.
        goForeground(engine.state.value)
        when (intent?.action) {
            ACTION_PLAY -> engine.resume()
            ACTION_PAUSE -> engine.pause()
            ACTION_STOP -> engine.stop()
        }
        render(engine.state.value)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        stayAwake(false)
        abandonFocus()
        listenForNoisy(false)
        media.release()
        super.onDestroy()
    }

    private fun render(state: ReadAloudState) {
        media.setPlaybackState(playbackState(state))
        media.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, titleOf(state))
                .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.app_name))
                .putString(MediaMetadata.METADATA_KEY_ALBUM, state.title)
                .build()
        )
        when (state.status) {
            ReadAloudState.Status.PLAYING -> {
                requestFocus()
                listenForNoisy(true)
                stayAwake(true)
                goForeground(state)
            }
            ReadAloudState.Status.PAUSED -> {
                listenForNoisy(false)
                stayAwake(false)
                if (pausedForFocus) {
                    // Back in a moment: stays in the foreground.
                    goForeground(state)
                } else {
                    abandonFocus()
                    stopForeground(STOP_FOREGROUND_DETACH)
                    notifyQuietly(state)
                }
            }
            ReadAloudState.Status.IDLE -> {
                abandonFocus()
                listenForNoisy(false)
                stayAwake(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                notifications.cancel(NOTIFICATION_ID)
                stopSelf()
            }
        }
    }

    private fun goForeground(state: ReadAloudState) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(state), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification(state))
            }
        } catch (e: Exception) {
            // Not allowed from the background on newer Android (rare: resuming after another app's sound):
            // the reading goes on, just without the notification.
        }
    }

    private fun stayAwake(on: Boolean) {
        // Released after a few hours however it went, should anything keep it held.
        if (on) awake.acquire(WAKE_LIMIT_MS) else if (awake.isHeld) awake.release()
    }

    private fun notifyQuietly(state: ReadAloudState) {
        try {
            notifications.notify(NOTIFICATION_ID, notification(state))
        } catch (e: SecurityException) {
            // Notifications not allowed.
        }
    }

    private fun requestFocus() {
        val request = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusChange)
            .build()
            .also { focus = it }
        pausedForFocus = false
        if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            // Something else has the sound (a phone call): no reading over it.
            engine.pause()
        }
    }

    private fun abandonFocus() {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        pausedForFocus = false
    }

    private fun listenForNoisy(on: Boolean) {
        if (on == listeningForNoisy) return
        listeningForNoisy = on
        if (on) {
            ContextCompat.registerReceiver(
                this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            unregisterReceiver(noisy)
        }
    }

    private fun playbackState(state: ReadAloudState): PlaybackState = PlaybackState.Builder()
        .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
        .setState(
            when (state.status) {
                ReadAloudState.Status.PLAYING -> PlaybackState.STATE_PLAYING
                ReadAloudState.Status.PAUSED -> PlaybackState.STATE_PAUSED
                ReadAloudState.Status.IDLE -> PlaybackState.STATE_STOPPED
            },
            PlaybackState.PLAYBACK_POSITION_UNKNOWN,
            1f
        )
        .build()

    private fun titleOf(state: ReadAloudState): String = state.verse?.label ?: state.title.ifEmpty { getString(R.string.app_name) }

    private fun notification(state: ReadAloudState): Notification {
        val playing = state.status != ReadAloudState.Status.PAUSED
        val toggle = Notification.Action.Builder(
            Icon.createWithResource(this, if (playing) R.drawable.ic_read_pause else R.drawable.ic_read_play),
            if (playing) "Pause" else "Play",
            command(if (playing) ACTION_PAUSE else ACTION_PLAY)
        ).build()
        val stop = Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_read_stop), "Stop", command(ACTION_STOP)).build()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titleOf(state))
            .setContentText(
                when {
                    !playing -> "Paused"
                    state.telugu -> "Reading aloud in Telugu"
                    else -> "Reading aloud in English"
                }
            )
            .setSubText(state.title.takeIf { it.isNotEmpty() })
            .setContentIntent(openApp())
            // Swiping a paused one away stops the reading.
            .setDeleteIntent(command(ACTION_STOP))
            .setOngoing(playing)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(toggle)
            .addAction(stop)
            .setStyle(Notification.MediaStyle().setMediaSession(media.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun command(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, ReadAloudService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    // Back to the app as it was left (not a widget's way in, which would clear its detours).
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_FROM_READ_ALOUD, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        /** On the intent that opens the app from the notification: open it as it was, nothing more. */
        const val EXTRA_FROM_READ_ALOUD = "from_read_aloud"

        private const val CHANNEL_ID = "read_aloud"
        private const val NOTIFICATION_ID = 3110
        private const val WAKE_LIMIT_MS = 3 * 60 * 60 * 1000L
        private const val ACTION_PLAY = "com.example.mybible.audio.PLAY"
        private const val ACTION_PAUSE = "com.example.mybible.audio.PAUSE"
        private const val ACTION_STOP = "com.example.mybible.audio.STOP"

        /** Something started playing: show the notification and keep the reading going. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ReadAloudService::class.java))
            } catch (e: Exception) {
                // Not allowed from the background on newer Android: the reading goes on without it.
            }
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reading aloud", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Pause, play or stop a reading aloud while the screen is off"
                    setShowBadge(false)
                }
            )
        }
    }
}
