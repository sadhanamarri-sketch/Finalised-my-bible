package com.example.mybible.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.BiblePlace
import com.example.mybible.data.HebrewMorphology
import com.example.mybible.data.LexiconLookupResult
import com.example.mybible.data.MorphologyParser
import com.example.mybible.data.Pronunciation
import com.example.mybible.data.linkNumber
import com.example.mybible.model.LinkedWord
import com.example.mybible.model.OriginalWordCard
import com.example.mybible.model.SavedWordItem
import com.example.mybible.model.SavedWordLanguage
import com.example.mybible.model.WordLinkKind
import com.example.mybible.model.WordSearchPreview
import com.example.mybible.model.WordStudy
import com.example.mybible.model.WordUse
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.GrammarTermsRow
import com.example.mybible.ui.components.HebrewWordParts
import com.example.mybible.ui.components.LexiconDefinitionText
import com.example.mybible.ui.components.openVoiceInstaller
import com.example.mybible.ui.theme.LiterataFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily
import kotlinx.coroutines.delay

/**
 * Full-page Greek word lexicon lookup, replacing the old GreekWordSheet
 * bottom sheet — same page-not-modal shape as CrossReferenceScreen/
 * SearchScreen: a Scaffold + BackTopBar, scroll position persisted across
 * the trip to Reader and back. The actual layout lives in
 * [LexiconWordPageContent], shared with [HebrewWordScreen] below (the old
 * two sheets were a near line-for-line duplicate of each other — converting
 * to pages was a natural point to stop duplicating the layout too).
 */
