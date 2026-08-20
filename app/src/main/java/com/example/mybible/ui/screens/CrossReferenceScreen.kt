package com.example.mybible.ui.screens

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.BackTopBar

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

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScrollIndex,
        initialFirstVisibleItemScrollOffset = savedScrollOffset
    )

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
            // the list below.
            if (sourceVerse != null) {
                val verse = sourceVerse!!
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "${verse.book} ${verse.chapter}:${verse.number}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = verse.text,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
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
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(crossReferences!!) { item ->
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
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.targetBook} ${item.targetChapter}:${item.targetVerse}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Navigate",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.previewText,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Serif,
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
