package com.example.mybible.ui.screens

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.BackTopBar

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val caseSensitive by viewModel.searchCaseSensitive.collectAsState()
    val savedScrollIndex by viewModel.searchScrollIndex.collectAsState()
    val savedScrollOffset by viewModel.searchScrollOffset.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScrollIndex,
        initialFirstVisibleItemScrollOffset = savedScrollOffset
    )

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
                    viewModel.endSearchSession()
                    viewModel.selectTab(NavTab.READER)
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
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    viewModel.commitSearchToHistory()
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
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
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Clear",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { viewModel.clearSearchHistory() }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchHistory) { term ->
                    AssistChip(
                        onClick = { viewModel.searchFromHistory(term) },
                        label = { Text(term, fontSize = 13.sp) },
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
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    isSearching -> " "
                    searchQuery.trim().length < 2 -> " "
                    searchResults.size == 1 -> "1 result"
                    else -> "${searchResults.size} results"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Case-sensitive",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = caseSensitive,
                    onCheckedChange = { viewModel.setSearchCaseSensitive(it) },
                    modifier = Modifier.testTag("search_case_sensitive_toggle")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(searchResults) { verse ->
                    Card(
                        onClick = { viewModel.openSearchResult(verse) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${verse.book} ${verse.chapter}:${verse.number}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = verse.text,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
    }
}
