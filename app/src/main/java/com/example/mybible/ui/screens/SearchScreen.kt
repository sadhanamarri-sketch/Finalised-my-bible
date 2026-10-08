package com.example.mybible.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.SheetChip
import com.example.mybible.model.SearchHit
import com.example.mybible.model.SearchOutcome
import com.example.mybible.model.SearchSource
import com.example.mybible.model.TopicCard
import com.example.mybible.model.ThemeMode
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.DsSwitch
import com.example.mybible.ui.theme.WorkSansFontFamily

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    themeMode: ThemeMode,
    modifier: Modifier = Modifier
) {
    // A Nave's topic opened from the results covers them, with its own back; the results keep
    // their place underneath (saved as they leave, like a trip to the Reader).
    val openTopic by viewModel.openTopic.collectAsState()
    val topic = openTopic
    if (topic != null) {
        TopicScreen(topic, viewModel, modifier)
    } else {
        SearchPage(viewModel, themeMode, modifier)
    }
}

@Composable
private fun SearchPage(
    viewModel: MainViewModel,
    themeMode: ThemeMode,
    modifier: Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val outcome by viewModel.searchOutcome.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val caseSensitive by viewModel.searchCaseSensitive.collectAsState()
    val savedScrollIndex by viewModel.searchScrollIndex.collectAsState()
    val savedScrollOffset by viewModel.searchScrollOffset.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val lastTappedKey by viewModel.searchLastTappedKey.collectAsState()

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScrollIndex,
        initialFirstVisibleItemScrollOffset = savedScrollOffset
    )

    // Marks the last-tapped result with an accent bar on return, so the
    // user can spot which one they already visited — cleared on the first
    // scroll after landing (same "scroll to clear" idea as Reader's
    // xrefFocusActive), not on a timer or tap, since scrolling is the
    // natural signal that they've moved on to browsing something else.
    LaunchedEffect(lastTappedKey) {
        if (lastTappedKey == null) return@LaunchedEffect
        val landedIndex = listState.firstVisibleItemIndex
        val landedOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (idx, offset) ->
                if (idx != landedIndex || kotlin.math.abs(offset - landedOffset) > 4) {
                    viewModel.clearSearchLastTapped()
                }
            }
    }

    // Auto-focus the search field and pop the keyboard the moment this
    // screen appears (e.g. tapping Search on the home screen widget) —
    // otherwise the user lands here and has to manually tap the field
    // before they can type. Skipped when arriving via "Return to search
    // results" (see suppressNextSearchAutofocus), since that flow is meant
    // to drop the user back into browsing their existing results, not
    // straight into the keyboard.
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val suppressAutofocus by viewModel.suppressNextSearchAutofocus.collectAsState()

    // The field is driven by a local TextFieldValue (not the raw String
    // from the ViewModel) so the cursor position can be controlled
    // explicitly. With the plain-String OutlinedTextField overload, a
    // fresh composable instance always starts with an internal selection
    // of (0,0) — so returning to Search with a query already in it (e.g.
    // after backing out to Reader and back) and calling requestFocus()
    // below would drop the cursor before the first character instead of
    // after the last one.
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = searchQuery, selection = TextRange(searchQuery.length)))
    }
    // Keeps the field in sync when the query changes from outside typing
    // (e.g. tapping a "recent search" chip) — cursor goes to the end in
    // that case too, matching how a real search field behaves.
    LaunchedEffect(searchQuery) {
        if (textFieldValue.text != searchQuery) {
            textFieldValue = TextFieldValue(text = searchQuery, selection = TextRange(searchQuery.length))
        }
    }

    // Search's index takes a moment to build the first time: start on it now, while the user types.
    LaunchedEffect(Unit) { viewModel.prepareSearch() }

    LaunchedEffect(Unit) {
        if (suppressAutofocus) {
            viewModel.consumeSuppressSearchAutofocus()
        } else {
            // The field sits in Scaffold's content, which is composed as the
            // page is first laid out. When Search is in the app's very first
            // frame (a widget shortcut opening it), this can run before that
            // and requestFocus() throws; a frame later the field is there.
            withFrameNanos { }
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // SearchScreen is fully disposed (not just hidden) when the user
    // switches tabs — save scroll position on the way out so "return to
    // search results" lands back in the same spot.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveSearchScrollPosition(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Search",
                onBack = {
                    // Same "hide before navigating away" as the field's own
                    // search action below — otherwise a focused field's
                    // keyboard state stays dangling on a screen the user
                    // has already left, and can resurface later (e.g. once
                    // a sheet opened from a still-composed screen closes).
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    viewModel.backToSearchSourceVerse()
                    viewModel.selectTab(NavTab.READER)
                },
                actions = {
                    IconButton(onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        viewModel.openSavedWordsScreen()
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Saved Words"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                viewModel.onSearchQueryChanged(it.text)
            },
            label = { Text("Search in the Bible") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearSearchInput() }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    viewModel.commitSearchToHistory()
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            ),
            // Coral focus ring instead of Material's default primary blue —
            // matches the flat/line-bordered look the rest of the app's
            // inputs use (see NeTextField) rather than the stock Material
            // outlined-field treatment.
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocusRequester)
                .testTag("search_input_field")
        )

        // Recent searches — only worth showing once there's nothing typed
        // and nothing already found; once results are on screen the
        // suggestions would just be clutter above them.
        if (searchHistory.isNotEmpty() && searchQuery.isBlank() && outcome.hits.isEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent searches",
                    fontSize = 12.5.sp,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Clear",
                    fontSize = 12.5.sp,
                    fontFamily = WorkSansFontFamily,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { viewModel.clearSearchHistory() }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchHistory) { term ->
                    // Bordered pill instead of Material's default filled
                    // AssistChip — matches the neutral chip look used for
                    // note references (see NoteEditorScreen's ref-chip)
                    // rather than a solid Material surface-tint fill.
                    AssistChip(
                        onClick = { viewModel.searchFromHistory(term) },
                        label = { Text(term, fontSize = 13.sp, fontFamily = WorkSansFontFamily, letterSpacing = 0.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove from recent searches",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { viewModel.removeSearchHistoryItem(term) }
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            trailingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                isSearching -> " "
                searchQuery.trim().length < 2 -> " "
                else -> countLine(outcome)
            },
            fontSize = 13.sp,
            fontFamily = WorkSansFontFamily,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Case-sensitive",
                fontSize = 13.sp,
                fontFamily = WorkSansFontFamily,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            DsSwitch(
                checked = caseSensitive,
                onCheckedChange = { viewModel.setSearchCaseSensitive(it) },
                modifier = Modifier.testTag("search_case_sensitive_toggle")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isSearching) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                CircularProgressIndicator()
            }
        } else if (searchQuery.trim().length < 2) {
            SearchHelp()
        } else {
            // Elevated Cards, not flat/hairline-divided rows — the one
            // place in this pass keeping Material's card-with-shadow look
            // rather than the flat-bordered treatment used elsewhere
            // (Cross References/Highlighted Verses), by request.
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (outcome.topics.isNotEmpty()) {
                    item(key = "topics") {
                        TopicCards(outcome.topics, onOpen = { viewModel.openTopic(it.topicId, it.section) })
                    }
                }
                // Shown even when nothing is left on: it's how to turn things back on.
                if (outcome.sources.isNotEmpty()) {
                    item(key = "sources") {
                        SourceChips(outcome.sources, themeMode, onToggle = { viewModel.toggleSearchSource(it) })
                    }
                }
                outcome.suggestion?.let { suggestion ->
                    item(key = "suggestion") {
                        DidYouMean(suggestion, onClick = { viewModel.searchFromHistory(suggestion) })
                    }
                }
                if (outcome.hits.isEmpty() && outcome.suggestion == null) {
                    item(key = "none") {
                        Text(
                            text = if (outcome.sources.any { !it.enabled }) "Nothing left with these switched off" else "No matching verses found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = WorkSansFontFamily,
                            letterSpacing = 0.sp,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (outcome.closeMatches) {
                    item(key = "close-heading") { ResultsHeading("No verse has every word. These have all but one:", spaced = false) }
                }
                val card: @Composable (SearchHit) -> Unit = { hit ->
                    SearchResultCard(
                        hit = hit,
                        isLastTapped = hit.key() == lastTappedKey,
                        onClick = { viewModel.openSearchResult(hit.verse) }
                    )
                }
                items(outcome.hits.subList(0, outcome.exactCount), key = { it.key() }) { card(it) }
                if (outcome.relatedCount > 0 && !outcome.closeMatches) {
                    item(key = "related-heading") { ResultsHeading("Same meaning, other words") }
                }
                items(outcome.hits.subList(outcome.exactCount, outcome.hits.size), key = { it.key() }) { card(it) }
            }
        }
    }
    }
}

