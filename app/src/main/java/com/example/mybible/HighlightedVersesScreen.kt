@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.mybible

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.HIGHLIGHT_COLOR_DEFS
import com.example.mybible.model.ThemeMode
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.components.BIBLE_BOOKS
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.NeSectionLabel
import com.example.mybible.ui.components.parseHexColorOrNull
import com.example.mybible.ui.theme.SourceSerif4FontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily
import kotlinx.coroutines.launch

data class HighlightedVerseItem(
    val key: String,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val colorName: String = "Default",
    // The color itself, lowercase — what the color filter goes by, since a
    // label can be renamed.
    val colorHex: String = "",
    // When it was highlighted (or last recolored), for Newest first.
    val updatedAt: Long = 0L,
    // Text of the linked quick-note (see HighlightItem.noteId), when one
    // exists — shown as a truncated italic preview under the verse.
    val noteText: String? = null
)

// A color in use, as the filter sheet lists it: palette order, current label.
private data class ColorOption(val hex: String, val label: String)

/**
 * "Highlighted Verses" page: every highlight, newest first by default (or
 * in Bible order — the choice is saved), under date or book headings.
 *  - Filters sit behind the top bar's filter button, in a sheet shaped like
 *    Notes' "Filter Notes", so the list starts right under the title. While
 *    a place or color is picked the button gets a dot, and one line under
 *    the title says what's on, with Clear.
 *  - Filters, search and scroll position live in MainViewModel, so opening
 *    a verse and tapping Return comes back to the same list. Leaving the
 *    page with back starts it over.
 *  - rows are filled, rounded cards (surfaceContainerHigh) — matches the
 *    elevated-card look used by Search results and the Notes list.
 */
