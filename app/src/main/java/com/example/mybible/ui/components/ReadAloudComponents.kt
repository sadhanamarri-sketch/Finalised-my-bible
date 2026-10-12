package com.example.mybible.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Slider
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * Reading aloud's settings, laid out like the app's Settings: the language, then for each language read its
 * own speed, pitch and voice (each tried as it's changed), more voices, and, from Verse Scroll
 * ([inVerseScroll]), whether to go on to the next card by itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // A language's voice, speed or pitch changed while it's being read applies at once (the other's as
    // it comes up); with nothing being read, a few words let you hear it.
    fun changed(telugu: Boolean) {
        when {
            !state.active -> readAloud.trySpeaking(if (telugu) TELUGU_SAMPLE else ENGLISH_SAMPLE, telugu)
            state.telugu == telugu -> readAloud.refresh()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The page's own color, as the app's Settings: its controls and dividers show against it.
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Text(
                text = "Reading aloud",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 18.dp)
            )
            if (state.noVoice) {
                DsNote(
                    "This phone has no voice to read with. Google's Speech Services, from the Play Store, adds one.",
                    modifier = Modifier.padding(bottom = 18.dp)
                )
            }

            DsSectionLabel("Read in", isFirst = true)
            DsSegmentedRow(
                options = ReadLanguage.entries,
                selected = settings.language,
                label = { it.label },
                onSelect = settings::updateLanguage
            )
            DsNote(
                when (settings.language) {
                    ReadLanguage.ENGLISH -> "The King James text, verse by verse."
                    ReadLanguage.TELUGU -> "The Telugu Bible's text, verse by verse."
                    ReadLanguage.BOTH -> "Each verse in English, then in Telugu."
                },
                modifier = Modifier.padding(top = 10.dp)
            )
            // Each language read, with its own speed, pitch and voice.
            val read = when (settings.language) {
                ReadLanguage.ENGLISH -> listOf(false)
                ReadLanguage.TELUGU -> listOf(true)
                ReadLanguage.BOTH -> listOf(false, true)
            }
            for (telugu in read) {
                key(telugu) {
                    DsSectionDivider()
                    LanguageSettings(
                        language = if (telugu) "Telugu" else "English",
                        settings = settings,
                        telugu = telugu,
                        voices = if (telugu) teluguVoices else englishVoices,
                        selected = readAloud.voiceFor(telugu),
                        measuring = measuring,
                        noVoice = telugu && noTeluguVoice,
                        onChanged = { changed(telugu) },
                        onPlay = { voice -> readAloud.previewVoice(if (telugu) TELUGU_SAMPLE else ENGLISH_SAMPLE, telugu, voice = voice.name) },
                        onGetVoice = { openVoiceInstaller(context) }
                    )
                }
            }
            when {
                measuring -> DsNote("Listening to each voice to sort them from the deepest…", modifier = Modifier.padding(top = 12.dp))
                state.status == ReadAloudState.Status.PLAYING && readAloud.voicesUnmeasured(englishVoices + teluguVoices) ->
                    DsNote("Pause the reading to sort the voices from the deepest.", modifier = Modifier.padding(top = 12.dp))
            }

            DsSectionDivider()
            DsSectionLabel("More voices")
            DsNote("Google's speech engine has more voices to download, some of them men's. The phone's speech settings choose its speech engine.")
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                DsOutlineAccentButton(text = "Get more voices", onClick = { openVoiceInstaller(context) }, modifier = Modifier.weight(1f))
                DsOutlineAccentButton(text = "Speech settings", onClick = { openSpeechSettings(context) }, modifier = Modifier.weight(1f))
            }

            if (inVerseScroll) {
                DsSectionDivider()
                DsSectionLabel("Verse Scroll")
                DsToggleRow(
                    label = "Continue reading",
                    subLabel = if (settings.continueReading) "Once a verse has been read, Verse Scroll moves on to the next card and reads it, hands-free, until you pause."
                    else "Each card is read as it comes up; swipe on for the next.",
                    checked = settings.continueReading,
                    onCheckedChange = settings::updateContinueReading
                )
            }
            DsSectionDivider()
            DsNote(
                "Read in your phone's own voice, offline. It keeps reading with the screen off; pause or stop it from the notification.",
                modifier = Modifier.padding(top = 14.dp)
            )
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

// One language's own settings, a section of their own: its speed, its pitch and its voice. Without a
// voice for it on the phone ([noVoice]), a way to get one instead.
@Composable
private fun LanguageSettings(
    language: String,
    settings: ReadAloudSettings,
    telugu: Boolean,
    voices: List<VoiceOption>,
    selected: String?,
    measuring: Boolean,
    noVoice: Boolean,
    onChanged: () -> Unit,
    onPlay: (VoiceOption) -> Unit,
    onGetVoice: () -> Unit
) {
    DsSectionLabel(language)
    if (noVoice) {
        DsNote(
            if (settings.language == ReadLanguage.BOTH) "This phone has no Telugu voice yet, so only the English is read."
            else "This phone has no Telugu voice yet, so nothing can be read in Telugu."
        )
        DsOutlineAccentButton(text = "Get a Telugu voice", onClick = onGetVoice, modifier = Modifier.padding(top = 14.dp))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SpeedAndPitch(language, settings, telugu, onChanged)
            if (voices.isNotEmpty()) {
                VoiceList(
                    title = "Voice",
                    voices = voices,
                    selected = selected,
                    measuring = measuring,
                    onPick = { voice ->
                        settings.updateVoice(telugu, name = voice.name)
                        onChanged()
                    },
                    onPlay = onPlay
                )
            }
        }
    }
}