// Same "book:chapter:verse" as MainViewModel's searchLastTappedKey.
private fun SearchHit.key() = "${verse.book}:${verse.chapter}:${verse.number}"

private fun countLine(outcome: SearchOutcome): String {
    fun verses(n: Int) = if (n == 1) "1 verse" else "$n verses"
    return when {
        outcome.hits.isEmpty() -> "No verses found"
        outcome.closeMatches -> verses(outcome.hits.size) + " with all but one word"
        outcome.relatedCount == 0 -> verses(outcome.exactCount)
        outcome.exactCount == 0 -> verses(outcome.relatedCount) + " with the same meaning"
        else -> verses(outcome.exactCount) + " · ${outcome.relatedCount} more with the same meaning"
    }
}

// What else the search matched besides the words as typed — their other forms, King James
// wording, Greek and Hebrew words — each switched off and on with a tap.
@Composable
private fun SourceChips(sources: List<SearchSource>, themeMode: ThemeMode, onToggle: (List<String>) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sources, key = { it.ids.joinToString(",") }) { source ->
            SheetChip(
                text = source.label,
                selected = source.enabled,
                themeMode = themeMode,
                count = source.count,
                onClick = { onToggle(source.ids) }
            )
        }
    }
}

// Nave's topics the search names: the first few, the rest a tap away.
@Composable
private fun TopicCards(topics: List<TopicCard>, onOpen: (TopicCard) -> Unit) {
    var showAll by remember(topics) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultsHeading("Topics")
        for (topic in if (showAll) topics else topics.take(TOPICS_SHOWN)) TopicCardRow(topic) { onOpen(topic) }
        if (!showAll && topics.size > TOPICS_SHOWN) {
            Text(
                text = "More topics (${topics.size - TOPICS_SHOWN})",
                fontSize = 13.5.sp,
                fontFamily = WorkSansFontFamily,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { showAll = true }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

private const val TOPICS_SHOWN = 3

@Composable
private fun TopicCardRow(topic: TopicCard, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.name + (topic.sectionLabel?.let { " › $it" } ?: ""),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // "Anxiety: see Care" — why a search for anxiety shows Care.
                val detail = topic.via?.let { "$it: see ${topic.name}" } ?: topic.summary
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        fontSize = 13.sp,
                        fontFamily = WorkSansFontFamily,
                        letterSpacing = 0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "${topic.referenceCount}",
                fontSize = 13.sp,
                fontFamily = WorkSansFontFamily,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DidYouMean(suggestion: String, onClick: () -> Unit) {
    Text(
        text = buildAnnotatedString {
            append("Did you mean ")
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append("“$suggestion”")
            }
            append("?")
        },
        fontSize = 14.sp,
        fontFamily = WorkSansFontFamily,
        letterSpacing = 0.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    )
}

// spaced: a short label, letter-spaced like "Recent searches"; else a plain sentence.
@Composable
private fun ResultsHeading(text: String, spaced: Boolean = true) {
    Text(
        text = text,
        fontSize = if (spaced) 12.5.sp else 13.sp,
        fontFamily = WorkSansFontFamily,
        letterSpacing = if (spaced) 1.sp else 0.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

// Before anything is typed: what Search does, and where its Greek and Hebrew comes from.
@Composable
private fun SearchHelp() {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
            Text(
                text = "Type a word or a phrase (“worry”, “love one another”) or a reference (“John 3”, “John 3:16”). " +
                    "Search finds other forms of your words, how the King James says them, verses with the same Greek or Hebrew word, " +
                    "and topics from Nave’s Topical Bible.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = WorkSansFontFamily,
                letterSpacing = 0.sp,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = "Greek and Hebrew meanings: STEPBible.org, Tyndale House (CC BY 4.0). " +
                "King James renderings: Strong’s, via Open Scriptures (CC BY-SA). " +
                "Topics: Nave’s Topical Bible, structured by BibleData (CC BY 4.0).",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            fontFamily = WorkSansFontFamily,
            letterSpacing = 0.sp,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
    }
}

// Extracted from the results list into its own composable for readability.
@Composable
private fun SearchResultCard(
    hit: SearchHit,
    isLastTapped: Boolean,
    onClick: () -> Unit
) {
    val verse = hit.verse
    // Gold, like the reference above it: primary is near-black in the light themes, a grey wash.
    val highlight = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
    val text = remember(hit, highlight) {
        buildAnnotatedString {
            append(hit.text)
            for (range in hit.highlights) {
                if (range.first >= 0 && range.last < hit.text.length) {
                    addStyle(SpanStyle(background = highlight), range.first, range.last + 1)
                }
            }
        }
    }
    // A search typed in Telugu shows the Telugu it matched, in the system's Telugu font (see
    // VerseComponents) rather than the English serif.
    val inTelugu = hit.text != verse.text
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Same left-edge accent bar as Reader's highlight style (idea #1)
        // — height(Min) is required for the bar's fillMaxHeight to have
        // anything bounded to fill, since this Row sits in a wrap-content
        // Card. Unlike the verse-reader card, this row's content (a
        // reference line + a single Text of verse text) doesn't have
        // dynamically-wrapping interlinear chips, so it isn't at risk of
        // the same intrinsic-height clipping bug fixed there.
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Fades out slowly rather than an instant on/off — appears
            // immediately on landing (enter = None) but eases out over a
            // full second once cleared (see clearSearchLastTapped's
            // caller), giving the eye time to register which card it was
            // before it's gone.
            AnimatedVisibility(
                visible = isLastTapped,
                enter = EnterTransition.None,
                exit = fadeOut(animationSpec = tween(durationMillis = 1000))
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${verse.book} ${verse.chapter}:${verse.number}".uppercase(),
                        fontSize = 13.sp,
                        fontFamily = com.example.mybible.ui.theme.WorkSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Navigate",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = text,
                    fontSize = if (inTelugu) 16.sp else 17.sp,
                    fontFamily = if (inTelugu) null else com.example.mybible.ui.theme.SourceSerif4FontFamily,
                    lineHeight = if (inTelugu) 27.sp else 29.07.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Why a verse without the words themselves is here: "KJV wording: “careful”",
                // "Greek merimnaō, meaning “to worry”".
                if (hit.reasons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        // Other wording gets a "translation" mark; a close match's "Without “set”" doesn't.
                        if (hit.related) {
                            Icon(
                                imageVector = Icons.Outlined.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp).size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = hit.reasons.joinToString(" · "),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            fontFamily = WorkSansFontFamily,
                            letterSpacing = 0.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
