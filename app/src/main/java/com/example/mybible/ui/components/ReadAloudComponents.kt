package com.example.mybible.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.mybible.audio.EngineOption
import com.example.mybible.audio.ReadAloud
import com.example.mybible.audio.ReadAloudSettings
import com.example.mybible.audio.ReadAloudState
import com.example.mybible.audio.ReadLanguage
import com.example.mybible.audio.Recordings
import com.example.mybible.audio.VoiceOption
import com.example.mybible.ui.theme.WorkSansFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The Reader's reading-aloud bar, over its bottom pill while a chapter is read aloud: previous (held, back
 * to the chapter's first verse), play or pause, next, the verse being read (tap it to go to it), the
 * settings, and stop.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReadAloudBar(
    state: ReadAloudState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapterStart: () -> Unit,
    onOpenVerse: () -> Unit,
    onSettings: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playing = state.status == ReadAloudState.Status.PLAYING
    val haptics = LocalHapticFeedback.current
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
            // Previous: the verse again, or early in it the one before; held, the chapter from its first verse.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = "Previous verse",
                        onLongClickLabel = "Read from the start of the chapter",
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onChapterStart()
                        },
                        onClick = onPrevious
                    )
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous verse", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(38.dp).testTag("read_aloud_play")) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause reading aloud" else "Go on reading aloud",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next verse", tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 132.dp)
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
                        state.recording -> if (state.telugu) "Telugu recording" else "English recording"
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
 * What the reading bar offers for now, over it: a few words ([text], if any) and a button or two
 * ("Undo", "Follow", "Back" and "Read here", "Next chapter").
 */
