package com.example.mybible.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.mybible.MainActivity
import com.example.mybible.R
import com.example.mybible.ui.NavTab

/**
 * Home screen widget: resizable quick-access card, 4x2 by default and
 * shrinkable down to 4x1. Redesigned from the original Verse-of-the-Day
 * layout to mirror the "Continue Reading" pill + 4-icon quick-action row
 * pattern (Highlights / Studied / Notes / Search), styled to match the
 * app's Classic Dark theme.
 *
 * At 4x1 there's only room for one row, so both the Verse of the Day card
 * and the quick-action icon row are dropped (see [WidgetContent]'s
 * `isCompact` branch) and the Continue Reading pill alone grows to fill
 * the widget.
 *
 * [VerseOfDayRepository]/[VerseOfDayData] are still used for the 4x2 verse
 * card, but are skipped entirely when rendering the compact 4x1 layout.
 */
class VerseOfDayWidget : GlanceAppWidget() {

    // SizeMode.Responsive with only two sizes is unreliable here: hosts can
    // treat a 2-entry set as a landscape/portrait pair rather than picking
    // by actual widget bounds, so live drag-resizing (4x2 -> 4x1) may never
    // switch content. Exact ties LocalSize.current to the widget's real,
    // live measured size (via onAppWidgetOptionsChanged, handled internally
    // by Glance) and recomposes on every resize, which is what the
    // isCompact branch in WidgetContent below actually needs.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = WidgetColors.forCurrentTheme(context)
        val prefs = context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
        val lastBook = prefs.getString("last_book", "Genesis") ?: "Genesis"
        val lastChapter = prefs.getInt("last_chapter", 1)
        val todayVerse = VerseOfDayRepository.verseForToday()

        provideContent {
            WidgetContent(
                palette = palette,
                lastBook = lastBook,
                lastChapter = lastChapter,
                verse = todayVerse
            )
        }
    }
}

