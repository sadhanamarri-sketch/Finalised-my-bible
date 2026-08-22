package com.example.mybible.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import kotlinx.coroutines.delay
import com.example.mybible.model.CompletedVerseItem
import com.example.mybible.model.HighlightColorDef
import com.example.mybible.model.HighlightItem
import com.example.mybible.model.NoteItem
import com.example.mybible.ui.components.RenameHighlightColorDialog
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.ReaderPickMode
import com.example.mybible.ui.components.VerseActionToolbar
import com.example.mybible.ui.components.VerseCard
import com.example.mybible.ui.components.PickModeBanner
import com.example.mybible.ui.components.BIBLE_BOOKS

// Notes attached to an exact verse — checks every entry in `refs`, not just
// the legacy single book/chapter/verse fields. A note picked with multiple
// references (see MainViewModel's picking mode) attaches to *all* of them,
// so the old refs-blind check was missing markers/previews for every verse
// past the first one on a multi-ref note.
private fun com.example.mybible.model.NoteItem.matchesVerse(book: String, chapter: Int, verse: Int): Boolean {
    if (refs.isNotEmpty()) {
        return refs.any { it.book == book && it.chapter == chapter && it.verse == verse }
    }
    return this.book == book && this.chapter == chapter && this.verse == verse
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val verses by viewModel.verses.collectAsState()
    val verseNumbersWithXrefs by viewModel.verseNumbersWithXrefs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentBook by viewModel.currentBook.collectAsState()
    val currentChapter by viewModel.currentChapter.collectAsState()

    val redLetterEnabled by viewModel.redLetterEnabled.collectAsState()
    val showTeluguInline by viewModel.showTeluguInline.collectAsState()
    val showInterlinear by viewModel.showInterlinear.collectAsState()
    val fontSizeSp by viewModel.fontSizeSp.collectAsState()
    val teluguFontSizeSp by viewModel.teluguFontSizeSp.collectAsState()
    val greekFontSizeSp by viewModel.greekFontSizeSp.collectAsState()
    val hebrewFontSizeSp by viewModel.hebrewFontSizeSp.collectAsState()
    val verseSpacingDp by viewModel.verseSpacingDp.collectAsState()
    val englishLineHeightMultiplier by viewModel.englishLineHeightMultiplier.collectAsState()
    val teluguLineHeightMultiplier by viewModel.teluguLineHeightMultiplier.collectAsState()
    val englishFontFamilyName by viewModel.englishFontFamilyName.collectAsState()

    val isBlurModeEnabled by viewModel.isBlurModeEnabled.collectAsState()
    val crossReferenceReturnAvailable by viewModel.crossReferenceReturnAvailable.collectAsState()
    val focusedVerseNumber by viewModel.focusedVerseNumber.collectAsState()
    val focusedVerseBlurEnabled by viewModel.focusedVerseBlurEnabled.collectAsState()
    val focusedVersePinToTop by viewModel.focusedVersePinToTop.collectAsState()
    val searchReturnAvailable by viewModel.searchReturnAvailable.collectAsState()
    val lexiconReturnTab by viewModel.lexiconReturnTab.collectAsState()
    val noteReturnItem by viewModel.noteReturnItem.collectAsState()
    val highlightsReturnAvailable by viewModel.highlightsReturnAvailable.collectAsState()
    val studiedReturnAvailable by viewModel.studiedReturnAvailable.collectAsState()
    val readerAnchor by viewModel.readerAnchor.collectAsState()

    val completedVerses by viewModel.completedVerses.collectAsState(initial = emptyList())
    val highlights by viewModel.highlights.collectAsState(initial = emptyList())
    val highlightColorDefs by viewModel.highlightColorDefs.collectAsState(initial = emptyList())
    val notes by viewModel.notes.collectAsState(initial = emptyList())

    val selectedVerse by viewModel.selectedVerse.collectAsState()
    val readerPickMode by viewModel.readerPickMode.collectAsState()
    val pickBannerMessage by viewModel.pickBannerMessage.collectAsState()
    val pickedNoteRefs by viewModel.pickedNoteRefs.collectAsState()
    val noteToEdit by viewModel.noteToEdit.collectAsState()

    // Best-effort initial position to avoid a visible top-of-chapter flash
    // on a cold start (e.g. via the widget), before the real scroll-to-
    // focus effect below corrects it once verses/focusedVerseNumber are
    // actually ready — see MainViewModel.consumeInitialScrollHint's own
    // doc for why this is a separate, one-shot, plain value rather than
    // reading focusedVerseNumber directly here (that ordering is load-
    // bearing for the scroll-away watcher elsewhere in this file).
    // Captured into `remember` (not passed inline) so the one-shot
    // consumption happens exactly once, on this composable's first
    // composition, rather than once per recomposition. Approximates
    // "verse N sits at LazyColumn index N" (index 0 is the chapter
    // header) since chapters are verse-numbered 1..count without gaps in
    // the vast majority of cases; any off-by-a-little from a rare gap is
    // just a small correction once the real verse list loads, not a full
    // top-of-chapter jump.
    val initialScrollHint = remember { viewModel.consumeInitialScrollHint() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollHint ?: 0)

    // Snapshots the top-visible verse into MainViewModel right before
    // ReaderScreen is torn down (visiting another tab) — see
    // MainViewModel.saveReaderAnchor's doc for why this exists and how
    // it's consumed on the next mount. rememberUpdatedState is required
    // here, not a plain closure over `verses`/`currentBook`/`currentChapter`:
    // DisposableEffect(Unit)'s block (and everything it captures) only
    // actually runs once, at first composition, but onDispose needs
    // whatever the *latest* values were at the moment of teardown, which
    // can be many recompositions later.
    val latestVerses = rememberUpdatedState(verses)
    val latestBook = rememberUpdatedState(currentBook)
    val latestChapter = rememberUpdatedState(currentChapter)
    DisposableEffect(Unit) {
        onDispose {
            val topVerseNumber = latestVerses.value
                .getOrNull(listState.firstVisibleItemIndex - 1)
                ?.number
            viewModel.saveReaderAnchor(latestBook.value, latestChapter.value, topVerseNumber)
        }
    }

    // Keeps MainViewModel's liveTopVerse current for as long as Reader
    // stays on screen — unlike the anchor above (only snapshotted at
    // tab-switch-away), this needs to reflect the actual top-visible verse
    // at any moment, since backgrounding the app with the home button
    // while still on the Reader tab never disposes this composable at all.
    // See MainViewModel.reportLiveTopVerse's doc.
    LaunchedEffect(listState, currentBook, currentChapter, verses) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx ->
                val topVerseNumber = verses.getOrNull(idx - 1)?.number
                viewModel.reportLiveTopVerse(currentBook, currentChapter, topVerseNumber)
            }
    }

    // Per-item measured heights (px), keyed by LazyColumn item index —
    // needed to reconstruct an absolute "scrollTop"/"scrollHeight" the way
    // Capacitor's DOM scrollEl.scrollTop/scrollHeight naturally have them,
    // since LazyListLayoutInfo only exposes offsets relative to the
    // viewport for whatever's currently visible. Fed continuously as items
    // are measured; unmeasured items (below/above the current viewport)
    // fall back to the running average in centerItemIndex below.
    val itemHeights = remember { mutableStateMapOf<Int, Int>() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { infos ->
                infos.forEach { info -> itemHeights[info.index] = info.size }
            }
    }

    // Which way the chapter changed most recently — drives the slide
    // direction of the AnimatedContent around the verse list below.
    // true = moved forward (next chapter), false = moved backward (prev).
    var chapterSwipeForward by remember { mutableStateOf(true) }

    // Temporary "cross-reference focus blur": when a jump lands on a
    // specific verse (cross-reference tap, or the return-to-source jump),
    // the target verse should sit centered in the viewport with the rest
    // of the chapter blurred — same visual as manual Blur Mode — until the
    // user actually scrolls away from it, at which point it behaves like
    // normal reading again. `xrefFocusLandedIndex`/`xrefFocusLandedOffset`
    // record exactly where we scrolled to (item index *and* pixel offset,
    // since the centering below rarely lands at offset 0); comparing
    // against that captured position — rather than watching for any scroll
    // event, or assuming offset 0 — avoids false-clearing from the
    // scrollToItem calls themselves, which also flow through the same
    // nested-scroll machinery a real drag would.
    var xrefFocusActive by remember { mutableStateOf(false) }
    var xrefFocusLandedIndex by remember { mutableStateOf(-1) }
    var xrefFocusLandedOffset by remember { mutableStateOf(0) }
    // Tracks which chapter the effect below last ran for, so it can tell a
    // genuine chapter change (which should reset scroll to the top when
    // there's no focus target) apart from focusedVerseNumber simply being
    // cleared within the *same* chapter — e.g. tapping a blurred verse to
    // dismiss focus, or scrolling away from the landed verse — neither of
    // which should snap the reader back to the top of the chapter.
    var lastFocusEffectChapterKey by remember { mutableStateOf<Pair<String, Int>?>(null) }

    // Scroll to top (or the focused cross-reference/search verse) when the
    // chapter changes. Keyed on `verses` too, not just currentBook/
    // currentChapter: those two update synchronously the moment navigation
    // starts, but the new chapter's verse list arrives later from a
    // suspend/Room query. Without `verses` as a key this effect ran
    // immediately against the *previous* chapter's still-loaded list, so
    // indexOfFirst either matched the wrong verse or (usually for higher
    // verse numbers, which are less likely to coincidentally exist at the
    // same index in the old chapter) found nothing and fell back to
    // scrolling to item 0 — i.e. dropping at the top of the chapter instead
    // of the target verse. Also keyed on focusedVerseNumber: a
    // cross-reference jump within the *same* chapter never changes
    // currentBook/currentChapter (loadChapter is called with the same
    // values, which a MutableStateFlow drops as a no-op), so without this
    // key a same-chapter xref tap wouldn't re-scroll or re-focus at all.
    LaunchedEffect(currentBook, currentChapter, verses, focusedVerseNumber, focusedVerseBlurEnabled, focusedVersePinToTop, readerAnchor) {
        if (verses.isEmpty()) return@LaunchedEffect
        val chapterKey = currentBook to currentChapter
        val isNewChapter = chapterKey != lastFocusEffectChapterKey
        lastFocusEffectChapterKey = chapterKey
        if (isNewChapter) {
            // A different chapter means a different set of row heights at
            // the same indices (e.g. leaving Psalm 119 for a 3-verse
            // chapter) — old cached heights would skew centerItemIndex's
            // ramp math until re-measured, so start clean each time the
            // chapter actually changes.
            itemHeights.clear()
        }
        val targetVerseNumber = focusedVerseNumber
        if (targetVerseNumber != null) {
            val idx = verses.indexOfFirst { it.number == targetVerseNumber }
            if (idx >= 0) {
                val targetIndex = idx + 1
                // First bring the target item into the laid-out set so its
                // actual measured height is available, then (unless
                // pinToTop) re-scroll with a computed offset so the verse
                // lands centered in the viewport instead of flush with the
                // top — pinToTop skips that: it means this is a scroll-
                // position *restore* (Telugu/interlinear toggle), where the
                // point is to leave the anchor verse exactly where it
                // already was on screen, not spotlight a new one.
                listState.scrollToItem(targetIndex)
                if (!focusedVersePinToTop) {
                    val layoutInfo = listState.layoutInfo
                    val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
                    if (itemInfo != null) {
                        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                        val extraSpace = (viewportHeight - itemInfo.size).coerceAtLeast(0)
                        if (extraSpace > 0) {
                            // Negative scrollOffset pushes the item down from
                            // the top of the viewport by half the leftover
                            // space, centering it.
                            listState.scrollToItem(targetIndex, -(extraSpace / 2))
                        }
                    }
                }
                xrefFocusLandedIndex = listState.firstVisibleItemIndex
                xrefFocusLandedOffset = listState.firstVisibleItemScrollOffset
                xrefFocusActive = focusedVerseBlurEnabled
            } else {
                listState.scrollToItem(0)
                xrefFocusActive = false
            }
        } else if (isNewChapter) {
            // No explicit jump target — this is either the very first
            // load, or ReaderScreen was just remounted after a visit to
            // another tab. In the remount case, restore to wherever the
            // reader last was in *this exact* book/chapter (see
            // MainViewModel.saveReaderAnchor's doc) rather than always
            // dropping to the top — a genuine chapter change (swiping
            // forward/back) naturally won't match the anchor's stale
            // book/chapter, so it still falls through to scrolling to 0.
            val anchor = readerAnchor
            val anchorIdx = if (anchor != null && anchor.book == currentBook && anchor.chapter == currentChapter) {
                verses.indexOfFirst { it.number == anchor.verse }
            } else {
                -1
            }
            if (anchorIdx >= 0) {
                listState.scrollToItem(anchorIdx + 1)
                viewModel.consumeReaderAnchor()
            } else {
                listState.scrollToItem(0)
            }
            xrefFocusActive = false
        } else {
            // Same chapter, no focus target — this is focus being cleared
            // (blur dismissed by tap, or the user scrolled away from the
            // landed verse), not a fresh chapter open, so leave the scroll
            // position exactly where the reader already is.
            xrefFocusActive = false
        }
    }

    // Watches for the user actually moving away from the landed verse and
    // clears the temporary jump-target state — both the local blur flag
    // and the ViewModel's focusedVerseNumber itself. The first emission
    // from snapshotFlow is the position we just landed on (matches the
    // target, so the condition below is false and nothing clears); any
    // further emission means the list position changed after that — which
    // can only happen from here on by the user scrolling, since nothing
    // else programmatically scrolls after the initial jump. A few pixels
    // of slack on the offset absorbs float/measurement rounding rather
    // than requiring an exact match.
    //
    // Keyed on focusedVerseNumber (not just xrefFocusActive): a plain
    // non-blurred jump (focusVerse = false — e.g. the Studied tab's browse
    // entry point) lands with xrefFocusActive already false, so gating on
    // that alone meant this effect never ran for that case and
    // focusedVerseNumber stayed set forever. See clearVerseFocus()'s doc
    // for what that broke.
    //
    // Deliberately clearVerseFocus() only, NOT clearStaleReaderDetours() —
    // a version of this once called clearStaleReaderDetours() so scrolling
    // away also silently dismissed the "return to X" banner (reasoning:
    // an untouched banner was blocking widget position-saves indefinitely
    // via isDetourActive()). That broke normal reading: scrolling on to
    // keep reading after a cross-reference/search/etc. jump killed the
    // Return banner and its back-navigation immediately, while tapping the
    // blurred verse first (clearVerseFocus only) left it working — the
    // banner must only go away via its own explicit dismiss/Return, or a
    // real tab switch/backgrounding, never from the reader just scrolling.
    LaunchedEffect(focusedVerseNumber, xrefFocusLandedIndex, xrefFocusLandedOffset) {
        if (focusedVerseNumber == null) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (idx, offset) ->
                if (idx != xrefFocusLandedIndex || kotlin.math.abs(offset - xrefFocusLandedOffset) > 4) {
                    xrefFocusActive = false
                    viewModel.clearVerseFocus()
                }
            }
    }


    // Centered verse index calculation for Blur Mode — a direct port of
    // Capacitor's applyBlurFocus (see www/index.html), including its ramp:
    // the reference point isn't a hard switch between "top edge" and "true
    // center", it's a continuous linear ramp from the top edge up to true
    // center as you scroll the first half-screen (and mirrored at the
    // bottom). A discrete on/off switch — which is what this used to be —
    // makes the focused verse jump by several verses in a single frame the
    // instant the switch flips, which is what looked like an abrupt/broken
    // transition. LazyListLayoutInfo only gives offsets relative to the
    // current viewport, not an absolute scrollTop/scrollHeight the way the
    // DOM does, so itemHeights (above) reconstructs that: absolute top for
    // item i = sum of heights of items 0 until i.
    val centerItemIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems <= 1) return@derivedStateOf -1
            // Item index 0 is the "Book Chapter" title row, not a verse —
            // Capacitor's `.verse` query never includes it either. Still
            // counted as part of the scrollable content's height below
            // (matching scrollHeight, which includes it in the DOM too),
            // just excluded from the candidate list.
            val visibleItems = layoutInfo.visibleItemsInfo.filter { it.index >= 1 }
            if (visibleItems.isEmpty()) return@derivedStateOf -1

            val avgHeight = if (itemHeights.isNotEmpty()) {
                itemHeights.values.map { it.toFloat() }.average().toFloat()
            } else 120f

            // Single pass: absolute top of every item (known height where
            // measured, average as a fallback for anything never laid
            // out), which doubles as scrollHeight once the loop finishes.
            val absoluteTop = FloatArray(totalItems)
            var cum = 0f
            for (i in 0 until totalItems) {
                absoluteTop[i] = cum
                cum += itemHeights[i]?.toFloat() ?: avgHeight
            }
            val scrollHeight = cum

            val viewH = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
            val top = absoluteTop[listState.firstVisibleItemIndex] + listState.firstVisibleItemScrollOffset
            val maxScroll = (scrollHeight - viewH).coerceAtLeast(0f)
            val distFromBottom = maxScroll - top
            val half = viewH / 2f
            val offset = when {
                maxScroll <= 0f -> 0f // whole chapter fits on screen — anchor to top (verse 1)
                top < half -> top // ramp: top edge -> center
                distFromBottom < half -> viewH - distFromBottom // ramp: center -> bottom edge
                else -> half // pure center-tracking through the middle of the chapter
            }
            val refPoint = top + offset

            visibleItems.minByOrNull { item ->
                val mid = absoluteTop[item.index] + item.size / 2f
                kotlin.math.abs(mid - refPoint)
            }?.index ?: -1
        }
    }

    var showReaderMenu by remember { mutableStateOf(false) }

    // --- Auto-hide the bottom pill on scroll, matching Capacitor's
    // setupAutoHideBars/hideBars/showBars. Capacitor drives this off
    // scrollTop deltas on the DOM scroller; the closest Compose equivalent
    // is a NestedScrollConnection, which hands us the same kind of raw
    // pixel deltas per scroll event rather than something derived from
    // LazyColumn item indices (which would be unreliable here since verses
    // vary a lot in rendered height).
    var barsHidden by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val hideShowThresholdPx = with(density) { 6.dp.toPx() }

    // Mirrors Capacitor's canHide(): bars are never allowed to hide while
    // a cross-reference back-bar is showing, a sheet/menu is open, or a
    // verse is selected (selection already swaps the pill for the action
    // toolbar, but this also covers the moment the toolbar is dismissing).
    val canHideBars = !crossReferenceReturnAvailable && !searchReturnAvailable && lexiconReturnTab == null && noteReturnItem == null && !highlightsReturnAvailable && !studiedReturnAvailable && !showReaderMenu && selectedVerse == null &&
        readerPickMode == ReaderPickMode.NONE &&
        readerPickMode == ReaderPickMode.NONE

    LaunchedEffect(canHideBars) {
        if (!canHideBars) barsHidden = false
    }

    // Mirrors Capacitor's atBoundary(): index 0 is the title row (see the
    // centerItemIndex comment above), so "top" means the title row is still
    // in view near its natural position.
    val atScrollBoundary by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 12) ||
                !listState.canScrollForward
        }
    }
    // Settle delay before forcing bars back on at a boundary, so a fast
    // fling that briefly overshoots doesn't flicker them in and out —
    // same reasoning as Capacitor's settleTimer.
    LaunchedEffect(atScrollBoundary) {
        if (atScrollBoundary) {
            delay(120)
            if (atScrollBoundary) barsHidden = false
        }
    }

    val nestedScrollConnection = remember(canHideBars) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!canHideBars) return Offset.Zero
                if (available.y < -hideShowThresholdPx) barsHidden = true
                else if (available.y > hideShowThresholdPx) barsHidden = false
                return Offset.Zero
            }
        }
    }

    val barsHiddenProgress by animateFloatAsState(
        targetValue = if (barsHidden) 1f else 0f,
        animationSpec = tween(280),
        label = "barsHiddenProgress"
    )

    // Measured height of whatever's floating over the bottom of the list
    // right now (the chapter-nav pill, or the verse-action toolbar when a
    // verse is selected) — fed into the LazyColumn's bottom padding below
    // instead of a guessed constant, so the last verse never ends up
    // physically hidden underneath it. Mirrors Capacitor's updateBarMetrics,
    // which measures the real header/nav height rather than hardcoding it.
    var bottomOverlayHeightPx by remember { mutableStateOf(0) }
    val bottomOverlayPaddingDp = with(androidx.compose.ui.platform.LocalDensity.current) {
        (bottomOverlayHeightPx.toDp() + 24.dp).coerceAtLeast(24.dp)
    }

    // Reader owns the whole screen edge-to-edge now (no Scaffold bars to
    // reserve space) — statusBarsPadding here replaces what the old
    // Scaffold topBar used to provide, so the chapter title/first verse
    // never sits under the status bar/notch.
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .nestedScroll(nestedScrollConnection)
            // Tap-to-reveal, folded into this same modifier chain instead of
            // a separate full-screen sibling Box drawn over the LazyColumn.
            // A sibling overlay depends on Compose's sibling hit-test order
            // to "win" against LazyColumn's own gesture detectors — that
            // order isn't part of the documented contract, so it can (and
            // did) regress into either swallowing every tap or doing
            // nothing at all. Running in PointerEventPass.Initial here makes
            // it deterministic: this always sees the pointer BEFORE any
            // descendant (VerseCard, LazyColumn drag) does, because it's an
            // ancestor in the same chain, not a competing sibling.
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                while (true) {
                    awaitPointerEventScope {
                        // Never consume the down itself — LazyColumn's own
                        // scrollable needs an untouched down to start a drag
                        // from on the very first touch. If we ate the down
                        // here, any scroll attempt while bars are hidden
                        // would be swallowed whole, forcing a second swipe —
                        // the exact bug this is meant to avoid.
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        if (!barsHidden) return@awaitPointerEventScope

                        val pointerId = down.id
                        var totalMovement = 0f
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                // Genuine release. Only now — and only if the
                                // gesture never moved beyond touch slop (so
                                // it was a tap, not a scroll that LazyColumn
                                // already claimed) — consume it in Initial
                                // pass, pre-empting VerseCard's own Main-pass
                                // click detector so tapping while hidden
                                // reveals the pill instead of also selecting
                                // a verse.
                                if (totalMovement < touchSlop) {
                                    change.consume()
                                    barsHidden = false
                                }
                                break
                            }
                            totalMovement += (change.position - change.previousPosition).getDistance()
                            if (totalMovement >= touchSlop) {
                                // This is becoming a drag/scroll, not a tap —
                                // back off completely and let LazyColumn's
                                // scrollable (Main pass) own it from here.
                                break
                            }
                        }
                        // When bars are already visible, nothing above ever
                        // runs, so the event falls through untouched to the
                        // LazyColumn/VerseCard exactly as if this modifier
                        // didn't exist.
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Picking-mode banner — shown instead of (takes priority over)
            // the cross-reference back bar, matching Capacitor's #pickBanner
            // / #selectBanner sitting in the same fixed top slot.
            if (readerPickMode != ReaderPickMode.NONE) {
                PickModeBanner(
                    message = pickBannerMessage,
                    doneLabel = if (readerPickMode == ReaderPickMode.NOTE_PICK) "Done" else "Done",
                    onDone = { viewModel.finishPicking() }
                )
            }
            // "Return to cross references" banner — shown after tapping a
            // cross-reference result, so the user can hop straight back to
            // the same list/scroll position instead of re-opening it, or
            // dismiss it to just keep reading. Mirrors the "return to
            // search results" banner below; mutually exclusive with it,
            // same fixed top slot.
            if (readerPickMode == ReaderPickMode.NONE && crossReferenceReturnAvailable) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Cross references",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Return to cross references",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.returnToCrossReferences() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Return", fontSize = 12.sp)
                            }
                        }
                        IconButton(
                            onClick = { viewModel.dismissCrossReferenceReturnBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // "Return to search results" banner — shown after tapping a
            // search result, so the user can hop straight back to the same
            // results/scroll position instead of re-searching, or dismiss
            // it to just keep reading. Mutually exclusive with the
            // cross-reference banner above, same fixed top slot.
            if (readerPickMode == ReaderPickMode.NONE && !crossReferenceReturnAvailable && searchReturnAvailable) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Return to search results",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.returnToSearchResults() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Return", fontSize = 12.sp)
                            }
                        }
                        IconButton(
                            onClick = { viewModel.dismissSearchReturnBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // "Return to lexicon" banner — shown after tapping a Scripture
            // reference inside a Greek/Hebrew lexicon definition, so the
            // user can hop straight back to that word's page instead of
            // re-searching for it, or dismiss it to just keep reading.
            // Mutually exclusive with the cross-reference/search banners
            // above, same fixed top slot.
            if (readerPickMode == ReaderPickMode.NONE && !crossReferenceReturnAvailable && !searchReturnAvailable && lexiconReturnTab != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Lexicon",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (lexiconReturnTab == NavTab.HEBREW_WORD) "Return to Hebrew word" else "Return to Greek word",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.returnToLexicon() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Return", fontSize = 12.sp)
                            }
                        }
                        IconButton(
                            onClick = { viewModel.dismissLexiconReturnBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // "Return to note" banner — shown after tapping a verse mention
            // inside a note being read, so the user can hop straight back
            // to it instead of finding it again in the list, or dismiss it
            // to just keep reading. Mutually exclusive with the cross-
            // reference/search/lexicon banners above, same fixed top slot.
            if (readerPickMode == ReaderPickMode.NONE && !crossReferenceReturnAvailable && !searchReturnAvailable && lexiconReturnTab == null && noteReturnItem != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = "Note",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Return to note",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.returnToNote() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Return", fontSize = 12.sp)
                            }
                        }
                        IconButton(
                            onClick = { viewModel.dismissNoteReturnBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // "Return to highlighted verses" banner — shown after tapping a
            // highlighted-verse result, same reasoning as "Return to search
            // results": Highlighted Verses has no "origin verse" to undo
            // back to, it's opened as its own destination, so both this
            // banner and system back land back on that list. Mutually
            // exclusive with the cross-reference/search/lexicon/note
            // banners above, same fixed top slot. All 4 accent container
            // roles (primary/secondary/tertiary/error) are already taken by
            // those, so this uses the neutral surfaceContainerHigh instead,
            // with the coral "Return" button matching the rest of the app's
            // convention for the one interactive accent in an otherwise
            // neutral row.
            if (readerPickMode == ReaderPickMode.NONE && !crossReferenceReturnAvailable && !searchReturnAvailable && lexiconReturnTab == null && noteReturnItem == null && highlightsReturnAvailable) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Highlight,
                                contentDescription = "Highlighted verses",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Return to highlighted verses",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.returnToHighlightedVerses() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Return", fontSize = 12.sp)
                            }
                        }
                        IconButton(
                            onClick = { viewModel.dismissHighlightsReturnBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // "Return to Studied" banner — shown after jumping in from
            // StudiedScreen's "Recently Studied" card or its verse grid,
            // same reasoning as the highlighted-verses banner above
            // (Studied is its own destination too, no "origin verse" to
            // undo back to). Mutually exclusive with all 5 banners above,
            // same fixed top slot. Same neutral surfaceContainerHigh
            // treatment as the highlights banner — the two are never shown
            // at once, so sharing that look isn't confusing in practice.
            if (readerPickMode == ReaderPickMode.NONE && !crossReferenceReturnAvailable && !searchReturnAvailable && lexiconReturnTab == null && noteReturnItem == null && !highlightsReturnAvailable && studiedReturnAvailable) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Studied",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Return to Studied",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.returnToStudied() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Return", fontSize = 12.sp)
                            }
                        }
                        IconButton(
                            onClick = { viewModel.dismissStudiedReturnBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (isLoading && verses.isEmpty()) {
                // Only the very first load (nothing to show yet) gets the
                // spinner. Every subsequent chapter change also flips
                // isLoading briefly, but swapping this whole subtree out
                // for a spinner on every swipe would tear down the
                // AnimatedContent below and kill its slide transition —
                // local chapter loads are fast enough that staying mounted
                // and just letting the list update in place looks better.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                AnimatedContent(
                    targetState = currentBook to currentChapter,
                    transitionSpec = {
                        // Direction mirrors the swipe (or the ‹ › buttons,
                        // which set the same flag) — content slides out the
                        // way the finger moved and the new chapter slides
                        // in from the opposite edge, like a page turn.
                        if (chapterSwipeForward) {
                            (slideInHorizontally(tween(280)) { w -> w } + fadeIn(tween(180)))
                                .togetherWith(slideOutHorizontally(tween(280)) { w -> -w } + fadeOut(tween(180)))
                        } else {
                            (slideInHorizontally(tween(280)) { w -> -w } + fadeIn(tween(180)))
                                .togetherWith(slideOutHorizontally(tween(280)) { w -> w } + fadeOut(tween(180)))
                        }.using(SizeTransform(clip = false))
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        // Horizontal swipe-to-change-chapter. Orthogonal to
                        // the LazyColumn's own vertical scroll gesture below
                        // it — detectHorizontalDragGestures only commits once
                        // a drag clears the horizontal touch slop, so a
                        // vertical scroll never gets hijacked, and a mostly-
                        // horizontal swipe never fights the list for it.
                        .pointerInput(currentBook, currentChapter) {
                            var totalDrag = 0f
                            val threshold = 96.dp.toPx()
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                onDragEnd = {
                                    when {
                                        totalDrag <= -threshold -> {
                                            chapterSwipeForward = true
                                            viewModel.nextChapter()
                                        }
                                        totalDrag >= threshold -> {
                                            chapterSwipeForward = false
                                            viewModel.prevChapter()
                                        }
                                    }
                                }
                            )
                        },
                    label = "chapterSwipe"
                ) { _ ->
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomOverlayPaddingDp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("reader_verses_list")
                ) {
                    item {
                        Text(
                            text = "$currentBook $currentChapter",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    }

                    itemsIndexed(verses) { idx, verse ->
                        val isCompleted = completedVerses.any {
                            it.book == verse.book && it.chapter == verse.chapter && it.verse == verse.number
                        }
                        val highlightObj = highlights.find {
                            it.book == verse.book && it.chapter == verse.chapter && it.verse == verse.number
                        }
                        val hasNote = notes.any { it.matchesVerse(verse.book, verse.chapter, verse.number) }

                        // Auto-focus (bold) the vertically-centered verse — but
                        // only in Blur Mode, where that's the whole point (every
                        // other verse gets dimmed around it). Previously this
                        // fired in normal reading too, since centerItemIndex
                        // recalculates on every scroll frame: whichever verse
                        // crossed the screen's center got bolded, and because
                        // bold glyphs are wider, that caused a visible
                        // reflow/jerk on that verse as you scrolled past it.
                        val isFocused = if (focusedVerseNumber != null) {
                            verse.number == focusedVerseNumber
                        } else if (isBlurModeEnabled) {
                            (idx + 1) == centerItemIndex
                        } else {
                            false
                        }

                        // Manual Blur Mode is always on once toggled; the
                        // cross-reference focus blur is a temporary version
                        // of the same effect that turns itself off the
                        // moment the user scrolls away from the landed verse
                        // (see xrefFocusActive above).
                        val effectiveBlurEnabled = isBlurModeEnabled || xrefFocusActive

                        // Every tap-driven callback below (whole-verse tap,
                        // and the word-level Greek/Hebrew/English/xref-marker
                        // taps, which are separate click targets inside
                        // VerseCard that don't go through onVerseClick at
                        // all) routes through here first: tapping anything on
                        // a blurred, non-focused verse dismisses blur/focus
                        // instead of acting on it. Previously only
                        // onVerseClick had any blur awareness, so a word tap
                        // could still open a lookup for a verse you couldn't
                        // even read.
                        fun runUnlessBlurred(action: () -> Unit) {
                            if (effectiveBlurEnabled && !isFocused) {
                                if (isBlurModeEnabled) viewModel.toggleBlurMode()
                                if (xrefFocusActive) {
                                    xrefFocusActive = false
                                    viewModel.clearVerseFocus()
                                }
                            } else {
                                action()
                            }
                        }

                        // While a pick mode is active, tapping a verse means
                        // "add/toggle this verse" instead of opening the
                        // normal selection toolbar — mirrors Capacitor's
                        // onVerseTap() branching on pickingMode/selectMode
                        // before falling through to openVerseSheet().
                        val isPicked = when (readerPickMode) {
                            ReaderPickMode.NOTE_PICK -> {
                                val onDraft = noteToEdit?.refs?.any {
                                    it.book == verse.book && it.chapter == verse.chapter && it.verse == verse.number
                                } == true
                                val onPickedList = pickedNoteRefs.any {
                                    it.book == verse.book && it.chapter == verse.chapter && it.verse == verse.number
                                }
                                onDraft || onPickedList
                            }
                            ReaderPickMode.STUDY_PICK -> isCompleted
                            ReaderPickMode.NONE -> false
                        }

                        VerseCard(
                            verse = verse,
                            isSelected = if (readerPickMode != ReaderPickMode.NONE) isPicked else selectedVerse?.number == verse.number,
                            isCompleted = isCompleted,
                            highlightColorHex = highlightObj?.colorHex,
                            redLetterEnabled = redLetterEnabled,
                            showInterlinear = showInterlinear,
                            fontSizeSp = fontSizeSp,
                            hasNotes = hasNote,
                            hasXref = verse.number in verseNumbersWithXrefs,
                            isBlurModeEnabled = effectiveBlurEnabled,
                            isFocusedVerse = isFocused,
                            verseSpacingDp = verseSpacingDp,
                            englishLineHeightMultiplier = englishLineHeightMultiplier,
                            teluguLineHeightMultiplier = teluguLineHeightMultiplier,
                            englishFontFamilyName = englishFontFamilyName,
                            teluguFontSizeSp = teluguFontSizeSp,
                            greekFontSizeSp = greekFontSizeSp,
                            hebrewFontSizeSp = hebrewFontSizeSp,
                            onVerseClick = {
                                runUnlessBlurred {
                                    if (readerPickMode != ReaderPickMode.NONE) {
                                        viewModel.onPickModeVerseTap(verse)
                                    } else if (selectedVerse?.number == verse.number) {
                                        viewModel.setSelectedVerse(null)
                                    } else {
                                        viewModel.setSelectedVerse(verse)
                                    }
                                }
                            },
                            onVerseLongClick = {
                                if (readerPickMode == ReaderPickMode.NONE) {
                                    viewModel.startStudyPickingFromLongPress(verse)
                                } else {
                                    // Already in a picking session (note or
                                    // study) — long-press on a verse,
                                    // including one already picked, should
                                    // behave the same as a tap here: toggle
                                    // it. Previously this branch was a no-op,
                                    // so long-pressing an already-selected
                                    // verse to deselect it silently did
                                    // nothing.
                                    viewModel.onPickModeVerseTap(verse)
                                }
                            },
                            onGreekWordClick = { gWord ->
                                runUnlessBlurred { viewModel.selectGreekWord(gWord, verse) }
                            },
                            onHebrewWordClick = { hWord ->
                                runUnlessBlurred { viewModel.selectHebrewWord(hWord, verse) }
                            },
                            onCrossReferenceMarkerClick = {
                                // Bypasses the verse action sheet entirely —
                                // tapping the dagger jumps straight to cross
                                // references, reusing the same sheet/history
                                // stack a manual "Cross References" tap would.
                                runUnlessBlurred { viewModel.openCrossReferences(verse) }
                            },
                            onEnglishWordClick = { word ->
                                runUnlessBlurred { viewModel.openEnglishWordLookup(word) }
                            }
                        )
                    }
                }
                }
            }
        }

        // Which color's rename dialog is open, if any — rendered as its
        // own AlertDialog (a separate window), so its position in this Box
        // doesn't matter — declared once here rather than nested inside the
        // toolbar branch below so it isn't torn down if the toolbar itself
        // closes while the rename dialog is still open.
        var colorToRename by remember { mutableStateOf<HighlightColorDef?>(null) }
        colorToRename?.let { def ->
            RenameHighlightColorDialog(
                colorDef = def,
                onDismiss = { colorToRename = null },
                onRename = { newLabel ->
                    viewModel.renameHighlightColor(def.colorHex, newLabel)
                    colorToRename = null
                }
            )
        }

        // Floating Action Toolbar for selected verse
        if (selectedVerse != null) {
            val verse = selectedVerse!!
            val notesOnVerse = notes.filter { it.matchesVerse(verse.book, verse.chapter, verse.number) }
            val currentHighlight = highlights.find {
                it.book == verse.book && it.chapter == verse.chapter && it.verse == verse.number
            }

            VerseActionToolbar(
                verse = verse,
                highlightColorDefs = highlightColorDefs,
                currentHighlightColorHex = currentHighlight?.colorHex,
                highlightHasLinkedNote = currentHighlight?.noteId != null,
                existingNotes = notesOnVerse,
                onSetHighlight = { hex ->
                    viewModel.setHighlightColor(verse.book, verse.chapter, verse.number, hex)
                },
                onAddQuickNote = { noteText ->
                    val currentHex = currentHighlight?.colorHex
                    if (currentHex != null) {
                        val colorLabel = highlightColorDefs.find { it.colorHex == currentHex }?.label ?: "Highlight"
                        viewModel.addHighlightQuickNote(
                            book = verse.book,
                            chapter = verse.chapter,
                            verse = verse.number,
                            verseText = verse.text,
                            colorHex = currentHex,
                            colorLabel = colorLabel,
                            noteText = noteText
                        )
                    }
                },
                onAddNote = {
                    viewModel.openNoteEditor(defaultVerse = verse)
                },
                onOpenNote = { note ->
                    // Matches the note-marker dot: opens straight into a
                    // read-only view of the note, not the editor — editing
                    // is a separate explicit action (the note's own overflow
                    // menu), not something a plain tap should land you in.
                    viewModel.setSelectedVerse(null)
                    viewModel.openNoteReader(note, originTab = NavTab.READER)
                },
                onToggleInterlinear = {
                    // Anchor on whatever verse is at the top of the
                    // viewport, same as the pill toggle below — not on
                    // `verse` itself, which is just whichever verse this
                    // selection toolbar happens to be attached to (often
                    // off-screen or near the bottom, since the toolbar is
                    // bottom-aligned) and would jump the view to it instead
                    // of preserving where the user was reading.
                    val anchorVerse = verses.getOrNull(listState.firstVisibleItemIndex - 1)?.number
                    viewModel.toggleInterlinear(anchorVerse)
                },
                onDismiss = {
                    viewModel.setSelectedVerse(null)
                },
                onRenameColor = { def -> colorToRename = def },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .onGloballyPositioned { bottomOverlayHeightPx = it.size.height }
            )
        } else {
            // The only chrome Reader has now — book label plus quick
            // toggles, full settings, and a menu into Notes/Studied/Search.
            // Chapter navigation is chevron-free: swipe left/right on the
            // Reader (see the pointerInput/drag handling above) or tap the
            // book label to open the book/chapter picker. Replaces the old
            // always-on-screen header + bottom nav entirely.
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
                    .testTag("fullscreen_floating_bar")
                    // Kept always composed (not removed via `if`) and only
                    // transformed, same as Capacitor's CSS transform
                    // approach — so bottomOverlayHeightPx keeps measuring a
                    // stable value and the LazyColumn's bottom padding never
                    // jumps as the pill hides/shows.
                    .graphicsLayer {
                        translationY = barsHiddenProgress * (size.height + 120.dp.toPx())
                        alpha = 1f - barsHiddenProgress
                    }
                    .onGloballyPositioned { bottomOverlayHeightPx = it.size.height }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // --- Book/chapter label — plain text, no accent chip.
                    // Tapping opens the full book/chapter picker; there's no
                    // other way to change chapters from the pill anymore
                    // (chevrons removed — use swipe or the picker).
                    Text(
                        text = "$currentBook $currentChapter",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clickable { viewModel.setShowBookPicker(true) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("pill_book_chapter")
                    )

                    VerticalDivider(
                        modifier = Modifier
                            .height(20.dp)
                            .padding(horizontal = 2.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    // --- Telugu / Greek / Blur — direct toggles, each with
                    // a glowing tinted background when active so on/off
                    // state reads at a glance without opening a sheet. ---
                    PillGlyphToggle(
                        glyph = "\u0C05",
                        active = showTeluguInline,
                        contentDescription = "Telugu inline translation",
                        onClick = {
                            // Item 0 in the LazyColumn is the "$currentBook
                            // $currentChapter" header (see itemsIndexed(verses)
                            // below), so verse array index = list index - 1.
                            // See toggleTeluguInline's doc for why this is
                            // needed at all.
                            val anchorVerse = verses.getOrNull(listState.firstVisibleItemIndex - 1)?.number
                            viewModel.toggleTeluguInline(anchorVerse)
                        },
                        modifier = Modifier.testTag("pill_telugu_toggle"),
                        offsetY = 1.dp
                    )

                    // The pill's glyph reflects whichever interlinear this
                    // toggle actually controls for the book currently open —
                    // Greek alpha for NT books, Hebrew aleph for OT ones —
                    // rather than always showing alpha even over Old
                    // Testament text it has no bearing on.
                    val isOldTestamentBook = BIBLE_BOOKS.indexOf(currentBook).let { it in 0..38 }
                    PillGlyphToggle(
                        glyph = if (isOldTestamentBook) "\u05D0" else "\u03B1",
                        active = showInterlinear,
                        contentDescription = if (isOldTestamentBook) "Hebrew interlinear" else "Greek interlinear",
                        onClick = {
                            // See toggleTeluguInline's/toggleInterlinear's
                            // doc for why this anchor is needed.
                            val anchorVerse = verses.getOrNull(listState.firstVisibleItemIndex - 1)?.number
                            viewModel.toggleInterlinear(anchorVerse)
                        },
                        modifier = Modifier.testTag("pill_greek_toggle"),
                        offsetY = (-1).dp
                    )

                    PillIconToggle(
                        icon = Icons.Default.BlurOn,
                        active = isBlurModeEnabled,
                        contentDescription = "Blur mode",
                        onClick = { viewModel.toggleBlurMode() },
                        modifier = Modifier.testTag("pill_blur_toggle")
                    )

                    VerticalDivider(
                        modifier = Modifier
                            .height(20.dp)
                            .padding(horizontal = 2.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    // --- Full settings screen ---
                    IconButton(
                        onClick = { viewModel.selectTab(NavTab.SETTINGS) },
                        modifier = Modifier.size(34.dp).testTag("pill_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // --- Menu: Notes / Studied / Search ---
                    Box {
                        IconButton(
                            onClick = { showReaderMenu = true },
                            modifier = Modifier.size(34.dp).testTag("pill_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Notes, Studied, Search, Highlighted Verses",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showReaderMenu,
                            onDismissRequest = { showReaderMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Notes") },
                                leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                                onClick = {
                                    showReaderMenu = false
                                    viewModel.selectTab(NavTab.NOTES)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Studied") },
                                leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                                onClick = {
                                    showReaderMenu = false
                                    viewModel.selectTab(NavTab.STUDIED)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Search") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                onClick = {
                                    showReaderMenu = false
                                    viewModel.selectTab(NavTab.SEARCH)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Highlighted Verses") },
                                leadingIcon = { Icon(Icons.Default.Highlight, contentDescription = null) },
                                onClick = {
                                    showReaderMenu = false
                                    viewModel.selectTab(NavTab.HIGHLIGHTS)
                                }
                            )
                        }
                    }
                }
            }
        }

    }
}

// --- Pill toggle buttons — glyph (Telugu/Greek/Hebrew script characters,
// which have no Material icon) and icon variants share the same active-state
// styling: a soft tinted circular backdrop plus a border, so on/off is
// visible at a glance without a label. Both live in the pill row itself
// now instead of behind the removed "Tune" quick-settings sheet. ---

@Composable
private fun PillGlyphToggle(
    glyph: String,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Per-glyph fine-tuning — different scripts sit differently against
    // their own baseline/x-height even with includeFontPadding disabled,
    // so each glyph gets its own nudge rather than a shared one.
    offsetY: androidx.compose.ui.unit.Dp = 0.dp
) {
    val activeColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(34.dp)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            fontSize = 17.sp,
            lineHeight = 17.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            // Compose's default Text reserves extra space below the
            // baseline for descenders (font padding), which visually
            // pushes single glyphs like this one down and off true center
            // even with contentAlignment = Center on the Box. Disabling it
            // centers the glyph precisely instead of guessing at a
            // hardcoded offset.
            style = LocalTextStyle.current.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            modifier = Modifier.offset(y = offsetY)
        )
    }
}

@Composable
private fun PillIconToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(34.dp)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
