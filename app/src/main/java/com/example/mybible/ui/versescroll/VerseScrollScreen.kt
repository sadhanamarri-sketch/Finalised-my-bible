package com.example.mybible.ui.versescroll

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.mybible.model.HIGHLIGHT_COLOR_DEFS
import com.example.mybible.model.HighlightItem
import com.example.mybible.model.NoteItem
import com.example.mybible.model.Verse
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.VerseActionToolbar
import com.example.mybible.ui.theme.WorkSansFontFamily
import com.example.mybible.versescroll.CheckInFeedCard
import com.example.mybible.versescroll.FeedMode
import com.example.mybible.versescroll.VerseFeedCard
import com.example.mybible.versescroll.VerseRef
import com.example.mybible.versescroll.VerseScrollController
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
     * Over a painted scene the quieter text is the main ink at 80%, and the gold is brighter (darker in
     * light themes) — the colors the scenes' dimming was measured against.
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
 * than a social feed. Discover deals widely referenced verses from across the Bible over painted,
 * gently moving scenes; double-tap highlights, long-press opens the full verse sheet, and the bottom
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
    val scenes = controller.paintedScenes
    val motion = scenes && controller.motion && !reduceMotion
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val highlightByKey = remember(highlights) { highlights.associateBy { VerseRef(it.book, it.chapter, it.verse).key } }
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
    var pickerMillis by remember { mutableLongStateOf(PICKER_MS) }
    var toast by remember { mutableStateOf<VsToast?>(null) }
    var sheetCard by remember { mutableStateOf<VerseFeedCard?>(null) }
    var linksCard by remember { mutableStateOf<Pair<Int, VerseFeedCard>?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val feedAlpha = remember { Animatable(1f) }
    val sweeps = remember { mutableStateMapOf<Long, Int>() }

    fun showToast(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        toast = VsToast(System.nanoTime(), message, action, onAction)
    }
    fun armPicker(ms: Long = PICKER_MS) {
        pickerMillis = ms
        pickerArm++
    }
    fun showPicker(index: Int) {
        toast = null
        pickerIndex = index
        armPicker()
    }
    fun hidePicker() {
        pickerIndex = -1
    }
    LaunchedEffect(pickerIndex, pickerArm) {
        if (pickerIndex >= 0) {
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

    // ---- highlights (stored per verse, the same highlights as everywhere else in the app) ----
    fun colorOf(card: VerseFeedCard): String? {
        val hexes = card.content.refs.map { highlightByKey[it.key]?.colorHex }
        val first = hexes.first() ?: return null
        return if (hexes.all { it == first }) first else null
    }
    fun setColor(card: VerseFeedCard, refs: List<VerseRef>, hex: String) {
        refs.forEach { viewModel.setHighlightColor(it.book, it.chapter, it.verse, hex) }
        controller.noteHighlighted(refs.map { it.key }, on = true)
        sweeps[card.uid] = (sweeps[card.uid] ?: 0) + 1
    }
    fun removeColor(card: VerseFeedCard) {
        val previous: List<HighlightItem> = card.content.refs.mapNotNull { highlightByKey[it.key] }
        if (previous.isEmpty()) return
        previous.forEach { viewModel.setHighlightColor(it.book, it.chapter, it.verse, "") }
        controller.noteHighlighted(previous.map { VerseRef(it.book, it.chapter, it.verse).key }, on = false)
        showToast("Highlight removed", "Undo") {
            previous.forEach { viewModel.restoreHighlight(it) }
            controller.noteHighlighted(previous.map { VerseRef(it.book, it.chapter, it.verse).key }, on = true)
        }
    }
    fun doubleTap(index: Int, card: VerseFeedCard): String {
        controller.dismissHint()
        val missing = card.content.refs.filter { highlightByKey[it.key] == null }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (missing.isEmpty()) {
            // Already highlighted: the highlight sweeps in again, and the color box offers a change.
            sweeps[card.uid] = (sweeps[card.uid] ?: 0) + 1
            showPicker(index)
            return highlightByKey[card.content.refs.first().key]?.colorHex ?: controller.doubleTapColor
        }
        val color = controller.doubleTapColor
        setColor(card, missing, color)
        showPicker(index)
        return color
    }
    fun asVerse(card: VerseFeedCard): Verse {
        val line = card.content.lines.first()
        return Verse(card.content.ref.book, card.content.ref.chapter, line.number, line.text, teluguText = line.telugu)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        val cards = controller.cards
        key(controller.feedId) {
            val pagerState = rememberPagerState(initialPage = controller.currentIndex) { cards.size }
            // The controller asks to move on after following a link or leaving a Rabbit hole.
            val request = controller.scrollRequest
            LaunchedEffect(request) {
                if (request == null) return@LaunchedEffect
                if (request.index < cards.size) pagerState.animateScrollToPage(request.index)
                controller.consumeScrollRequest()
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
                snapshotFlow { pagerState.settledPage to cards.size }.collect { (page, size) ->
                    if (page < size) controller.onCardShown(page)
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
                            highlightHex = { highlightByKey[it.key]?.colorHex },
                            colorLabel = ::labelOf,
                            notePreview = firstNote?.let(::notePreview),
                            sweepToken = sweeps[card.uid] ?: 0,
                            onPress = { hidePicker() },
                            onDoubleTap = { doubleTap(page, card) },
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
                            onBackToDiscover = {
                                if (controller.leaveRabbitHole(toRoot = false, advance = true) != null) showToast("Back to Discover")
                            }
                        )
                    }
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
                onBack = {
                    if (inRabbitHole) {
                        leave(toRoot = true) { root -> if (root != null) "Out of the Rabbit hole \u00b7 back at $root" else "Out of the Rabbit hole" }
                    } else {
                        viewModel.closeVerseScroll()
                    }
                },
                onDiscover = {
                    if (inRabbitHole) leave(toRoot = false) { "Discover \u00b7 widely-referenced verses from across the Bible" }
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
                    highlight = colorOf(verseCard)?.let { BarHighlight(it, labelOf(it)) },
                    linkCount = verseCard.content.linkCount,
                    onHighlight = {
                        // Highlights if needed, then opens the color box, where Remove lives.
                        val missing = verseCard.content.refs.filter { highlightByKey[it.key] == null }
                        if (colorOf(verseCard) == null && missing.isNotEmpty()) setColor(verseCard, missing, controller.doubleTapColor)
                        showPicker(pagerState.settledPage)
                    },
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
                        current = colorOf(boxCard),
                        onTouch = { armPicker() },
                        onPick = { hex ->
                            setColor(boxCard, boxCard.content.refs, hex)
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
        val highlight = highlightByKey[card.content.ref.key]
        VerseActionToolbar(
            verse = verse,
            highlightColorDefs = colorDefs,
            currentHighlightColorHex = highlight?.colorHex,
            onSetHighlight = { hex ->
                if (hex.isEmpty()) removeColor(card) else setColor(card, card.content.refs, hex)
            },
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
            onAddQuickNote = { text ->
                val hex = highlight?.colorHex ?: controller.doubleTapColor
                viewModel.addHighlightQuickNote(
                    verse.book, verse.chapter, verse.number, verse.text, hex, labelOf(hex), text
                )
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
            highlightHex = { highlightByKey[it.key]?.colorHex },
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

    if (settingsOpen) {
        VerseScrollSettingsSheet(
            colors = colors,
            defs = colorDefs,
            paintedScenes = controller.paintedScenes,
            motion = controller.motion,
            reduceMotion = reduceMotion,
            doubleTapColor = controller.doubleTapColor,
            showTelugu = controller.showTelugu,
            sceneCount = controller.sceneCount,
            onPaintedScenes = controller::updatePaintedScenes,
            onMotion = controller::updateMotion,
            onDoubleTapColor = controller::updateDoubleTapColor,
            onShowTelugu = controller::updateShowTelugu,
            onDismiss = { settingsOpen = false }
        )
    }
}

// The color box stays up this long after a double-tap, and closes this soon after a pick.
private const val PICKER_MS = 6000L
private const val PICKER_AFTER_PICK_MS = 380L
private const val TOAST_MS = 3800L
