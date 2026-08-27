package com.example.mybible.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.DsSwitch
import com.example.mybible.ui.theme.WorkSansFontFamily

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchVariantSuggestions by viewModel.searchVariantSuggestions.collectAsState()
    val searchCorrectedQuery by viewModel.searchCorrectedQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val caseSensitive by viewModel.searchCaseSensitive.collectAsState()
    val extensiveSearch by viewModel.searchExtensiveSearch.collectAsState()
    val savedScrollIndex by viewModel.searchScrollIndex.collectAsState()
    val savedScrollOffset by viewModel.searchScrollOffset.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val lastTappedKey by viewModel.searchLastTappedKey.collectAsState()

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScrollIndex,
        initialFirstVisibleItemScrollOffset = savedScrollOffset
    )

    // The "also try" suggestion chips live above the results list, not as
    // a sticky header inside it — scrolling the results doesn't move them
    // out of the way on its own. Tying their visibility to "are we back at
    // the very top of the list" gives the list the full screen once the
    // user scrolls into results, without needing a second scroll container.
    val isScrolledToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }

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

    LaunchedEffect(Unit) {
        if (suppressAutofocus) {
            viewModel.consumeSuppressSearchAutofocus()
        } else {
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
        if (searchHistory.isNotEmpty() && searchQuery.isBlank() && searchResults.isEmpty()) {
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
                searchResults.size == 1 -> "1 result"
                else -> "${searchResults.size} results"
            },
            fontSize = 13.sp,
            fontFamily = WorkSansFontFamily,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Case-sensitive and Extensive search are mutually exclusive (see
        // MainViewModel.setSearchCaseSensitive/setSearchExtensiveSearch) —
        // each one turning on disables and greys out the other, since a
        // case-sensitive search can't also run the typo-correction/
        // suggestion pipeline (see BibleRepository.searchBible's doc).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    enabled = !extensiveSearch,
                    modifier = Modifier.testTag("search_case_sensitive_toggle")
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Extensive search",
                    fontSize = 13.sp,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                DsSwitch(
                    checked = extensiveSearch,
                    onCheckedChange = { viewModel.setSearchExtensiveSearch(it) },
                    enabled = !caseSensitive,
                    modifier = Modifier.testTag("search_extensive_toggle")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Typo-correction note plus tappable "also try" word-form chips —
        // tapping one runs a brand new search for that exact word (same as
        // tapping a recent-search chip) rather than this screen eagerly
        // searching and displaying results for every variant up front,
        // which is what made a single search balloon into an unreadably
        // long page. Only shown while scrolled to the very top of the
        // results — once the user scrolls down to read, the chips step
        // aside instead of eating space above every screenful of results.
        AnimatedVisibility(
            visible = isScrolledToTop && (searchCorrectedQuery != null || searchVariantSuggestions.isNotEmpty())
        ) {
            Column {
                SearchSuggestions(
                    correctedQuery = searchCorrectedQuery,
                    variantSuggestions = searchVariantSuggestions,
                    onSuggestionClick = { viewModel.searchFromHistory(it) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        if (isSearching) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                CircularProgressIndicator()
            }
        } else if (searchResults.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "Type a keyword (e.g., 'love', 'faith') or a reference (e.g., 'John 3', 'John 3:16') to search" else "No matching verses found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 0.sp,
                    fontSize = 14.sp
                )
            }
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
                items(searchResults) { verse ->
                    val itemKey = "${verse.book}:${verse.chapter}:${verse.number}"
                    SearchResultCard(
                        verse = verse,
                        isLastTapped = itemKey == lastTappedKey,
                        onClick = { viewModel.openSearchResult(verse) }
                    )
                }
            }
        }
    }
    }
}

// Typo-correction note plus a row of tappable "also try" word-form chips —
// same AssistChip look as the "Recent searches" row above the field, for
// visual consistency. The chip row only shows up when there's something to
// offer (e.g. a word with no root-stripping candidates shows no row at all).
@Composable
private fun SearchSuggestions(
    correctedQuery: String?,
    variantSuggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    Column {
        if (correctedQuery != null) {
            Text(
                text = "Showing results for “$correctedQuery”",
                fontSize = 13.sp,
                fontFamily = WorkSansFontFamily,
                letterSpacing = 0.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        if (variantSuggestions.isNotEmpty()) {
            SuggestionChipRow(label = "Also try", words = variantSuggestions, onClick = onSuggestionClick)
        }
    }
}

@Composable
private fun SuggestionChipRow(label: String, words: List<String>, onClick: (String) -> Unit) {
    Text(
        text = label,
        fontSize = 12.5.sp,
        fontFamily = WorkSansFontFamily,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(words) { word ->
            AssistChip(
                onClick = { onClick(word) },
                label = { Text(word, fontSize = 13.sp, fontFamily = WorkSansFontFamily) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}

// Extracted from the results list into its own composable for readability.
@Composable
private fun SearchResultCard(
    verse: com.example.mybible.model.Verse,
    isLastTapped: Boolean,
    onClick: () -> Unit
) {
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
                        fontSize = 12.5.sp,
                        fontFamily = com.example.mybible.ui.theme.WorkSansFontFamily,
                        letterSpacing = 1.5.sp,
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
                    text = verse.text,
                    fontSize = com.example.mybible.ui.theme.VerseCardFontLab.fontSizeSp.sp,
                    fontFamily = com.example.mybible.ui.theme.verseCardFontFamily,
                    lineHeight = (com.example.mybible.ui.theme.VerseCardFontLab.fontSizeSp * com.example.mybible.ui.theme.VerseCardFontLab.lineHeightMultiplier).sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
