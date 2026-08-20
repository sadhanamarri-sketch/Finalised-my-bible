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
 * Compact home screen widget: a fixed 4x1 footprint. The "Continue
 * Reading" pill fills the middle, flanked by two stacked icon columns —
 * Highlights/Notes on the left, Studied/Search on the right — so all four
 * quick actions from [VerseOfDayWidget]'s 4x2 icon row are still reachable
 * in one row of height. A separate, fixed-size sibling to
 * [VerseOfDayWidget] (4x2, with the Verse of the Day card) rather than a
 * resize target of it — a live drag-resize of a Glance widget only
 * recomposes once the drag is released, and the launcher's live-crop
 * preview mid-drag made an in-place "shrink to 4x1" experience look broken
 * no matter the layout. Picking this widget from the widget picker instead
 * gives a fixed, reliable 4x1 footprint from the start.
 */
class ContinueReadingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

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

private val ICON_COLUMN_WIDTH = 34.dp

@Composable
private fun ContinueReadingContent(
    palette: WidgetPalette,
    lastBook: String,
    lastChapter: Int
) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .cornerRadius(24.dp)
            .padding(10.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxHeight().width(ICON_COLUMN_WIDTH)) {
            StackedIconButton(
                iconRes = R.drawable.ic_widget_highlight,
                contentDescription = "Highlights",
                tab = NavTab.HIGHLIGHTS,
                palette = palette,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            StackedIconButton(
                iconRes = R.drawable.ic_widget_notes,
                contentDescription = "Notes",
                tab = NavTab.NOTES,
                palette = palette,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Outer box paints the border color; a 1dp inset reveals it as a
        // ring around the inner pill (Glance has no Modifier.border()) —
        // same trick used by the Continue Reading row on VerseOfDayWidget.
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .background(palette.cardBorder)
                .cornerRadius(19.dp)
                .padding(1.dp)
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(palette.buttonBackground)
                    .cornerRadius(18.dp)
                    .padding(horizontal = 10.dp)
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
                    modifier = GlanceModifier.size(20.dp)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "Continue reading",
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = palette.buttonText
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "$lastBook $lastChapter",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = palette.accent
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.fillMaxHeight().width(ICON_COLUMN_WIDTH)) {
            StackedIconButton(
                iconRes = R.drawable.ic_widget_check,
                contentDescription = "Studied",
                tab = NavTab.STUDIED,
                palette = palette,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            StackedIconButton(
                iconRes = R.drawable.ic_widget_search,
                contentDescription = "Search",
                tab = NavTab.SEARCH,
                palette = palette,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
        }
    }
}

/**
 * Small round quick-action icon, one cell of the stacked left/right
 * columns flanking the Continue Reading pill. Same bordered-circle look as
 * VerseOfDayWidget's QuickActionButton, just sized down to fit two per
 * column within the widget's 4x1 height.
 */
@Composable
private fun StackedIconButton(
    iconRes: Int,
    contentDescription: String,
    tab: NavTab,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier
                .size(28.dp)
                .background(palette.cardBorder)
                .cornerRadius(14.dp)
                .padding(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(palette.buttonBackground)
                    .cornerRadius(13.dp)
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
                    modifier = GlanceModifier.size(15.dp)
                )
            }
        }
    }
}

/** Standard Glance receiver — this is the class declared in the manifest. */
class ContinueReadingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueReadingWidget()
}
