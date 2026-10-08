package com.example.mybible.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.BiblePlace
import com.example.mybible.data.HebrewMorphology
import com.example.mybible.data.LexiconLookupResult
import com.example.mybible.data.MorphologyParser
import com.example.mybible.model.SavedWordItem
import com.example.mybible.model.SavedWordLanguage
import com.example.mybible.model.WordSearchPreview
import com.example.mybible.model.WordStudy
import com.example.mybible.model.WordUse
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.GrammarTermsRow
import com.example.mybible.ui.components.HebrewWordParts
import com.example.mybible.ui.components.LexiconDefinitionText
import com.example.mybible.ui.theme.LiterataFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily

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

        LexiconWordPageContent(
            headerLabel = if (word.strongs.isNullOrBlank()) "GREEK WORD" else "STRONG'S ${word.strongs.uppercase()}",
            script = word.greek,
            transliteration = word.transliteration,
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

        LexiconWordPageContent(
            headerLabel = if (word.strongs.isNullOrBlank()) "HEBREW WORD" else "STRONG'S ${word.strongs.uppercase()}",
            script = HebrewMorphology.cleanWord(word.hebrew),
            transliteration = HebrewMorphology.cleanTransliteration(word.transliteration),
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
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun LexiconWordPageContent(
    headerLabel: String,
    script: String,
    transliteration: String,
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
    modifier: Modifier = Modifier
) {
    val foundEntry = (lexiconResult as? LexiconLookupResult.Found)?.entry

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
