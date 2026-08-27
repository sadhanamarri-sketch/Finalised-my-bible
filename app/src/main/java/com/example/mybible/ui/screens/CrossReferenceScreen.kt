package com.example.mybible.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.theme.WorkSansFontFamily

/**
 * Full-page cross-reference list, replacing the old CrossReferenceSheet
 * bottom sheet. Deliberately mirrors SearchScreen's shape: a Scaffold +
 * BackTopBar page (not a modal), results persisted in the ViewModel across
 * the trip to Reader and back (see crossReferenceList / _searchResults for
 * the equivalent), and a "return to [this page]" banner in Reader instead
 * of the old xrefHistory "back to base verse" breadcrumb stack — tapping a
 * reference now returns to this list, not straight back to the base verse.
 */
@Composable
fun CrossReferenceScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val sourceVerse by viewModel.crossReferenceSourceVerse.collectAsState()
    val crossReferences by viewModel.crossReferenceList.collectAsState()
    val savedScrollIndex by viewModel.crossReferenceScrollIndex.collectAsState()
    val savedScrollOffset by viewModel.crossReferenceScrollOffset.collectAsState()
    val lastTappedKey by viewModel.crossReferenceLastTappedKey.collectAsState()

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScrollIndex,
        initialFirstVisibleItemScrollOffset = savedScrollOffset
    )

    // Marks the last-tapped reference with an accent bar on return, so the
    // user can spot which one they already followed — cleared on the first
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
                    viewModel.clearCrossReferenceLastTapped()
                }
            }
    }

    // CrossReferenceScreen is fully disposed (not just hidden) when the
    // user switches tabs — save scroll position on the way out so "return
    // to cross references" lands back in the same spot. Same pattern as
    // SearchScreen.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveCrossReferenceScrollPosition(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Cross References",
                onBack = {
                    viewModel.endCrossReferenceSession()
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
            // Base verse — the verse these cross-references were opened
            // from, pinned at the top so it stays visible while scrolling
            // the list below. A coral wash (not the plain surface every
            // other card here uses) distinguishes it at a glance as the
            // source, not just another reference — idea #1's accent bar
            // would be redundant on this same card, so a tint instead of a
            // bar. The reference used to split book (coral) from
            // chapter:verse (gold); unified to one color since that split
            // read as arbitrary rather than meaningful.
            if (sourceVerse != null) {
                val verse = sourceVerse!!
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "${verse.book.uppercase()} ${verse.chapter}:${verse.number}",
                            fontSize = 13.sp,
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = verse.text,
                            fontSize = 17.sp,
                            fontFamily = com.example.mybible.ui.theme.SourceSerif4FontFamily,
                            lineHeight = 29.07.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            when {
                crossReferences == null -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator()
                    }
                }
                crossReferences!!.isEmpty() -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "No cross references found for this verse",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = WorkSansFontFamily,
                            letterSpacing = 0.sp,
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    // Elevated Cards — matches Search/Notes/Highlighted
                    // Verses.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(crossReferences!!) { item ->
                            val itemKey = "${item.targetBook}:${item.targetChapter}:${item.targetVerse}"
                            val isLastTapped = itemKey == lastTappedKey
                            Card(
                                onClick = {
                                    viewModel.navigateToCrossReference(
                                        item.targetBook,
                                        item.targetChapter,
                                        item.targetVerse
                                    )
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Same left-edge accent bar as Reader's
                                // highlight style (idea #1) — height(Min) is
                                // required for the bar's fillMaxHeight to
                                // have anything bounded to fill, since this
                                // Row sits in a wrap-content Card.
                                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                    // Fades out slowly rather than an instant
                                    // on/off — appears immediately on landing
                                    // (enter = None) but eases out over a full
                                    // second once cleared (see
                                    // clearCrossReferenceLastTapped's caller),
                                    // giving the eye time to register which
                                    // card it was before it's gone.
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
                                                text = "${item.targetBook} ${item.targetChapter}:${item.targetVerse}".uppercase(),
                                                fontSize = 13.sp,
                                                fontFamily = WorkSansFontFamily,
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
                                            text = item.previewText,
                                            fontSize = 17.sp,
                                            fontFamily = com.example.mybible.ui.theme.SourceSerif4FontFamily,
                                            lineHeight = 29.07.sp,
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
    }
}