// A language's speed and pitch, each a slider.
@Composable
private fun SpeedAndPitch(language: String, settings: ReadAloudSettings, telugu: Boolean, onChanged: () -> Unit) {
    SliderSetting(
        label = "Speed",
        description = "$language speed",
        value = settings.speed(telugu),
        range = ReadAloudSettings.MIN_SPEED..ReadAloudSettings.MAX_SPEED,
        usual = ReadAloudSettings.DEFAULT_SPEED,
        ends = "Slower" to "Faster",
        onChange = { speed ->
            settings.updateSpeed(telugu, speed)
            onChanged()
        }
    )
    SliderSetting(
        label = "Pitch",
        description = "$language pitch",
        value = settings.pitch(telugu),
        range = ReadAloudSettings.MIN_PITCH..ReadAloudSettings.MAX_PITCH,
        usual = ReadAloudSettings.DEFAULT_PITCH,
        ends = "Deeper" to "Higher",
        onChange = { pitch ->
            settings.updatePitch(telugu, pitch)
            onChanged()
        }
    )
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
        SettingName(title)
        for (voice in shown) {
            VoiceRow(voice, selected = voice.name == selected, measuring = measuring, onPick = { onPick(voice) }, onPlay = { onPlay(voice) })
        }
        if (shown.size < voices.size) {
            Text(
                text = "All ${voices.size} voices",
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { all = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
        if (selected == null || voices.none { it.name == selected }) DsNote("Read in the phone's usual voice until the voices are measured.")
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
            Text(voice.label, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.SansSerif, fontSize = 15.sp)
            if (about.isNotEmpty()) {
                Text(about, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.SansSerif, fontSize = 13.sp)
            }
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Hear ${voice.label}", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// Voices shown before "All N voices".
private const val SHOWN_VOICES = 4

// A setting along a range ([usual] in it): dragged in steps of 0.05, set as it's let go, its value
// shown beside its name ("1.25×"), with Reset when it's moved off the usual. [description] names it
// for a screen reader.
@Composable
private fun SliderSetting(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    usual: Float,
    ends: Pair<String, String>,
    onChange: (Float) -> Unit
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown = dragged ?: value
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingName(label)
            Text(
                text = timesLabel(shown),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.5.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            if (kotlin.math.abs(shown - usual) > 0.001f) {
                Text(
                    text = "Reset",
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            dragged = null
                            onChange(usual)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Slider(
            value = shown,
            onValueChange = { dragged = (kotlin.math.round(it / SLIDER_STEP) * SLIDER_STEP).coerceIn(range) },
            onValueChangeFinished = {
                dragged?.let(onChange)
                dragged = null
            },
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = description }
        )
        Row {
            Text(ends.first, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.SansSerif, fontSize = 12.5.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text(ends.second, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.SansSerif, fontSize = 12.5.sp)
        }
    }
}

private const val SLIDER_STEP = 0.05f

// "1×", "1.25×", "0.85×".
private fun timesLabel(value: Float): String = String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.') + "×"

// A setting's name, as the app's Settings names its switches.
@Composable
private fun SettingName(text: String) {
    Text(text, fontSize = 17.sp, fontFamily = FontFamily.Serif, color = MaterialTheme.colorScheme.onSurface)
}

private const val ENGLISH_SAMPLE = "For God so loved the world."
private const val TELUGU_SAMPLE = "దేవుడు లోకమును ఎంతో ప్రేమించెను."