@Composable
private fun WidgetContent(
    palette: WidgetPalette,
    lastBook: String,
    lastChapter: Int,
    verse: VerseOfDay
) {
    val context = LocalContext.current
    // sizeMode = SizeMode.Exact (above) keeps LocalSize.current in sync with
    // the widget's real, live measured height as the user drags it — a 4x2
    // widget is ~110dp+ tall (minHeight), a 4x1 resize is ~40dp tall
    // (minResizeHeight, in verse_of_day_widget_info.xml), so 80dp sits
    // cleanly between the two. Below that threshold there isn't room for
    // the Verse-of-the-Day card or the quick-action icon row, so both are
    // dropped entirely and the Continue Reading row alone grows
    // (defaultWeight + larger type/padding below) to fill the whole widget
    // instead of leaving dead space or clipping.
    val isCompact = LocalSize.current.height < 80.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .cornerRadius(24.dp)
            .padding(14.dp)
    ) {
        if (!isCompact) {
            // --- 1. Verse of the Day (Top - expands naturally to fill top area) ---
            // Outer box paints the border color; a 1dp inset reveals it as a
            // ring around the inner card (Glance has no Modifier.border()).
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(palette.cardBorder)
                    .cornerRadius(19.dp)
                    .padding(1.dp)
            ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(palette.buttonBackground)
                    .cornerRadius(18.dp)
                    .padding(14.dp)
                    .clickable(
                        actionStartActivity(
                            Intent(context, MainActivity::class.java)
                                .putExtra(WidgetActionKeys.EXTRA_VERSE_BOOK, verse.book)
                                .putExtra(WidgetActionKeys.EXTRA_VERSE_CHAPTER, verse.chapter)
                                .putExtra(WidgetActionKeys.EXTRA_VERSE_VERSE, verse.verse)
                        )
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.fillMaxWidth()
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_sparkle),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(palette.accent),
                        modifier = GlanceModifier.size(18.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = "VERSE OF THE DAY",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = palette.accent
                        )
                    )
                    Text(
                        text = " • ${verse.reference}",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = palette.buttonText
                        ),
                        maxLines = 1
                    )
                }
                Spacer(modifier = GlanceModifier.height(8.dp))
                // Vertically centers the quote in whatever space is left under
                // the header — verses range from ~39 to ~185 characters (see
                // VerseOfDayRepository), so a fixed top-aligned Text left a
                // visible dead gap under short verses while the card's height
                // stayed constant (driven by the outer Column's defaultWeight,
                // which fills the widget's fixed layout regardless of content).
                // Centering means short verses just sit centered — which reads
                // as deliberate — instead of glued to the top with empty space
                // below; long verses still get the full available height.
                Box(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "\"${verse.text}\"",
                        style = TextStyle(
                            fontSize = 12.5.sp,
                            color = palette.buttonText
                        ),
                        maxLines = 4
                    )
                }
            }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))
        }

        // --- 2. Continue Reading {chapter name, chapter number} ---
        // Compact (4x1, no verse card above and no icon row below) grows to
        // fill the entire remaining vertical space, with larger icon/type
        // to match.
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .then(if (isCompact) GlanceModifier.defaultWeight() else GlanceModifier)
                .background(palette.cardBorder)
                .cornerRadius(17.dp)
                .padding(1.dp)
        ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .then(if (isCompact) GlanceModifier.fillMaxHeight() else GlanceModifier)
                .background(palette.buttonBackground)
                .cornerRadius(16.dp)
                .padding(
                    horizontal = if (isCompact) 16.dp else 13.dp,
                    vertical = if (isCompact) 14.dp else 8.dp
                )
                .clickable(
                    actionStartActivity(
                        Intent(context, MainActivity::class.java)
                            .putExtra(WidgetActionKeys.EXTRA_CONTINUE_READING, true)
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_book),
                contentDescription = null,
                colorFilter = ColorFilter.tint(palette.accent),
                modifier = GlanceModifier.size(if (isCompact) 26.dp else 18.dp)
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Text(
                text = "Continue reading",
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = if (isCompact) 17.sp else 13.sp,
                    color = palette.buttonText
                )
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "$lastBook $lastChapter",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isCompact) 17.sp else 13.sp,
                    color = palette.accent
                ),
                maxLines = 1
            )
        }
        }

        if (!isCompact) {
            Spacer(modifier = GlanceModifier.height(10.dp))

            // --- 3. Big Quick-Action Circular Buttons (Claude Widget Style) ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionButton(
                    iconRes = R.drawable.ic_widget_highlight,
                    contentDescription = "Highlights",
                    tab = NavTab.HIGHLIGHTS,
                    palette = palette,
                    modifier = GlanceModifier.defaultWeight()
                )
                QuickActionButton(
                    iconRes = R.drawable.ic_widget_check,
                    contentDescription = "Studied",
                    tab = NavTab.STUDIED,
                    palette = palette,
                    modifier = GlanceModifier.defaultWeight()
                )
                QuickActionButton(
                    iconRes = R.drawable.ic_widget_notes,
                    contentDescription = "Notes",
                    tab = NavTab.NOTES,
                    palette = palette,
                    modifier = GlanceModifier.defaultWeight()
                )
                QuickActionButton(
                    iconRes = R.drawable.ic_widget_search,
                    contentDescription = "Search",
                    tab = NavTab.SEARCH,
                    palette = palette,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    iconRes: Int,
    contentDescription: String,
    tab: NavTab,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = GlanceModifier
                .size(52.dp)
                .background(palette.cardBorder)
                .cornerRadius(26.dp)
                .padding(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(palette.buttonBackground)
                .cornerRadius(25.dp)
                .clickable(
                    actionStartActivity(
                        Intent(context, MainActivity::class.java)
                            .putExtra(WidgetActionKeys.EXTRA_OPEN_TAB, tab.name)
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = contentDescription,
                colorFilter = ColorFilter.tint(palette.buttonText),
                modifier = GlanceModifier.size(24.dp)
            )
        }
        }
    }
}

/** Standard Glance receiver — this is the class declared in the manifest. */
class VerseOfDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VerseOfDayWidget()
}
