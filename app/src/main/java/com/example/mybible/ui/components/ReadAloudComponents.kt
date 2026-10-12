package com.example.mybible.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.provider.Settings
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mybible.audio.VoiceDepths
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.mybible.audio.ReadAloud
import com.example.mybible.audio.ReadAloudSettings
import com.example.mybible.audio.ReadAloudState
import com.example.mybible.audio.ReadLanguage
import com.example.mybible.audio.VoiceOption
import com.example.mybible.ui.theme.WorkSansFontFamily
import kotlinx.coroutines.delay

/**
 * The Reader's reading-aloud bar, over its bottom pill while a chapter is read aloud: play or pause, the
 * verse being read (tap it to go to it), the settings, and stop.
 */
@Composable
fun ReadAloudBar(
    state: ReadAloudState,
    onPlayPause: () -> Unit,
    onOpenVerse: () -> Unit,
    onSettings: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playing = state.status == ReadAloudState.Status.PLAYING
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause reading aloud" else "Go on reading aloud",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 180.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenVerse)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = state.verse?.label ?: state.title,
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        !playing -> "Paused"
                        state.telugu -> "Reading in Telugu"
                        // Both languages, but no Telugu voice on the phone.
                        state.noTeluguVoice -> "English only: no Telugu voice"
                        else -> "Reading in English"
                    },
                    fontFamily = WorkSansFontFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Tune, contentDescription = "Reading aloud settings", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onStop, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Stop reading aloud", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Reading aloud's settings: the language, the speed, a voice for English and for Telugu (each tried as
 * it's picked), and, from Verse Scroll ([inVerseScroll]), whether to go on to the next card by itself.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReadAloudSheet(
    readAloud: ReadAloud,
    settings: ReadAloudSettings,
    inVerseScroll: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state by readAloud.state.collectAsState()
    val depthChanges by readAloud.voiceDepthChanges.collectAsState()
    val measuring by readAloud.measuringVoices.collectAsState()
    // Back from the phone's settings (voices added there, say): its voices again.
    var resumed by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumed++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // The phone's voices, once its speech engine has started (on first use, so it can take a moment),
    // the deepest first as they're measured.
    val voices by produceState(PhoneVoices.of(readAloud), depthChanges, resumed) {
        value = PhoneVoices.of(readAloud)
        repeat(40) {
            if (value.known) return@produceState
            delay(250)
            value = PhoneVoices.of(readAloud)
        }
    }
    // Each voice measured once, while nothing is being said.
    LaunchedEffect(voices.known, state.status, resumed) {
        if (voices.known && state.status != ReadAloudState.Status.PLAYING) readAloud.measureVoices()
    }
    val englishVoices = voices.english
    val teluguVoices = voices.telugu
    val noTeluguVoice = voices.hasTelugu == false || state.noTeluguVoice

    // A voice or speed picked while reading applies at once; otherwise a few words let you hear it.
    fun changed(telugu: Boolean) {
        if (state.active) readAloud.refresh() else readAloud.trySpeaking(if (telugu) TELUGU_SAMPLE else ENGLISH_SAMPLE, telugu)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
        ) {
            Text(
                text = "Reading aloud",
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            if (state.noVoice) {
                Note("This phone has no voice to read with. Google's Speech Services, from the Play Store, adds one.")
            }

            Group("Read in") {
                ReadLanguage.entries.forEach { language ->
                    Choice(language.label, settings.language == language) { settings.updateLanguage(language) }
                }
            }
            Note(
                when (settings.language) {
                    ReadLanguage.ENGLISH -> "The King James text, verse by verse."
                    ReadLanguage.TELUGU -> "The Telugu Bible's text, verse by verse."
                    ReadLanguage.BOTH -> "Each verse in English, then in Telugu."
                }
            )
            if (noTeluguVoice && settings.language != ReadLanguage.ENGLISH) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Note(
                        if (settings.language == ReadLanguage.BOTH) "This phone has no Telugu voice yet, so only the English is read."
                        else "This phone has no Telugu voice yet, so nothing can be read in Telugu."
                    )
                    Choice("Get a Telugu voice", selected = false) { openVoiceInstaller(context) }
                }
            }

            Group("Speed") {
                ReadAloudSettings.SPEEDS.forEach { speed ->
                    Choice(speedLabel(speed), settings.speed == speed) {
                        settings.updateSpeed(speed)
                        changed(telugu = settings.language == ReadLanguage.TELUGU)
                    }
                }
            }

            Group("Pitch") {
                ReadAloudSettings.PITCHES.forEach { (pitch, label) ->
                    Choice(label, settings.pitch == pitch) {
                        settings.updatePitch(pitch)
                        changed(telugu = settings.language == ReadLanguage.TELUGU)
                    }
                }
            }

            if (englishVoices.isNotEmpty()) {
                VoiceList(
                    title = "English voice",
                    voices = englishVoices,
                    selected = readAloud.voiceFor(telugu = false),
                    measuring = measuring,
                    onPick = { voice ->
                        settings.updateVoice(telugu = false, name = voice.name)
                        changed(telugu = false)
                    },
                    onPlay = { voice -> readAloud.previewVoice(ENGLISH_SAMPLE, telugu = false, voice = voice.name) }
                )
            }
            if (teluguVoices.isNotEmpty()) {
                VoiceList(
                    title = "Telugu voice",
                    voices = teluguVoices,
                    selected = readAloud.voiceFor(telugu = true),
                    measuring = measuring,
                    onPick = { voice ->
                        settings.updateVoice(telugu = true, name = voice.name)
                        changed(telugu = true)
                    },
                    onPlay = { voice -> readAloud.previewVoice(TELUGU_SAMPLE, telugu = true, voice = voice.name) }
                )
            }
            when {
                measuring -> Note("Listening to each voice to sort them from the deepest…")
                state.status == ReadAloudState.Status.PLAYING && readAloud.voicesUnmeasured(englishVoices + teluguVoices) ->
                    Note("Pause the reading to sort the voices from the deepest.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Group("More voices") {
                    Choice("Get more voices", selected = false) { openVoiceInstaller(context) }
                    Choice("Phone speech settings", selected = false) { openSpeechSettings(context) }
                }
                Note("Google's speech engine has more voices to download, some of them men's. The phone's speech settings choose its speech engine.")
            }

            if (inVerseScroll) {
                Group("Continue reading") {
                    Choice("Off", !settings.continueReading) { settings.updateContinueReading(false) }
                    Choice("On", settings.continueReading) { settings.updateContinueReading(true) }
                }
                Note(
                    if (settings.continueReading) "Once a verse has been read, Verse Scroll moves on to the next card and reads it, hands-free, until you pause."
                    else "Each card is read as it comes up; swipe on for the next."
                )
            }
            Note("Read in your phone's own voice, offline. It keeps reading with the screen off; pause or stop it from the notification.")
        }
    }
}