@Composable
fun ReadAloudOffer(
    text: String?,
    actions: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = if (text != null) 16.dp else 4.dp, end = 4.dp)
        ) {
            if (text != null) {
                Text(
                    text = text,
                    fontFamily = WorkSansFontFamily,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 180.dp)
                )
            }
            for ((label, onClick) in actions) {
                TextButton(onClick = onClick) {
                    Text(label, fontFamily = WorkSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

/** A line of help over the reading bar, shown once: "Tap a verse to hear it · Pause for highlights and notes". */
@Composable
fun ReadAloudHint(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        modifier = modifier
    ) {
        Text(
            text = text,
            fontFamily = WorkSansFontFamily,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/**
 * Reading aloud's settings, laid out like the app's Settings: the language, then for each language read
 * whether it's played from its recording on the phone (the folder it's in, and what was found there) or
 * read by the phone's voice, with its own speech engine, speed, pitch and voice (each tried as it's
 * changed), the phone's speech settings, and, from Verse Scroll ([inVerseScroll]), whether to go on to
 * the next card by itself.
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
    // The recordings on the phone (none in tests), what's been found of each, and the folder picker.
    val recordings = readAloud.recordings as? Recordings
    val noRecordings = remember { MutableStateFlow(emptyMap<Boolean, Recordings.Status>()) }
    val recordingStatus by (recordings?.status ?: noRecordings).collectAsState()
    var pickingFor by remember { mutableStateOf<Boolean?>(null) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        val telugu = pickingFor
        pickingFor = null
        if (folder != null && telugu != null && recordings != null) {
            recordings.choose(telugu, folder)
            settings.updateRecording(telugu, true)
        }
    }
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
    // The phone's speech engines (an AI voice app like VoxSherpa, installed since, too).
    val engines by produceState(readAloud.engines(), resumed) { value = readAloud.engines() }
    // An engine chosen for a language and since uninstalled: the phone's usual one again.
    LaunchedEffect(engines) {
        if (engines.isEmpty()) return@LaunchedEffect
        for (telugu in listOf(false, true)) {
            val chosen = settings.engine(telugu)
            if (chosen != null && engines.none { it.name == chosen }) settings.updateEngine(telugu, null)
        }
    }
    // Each language's engine's voices, once it has started (on first use, so it can take a moment; an
    // AI voice app, longer), the deepest of the usual engine's first as they're measured.
    val voices by produceState(PhoneVoices.of(readAloud), depthChanges, resumed, settings.englishEngine, settings.teluguEngine) {
        value = PhoneVoices.of(readAloud)
        repeat(80) {
            if (value.known) return@produceState
            delay(250)
            value = PhoneVoices.of(readAloud)
        }
    }
    // Each of the usual engine's voices measured once, while nothing is being said.
    LaunchedEffect(voices.known, state.status, resumed, settings.englishEngine, settings.teluguEngine) {
        if (voices.known && state.status != ReadAloudState.Status.PLAYING) readAloud.measureVoices()
    }
    val englishVoices = voices.english
    val teluguVoices = voices.telugu
    val noTeluguVoice = voices.hasTelugu == false || state.noTeluguVoice
    // The voices measured: the usual engine's.
    val measured = (if (settings.englishEngine == null) englishVoices else emptyList()) +
        (if (settings.teluguEngine == null) teluguVoices else emptyList())

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
                onSelect = { language ->
                    settings.updateLanguage(language)
                    // A recording would play on in the language it started in to the chapter's end.
                    if (state.recording) readAloud.restartVerse()
                }
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
                        recording = recordingStatus[telugu],
                        onReadWith = { fromRecording ->
                            settings.updateRecording(telugu, fromRecording)
                            // Being read, the verse starts again the new way.
                            if (state.active) readAloud.restartVerse()
                        },
                        onChooseFolder = {
                            pickingFor = telugu
                            pickFolder.launch(null)
                        },
                        onLookAgain = { recordings?.checkAgain(telugu) },
                        engines = engines,
                        voices = if (telugu) teluguVoices else englishVoices,
                        selected = readAloud.voiceFor(telugu),
                        measuring = measuring,
                        noVoice = telugu && noTeluguVoice,
                        onChanged = { changed(telugu) },
                        onPlay = { voice -> readAloud.previewVoice(if (telugu) TELUGU_SAMPLE else ENGLISH_SAMPLE, telugu, voice = voice.name) },
                        onGetVoices = { openVoiceInstaller(context, engine = settings.engine(telugu)) }
                    )
                }
            }
            when {
                measuring -> DsNote("Listening to each voice to sort them from the deepest…", modifier = Modifier.padding(top = 12.dp))
                state.status == ReadAloudState.Status.PLAYING && readAloud.voicesUnmeasured(measured) ->
                    DsNote("Pause the reading to sort the voices from the deepest.", modifier = Modifier.padding(top = 12.dp))
            }

            DsSectionDivider()
            DsSectionLabel("More voices")
            DsNote(
                "Google's speech engine has more voices to download, some of them men's. An AI voice app, like " +
                    "VoxSherpa TTS with its Piper voices, installs as another speech engine to choose for a language. " +
                    "Get more voices, under a language, opens its engine's own voices."
            )
            DsOutlineAccentButton(text = "Phone speech settings", onClick = { openSpeechSettings(context) }, modifier = Modifier.padding(top = 14.dp))

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
                if (settings.continueReading) {
                    PauseBetweenCardsRow(settings.pauseBetweenCards, settings::updatePauseBetweenCards, Modifier.padding(top = 14.dp))
                }
            }
            DsSectionDivider()
            DsNote(
                "Read by your phone's speech engines, or played from recordings kept on your phone; offline either way. It keeps reading with the screen off; pause, skip or stop it from the notification or your earbuds (next and previous go a verse on or back, in Verse Scroll a card). In the Reader, hold the previous button to go back to the chapter's first verse.",
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

/**
 * Verse Scroll reading on by itself (Continue reading): how long it waits with a card once it's been
 * read, before the next slides up. Off, or a few seconds.
 */
@Composable
fun PauseBetweenCardsRow(seconds: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        SettingName("Pause between cards")
        DsNote("A moment with each verse once it's been read, before the next card slides up.", modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        DsSegmentedRow(
            options = ReadAloudSettings.PAUSE_CHOICES,
            selected = seconds,
            label = { if (it == 0) "Off" else "$it s" },
            onSelect = onSelect
        )
    }
}

// Each language's engine's voices, and whether there's a Telugu one: null until the engines have started.
private class PhoneVoices(
    val english: List<VoiceOption>,
    val telugu: List<VoiceOption>,
    val hasTelugu: Boolean?,
    private val englishReady: Boolean?
) {
    val known: Boolean get() = hasTelugu != null && englishReady != null

    companion object {
        fun of(readAloud: ReadAloud) = PhoneVoices(
            readAloud.voices(telugu = false),
            readAloud.voices(telugu = true),
            readAloud.hasTeluguVoice(),
            readAloud.englishReady()
        )
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
 * Where a speech engine's voices are added ([engine] its package; null, the phone's usual one): an AI
 * voice app's own app, where its voices are downloaded and chosen, or an engine's page for voices
 * (Google's lists its languages); failing those, the phone's text-to-speech settings. Never left to
 * Android to choose an engine, which may pick one whose page has nothing to choose.
 */
fun openVoiceInstaller(context: Context, engine: String? = null) {
    val named = engine ?: try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
    } catch (e: Exception) {
        null
    }
    val tries = listOfNotNull(
        named?.let { context.packageManager.getLaunchIntentForPackage(it) },
        named?.let { Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(it) },
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

// One language's own settings, a section of their own: whether it's played from its recording on the
// phone ([recording], what's known of that; null: recordings aren't to be had) or read by the phone's
// voice; then the recording's folder and speed, or the speech engine it's read with (when the phone has
// more than one), its speed, its pitch and its voice. Without a voice for it ([noVoice]), a way to get
// one instead.
@Composable
private fun LanguageSettings(
    language: String,
    settings: ReadAloudSettings,
    telugu: Boolean,
    recording: Recordings.Status?,
    onReadWith: (Boolean) -> Unit,
    onChooseFolder: () -> Unit,
    onLookAgain: () -> Unit,
    engines: List<EngineOption>,
    voices: List<VoiceOption>,
    selected: String?,
    measuring: Boolean,
    noVoice: Boolean,
    onChanged: () -> Unit,
    onPlay: (VoiceOption) -> Unit,
    onGetVoices: () -> Unit
) {
    // The engine chosen, while it's still on the phone; else the usual one (null).
    val chosen = settings.engine(telugu)?.takeIf { name -> engines.isEmpty() || engines.any { it.name == name } }
    val engineName = engines.firstOrNull { it.name == chosen }?.label
    DsSectionLabel(language)
    if (recording != null) {
        DsSegmentedRow(
            options = listOf(true, false),
            selected = settings.recording(telugu),
            label = { if (it) "Recording" else "Phone's voice" },
            onSelect = onReadWith,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (settings.recording(telugu)) {
            RecordingSettings(language, settings, telugu, recording, onChooseFolder, onLookAgain, onChanged)
            return
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (engines.size > 1 || chosen != null) {
            EngineChoice(
                engines = engines,
                chosen = chosen,
                onPick = { engine ->
                    settings.updateEngine(telugu, if (engine.usual) null else engine.name)
                    onChanged()
                }
            )
        }
        if (noVoice) {
            Column {
                DsNote(
                    when {
                        chosen != null -> "${engineName ?: "This speech engine"} has no Telugu voice: choose another speech engine above."
                        settings.language == ReadLanguage.BOTH -> "This phone has no Telugu voice yet, so only the English is read."
                        else -> "This phone has no Telugu voice yet, so nothing can be read in Telugu."
                    }
                )
                DsOutlineAccentButton(text = "Get a Telugu voice", onClick = onGetVoices, modifier = Modifier.padding(top = 14.dp))
            }
        } else {
            SpeedAndPitch(language, settings, telugu, onChanged)
            Column {
                if (voices.isNotEmpty()) {
                    VoiceList(
                        title = "Voice",
                        voices = voices,
                        selected = selected,
                        measuring = measuring && chosen == null,
                        nothingChosen = if (chosen == null) "Read in the phone's usual voice until the voices are measured."
                        else "Read in ${engineName ?: "the app"}'s own voice unless you pick one.",
                        onPick = { voice ->
                            settings.updateVoice(telugu, name = voice.name)
                            onChanged()
                        },
                        onPlay = onPlay
                    )
                }
                DsOutlineAccentButton(text = "Get more voices", onClick = onGetVoices, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

// A language's recording on the phone: which one it is, the folder it's in and what was found there,
// and its speed (the narrator's own voice and pitch, whatever the speed).
@Composable
private fun RecordingSettings(
    language: String,
    settings: ReadAloudSettings,
    telugu: Boolean,
    status: Recordings.Status,
    onChooseFolder: () -> Unit,
    onLookAgain: () -> Unit,
    onChanged: () -> Unit
) {
    val checking = status.checking
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DsNote(recordingNote(telugu, status))
            when {
                status.folder == null -> DsNote("Choose the folder they're in on your phone. They're played from there, never copied.")
                // Before it's known which recording's files to look over, how many isn't.
                checking != null && checking.second == 0 -> DsNote("Looking over the files in ${status.folder}\u2026")
                checking != null -> DsNote("Looking over the files in ${status.folder}: ${checking.first} of ${checking.second}\u2026")
                status.lost -> DsNote("${status.folder} can't be opened any more. Choose it again.")
                else -> {
                    Text(
                        text = "${status.folder}: ${count(status.chapters)} of ${count(status.chaptersInAll)} chapters ready",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 15.sp
                    )
                    if (status.chapters == 0) {
                        DsNote("None of ${if (telugu) "the recording's" else "either recording's"} files are in this folder. Choose the one they're in.")
                    }
                    if (status.missing.isNotEmpty() && status.chapters > 0) {
                        DsNote("Not in the folder, so read by the phone's voice: ${briefly(status.missing)}.")
                    }
                    if (status.unlike.isNotEmpty()) {
                        DsNote("Damaged, or not this recording (another length), so read by the phone's voice: ${briefly(status.unlike)}.")
                    }
                    if (status.failed.isNotEmpty()) {
                        DsNote("Couldn't be opened just now: ${briefly(status.failed)}. Moved? Look over the folder again.")
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DsOutlineAccentButton(
                text = if (status.folder == null || status.lost) "Choose folder" else "Choose another folder",
                onClick = onChooseFolder,
                enabled = checking == null
            )
            if (status.folder != null && !status.lost && checking == null) {
                DsOutlineAccentButton(text = "Look over the folder again", onClick = onLookAgain)
            }
        }
        SliderSetting(
            label = "Speed",
            description = "$language recording speed",
            value = settings.speed(telugu),
            range = ReadAloudSettings.MIN_SPEED..ReadAloudSettings.MAX_SPEED,
            usual = ReadAloudSettings.DEFAULT_SPEED,
            ends = "Slower" to "Faster",
            onChange = { speed ->
                settings.updateSpeed(telugu, speed)
                onChanged()
            }
        )
    }
}

// "1,189".
private fun count(n: Int): String = String.format(java.util.Locale.US, "%,d", n)

// The first few of a list of chapters, and how many more.
private fun briefly(chapters: List<String>): String =
    if (chapters.size <= BRIEF) chapters.joinToString(", ") else chapters.take(BRIEF).joinToString(", ") + " and ${chapters.size - BRIEF} more"

private const val BRIEF = 8

// Which recording a language's is: the one found in its folder, else the ones it can be.
private fun recordingNote(telugu: Boolean, status: Recordings.Status): String =
    when (status.recording.takeIf { status.checking == null && status.chapters > 0 }) {
        Recordings.Recording.KJV_DRAMATIZED -> KJV_DRAMATIZED
        Recordings.Recording.KJV_NARRATED -> KJV_NARRATED
        else -> if (telugu) TELUGU_RECORDING else ENGLISH_RECORDINGS
    }

private const val ENGLISH_RECORDINGS = "The King James Version, either of two recordings, its files kept just as they came: the KJV " +
    "Dramatized Audio from the Internet Archive (one MP4 a book), or Faith Comes By Hearing's (one MP3 a chapter: ENGKJVO1DA and ENGKJVN1DA)."
private const val KJV_DRAMATIZED = "The KJV Dramatized Audio from the Internet Archive: one MP4 a book, kept just as they came."
private const val KJV_NARRATED = "The King James Version from Faith Comes By Hearing: one MP3 a chapter (ENGKJVO1DA and ENGKJVN1DA), kept just as they came."
private const val TELUGU_RECORDING = "The Telugu Audio Bible from YGFM: one file a book (Psalms in two), kept just as they came."

// The phone's speech engines to read a language with, its usual one marked so. An AI voice app's
// voices sound more natural, but it can take a moment to start.
@Composable
private fun EngineChoice(engines: List<EngineOption>, chosen: String?, onPick: (EngineOption) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SettingName("Speech engine")
        for (engine in engines) {
            val picked = if (chosen == null) engine.usual else engine.name == chosen
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .selectable(selected = picked, role = Role.RadioButton, onClick = { onPick(engine) })
                    .padding(start = 2.dp)
            ) {
                RadioButton(selected = picked, onClick = null)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Text(engine.label, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.SansSerif, fontSize = 15.sp)
                    if (engine.usual) {
                        Text("The phone's usual one", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.SansSerif, fontSize = 13.sp)
                    }
                }
            }
        }
        if (chosen != null) DsNote("An AI voice app can take a moment to start, and may not change the pitch.")
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
    nothingChosen: String,
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
        if (selected == null || voices.none { it.name == selected }) DsNote(nothingChosen)
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
