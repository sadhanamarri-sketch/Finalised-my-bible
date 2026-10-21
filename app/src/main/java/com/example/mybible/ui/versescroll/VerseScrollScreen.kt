package com.example.mybible.ui.versescroll

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mybible.audio.ReadAloudState
import com.example.mybible.audio.ReadingPlace
import com.example.mybible.data.MAX_VERSE_COLORS
import com.example.mybible.model.HIGHLIGHT_COLOR_DEFS
import com.example.mybible.model.HighlightItem
import com.example.mybible.model.NoteItem
import com.example.mybible.model.Verse
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.ReadAloudSheet
import com.example.mybible.ui.components.readLanguageName
import com.example.mybible.ui.components.rememberReadAloudNotificationAsk
import com.example.mybible.ui.components.VerseActionToolbar
import com.example.mybible.ui.theme.WorkSansFontFamily
import com.example.mybible.versescroll.CheckInFeedCard
import com.example.mybible.versescroll.EndFeedCard
import com.example.mybible.versescroll.FeedMode
import com.example.mybible.versescroll.VerseFeedCard
import com.example.mybible.versescroll.VerseRef
import com.example.mybible.versescroll.VerseScrollController
import com.example.mybible.versescroll.VerseSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The design's color roles, taken from the app's theme. */
@Immutable
internal data class VsColors(
    val dark: Boolean,
    val bg: Color,
    val ink: Color,
    val soft: Color,
    val surface: Color,
    val line: Color,
    val primary: Color,
    val onPrimary: Color,
    val gold: Color,
    val toastBg: Color,
    val toastInk: Color,
    val toastAction: Color
) {
    /**
     * Over a photo the quieter text is the main ink at 80%, and the gold is brighter (darker in light
     * themes) — the colors the photos' dimming was measured against.
     */
    fun onScene(): VsColors = copy(
        soft = ink.copy(alpha = 0.8f),
        gold = if (dark) Color(0xFFE8C27A) else Color(0xFF5E4813)
    )
}

@Composable
private fun verseScrollColors(): VsColors {
    val cs = MaterialTheme.colorScheme
    // Dark or light from the theme's own background, the same test MainActivity uses for the status bar.
    val dark = cs.background.luminance() < 0.5f
    return VsColors(
        dark = dark,
        bg = cs.background,
        ink = cs.onBackground,
        soft = cs.onSurfaceVariant,
        surface = cs.surface,
        line = cs.outlineVariant,
        primary = cs.primary,
        onPrimary = cs.onPrimary,
        gold = cs.tertiary,
        toastBg = cs.onBackground,
        toastInk = cs.background,
        toastAction = if (dark) Color(0xFF7A5F1E) else Color(0xFFE2C077)
    )
}

/** The phone's "remove animations" setting: scenes then stay still, whatever Verse Scroll's own setting says. */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** First line of a note, cut to fit under a verse. */
private fun notePreview(note: NoteItem): String? {
    val line = note.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
    return if (line.length > 90) line.take(89) + "…" else line
}

private fun noteRefs(note: NoteItem): List<VerseRef> =
    note.refs.map { VerseRef(it.book, it.chapter, it.verse) }
        .ifEmpty { if (note.book.isNotEmpty()) listOf(VerseRef(note.book, note.chapter, note.verse)) else emptyList() }

/**
 * Verse Scroll: one verse at a time, full screen, swiping up for the next — a calmer thing to scroll
 * than a social feed. Discover deals widely referenced verses from across the Bible over slowly drifting
 * nature photos; double-tap opens the color box, long-press the full verse sheet, and the bottom
 * bar adds a note, reads the verse in context, or shows its links.
 */
