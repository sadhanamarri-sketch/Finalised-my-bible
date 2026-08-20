package com.example.mybible.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.data.LexiconLookupResult
import com.example.mybible.data.MorphologyParser
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.LexiconDefinitionText

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
    val scrollState = rememberScrollState(initial = savedScrollPosition)

    DisposableEffect(Unit) {
        onDispose { viewModel.saveGreekWordScrollPosition(scrollState.value) }
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Greek Word",
                onBack = { viewModel.closeGreekWordPage() }
            )
        },
        modifier = modifier
    ) { padding ->
        val word = greekWord ?: return@Scaffold
        val foundEntry = (lexiconResult as? LexiconLookupResult.Found)?.entry
        // Capacitor only overwrites the inline gloss with the lexicon's
        // gloss when the tapped word didn't already carry one of its own.
        val displayedGloss = word.englishGloss.ifBlank { foundEntry?.gloss.orEmpty() }
        val inlineMorphology = MorphologyParser.describe(word.morphology).ifBlank { null }
        val lexiconMorphologyLine = foundEntry?.morphology
            ?.takeIf { it.isNotBlank() }
            ?.let { "Lexicon morphology: ${MorphologyParser.describe(it)}" }

        LexiconWordPageContent(
            headerLabel = if (word.strongs.isNullOrBlank()) "GREEK WORD" else "STRONG'S ${word.strongs.uppercase()}",
            script = word.greek,
            transliteration = word.transliteration,
            morphologyLine = inlineMorphology,
            gloss = displayedGloss,
            lexiconMorphologyLine = lexiconMorphologyLine,
            lexiconResult = lexiconResult,
            isLoading = isLoading,
            scrollState = scrollState,
            onReferenceClick = { book, chapter, verse ->
                viewModel.openVerseMentionPreview(book, chapter, verse, NavTab.GREEK_WORD)
            },
            modifier = Modifier.padding(padding)
        )
    }
}

/**
 * Hebrew counterpart to GreekWordScreen above — same structure, just backed
 * by TAHOT's inline gloss/grammar instead of TAGNT's, and by
 * getHebrewLexiconEntry (H-prefixed Strong's numbers) instead of
 * getLexiconEntry. No Hebrew morphology parser exists yet
 * (MorphologyParser.describe is Robinson/Greek-specific), so the ETCBC
 * grammar code TAHOT provides is shown as-is rather than expanded into
 * prose, and there's no "Lexicon morphology" line (TBESH's lexicon-level
 * morphology field isn't surfaced here, matching the old HebrewWordSheet).
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
    val scrollState = rememberScrollState(initial = savedScrollPosition)

    DisposableEffect(Unit) {
        onDispose { viewModel.saveHebrewWordScrollPosition(scrollState.value) }
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Hebrew Word",
                onBack = { viewModel.closeHebrewWordPage() }
            )
        },
        modifier = modifier
    ) { padding ->
        val word = hebrewWord ?: return@Scaffold
        val foundEntry = (lexiconResult as? LexiconLookupResult.Found)?.entry
        val displayedGloss = word.englishGloss.ifBlank { foundEntry?.gloss.orEmpty() }

        LexiconWordPageContent(
            headerLabel = if (word.strongs.isNullOrBlank()) "HEBREW WORD" else "STRONG'S ${word.strongs.uppercase()}",
            script = word.hebrew,
            transliteration = word.transliteration,
            morphologyLine = word.morphology.ifBlank { null }?.let { "Grammar: $it" },
            gloss = displayedGloss,
            lexiconMorphologyLine = null,
            lexiconResult = lexiconResult,
            isLoading = isLoading,
            scrollState = scrollState,
            onReferenceClick = { book, chapter, verse ->
                viewModel.openVerseMentionPreview(book, chapter, verse, NavTab.HEBREW_WORD)
            },
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun LexiconWordPageContent(
    headerLabel: String,
    script: String,
    transliteration: String,
    // Inline (per-occurrence) grammar line, already formatted by the
    // caller; null/blank means don't show it.
    morphologyLine: String?,
    gloss: String,
    // Greek-only "Lexicon morphology: ..." line, already formatted; null
    // for Hebrew (see HebrewWordScreen's doc).
    lexiconMorphologyLine: String?,
    lexiconResult: LexiconLookupResult?,
    isLoading: Boolean,
    scrollState: ScrollState,
    onReferenceClick: (book: String, chapter: Int, verse: Int) -> Unit,
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
        if (!morphologyLine.isNullOrBlank()) {
            Text(
                text = morphologyLine,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (gloss.isNotBlank()) {
            Text(
                text = gloss,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        if (!foundEntry?.lemma.isNullOrBlank()) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "Lexical form: ${foundEntry!!.lemma}",
                    fontSize = 12.5.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (foundEntry!!.transliteration.isNotBlank()) {
                    Text(
                        text = foundEntry!!.transliteration,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (!lexiconMorphologyLine.isNullOrBlank()) {
                    Text(
                        text = lexiconMorphologyLine,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp)
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
