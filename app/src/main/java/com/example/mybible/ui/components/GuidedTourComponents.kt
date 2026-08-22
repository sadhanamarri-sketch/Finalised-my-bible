package com.example.mybible.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.ui.NavTab

/**
 * One stop on the guided tour — [tab] is switched to (via MainActivity's
 * LaunchedEffect on the current step) so the user sees the real screen the
 * step is describing, not an icon standing in for it. Deliberately does
 * NOT try to spotlight/highlight an individual button or row within that
 * screen — reliably positioning a cutout over an arbitrary live composable
 * across half a dozen different screens would need onGloballyPositioned
 * plumbing threaded through all of them, for a payoff (a highlighted
 * rectangle) this bottom-card + real-screen-behind-it approach already
 * delivers on: "where do I find this," not "which exact pixel is it."
 */
data class TourStep(
    val tab: NavTab,
    val title: String,
    val description: String
)

/** The 6 essentials — one per real destination tab, each explaining just
 *  enough to get started. */
val CURATED_TOUR_STEPS: List<TourStep> = listOf(
    TourStep(
        NavTab.READER,
        "Reading",
        "This is where you'll spend most of your time. Swipe left or right to move between chapters, and tap the book name at the top to jump anywhere in Scripture."
    ),
    TourStep(
        NavTab.READER,
        "Tap a verse",
        "Tap any verse to highlight it, jot down a quick note, see its cross references, or open a Greek/Hebrew word breakdown."
    ),
    TourStep(
        NavTab.HIGHLIGHTS,
        "Highlighted Verses",
        "Every verse you highlight shows up here, organized by color, with any notes you attached right underneath."
    ),
    TourStep(
        NavTab.NOTES,
        "Notes",
        "Your study notes live here \u2014 tagged, dated, and linked back to the verses that inspired them."
    ),
    TourStep(
        NavTab.SEARCH,
        "Search",
        "Look up a word or phrase, or type a reference directly, like \u201cJohn 3:16\u201d, to jump straight there."
    ),
    TourStep(
        NavTab.SETTINGS,
        "Settings",
        "Fonts, themes, reading reminders, and backups all live here \u2014 feel free to make this app your own."
    )
)

/** Curated core plus deeper-cut features, still one step per idea rather
 *  than trying to choreograph a trip into a sub-screen (Tags, Note Editor,
 *  Manage Highlight Colors, etc.) blind. */
val FULL_TOUR_STEPS: List<TourStep> = CURATED_TOUR_STEPS + listOf(
    TourStep(
        NavTab.READER,
        "Focus mode",
        "Turn on \u201cBlur unread verses\u201d in Settings to keep your eyes on the verse you're reading, one at a time, without the rest of the page pulling your attention ahead."
    ),
    TourStep(
        NavTab.STUDIED,
        "Studied",
        "Track your reading progress book by book, across the whole Bible \u2014 Old and New Testament."
    ),
    TourStep(
        NavTab.NOTES,
        "Tags",
        "Give your notes tags \u2014 like \u201cPrayer\u201d or \u201cPromise\u201d \u2014 so you can filter down to exactly the ones you're looking for later."
    ),
    TourStep(
        NavTab.SETTINGS,
        "Reminders",
        "Turn on reading reminders for a few gentle nudges a day \u2014 the frequency, active hours, and which message themes rotate are all yours to set."
    ),
    TourStep(
        NavTab.SETTINGS,
        "Backup & sync",
        "Back up your notes, highlights, and progress to a local file or Google Drive, so nothing is ever lost switching devices."
    )
)

/**
 * Upfront choice shown before the tour starts (see MainViewModel.TourMode.
 * CHOOSING) — "curated" picks [CURATED_TOUR_STEPS], "everything" picks
 * [FULL_TOUR_STEPS]. Dismissing (tapping outside, or system back) skips
 * the tour entirely rather than defaulting to either variant.
 */
@Composable
fun TourChoiceDialog(
    onChooseCurated: () -> Unit,
    onChooseEverything: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Welcome to My Bible") },
        text = {
            Text(
                "Want the quick tour of the essentials, or the full walkthrough of everything the app can do? " +
                    "Either way, you can always come back to the full tour later from Settings."
            )
        },
        confirmButton = {
            Button(onClick = onChooseEverything) { Text("Everything") }
        },
        dismissButton = {
            TextButton(onClick = onChooseCurated) { Text("Curated core") }
        }
    )
}

/** Shown once, right after a curated-only tour ends — see
 *  MainViewModel.tourJustFinishedCurated's doc for why this doesn't also
 *  fire after the full tour or a skip. */
@Composable
fun TourCuratedEndDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("That's the essentials!") },
        text = { Text("There's more to explore whenever you're ready \u2014 the full detailed tour is always available from Settings.") },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Got it") }
        }
    )
}

/**
 * The tour itself: a light scrim over whatever's really on screen (Reader,
 * Highlighted Verses, etc. — MainActivity switches the actual tab to match
 * [stepIndex] before rendering this) plus a bottom card with the current
 * step's title/description, progress dots, and Back/Next/Skip — tap-through
 * only, no auto-advance timer.
 */
@Composable
fun GuidedTourOverlay(
    steps: List<TourStep>,
    stepIndex: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val step = steps[stepIndex]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            // Absorbs every touch in the scrim area so the real screen
            // underneath (visible, but only as a "here's where this is"
            // backdrop) can't be tapped mid-tour — indication = null since
            // this isn't a real button, just a touch sink.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    steps.indices.forEach { idx ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (idx == stepIndex) 8.dp else 5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (idx <= stepIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
                Text(
                    text = step.title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = step.description,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onSkip) { Text("Skip") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (stepIndex > 0) {
                            TextButton(onClick = onBack) { Text("Back") }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Button(onClick = onNext) {
                            Text(if (stepIndex == steps.lastIndex) "Done" else "Next")
                        }
                    }
                }
            }
        }
    }
}