@Composable
fun HighlightedVersesScreen(
    viewModel: MainViewModel,
    themeMode: ThemeMode,
    onOpenVerse: (HighlightedVerseItem) -> Unit,
    onClose: () -> Unit
) {
    val highlights by viewModel.highlightedVerseItems.collectAsState()
    // Live, not frozen: "This chapter" and "This book" follow wherever the
    // Reader is now, not where it was when this page opened.
    val currentBook by viewModel.currentBook.collectAsState()
    val currentChapter by viewModel.currentChapter.collectAsState()
    val filter by viewModel.highlightsFilter.collectAsState()
    val newestFirst by viewModel.highlightsNewestFirst.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    val shown = remember(highlights, filter, currentBook, currentChapter) {
        highlights.filtered(filter.place, filter.colorHexes, filter.query, currentBook, currentChapter)
    }
    val groups = remember(shown, newestFirst) { groupHighlights(shown, newestFirst) }
    val colorOptions = remember(highlights, filter.colorHexes) { colorOptionsOf(highlights, filter.colorHexes) }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.highlightsScrollIndex.value,
        initialFirstVisibleItemScrollOffset = viewModel.highlightsScrollOffset.value
    )
    // The page is torn down with its tab — note where the list was, for a Return.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveHighlightsScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }
    // A new filter, search or order starts the list from the top — but not
    // on the way back from a verse, which restores the position above.
    val listKey = filter.copy(searchOpen = false) to newestFirst
    var listedKey by remember { mutableStateOf(listKey) }
    LaunchedEffect(listKey) {
        if (listKey != listedKey) {
            listedKey = listKey
            listState.scrollToItem(0)
        }
    }

    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focusSearch by remember { mutableStateOf(false) }
    // Only when the search button opens it — not when it's back open after a Return.
    LaunchedEffect(focusSearch, filter.searchOpen) {
        if (focusSearch && filter.searchOpen) {
            focusSearch = false
            // The field is laid out a frame after it's composed (Scaffold content).
            withFrameNanos { }
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }
    fun closeSearch() = viewModel.updateHighlightsFilter { it.copy(searchOpen = false, query = "") }
    // Back closes the search before it leaves the page.
    BackHandler(enabled = filter.searchOpen) { closeSearch() }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Highlighted Verses",
                onBack = onClose,
                backContentDescription = "Back to Reader",
                actions = {
                    IconButton(onClick = {
                        if (filter.searchOpen) {
                            closeSearch()
                        } else {
                            viewModel.updateHighlightsFilter { it.copy(searchOpen = true) }
                            focusSearch = true
                        }
                    }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = if (filter.searchOpen) "Close search" else "Search highlights",
                            tint = if (filter.searchOpen) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    // Like Notes' filter button: coral, with a dot, while a place or color is picked.
                    IconButton(onClick = { showFilterSheet = true }) {
                        Box {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = if (filter.narrowed) "Filter highlights, filter on" else "Filter highlights",
                                tint = if (filter.narrowed) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                            if (filter.narrowed) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp, y = (-3).dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (filter.searchOpen) {
                HighlightSearchField(
                    query = filter.query,
                    onQueryChange = { query -> viewModel.updateHighlightsFilter { it.copy(query = query) } },
                    onClose = { closeSearch() },
                    focusRequester = searchFocus
                )
            }
            if (filter.narrowed) {
                FilterSummary(
                    text = summaryOf(filter, colorOptions, currentBook, currentChapter),
                    onOpen = { showFilterSheet = true },
                    onClear = {
                        viewModel.updateHighlightsFilter { it.copy(place = HighlightPlace.WholeBible, colorHexes = emptySet()) }
                    }
                )
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                            highlights.isEmpty() -> "No highlighted verses"
                            filter.query.isNotBlank() -> "No highlighted verses match your search"
                            else -> "No highlighted verses match these filters"
                        },
                        fontFamily = WorkSansFontFamily,
                        letterSpacing = 0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groups.forEach { group ->
                        stickyHeader(key = "group:${group.title}") { GroupHeading(group.title, group.items.size) }
                        items(group.items, key = { it.key }) { item ->
                            HighlightedVerseRow(item = item, onClick = { onOpenVerse(item) })
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        HighlightsFilterSheet(
            highlights = highlights,
            filter = filter,
            newestFirst = newestFirst,
            colorOptions = colorOptions,
            shownCount = shown.size,
            currentBook = currentBook,
            currentChapter = currentChapter,
            themeMode = themeMode,
            onFilterChange = viewModel::updateHighlightsFilter,
            onNewestFirstChange = viewModel::setHighlightsNewestFirst,
            onDismiss = { showFilterSheet = false }
        )
    }
}

private fun HighlightPlace.label(currentBook: String, currentChapter: Int): String = when (this) {
    HighlightPlace.WholeBible -> "Whole Bible"
    HighlightPlace.ThisChapter -> "$currentBook $currentChapter"
    HighlightPlace.ThisBook -> currentBook
    HighlightPlace.OldTestament -> "Old Testament"
    HighlightPlace.NewTestament -> "New Testament"
    is HighlightPlace.Book -> name
}

// Colors in use, plus any picked ones no longer in use (so they can still be
// seen and unpicked), in the palette's order.
private fun colorOptionsOf(highlights: List<HighlightedVerseItem>, picked: Set<String>): List<ColorOption> {
    val palette = HIGHLIGHT_COLOR_DEFS.map { it.colorHex.lowercase() }
    val labels = HIGHLIGHT_COLOR_DEFS.associate { it.colorHex.lowercase() to it.label } +
        highlights.associate { it.colorHex to it.colorName }
    return (highlights.map { it.colorHex } + picked).distinct()
        .sortedBy { hex -> palette.indexOf(hex).let { if (it < 0) palette.size else it } }
        .map { hex -> ColorOption(hex, labels[hex] ?: "Uncategorized") }
}

private fun summaryOf(filter: HighlightsFilter, colors: List<ColorOption>, currentBook: String, currentChapter: Int): String {
    val parts = mutableListOf<String>()
    if (filter.place != HighlightPlace.WholeBible) parts += filter.place.label(currentBook, currentChapter)
    val picked = colors.filter { it.hex in filter.colorHexes }.map { it.label }
    if (picked.isNotEmpty()) parts += picked.joinToString(", ")
    return parts.joinToString(" · ")
}

@Composable
private fun ColorDot(hex: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(parseHexColorOrNull(hex) ?: Color.Gray)
            // Keeps the pale colors visible on the light themes.
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), CircleShape)
    )
}

@Composable
private fun GroupHeading(title: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Opaque: it stays pinned at the top while its cards scroll under it.
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp, bottom = 6.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 12.5.sp,
            letterSpacing = 1.5.sp,
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "  $count",
            fontSize = 12.5.sp,
            letterSpacing = 0.sp,
            fontFamily = WorkSansFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Same look as Notes' search bar, a little shorter, shown only while searching.
@Composable
private fun HighlightSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester
) {
    val focusManager = LocalFocusManager.current
    // A local TextFieldValue, as SearchScreen does, so a query kept from
    // before a Return comes back with the cursor at its end.
    var value by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(query) {
        if (value.text != query) value = TextFieldValue(query, TextRange(query.length))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(start = 12.dp)
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    onQueryChange(it.text)
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = WorkSansFontFamily,
                    fontSize = 16.sp,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
            if (value.text.isEmpty()) {
                Text(
                    text = "Search highlights…",
                    fontSize = 16.sp,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// Notes' active-filter line: what's on (tap to change it), and Clear.
@Composable
private fun FilterSummary(text: String, onOpen: () -> Unit, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp)
    ) {
        Icon(
            Icons.Default.FilterList,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontFamily = WorkSansFontFamily,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = "Clear",
            fontSize = 12.5.sp,
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onClear)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

// Shaped like Notes' "Filter Notes" sheet: title and Clear all, a section
// per kind of filter, one button. Changes apply as they're tapped; the
// button counts what they leave and closes the sheet.
@Composable
private fun HighlightsFilterSheet(
    highlights: List<HighlightedVerseItem>,
    filter: HighlightsFilter,
    newestFirst: Boolean,
    colorOptions: List<ColorOption>,
    shownCount: Int,
    currentBook: String,
    currentChapter: Int,
    themeMode: ThemeMode,
    onFilterChange: ((HighlightsFilter) -> HighlightsFilter) -> Unit,
    onNewestFirstChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // What a choice would show, with the other filters as they are.
    fun count(place: HighlightPlace = filter.place, colors: Set<String> = filter.colorHexes) =
        highlights.filtered(place, colors, filter.query, currentBook, currentChapter).size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Filter Highlights", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (filter.narrowed) {
                    Text(
                        text = "Clear all",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onFilterChange { it.copy(place = HighlightPlace.WholeBible, colorHexes = emptySet()) } }
                            .padding(vertical = 6.dp)
                    )
                }
            }

            NeSectionLabel("Show")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    HighlightPlace.WholeBible,
                    HighlightPlace.ThisChapter,
                    HighlightPlace.ThisBook,
                    HighlightPlace.OldTestament,
                    HighlightPlace.NewTestament
                ).forEach { place ->
                    SheetChip(
                        text = place.label(currentBook, currentChapter),
                        count = count(place = place),
                        selected = filter.place == place,
                        themeMode = themeMode,
                        onClick = { onFilterChange { it.copy(place = place) } }
                    )
                }
                // Any other book with highlights, from a menu.
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    val chosen = filter.place as? HighlightPlace.Book
                    SheetChip(
                        text = chosen?.name ?: "Other book",
                        count = chosen?.let { count(place = it) },
                        selected = chosen != null,
                        themeMode = themeMode,
                        trailing = Icons.Default.ArrowDropDown,
                        onClick = { menuOpen = true }
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        highlights.map { it.book }.distinct().sortedBy { BIBLE_BOOKS.indexOf(it) }.forEach { book ->
                            DropdownMenuItem(
                                text = { Text(book, fontFamily = WorkSansFontFamily, letterSpacing = 0.sp) },
                                trailingIcon = {
                                    Text(
                                        text = "${count(place = HighlightPlace.Book(book))}",
                                        fontFamily = WorkSansFontFamily,
                                        letterSpacing = 0.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onFilterChange { it.copy(place = HighlightPlace.Book(book)) }
                                }
                            )
                        }
                    }
                }
            }

            NeSectionLabel("Colors")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colorOptions.forEach { option ->
                    val picked = option.hex in filter.colorHexes
                    val n = count(colors = setOf(option.hex))
                    // Colors with nothing in this place are left out, unless picked.
                    if (n > 0 || picked) {
                        SheetChip(
                            text = option.label,
                            count = n,
                            selected = picked,
                            themeMode = themeMode,
                            leading = { ColorDot(option.hex, 12.dp) },
                            onClick = {
                                onFilterChange {
                                    it.copy(colorHexes = if (picked) it.colorHexes - option.hex else it.colorHexes + option.hex)
                                }
                            }
                        )
                    }
                }
            }

            NeSectionLabel("Order")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetChip(
                    text = "Newest first",
                    selected = newestFirst,
                    themeMode = themeMode,
                    onClick = { onNewestFirstChange(true) }
                )
                SheetChip(
                    text = "Bible order",
                    selected = !newestFirst,
                    themeMode = themeMode,
                    onClick = { onNewestFirstChange(false) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (shownCount) {
                        0 -> "No matching verses"
                        1 -> "Show 1 verse"
                        else -> "Show $shownCount verses"
                    }
                )
            }
        }
    }
}