// The phone's voices for each language, and whether it has a Telugu one: null until its speech engine
// has started.
private class PhoneVoices(val english: List<VoiceOption>, val telugu: List<VoiceOption>, val hasTelugu: Boolean?) {
    val known: Boolean get() = hasTelugu != null

    companion object {
        fun of(readAloud: ReadAloud) = PhoneVoices(readAloud.voices(telugu = false), readAloud.voices(telugu = true), readAloud.hasTeluguVoice())
    }
}

/**
 * Asks once, on Android 13 and later, to show notifications: with the screen off, reading aloud is
 * paused and stopped from its notification. Call it as a reading starts.
 */
@Composable
fun rememberReadAloudNotificationAsk(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        ask@{
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@ask
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return@ask
            val prefs = context.getSharedPreferences(ReadAloudSettings.PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return@ask
            prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"

// "1×", "1.25×".
private fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')) + "×"

/**
 * The phone's speech engine's own page for adding voices (Google's lists its languages), or failing
 * that, the phone's text-to-speech settings.
 */
fun openVoiceInstaller(context: Context) {
    val tries = listOf(
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
        Intent("com.android.settings.TTS_SETTINGS")
    )
    for (intent in tries) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // Not on this phone: the next.
        }
    }
}

/** The phone's text-to-speech settings: its speech engine, and its pitch and speed for other apps. */
fun openSpeechSettings(context: Context) {
    val tries = listOf(
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in tries) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // Not on this phone: the next.
        }
    }
}

// A language's voices, the deepest first, each with how it sounds and a button to hear it; the one
// read in is marked. Past a few, the rest behind "All N voices".
@Composable
private fun VoiceList(
    title: String,
    voices: List<VoiceOption>,
    selected: String?,
    measuring: Boolean,
    onPick: (VoiceOption) -> Unit,
    onPlay: (VoiceOption) -> Unit
) {
    var all by remember(voices.size) { mutableStateOf(false) }
    val shown = if (all || voices.size <= SHOWN_VOICES + 1) voices else {
        voices.filterIndexed { i, voice -> i < SHOWN_VOICES || voice.name == selected }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontFamily = WorkSansFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.5.sp)
        for (voice in shown) {
            VoiceRow(voice, selected = voice.name == selected, measuring = measuring, onPick = { onPick(voice) }, onPlay = { onPlay(voice) })
        }
        if (shown.size < voices.size) {
            Text(
                text = "All ${voices.size} voices",
                color = MaterialTheme.colorScheme.primary,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { all = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
        if (selected == null) {
            Note("Read in the phone's usual voice until the voices are measured.")
        }
    }
}

@Composable
private fun VoiceRow(voice: VoiceOption, selected: Boolean, measuring: Boolean, onPick: () -> Unit, onPlay: () -> Unit) {
    val about = listOfNotNull(
        voice.depth?.let(VoiceDepths::describe) ?: if (measuring && !voice.online) "Measuring…" else null,
        if (voice.online) "needs internet" else null
    ).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onPick)
            .padding(start = 2.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Text(voice.label, color = MaterialTheme.colorScheme.onSurface, fontFamily = WorkSansFontFamily, fontSize = 14.sp)
            if (about.isNotEmpty()) {
                Text(about, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = WorkSansFontFamily, fontSize = 12.sp)
            }
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Hear ${voice.label}", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// Voices shown before "All N voices".
private const val SHOWN_VOICES = 4

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(label: String, options: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontFamily = WorkSansFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.5.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options()
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = WorkSansFontFamily,
        fontSize = 12.5.sp,
        lineHeight = 1.45.em
    )
}

// A pill to pick, like Verse Scroll's settings.
@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Text(
        text = label,
        color = if (selected) MaterialTheme.colorScheme.background else ink,
        fontFamily = WorkSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) ink else Color.Transparent)
            .border(1.dp, if (selected) ink else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

private const val ENGLISH_SAMPLE = "For God so loved the world."
private const val TELUGU_SAMPLE = "దేవుడు లోకమును ఎంతో ప్రేమించెను."
