package com.example.mybible.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.TopicItem
import com.example.mybible.model.TopicRow
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.OpenTopic
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.theme.SourceSerif4FontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily

/**
 * A Nave's Topical Bible topic, opened from Search's topic cards: its main headings, each folding
 * open to its verses and subheadings, and its "see" links to other topics. A verse opens in the
 * Reader, whose banner comes back here; back closes the topic, onto the one under it or the
 * results.
 */
@Composable
fun TopicScreen(topic: OpenTopic, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val page = topic.page
    val lastTappedKey by viewModel.searchLastTappedKey.collectAsState()
    // Keyed by topic: a link opens another one in this same spot, with its own place in its list.
    val listState = remember(page.topicId) { LazyListState(topic.scrollIndex, topic.scrollOffset) }

    BackHandler { viewModel.closeTopic() }

    DisposableEffect(page.topicId) {
        onDispose {
            viewModel.saveTopicScrollPosition(page.topicId, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }

    // Opened at a heading ("forgive enemies": Of enemies): bring it to the top, once.
    LaunchedEffect(page.topicId, topic.focusSection) {
        val focus = topic.focusSection ?: return@LaunchedEffect
        var index = 1 // after the subtitle
        for (item in page.items) {
            if (item is TopicItem.Section && item.index == focus) break
            index += itemCount(item, topic.expanded)
        }
        listState.scrollToItem(index)
        viewModel.consumeTopicFocus()
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = page.name,
                onBack = { viewModel.closeTopic() },
                backContentDescription = "Back to search results"
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item(key = "subtitle") {
                Text(
                    text = "Nave’s Topical Bible · " +
                        if (page.referenceCount == 1) "1 reference" else "${page.referenceCount} references",
                    fontSize = 13.sp,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
                )
            }
            for ((i, item) in page.items.withIndex()) {
                when (item) {
                    is TopicItem.Link -> item(key = "link-$i") {
                        LinkRow(level = 0, text = item.text) { viewModel.openTopic(item.topicId, item.section) }
                    }
                    is TopicItem.Section -> section(item, item.index in topic.expanded, lastTappedKey, viewModel)
                }
            }
            item(key = "credit") {
                Text(
                    text = "Nave’s Topical Bible (public domain), as structured by BibleData, Brady Stephenson (CC BY 4.0).",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

// How many list items an item of the page takes: what scrolling to a section counts past.
private fun itemCount(item: TopicItem, expanded: Set<Int>): Int = when (item) {
    is TopicItem.Link -> 1
    is TopicItem.Section -> (if (item.label.isNotBlank()) 1 else 0) +
        (if (item.label.isBlank() || item.index in expanded) item.rows.size else 0)
}

private fun LazyListScope.section(
    section: TopicItem.Section,
    expanded: Boolean,
    lastTappedKey: String?,
    viewModel: MainViewModel
) {
    if (section.label.isNotBlank()) {
        item(key = "section-${section.index}") {
            SectionHeading(section, expanded) { viewModel.toggleTopicSection(section.index) }
        }
    }
    // A section without a heading of its own can't fold.
    if (!expanded && section.label.isNotBlank()) return
    itemsIndexed(section.rows, key = { i, _ -> "section-${section.index}-$i" }) { _, row ->
        when (row) {
            is TopicRow.Heading -> SubHeading(row)
            is TopicRow.Reference -> {
                val key = "${row.verse.book}:${row.verse.chapter}:${row.verse.number}"
                ReferenceRow(row, isLastTapped = key == lastTappedKey) { viewModel.openSearchResult(row.verse) }
            }
            is TopicRow.Link -> LinkRow(level = row.level, text = row.text) { viewModel.openTopic(row.topicId, row.section) }
        }
    }
}

@Composable
private fun SectionHeading(section: TopicItem.Section, expanded: Boolean, onToggle: () -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = section.label,
                fontSize = 15.5.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = WorkSansFontFamily,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (section.referenceCount > 0) {
                Text(
                    text = "${section.referenceCount}",
                    fontSize = 13.sp,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Fold" else "Unfold",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(22.dp)
            )
        }
    }
}

@Composable
private fun SubHeading(row: TopicRow.Heading) {
    Text(
        text = row.label,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = WorkSansFontFamily,
        letterSpacing = 0.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = indent(row.level), end = 16.dp, top = 10.dp, bottom = 2.dp)
    )
}

// Nave's nesting, as an indent: a main heading's own verses at the margin, a subheading's under it.
private fun indent(level: Int) = (16 + 14 * level.coerceAtMost(3)).dp

@Composable
private fun ReferenceRow(row: TopicRow.Reference, isLastTapped: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
    ) {
        // The verse opened last, marked like Search's results on the way back.
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (isLastTapped) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
        )
        Column(modifier = Modifier.padding(start = indent(row.level) - 3.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
            Text(
                text = row.label.uppercase(),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = WorkSansFontFamily,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = row.preview,
                fontSize = 15.5.sp,
                lineHeight = 24.sp,
                fontFamily = SourceSerif4FontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LinkRow(level: Int, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = indent(level), end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            fontSize = 14.sp,
            fontFamily = WorkSansFontFamily,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