// Also Search's chips under the search box (see SearchScreen's SourceChips).
@Composable
internal fun SheetChip(
    text: String,
    selected: Boolean,
    themeMode: ThemeMode,
    onClick: () -> Unit,
    count: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: ImageVector? = null
) {
    // Classic Dark's primaryContainer (a rust/coral) and primary (a lighter
    // coral) sit too close in hue/lightness for the usual primary-on-
    // primaryContainer label to read clearly, so selected chips get black
    // text there instead — as the old color chips did.
    val selectedContent = if (themeMode == ThemeMode.CLASSIC_DARK) Color.Black else MaterialTheme.colorScheme.primary
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row {
                Text(text, fontFamily = WorkSansFontFamily, letterSpacing = 0.sp)
                if (count != null) {
                    Text(
                        text = "  $count",
                        fontFamily = WorkSansFontFamily,
                        letterSpacing = 0.sp,
                        color = if (selected) selectedContent.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        // A color chip keeps its dot when picked; the others get a check.
        leadingIcon = leading ?: if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null,
        trailingIcon = trailing?.let { icon ->
            { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = selectedContent,
            selectedLeadingIconColor = selectedContent,
            selectedTrailingIconColor = selectedContent
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            selectedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun HighlightedVerseRow(
    item: HighlightedVerseItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            // One line: the highlight's own color, its label in small-caps
            // terracotta (primary), and the reference in small-caps gold
            // (tertiary) — the reference treatment shared with Search and
            // Cross References.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                ColorDot(item.colorHex, 10.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.colorName.uppercase(),
                    fontSize = 12.5.sp,
                    letterSpacing = 1.5.sp,
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${item.book} ${item.chapter}:${item.verse}".uppercase(),
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            // Verse text — onSurface (not a literal white) so it stays
            // readable against the light-toned themes too; in Classic Dark
            // this already resolves to a near-white cream.
            Text(
                text = item.text,
                fontSize = 17.sp,
                fontFamily = SourceSerif4FontFamily,
                lineHeight = 29.07.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!item.noteText.isNullOrBlank()) {
                Text(
                    text = item.noteText.let { if (it.length > 90) it.take(89) + "…" else it },
                    fontSize = 13.sp,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 0.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
