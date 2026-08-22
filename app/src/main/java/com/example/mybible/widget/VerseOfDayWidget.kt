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
 * Home screen widget: fixed 4x2 quick-access card. Redesigned from the
 * original Verse-of-the-Day layout to mirror the "Continue Reading" pill +
 * 4-icon quick-action row pattern (Highlights / Studied / Notes / Search),
 * styled to match the app's Classic Dark theme.
 *
 * [VerseOfDayRepository]/[VerseOfDayData] are no longer used by this widget
 * but are left in place rather than deleted, in case a curated
 * verse-of-the-day surface is wanted elsewhere later.
 */
class VerseOfDayWidget : GlanceAppWidget() {

    // Single fixed layout
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = WidgetColors.forCurrentTheme(context)
        val prefs = context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
        val lastBook = prefs.getString("last_book", "Genesis") ?: "Genesis"
        val lastChapter = prefs.getInt("last_chapter", 1)
        val lastVerse = prefs.getInt("last_verse", -1)
        val todayVerse = VerseOfDayRepository.verseForToday()

        provideContent {
            WidgetContent(
                palette = palette,
                lastBook = lastBook,
                lastChapter = lastChapter,
                lastVerse = lastVerse,
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
    lastVerse: Int,
    verse: VerseOfDay
) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .cornerRadius(24.dp)
            .padding(14.dp)
    ) {
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

        // --- 2. Continue Reading {chapter name, chapter number} ---
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(palette.cardBorder)
                .cornerRadius(17.dp)
                .padding(1.dp)
        ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(palette.buttonBackground)
                .cornerRadius(16.dp)
                .padding(horizontal = 13.dp, vertical = 8.dp)
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
                modifier = GlanceModifier.size(18.dp)
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Text(
                text = "Continue reading",
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = palette.buttonText
                )
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                // Verse suffix is temporary diagnostics for the widget
                // exact-resume bug — see the matching comment in
                // ContinueReadingWidget. Remove once resolved.
                text = if (lastVerse > 0) "$lastBook $lastChapter:$lastVerse" else "$lastBook $lastChapter",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = palette.accent
                ),
                maxLines = 1
            )
        }
        }

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