@Composable
fun VerseScrollScreen(viewModel: MainViewModel) {
    val controller = viewModel.verseScroll
    val highlights by viewModel.highlights.collectAsState(initial = emptyList())
    val colorDefs by viewModel.highlightColorDefs.collectAsState(initial = HIGHLIGHT_COLOR_DEFS)
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    val colors = verseScrollColors()
    val reduceMotion = rememberReduceMotion()
    // Whether Verse Scroll is up (started), for a reading going on by itself to slide along to the next card.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.screenShowing = true
                Lifecycle.Event.ON_STOP -> controller.screenShowing = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.screenShowing = false
        }
    }
    val scenes = controller.backgroundPhotos
    val motion = scenes && controller.motion && !reduceMotion
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Each verse's highlight colors, in the order they were put on (up to three).
    val highlightsByKey = remember(highlights) { highlights.groupBy { VerseRef(it.book, it.chapter, it.verse).key } }
    fun hexesOf(ref: VerseRef): List<String> = highlightsByKey[ref.key].orEmpty().map { it.colorHex }
    val labelByHex = remember(colorDefs) { colorDefs.associate { it.colorHex.uppercase() to it.label } }
    fun labelOf(hex: String) = labelByHex[hex.uppercase()] ?: hex
    val notesByKey = remember(notes) {
        val map = HashMap<String, MutableList<NoteItem>>()
        notes.forEach { note -> noteRefs(note).forEach { map.getOrPut(it.key) { mutableListOf() } += note } }
        map.mapValues { (_, list) -> list.sortedByDescending { it.updatedAt } }
    }

    DisposableEffect(controller) {
        controller.onOpen()
        onDispose { controller.onLeave() }
    }

    // ---- screen-only state ----
    var pickerIndex by remember { mutableIntStateOf(-1) }
    var pickerArm by remember { mutableIntStateOf(0) }
    var pickerMillis by remember { mutableLongStateOf(0L) }
    // A color picked in the box a double-tap opened: the card's ring pops in it.
    var pickerFromTap by remember { mutableStateOf(false) }
    // A tap that only closed the color box does nothing more (no look-up, no play or pause).
    var pressClosedPicker by remember { mutableStateOf(false) }
    var burst by remember { mutableStateOf<Pair<Int, CardBurst>?>(null) }
    var toast by remember { mutableStateOf<VsToast?>(null) }
    var sheetCard by remember { mutableStateOf<VerseFeedCard?>(null) }
    var linksCard by remember { mutableStateOf<Pair<Int, VerseFeedCard>?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var readAloudOpen by remember { mutableStateOf(false) }
    val readAloudState by viewModel.readAloudState.collectAsState()
    val askForNotifications = rememberReadAloudNotificationAsk()
    val feedAlpha = remember { Animatable(1f) }

    fun showToast(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        toast = VsToast(System.nanoTime(), message, action, onAction)
    }
    // The color box closes [ms] from now (0: not by itself).
    fun armPicker(ms: Long) {
        pickerMillis = ms
        pickerArm++
    }
    fun showPicker(index: Int, fromTap: Boolean = false) {
        toast = null
        pickerIndex = index
        pickerFromTap = fromTap
        armPicker(0)
    }
    fun hidePicker() {
        pickerIndex = -1
    }
    LaunchedEffect(pickerIndex, pickerArm) {
        if (pickerIndex >= 0 && pickerMillis > 0) {
            delay(pickerMillis)
            pickerIndex = -1
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(TOAST_MS)
            toast = null
        }
    }
    // Both languages but no Telugu voice: said once, as the cards are read on in English.
    val englishOnly = readAloudState.active && readAloudState.place == ReadingPlace.VERSE_SCROLL && readAloudState.noTeluguVoice
    LaunchedEffect(englishOnly) {
        if (englishOnly) showToast("No Telugu voice on this phone yet, so just the English", "Settings") { readAloudOpen = true }
    }

    // ---- highlights (stored per verse and color, the same highlights as everywhere else in the app) ----
    // The colors every verse of the card is highlighted in, in the first verse's order.
    fun colorsOf(card: VerseFeedCard): List<String> {
        val perVerse = card.content.refs.map(::hexesOf)
        return perVerse.first().filter { hex -> perVerse.all { verse -> verse.any { it.equals(hex, ignoreCase = true) } } }
    }
    // Puts [hex] on [refs] (verses of the card that don't have it): not on one already in three colors.
    fun setColor(card: VerseFeedCard, refs: List<VerseRef>, hex: String) {
        val room = refs.filter { hexesOf(it).size < MAX_VERSE_COLORS }
        if (room.size < refs.size) showToast("A verse can have up to $MAX_VERSE_COLORS colors")
        if (room.isEmpty()) return
        room.forEach { viewModel.addHighlightColor(it.book, it.chapter, it.verse, hex) }
        controller.noteHighlighted(room.map { it.key }, on = true)
    }
    // A color in the color box or the verse sheet: off the card when all of it has the color, else on.
    fun toggleColor(card: VerseFeedCard, hex: String) {
        if (colorsOf(card).any { it.equals(hex, ignoreCase = true) }) {
            card.content.refs.forEach { viewModel.removeHighlightColor(it.book, it.chapter, it.verse, hex) }
            // A verse left with no color at all no longer counts as highlighted this visit.
            controller.noteHighlighted(card.content.refs.filter { hexesOf(it).size == 1 }.map { it.key }, on = false)
        } else {
            setColor(card, card.content.refs.filter { ref -> hexesOf(ref).none { it.equals(hex, ignoreCase = true) } }, hex)
        }
    }
    // Remove: every color off the card, with Undo.
    fun removeColor(card: VerseFeedCard) {
        val previous: List<HighlightItem> = card.content.refs.flatMap { highlightsByKey[it.key].orEmpty() }
        if (previous.isEmpty()) return
        val keys = previous.map { VerseRef(it.book, it.chapter, it.verse).key }.distinct()
        card.content.refs.forEach { viewModel.clearHighlight(it.book, it.chapter, it.verse) }
        controller.noteHighlighted(keys, on = false)
        showToast("Highlight removed", "Undo") {
            viewModel.restoreHighlights(previous)
            controller.noteHighlighted(keys, on = true)
        }
    }
    // A double-tap: the color box, and nothing on the verse until a color is picked there.
    fun doubleTap(index: Int) {
        controller.dismissHint()
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        showPicker(index, fromTap = true)
    }
    fun asVerse(card: VerseFeedCard): Verse {
        val line = card.content.lines.first()
        return Verse(card.content.ref.book, card.content.ref.chapter, line.number, line.text, teluguText = line.telugu)
    }
    // A word tapped on a card: its Webster's 1828 sheet, over Verse Scroll. Reading aloud pauses while
    // it's open, and plays on by itself once it's closed.
    val lookingUp by viewModel.selectedEnglishWord.collectAsState()
    var pausedForWord by remember { mutableStateOf(false) }
    fun lookUp(word: String, card: VerseFeedCard) {
        hidePicker()
        if (viewModel.readAloudState.value.status == ReadAloudState.Status.PLAYING) {
            viewModel.pauseReading()
            pausedForWord = true
        }
        viewModel.openEnglishWordLookup(word, baseVerse = asVerse(card))
    }
    LaunchedEffect(lookingUp) {
        if (lookingUp != null || !pausedForWord) return@LaunchedEffect
        pausedForWord = false
        if (viewModel.readAloudState.value.status == ReadAloudState.Status.PAUSED) viewModel.resumeReading()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        val cards = controller.cards
        key(controller.feedId) {
            val pagerState = rememberPagerState(initialPage = controller.currentIndex) { cards.size }
            // The controller asks to move on after following a link or leaving a Rabbit hole, or as reading
            // aloud goes on by itself to the next card: then a slower, eased slide.
            val request = controller.scrollRequest
            LaunchedEffect(request) {
                if (request == null) return@LaunchedEffect
                try {
                    if (request.index < cards.size) {
                        if (request.gentle && !reduceMotion) {
                            pagerState.animateScrollToPage(request.index, animationSpec = tween(GENTLE_SLIDE_MS, easing = FastOutSlowInEasing))
                        } else {
                            pagerState.animateScrollToPage(request.index)
                        }
                    }
                } finally {
                    // There, or a swipe took over: done with it either way.
                    controller.consumeScrollRequest(request)
                }
            }

            // Leaving a Rabbit hole: a quick fade when it jumps back to where the hole started.
            fun leave(toRoot: Boolean, message: (String?) -> String) {
                scope.launch {
                    hidePicker()
                    val rootLabel = controller.rabbitHoleRoot(pagerState.settledPage)?.content?.label
                    val jumps = toRoot && rootLabel != null
                    if (jumps && !reduceMotion) feedAlpha.animateTo(0f, tween(160))
                    val target = controller.leaveRabbitHole(toRoot)
                    if (target != null) {
                        pagerState.scrollToPage(target)
                        showToast(message(rootLabel))
                    }
                    if (feedAlpha.value < 1f) feedAlpha.animateTo(1f, tween(160))
                }
            }
            // The first back press leaves a Rabbit hole; the next leaves Verse Scroll.
            BackHandler(enabled = controller.mode == FeedMode.RABBIT_HOLE && pickerIndex < 0) {
                leave(toRoot = true) { root -> if (root != null) "Out of the Rabbit hole \u00b7 back at $root" else "Out of the Rabbit hole" }
            }
            // Also re-checked as cards arrive: the first ones load after the pager has already settled on page 0.
            LaunchedEffect(pagerState) {
                // On the way to a card asked for (the reading going on to it), the card being left isn't
                // shown again as more cards are dealt; once there, or a swipe took over, the card settled on is.
                snapshotFlow { Triple(pagerState.settledPage, cards.size, controller.scrollRequest) }.collect { (page, size, moving) ->
                    if (page < size && (moving == null || moving.index == page)) controller.onCardShown(page)
                }
            }
            // A swipe closes the color box, as does touching the verse (see onPress below).
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling -> if (scrolling) hidePicker() }
            }
            VerticalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(state = pagerState, pagerSnapDistance = PagerSnapDistance.atMost(1)),
                key = { page -> cards.getOrNull(page)?.uid ?: page.toLong() },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = feedAlpha.value }
            ) { page ->
                when (val card = cards.getOrNull(page)) {
                    is VerseFeedCard -> {
                        val scene = if (scenes) controller.scene(card.sceneId) else null
                        val image by produceState<ImageBitmap?>(
                            initialValue = card.sceneId?.let { controller.cachedImage(it) },
                            card.sceneId, scenes
                        ) {
                            if (scenes && value == null) card.sceneId?.let { value = controller.loadImage(it) }
                        }
                        val firstNote = card.content.refs.firstNotNullOfOrNull { notesByKey[it.key]?.firstOrNull() }
                        VerseCardPage(
                            card = card,
                            scene = scene,
                            image = image,
                            colors = colors,
                            motion = motion,
                            reduceMotion = reduceMotion,
                            isCurrent = pagerState.settledPage == page,
                            showTelugu = controller.showTelugu,
                            teluguSize = controller.teluguSize,
                            // A search's verses (not a Rabbit hole's links): what found each.
                            words = (controller.source as? VerseSource.Found)?.takeIf { card.from == null }
                                ?.let { found -> card.content.refs.flatMap(found::wordsOf).distinct().map { it.label } }
                                .orEmpty(),
                            highlightHexes = ::hexesOf,
                            colorLabel = ::labelOf,
                            notePreview = firstNote?.let(::notePreview),
                            onPress = {
                                pressClosedPicker = pickerIndex >= 0
                                hidePicker()
                            },
                            onDoubleTap = { doubleTap(page) },
                            onWord = { word -> if (!pressClosedPicker) lookUp(word, card) },
                            onTapAround = {
                                if (!pressClosedPicker) {
                                    controller.playOrPause()
                                    // Nothing to read with (Telugu alone, and no Telugu voice): the settings say why.
                                    val now = viewModel.readAloudState.value
                                    if (!now.active && (now.noTeluguVoice || now.noVoice)) readAloudOpen = true
                                    if (now.active) askForNotifications()
                                }
                            },
                            burst = burst?.takeIf { it.first == page }?.second,
                            onLongPress = {
                                hidePicker()
                                toast = null
                                sheetCard = card
                            },
                            onLinkedFrom = {
                                val from = card.from?.ref
                                val source = (page - 1 downTo 0).firstOrNull { (cards[it] as? VerseFeedCard)?.content?.ref == from }
                                if (source != null) scope.launch { pagerState.animateScrollToPage(source) }
                            }
                        )
                    }
                    is CheckInFeedCard -> {
                        val last = cards.subList(0, page).lastOrNull { it is VerseFeedCard } as VerseFeedCard?
                        val root = card.rootIndex?.takeIf { controller.isInRabbitHole(card) }
                        CheckInPage(
                            colors = colors,
                            versesToday = controller.versesToday,
                            highlighted = controller.highlightedThisVisit,
                            lastVerse = last?.content?.ref,
                            lastVerseLabel = last?.content?.label,
                            onRead = {
                                last?.content?.ref?.let { viewModel.readFromVerseScroll(it.book, it.chapter, it.verse) }
                            },
                            onKeepGoing = { scope.launch { pagerState.animateScrollToPage(page + 1) } },
                            rabbitHoleFrom = root?.let { (cards.getOrNull(it) as? VerseFeedCard)?.content?.label },
                            rabbitHoleDepth = root?.let { r -> cards.subList(r + 1, page).count { it is VerseFeedCard } } ?: 0,
                            backToLabel = controller.source.label,
                            onBackToDiscover = {
                                if (controller.leaveRabbitHole(toRoot = false, advance = true) != null) showToast("Back to ${controller.source.label}")
                            }
                        )
                    }
                    is EndFeedCard -> EndPage(
                        colors = colors,
                        label = card.label,
                        onStartAgain = { controller.startAgain() },
                        onOtherVerses = { sourcesOpen = true }
                    )
                    null -> Box(Modifier.fillMaxSize())
                }
            }

            // Loads the pictures for the cards either side ahead of time, so a swipe never lands on an empty scene.
            LaunchedEffect(pagerState, scenes) {
                if (!scenes) return@LaunchedEffect
                snapshotFlow { pagerState.currentPage to cards.size }.collect { (page, _) ->
                    for (j in (page - 1)..(page + 2)) {
                        (cards.getOrNull(j) as? VerseFeedCard)?.sceneId?.let { controller.loadImage(it) }
                    }
                }
            }

            val current = cards.getOrNull(pagerState.settledPage)
            val verseCard = current as? VerseFeedCard
            val picking = pickerIndex >= 0 && pickerIndex == pagerState.settledPage

            val inRabbitHole = controller.mode == FeedMode.RABBIT_HOLE
            VerseScrollTopBar(
                colors = colors,
                scenes = scenes && verseCard != null,
                rabbitHole = inRabbitHole,
                backTo = controller.rabbitHoleRoot(pagerState.settledPage)?.content?.label,
                modeLabel = controller.source.label,
                language = viewModel.readAloudSettings.language,
                reading = controller.speakerOn,
                sleepChip = if (readAloudState.place == ReadingPlace.VERSE_SCROLL && readAloudState.sleep != null) {
                    {
                        SleepChip(
                            readAloudState, colors, scenes && verseCard != null, viewModel.readAloud::sleepLeftMs,
                            onSleepIn = viewModel::sleepIn, onCancel = viewModel::cancelSleep
                        )
                    }
                } else null,
                onBack = {
                    if (inRabbitHole) {
                        leave(toRoot = true) { root -> if (root != null) "Out of the Rabbit hole \u00b7 back at $root" else "Out of the Rabbit hole" }
                    } else {
                        viewModel.closeVerseScroll()
                    }
                },
                onDiscover = {
                    if (inRabbitHole) {
                        leave(toRoot = false) { "${controller.source.label} \u00b7 ${controller.source.blurb}" }
                    } else {
                        hidePicker()
                        sourcesOpen = true
                    }
                },
                onRabbitHole = {
                    if (!inRabbitHole) {
                        val index = controller.verseIndexAtOrBefore(pagerState.settledPage)
                        val from = index?.let { cards[it] as? VerseFeedCard }
                        if (index != null && from != null) {
                            hidePicker()
                            controller.followFrom(index, link = null, advance = false)
                            showToast("Rabbit hole \u00b7 your next swipes follow links from ${from.content.label}")
                        }
                    }
                },
                onBackTo = {
                    leave(toRoot = true) { root -> if (root != null) "Out of the Rabbit hole \u00b7 back at $root" else "Out of the Rabbit hole" }
                },
                onLanguage = {
                    hidePicker()
                    if (viewModel.nextReadLanguage()) {
                        showToast("Reading in ${readLanguageName(viewModel.readAloudSettings.language)}")
                    } else {
                        // No other language to go to (no Telugu voice or recording): the settings say why.
                        readAloudOpen = true
                    }
                },
                onSettings = {
                    hidePicker()
                    settingsOpen = true
                }
            )

            if (verseCard != null && !picking) {
                VersesTodayCounter(controller.versesToday, if (scenes) colors.onScene() else colors)
                VerseScrollBottomBar(
                    colors = colors,
                    scenes = scenes,
                    highlight = colorsOf(verseCard).takeIf { it.isNotEmpty() }?.let { hexes ->
                        BarHighlight(hexes, labelOf(hexes.first()) + if (hexes.size > 1) " +${hexes.size - 1}" else "")
                    },
                    linkCount = verseCard.content.linkCount,
                    // The color box, where Remove lives too.
                    onHighlight = { showPicker(pagerState.settledPage) },
                    onNote = { viewModel.openNoteEditor(defaultVerse = asVerse(verseCard)) },
                    onRead = {
                        val ref = verseCard.content.ref
                        viewModel.readFromVerseScroll(ref.book, ref.chapter, ref.verse)
                    },
                    onLinks = {
                        hidePicker()
                        toast = null
                        linksCard = pagerState.settledPage to verseCard
                    }
                )
            }

            val slide = with(LocalDensity.current) { 10.dp.roundToPx() }
            AnimatedVisibility(
                visible = picking && verseCard != null,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { slide },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { slide },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val boxCard = verseCard
                if (boxCard != null) {
                    ColorBox(
                        colors = colors,
                        defs = colorDefs,
                        current = colorsOf(boxCard),
                        onPick = { hex ->
                            val adding = colorsOf(boxCard).none { it.equals(hex, ignoreCase = true) }
                            toggleColor(boxCard, hex)
                            if (adding && pickerFromTap) burst = pagerState.settledPage to CardBurst(System.nanoTime(), hex)
                            armPicker(PICKER_AFTER_PICK_MS)
                        },
                        onNote = {
                            hidePicker()
                            sheetCard = boxCard
                        },
                        onRemove = {
                            hidePicker()
                            removeColor(boxCard)
                        }
                    )
                }
            }
        }

        if (controller.sourceEmpty) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp)
            ) {
                Text(
                    text = when (controller.source) {
                        VerseSource.Highlighted -> "No highlighted verses yet. Highlight a verse and it will come up here."
                        VerseSource.Studied -> "No verses marked studied yet."
                        else -> "No verses here."
                    },
                    color = colors.soft,
                    fontFamily = WorkSansFontFamily,
                    fontSize = 15.sp,
                    lineHeight = 1.5.em,
                    textAlign = TextAlign.Center
                )
                PillButton("Other verses", primary = true, colors = colors, onClick = { sourcesOpen = true })
            }
        }

        if (controller.waitingForBibleText) {
            Text(
                text = "Verse Scroll needs the Bible text, which is still downloading. Try again in a moment.",
                color = colors.soft,
                fontFamily = WorkSansFontFamily,
                fontSize = 15.sp,
                lineHeight = 1.5.em,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp)
            )
        }

        val shownToast = toast
        if (shownToast != null) {
            ToastBar(shownToast, colors, onDismiss = { toast = null })
        }

        if (!controller.hintSeen && controller.cards.isNotEmpty()) {
            FirstTimeHint(colors, reduceMotion, onDismiss = { controller.dismissHint() })
        }
    }

    // Back closes the color box before it leaves Verse Scroll.
    BackHandler(enabled = pickerIndex >= 0) { hidePicker() }

    sheetCard?.let { card ->
        val verse = asVerse(card)
        // A quick note goes with the color put on last.
        val highlight = highlightsByKey[card.content.ref.key]?.lastOrNull()
        VerseActionToolbar(
            verse = verse,
            highlightColorDefs = colorDefs,
            currentHighlightColorHexes = colorsOf(card),
            onToggleHighlight = { hex -> toggleColor(card, hex) },
            onClearHighlight = { removeColor(card) },
            onAddNote = {
                sheetCard = null
                viewModel.openNoteEditor(defaultVerse = verse)
            },
            onToggleInterlinear = {},
            onDismiss = { sheetCard = null },
            existingNotes = notesByKey[card.content.ref.key].orEmpty(),
            onOpenNote = { note ->
                sheetCard = null
                viewModel.openNoteReader(note, originTab = NavTab.VERSE_SCROLL)
            },
            // Offered only with a color on the verse, as in the Reader.
            onAddQuickNote = { text ->
                val hex = highlight?.colorHex
                if (hex != null) viewModel.addHighlightQuickNote(verse.book, verse.chapter, verse.number, verse.text, hex, labelOf(hex), text)
            },
            highlightHasLinkedNote = highlight?.noteId != null
        )
    }

    linksCard?.let { (index, card) ->
        val previews by produceState<List<VerseScrollController.LinkPreview>?>(null, card.uid) {
            value = controller.linkPreviews(card)
        }
        LinksSheet(
            colors = colors,
            label = card.content.label,
            total = card.content.linkCount,
            previews = previews,
            highlightHexes = ::hexesOf,
            colorLabel = ::labelOf,
            onPick = { preview ->
                linksCard = null
                controller.followFrom(index, preview.link, advance = true)
                showToast("Rabbit hole from ${card.content.label}")
            },
            onFollow = {
                linksCard = null
                controller.followFrom(index, link = null, advance = true)
                showToast("Rabbit hole \u00b7 following links from ${card.content.label}")
            },
            onSeeAll = {
                linksCard = null
                viewModel.openCrossReferences(asVerse(card), origin = NavTab.VERSE_SCROLL)
            },
            onDismiss = { linksCard = null }
        )
    }

    if (sourcesOpen) {
        val studied by viewModel.completedVerses.collectAsState()
        val readerBook by viewModel.currentBook.collectAsState()
        val readerChapter by viewModel.currentChapter.collectAsState()
        VerseScrollSourceSheet(
            colors = colors,
            current = controller.source,
            fullCount = controller.fullCount,
            focusedCount = controller.focusedCount,
            // Verses, not colors: a verse can be highlighted in more than one.
            highlightedCount = highlightsByKey.size,
            studiedCount = studied.size,
            readerBook = readerBook,
            readerChapter = readerChapter,
            onChoose = { chosen ->
                sourcesOpen = false
                hidePicker()
                controller.choose(chosen)
            },
            onDismiss = { sourcesOpen = false }
        )
    }

    if (settingsOpen) {
        VerseScrollSettingsSheet(
            photos = controller.backgroundPhotos,
            motion = controller.motion,
            reduceMotion = reduceMotion,
            showTelugu = controller.showTelugu,
            teluguSize = controller.teluguSize,
            sceneCount = controller.sceneCount,
            onPhotos = controller::updateBackgroundPhotos,
            onMotion = controller::updateMotion,
            onShowTelugu = controller::updateShowTelugu,
            onTeluguSize = controller::adjustTeluguSize,
            continueReading = viewModel.readAloudSettings.continueReading,
            onContinueReading = viewModel.readAloudSettings::updateContinueReading,
            pauseBetweenCards = viewModel.readAloudSettings.pauseBetweenCards,
            onPauseBetweenCards = viewModel.readAloudSettings::updatePauseBetweenCards,
            onReadAloudSettings = {
                settingsOpen = false
                readAloudOpen = true
            },
            onDismiss = { settingsOpen = false }
        )
    }

    if (readAloudOpen) {
        ReadAloudSheet(
            readAloud = viewModel.readAloud,
            settings = viewModel.readAloudSettings,
            inVerseScroll = true,
            onDismiss = { readAloudOpen = false }
        )
    }
}

// The color box closes this soon after a pick.
private const val PICKER_AFTER_PICK_MS = 380L
private const val TOAST_MS = 3800L

// The slide to the next card as reading aloud goes on by itself: slower than a swipe's, and eased.
private const val GENTLE_SLIDE_MS = 700