@Composable
fun GreekWordScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val greekWord by viewModel.selectedGreekWord.collectAsState()
    val lexiconResult by viewModel.lexiconResult.collectAsState()
    val isLoading by viewModel.isLoadingLexicon.collectAsState()
    val savedScrollPosition by viewModel.greekWordScrollPosition.collectAsState()
    val searchPreview by viewModel.wordSearchPreview.collectAsState()
    val study by viewModel.wordStudy.collectAsState()
    val scrollState = rememberScrollState(initial = savedScrollPosition)
    val savedWords by viewModel.savedWords.collectAsState(initial = emptyList())
    val isSaved = greekWord?.let { w ->
        val key = SavedWordItem(language = SavedWordLanguage.GREEK, word = w.greek, transliteration = w.transliteration).dedupeKey()
        savedWords.any { it.dedupeKey() == key }
    } ?: false

    DisposableEffect(Unit) {
        onDispose { viewModel.saveGreekWordScrollPosition(scrollState.value) }
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Greek Word",
                onBack = { viewModel.closeGreekWordPage() },
                actions = {
                    IconButton(onClick = { viewModel.toggleSaveCurrentGreekWord() }) {
                        Icon(
                            imageVector = if (isSaved) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = if (isSaved) "Remove from saved words" else "Save word",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        val word = greekWord ?: return@Scaffold
        val foundEntry = (lexiconResult as? LexiconLookupResult.Found)?.entry
        // Capacitor only overwrites the inline gloss with the lexicon's
        // gloss when the tapped word didn't already carry one of its own.
        val displayedGloss = word.englishGloss.ifBlank { foundEntry?.gloss.orEmpty() }
        val grammar = remember(word.morphology) { MorphologyParser.terms(word.morphology) }
        val soundsLike = remember(word.greek) { Pronunciation.greek(word.greek) }

        LexiconWordPageContent(
            headerLabel = if (word.strongs.isNullOrBlank()) "GREEK WORD" else "STRONG'S ${word.strongs.uppercase()}",
            script = word.greek,
            transliteration = word.transliteration,
            soundsLike = soundsLike,
            onSay = { viewModel.sayPronunciation(soundsLike) },
            hasVoice = viewModel::hasPronunciationVoice,
            gloss = displayedGloss,
            grammar = { if (grammar.isNotEmpty()) GrammarTermsRow(grammar) },
            lexicalTransliteration = foundEntry?.transliteration.orEmpty(),
            lexiconResult = lexiconResult,
            isLoading = isLoading,
            scrollState = scrollState,
            onReferenceClick = { book, chapter, verse ->
                viewModel.openVerseMentionPreview(book, chapter, verse, NavTab.GREEK_WORD)
            },
            searchPreview = searchPreview,
            study = study,
            onFindEveryVerse = { query, place, offChips -> viewModel.findEveryVerseWithWord(query, place, offChips) },
            links = WordLinkActions(
                otherLanguage = "Hebrew",
                onOpen = { linked ->
                    // Its page goes over this one: this one's place is kept for coming back.
                    viewModel.saveGreekWordScrollPosition(scrollState.value)
                    viewModel.openLinkedWord(linked)
                },
                onUnlink = { linked -> viewModel.unlinkOpenWord(linked.number) },
                find = { query -> viewModel.wordsToLink(query, hebrew = true) },
                onLink = { card -> viewModel.linkOpenWord(card.number) }
            ),
            modifier = Modifier.padding(padding)
        )
    }
}

/**
 * Hebrew counterpart to GreekWordScreen above — same structure, just backed
 * by TAHOT's inline gloss/grammar instead of TAGNT's, and by
 * getHebrewLexiconEntry (H-prefixed Strong's numbers) instead of
 * getLexiconEntry. TAHOT writes a word in its parts (בְּ/רֵאשִׁ֖ית, "in/
 * beginning"), and HebrewMorphology reads them: the word is shown whole,
 * its transliteration to read aloud (be·re·SHIT), and its parts each with
 * a gloss and its grammar.
 */
@Composable
fun HebrewWordScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val hebrewWord by viewModel.selectedHebrewWord.collectAsState()
    val lexiconResult by viewModel.hebrewLexiconResult.collectAsState()
    val isLoading by viewModel.isLoadingHebrewLexicon.collectAsState()
    val savedScrollPosition by viewModel.hebrewWordScrollPosition.collectAsState()
    val searchPreview by viewModel.wordSearchPreview.collectAsState()
    val study by viewModel.wordStudy.collectAsState()
    val scrollState = rememberScrollState(initial = savedScrollPosition)
    val savedWords by viewModel.savedWords.collectAsState(initial = emptyList())
    val isSaved = hebrewWord?.let { w ->
        val key = SavedWordItem(language = SavedWordLanguage.HEBREW, word = w.hebrew, transliteration = w.transliteration).dedupeKey()
        savedWords.any { it.dedupeKey() == key }
    } ?: false

    DisposableEffect(Unit) {
        onDispose { viewModel.saveHebrewWordScrollPosition(scrollState.value) }
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Hebrew Word",
                onBack = { viewModel.closeHebrewWordPage() },
                actions = {
                    IconButton(onClick = { viewModel.toggleSaveCurrentHebrewWord() }) {
                        Icon(
                            imageVector = if (isSaved) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = if (isSaved) "Remove from saved words" else "Save word",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        val word = hebrewWord ?: return@Scaffold
        val foundEntry = (lexiconResult as? LexiconLookupResult.Found)?.entry
        val displayedGloss = word.englishGloss.ifBlank { foundEntry?.gloss.orEmpty() }
        val parts = remember(word) { HebrewMorphology.parts(word) }
        val soundsLike = remember(word.transliteration, foundEntry) {
            val syllables = HebrewMorphology.cleanTransliteration(word.transliteration)
            when {
                // As the verse has it: be·re·SHIT.
                '·' in syllables || syllables.any(Char::isUpperCase) -> Pronunciation.hebrew(syllables)
                // A word opened from a link has only "chesed": the lexicon's che.sed, then.
                !foundEntry?.transliteration.isNullOrBlank() ->
                    Pronunciation.hebrewDictionaryForm(foundEntry!!.transliteration, foundEntry.lemma.ifBlank { word.hebrew })
                else -> Pronunciation.hebrew(syllables)
            }
        }

        LexiconWordPageContent(
            headerLabel = if (word.strongs.isNullOrBlank()) "HEBREW WORD" else "STRONG'S ${word.strongs.uppercase()}",
            script = HebrewMorphology.cleanWord(word.hebrew),
            transliteration = HebrewMorphology.cleanTransliteration(word.transliteration),
            soundsLike = soundsLike,
            onSay = { viewModel.sayPronunciation(soundsLike) },
            hasVoice = viewModel::hasPronunciationVoice,
            gloss = HebrewMorphology.cleanGloss(displayedGloss),
            grammar = {
                when {
                    parts.size > 1 -> HebrewWordParts(parts)
                    parts.firstOrNull()?.grammar.orEmpty().isNotEmpty() -> GrammarTermsRow(parts[0].grammar)
                }
            },
            lexicalTransliteration = HebrewMorphology.cleanTransliteration(foundEntry?.transliteration.orEmpty()),
            lexiconResult = lexiconResult,
            isLoading = isLoading,
            scrollState = scrollState,
            onReferenceClick = { book, chapter, verse ->
                viewModel.openVerseMentionPreview(book, chapter, verse, NavTab.HEBREW_WORD)
            },
            searchPreview = searchPreview,
            study = study,
            onFindEveryVerse = { query, place, offChips -> viewModel.findEveryVerseWithWord(query, place, offChips) },
            links = WordLinkActions(
                otherLanguage = "Greek",
                onOpen = { linked ->
                    viewModel.saveHebrewWordScrollPosition(scrollState.value)
                    viewModel.openLinkedWord(linked)
                },
                onUnlink = { linked -> viewModel.unlinkOpenWord(linked.number) },
                find = { query -> viewModel.wordsToLink(query, hebrew = false) },
                onLink = { card -> viewModel.linkOpenWord(card.number) }
            ),
            modifier = Modifier.padding(padding)
        )
    }
}

/**
 * What a word page does with the words of the other language its word is linked to: opens one's page,
 * removes a link of your own, finds words to link and links one.
 */
private class WordLinkActions(
    /** "Hebrew" on a Greek word's page, "Greek" on a Hebrew one's. */
    val otherLanguage: String,
    val onOpen: (LinkedWord) -> Unit,
    val onUnlink: (LinkedWord) -> Unit,
    val find: suspend (query: String) -> List<OriginalWordCard>,
    val onLink: (OriginalWordCard) -> Unit
)

@Composable
private fun LexiconWordPageContent(
    headerLabel: String,
    script: String,
    transliteration: String,
    // How it sounds, spelled for an English reader (ah-GAH-pay), and its speaker.
    soundsLike: String,
    onSay: () -> Boolean?,
    hasVoice: () -> Boolean?,
    gloss: String,
    // The word's grammar here, as terms a tap explains (and a Hebrew word's parts).
    grammar: @Composable () -> Unit,
    // The lexicon's transliteration of the word's dictionary form, as the page shows it.
    lexicalTransliteration: String,
    lexiconResult: LexiconLookupResult?,
    isLoading: Boolean,
    scrollState: ScrollState,
    onReferenceClick: (book: String, chapter: Int, verse: Int) -> Unit,
    // What "Find every verse with this word" finds (null: not known yet, or nothing), and how the
    // word is used (its meanings, King James words, books).
    searchPreview: WordSearchPreview?,
    study: WordStudy?,
    // Runs the search for the word: in a place (one book), and with some of its meanings' chips off.
    onFindEveryVerse: (query: String, place: BiblePlace, offChips: Set<String>) -> Unit,
    links: WordLinkActions,
    modifier: Modifier = Modifier
) {
    val foundEntry = (lexiconResult as? LexiconLookupResult.Found)?.entry
    var linking by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        Text(
            text = headerLabel,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary
        )
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = script,
            fontSize = 28.sp,
            fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (transliteration.isNotBlank()) {
            Text(
                text = transliteration,
                fontSize = 15.5.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (soundsLike.isNotBlank()) SoundsLike(soundsLike, onSay, hasVoice)
        if (gloss.isNotBlank()) {
            Text(
                text = gloss,
                fontSize = 17.sp,
                fontFamily = LiterataFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Box(modifier = Modifier.padding(top = 10.dp)) { grammar() }

        searchPreview?.let { preview ->
            FindEveryVerseButton(preview, onClick = { onFindEveryVerse(preview.query, BiblePlace.WholeBible, emptySet()) })
            LinkedWordsCard(preview.linked, links, greekPage = links.otherLanguage == "Hebrew", onAdd = { linking = true })
            if (linking) {
                LinkWordSheet(
                    word = script,
                    transliteration = transliteration,
                    linked = remember(preview.linked) { preview.linked.mapNotNullTo(HashSet()) { linkNumber(it.number) } },
                    links = links,
                    onDismiss = { linking = false }
                )
            }
        }
        study?.let { s ->
            WordStudyCard(s, onSearch = { place, offChips -> onFindEveryVerse(s.query, place, offChips) })
        }

        if (!foundEntry?.lemma.isNullOrBlank()) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "Lexical form: ${foundEntry!!.lemma}",
                    fontSize = 12.5.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (lexicalTransliteration.isNotBlank()) {
                    Text(
                        text = lexicalTransliteration,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when {
            isLoading -> {
                Text(
                    text = "Loading definition…",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            lexiconResult is LexiconLookupResult.NoStrongsNumber -> {
                Text(
                    text = "No Strong’s number is tagged for this word.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            lexiconResult is LexiconLookupResult.NetworkError -> {
                Text(
                    text = "Couldn’t load the definition — check your connection and try again.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            lexiconResult is LexiconLookupResult.NotFound -> {
                Text(
                    text = "Full definition not available for this word.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            foundEntry != null -> {
                val body = foundEntry.definition.ifBlank { foundEntry.gloss }
                if (body.isNotBlank()) {
                    LexiconDefinitionText(definition = body, onReferenceClick = onReferenceClick)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// How the word sounds, spelled for an English reader (ah-GAH-pay), and a speaker that says just that, in
// the English reading voice — or says there's no voice, with a way to get one.
@Composable
private fun SoundsLike(soundsLike: String, onSay: () -> Boolean?, hasVoice: () -> Boolean?) {
    val context = LocalContext.current
    var noVoice by remember(soundsLike) { mutableStateOf(false) }
    // Tapped while the phone's speech engine was starting: whether it has the voice, once it knows.
    var asked by remember(soundsLike) { mutableStateOf(0) }
    LaunchedEffect(asked) {
        if (asked == 0) return@LaunchedEffect
        repeat(10) {
            delay(500)
            when (hasVoice()) {
                null -> {}
                false -> {
                    noVoice = true
                    return@LaunchedEffect
                }
                true -> return@LaunchedEffect
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("Sounds like ") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)) { append(soundsLike) }
            },
            fontSize = 14.sp,
            fontFamily = WorkSansFontFamily,
            modifier = Modifier.weight(1f, fill = false)
        )
        IconButton(
            onClick = {
                val voice = onSay()
                noVoice = voice == false
                if (voice == null) asked++
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Say it",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    if (noVoice) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "No voice on this phone to say it with yet.",
                fontSize = 13.sp,
                fontFamily = WorkSansFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { openVoiceInstaller(context) }) { Text("Get one", fontSize = 13.sp) }
        }
    }
}

/**
 * The words of the other language the word is linked to: the Septuagint's (ahavah for agapē), the one
 * it comes from (mashiach for Messias), and yours, which can be removed. A word opens its page; "Link a
 * Hebrew word" finds one to add. "Find every verse with this word" finds them all with it.
 */
@Composable
private fun LinkedWordsCard(linked: List<LinkedWord>, links: WordLinkActions, greekPage: Boolean, onAdd: () -> Unit) {
    var unlinking by remember { mutableStateOf<LinkedWord?>(null) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 6.dp)) {
            StudyHeading("${links.otherLanguage} words for it")
            if (linked.isEmpty()) {
                Text(
                    text = "No ${links.otherLanguage} word is linked to it yet.",
                    fontSize = 14.sp,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            for (word in linked) {
                LinkedWordRow(word, greekPage, onOpen = { links.onOpen(word) }, onUnlink = { unlinking = word })
            }
            TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Link a ${links.otherLanguage} word", fontSize = 14.sp, fontFamily = WorkSansFontFamily, letterSpacing = 0.sp)
            }
            if (linked.any { it.kind == WordLinkKind.SEPTUAGINT }) {
                Text(
                    text = "Septuagint: the Greek Old Testament translates the one with the other.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                )
            }
        }
    }
    unlinking?.let { word ->
        AlertDialog(
            onDismissRequest = { unlinking = null },
            title = { Text("Remove your link?") },
            text = { Text("${word.word} (${word.transliteration}) will no longer be linked to this word or found with it.") },
            confirmButton = {
                TextButton(onClick = {
                    links.onUnlink(word)
                    unlinking = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { unlinking = null }) { Text("Cancel") } }
        )
    }
}

// A linked word: the word and how it's said, what it means and in how many verses, and why it's
// linked. Tapping it opens its page; one of yours has a ✕ to remove it.
@Composable
private fun LinkedWordRow(word: LinkedWord, greekPage: Boolean, onOpen: () -> Unit, onUnlink: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface)) {
                        append(word.word)
                    }
                    append("  ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontSize = 14.5.sp, color = MaterialTheme.colorScheme.tertiary)) {
                        append(word.transliteration)
                    }
                }
            )
            Text(
                text = "${word.meaning} · ${if (word.verseCount == 1) "1 verse" else "${word.verseCount} verses"}",
                fontSize = 13.sp,
                fontFamily = WorkSansFontFamily,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when (word.kind) {
                    WordLinkKind.SEPTUAGINT -> "Septuagint"
                    WordLinkKind.ORIGIN -> if (greekPage) "Its origin" else "Comes from this word"
                    WordLinkKind.YOURS -> "Your link"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = WorkSansFontFamily,
                letterSpacing = 0.sp,
                color = if (word.kind == WordLinkKind.YOURS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
        }
        if (word.kind == WordLinkKind.YOURS) {
            IconButton(onClick = onUnlink, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove your link to ${word.transliteration}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(20.dp)
        )
    }
}

/**
 * Finds a word of the other language to link to [word]: by its letters, how it sounds, its Strong's
 * number or what it means. One already linked says so; tapping another links it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkWordSheet(
    word: String,
    transliteration: String,
    // The numbers of the words already linked to it (H0160).
    linked: Set<String>,
    links: WordLinkActions,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    // Null while nothing's typed, or while it's looked for.
    var found by remember { mutableStateOf<List<OriginalWordCard>?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(query) {
        found = null
        if (query.isBlank()) return@LaunchedEffect
        delay(200)
        found = links.find(query)
    }
    val hebrew = links.otherLanguage != "Greek"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "LINK A ${links.otherLanguage.uppercase()} WORD",
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = WorkSansFontFamily,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = buildAnnotatedString {
                    append("to ")
                    withStyle(SpanStyle(fontFamily = FontFamily.Serif)) { append(word) }
                    if (transliteration.isNotBlank()) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.tertiary)) { append("  $transliteration") }
                    }
                },
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("${links.otherLanguage} word, its sound, number or meaning") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
            // The keyboard up for typing, once the field is on screen (the sheet is its own window).
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            val results = found
            when {
                query.isBlank() -> LinkSheetNote(
                    if (hebrew) "For example חֶסֶד, chesed, H2617 or kindness." else "For example ἔλεος, eleos, G1656 or mercy."
                )
                results == null -> {}
                results.isEmpty() -> LinkSheetNote("No ${links.otherLanguage} word found for “${query.trim()}”.")
                else -> LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                    items(results, key = { it.number }) { card ->
                        val isLinked = linkNumber(card.number) in linked
                        LinkCandidateRow(card, isLinked, onClick = {
                            links.onLink(card)
                            onDismiss()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkSheetNote(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontFamily = WorkSansFontFamily,
        letterSpacing = 0.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp)
    )
}

// A word that can be linked: the word, how it's said and its number, what it means and in how many
// verses. One already linked can't be tapped.
@Composable
private fun LinkCandidateRow(card: OriginalWordCard, isLinked: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLinked, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface)) {
                        append(card.word.substringBefore(','))
                    }
                    append("  ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontSize = 14.5.sp, color = MaterialTheme.colorScheme.tertiary)) {
                        append(card.transliteration.substringBefore(','))
                    }
                }
            )
            Text(
                text = buildString {
                    append(card.meanings.joinToString(", "))
                    append(" · ")
                    append(if (card.verseCount == 1) "1 verse" else "${card.verseCount} verses")
                    if (card.language == "Aramaic") append(" · Aramaic")
                },
                fontSize = 13.sp,
                fontFamily = WorkSansFontFamily,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (isLinked) "Linked" else card.number,
            fontSize = 13.sp,
            fontWeight = if (isLinked) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = WorkSansFontFamily,
            letterSpacing = 0.sp,
            color = if (isLinked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

// Search for the word in both Testaments: its own verses, and the other Testament's with the word
// in its language ("104 verses, and 35 in the Old Testament with אַהֲבָה (ahavah)").
@Composable
private fun FindEveryVerseButton(preview: WordSearchPreview, onClick: () -> Unit) {
    fun verses(n: Int) = if (n == 1) "1 verse" else "$n verses"
    val detail = buildString {
        append(verses(preview.verseCount))
        if (preview.otherVerseCount > 0) {
            append(", and ${preview.otherVerseCount} in the ${preview.otherTestament} with ")
            append(preview.otherWords.joinToString(" or "))
        }
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Find every verse with this word",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = detail,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * How the word is used: its meaning in this verse among its others (aphiēmi: "forgive", of "to
 * release" 72, "forgive" 38, "permit" 23), the King James words for it as bars (love 75%,
 * charity 23%) and the books it's in most. A meaning or a book opens Search with just its verses.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordStudyCard(study: WordStudy, onSearch: (place: BiblePlace, offChips: Set<String>) -> Unit) {
    var allMeanings by remember(study) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            if (study.meanings.isNotEmpty()) {
                Column {
                    StudyHeading("Its meanings")
                    study.meaningHere?.let { here ->
                        Text(
                            text = buildAnnotatedString {
                                append("In this verse: ")
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append(here) }
                            },
                            fontSize = 14.5.sp,
                            fontFamily = WorkSansFontFamily,
                            letterSpacing = 0.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    val isHere = { use: WordUse -> use.label.equals(study.meaningHere, ignoreCase = true) }
                    val shown = if (allMeanings || study.meanings.size <= MEANINGS_SHOWN + 1) study.meanings else {
                        study.meanings.filterIndexed { i, use -> i < MEANINGS_SHOWN || isHere(use) }
                    }
                    // No space between rows: each chip's touch target is taller than the chip.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (use in shown) {
                            StudyChip(use.label, use.count, highlighted = isHere(use), onClick = { onSearch(BiblePlace.WholeBible, use.offChips) })
                        }
                        if (shown.size < study.meanings.size) {
                            StudyChip("${study.meanings.size - shown.size} more", null, highlighted = false, onClick = { allMeanings = true })
                        }
                    }
                }
            }

            if (study.kingJames.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudyHeading("In the King James")
                    for (use in study.kingJames) KingJamesBar(use)
                    Text(
                        text = "The share of its verses with each word",
                        fontSize = 12.sp,
                        fontFamily = WorkSansFontFamily,
                        letterSpacing = 0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (study.books.isNotEmpty()) {
                Column {
                    StudyHeading(if (study.bookCount == 1) "Where it’s used" else "Where it’s used most")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (use in study.books) {
                            StudyChip(use.label, use.count, highlighted = false, onClick = { onSearch(BiblePlace.Book(use.label), emptySet()) })
                        }
                    }
                    if (study.bookCount > study.books.size) {
                        Text(
                            text = "In ${study.bookCount} books in all",
                            fontSize = 12.sp,
                            fontFamily = WorkSansFontFamily,
                            letterSpacing = 0.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// Meanings shown before a "more" chip: davar has nine, nasa eighteen.
private const val MEANINGS_SHOWN = 6

@Composable
private fun StudyHeading(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = WorkSansFontFamily,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

// A meaning or a book, with its verses; the meaning in this verse stands out.
@Composable
private fun StudyChip(label: String, count: Int?, highlighted: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = buildAnnotatedString {
                append(label)
                if (count != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("  $count") }
                }
            },
            fontSize = 14.sp,
            fontFamily = WorkSansFontFamily,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = 0.sp,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// A King James word and the share of the word's verses that have it, as a thin bar: square at
// its start, rounded at its end, on a light track of the same color.
@Composable
private fun KingJamesBar(use: WordUse) {
    val barShape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
    ) {
        Text(
            text = use.label,
            fontSize = 14.sp,
            fontFamily = WorkSansFontFamily,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(96.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), barShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(use.count.coerceIn(1, 100) / 100f)
                    .background(MaterialTheme.colorScheme.primary, barShape)
            )
        }
        Text(
            text = "${use.count}%",
            fontSize = 13.sp,
            fontFamily = WorkSansFontFamily,
            letterSpacing = 0.sp,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
    }
}
