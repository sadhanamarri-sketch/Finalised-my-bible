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
 * Home screen widget: a fixed-width 4-column widget whose default footprint
 * is a single row (4x1) holding just the "Continue Reading" pill — kept as
 * the sole focus at that size since it's meant to attract attention, not
 * compete with icons for it. Dragging it taller (roughly 4x2 and up)
 * reveals the Highlights / Studied / Notes / Search quick-action row below
 * the pill, mirroring [VerseOfDayWidget]'s 4x2 icon row.
 *
 * sizeMode = SizeMode.Exact ties [LocalSize] to the widget's real, live
 * measured size and recomposes on every resize (Glance handles
 * onAppWidgetOptionsChanged internally), so the isExpanded branch in
 * [ContinueReadingContent] responds to the user actually dragging the
 * widget taller.
 */
class ContinueReadingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = WidgetColors.forCurrentTheme(context)
        val prefs = context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
        val lastBook = prefs.getString("last_book", "Genesis") ?: "Genesis"
        val lastChapter = prefs.getInt("last_chapter", 1)

        provideContent {
            ContinueReadingContent(
                palette = palette,
                lastBook = lastBook,
                lastChapter = lastChapter
            )
        }
    }
}

@Composable
private fun ContinueReadingContent(
    palette: WidgetPalette,
    lastBook: String,
    lastChapter: Int
) {
    val context = LocalContext.current
    // Default footprint is ~40dp tall (1 row); 4x2 and up is ~110dp+ (see
    // continue_reading_widget_info.xml), so 80dp cleanly separates the two.
    val isExpanded = LocalSize.current.height >= 80.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .cornerRadius(24.dp)
            .padding(10.dp)
    ) {
        // Outer box paints the border color; a 1dp inset reveals it as a
        // ring around the inner pill (Glance has no Modifier.border()) —
        // same trick used by the Continue Reading row on VerseOfDayWidget.
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .then(if (isExpanded) GlanceModifier.defaultWeight() else GlanceModifier.fillMaxHeight())
                .background(palette.cardBorder)
                .cornerRadius(19.dp)
                .padding(1.dp)
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(palette.buttonBackground)
                    .cornerRadius(18.dp)
                    .padding(horizontal = 16.dp)
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
                    modifier = GlanceModifier.size(26.dp)
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
                Text(
                    text = "Continue reading",
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                        color = palette.buttonText
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "$lastBook $lastChapter",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = palette.accent
                    ),
                    maxLines = 1
                )
            }
        }

        if (isExpanded) {
            Spacer(modifier = GlanceModifier.height(10.dp))

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

/**
 * Quick-action circular icon button for the expanded (4x2+) layout. Same
 * bordered-circle look as VerseOfDayWidget's QuickActionButton, sized down
 * slightly (44dp vs 52dp) since this widget's pill above already claims a
 * chunk of the vertical space a fresh 4x2 drag grants.
 */
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
                .size(44.dp)
                .background(palette.cardBorder)
                .cornerRadius(22.dp)
                .padding(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(palette.buttonBackground)
                    .cornerRadius(21.dp)
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
                    modifier = GlanceModifier.size(20.dp)
                )
            }
        }
    }
}

/** Standard Glance receiver — this is the class declared in the manifest. */
class ContinueReadingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueReadingWidget()
}
