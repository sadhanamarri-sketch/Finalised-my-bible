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
 * reveals a Highlighted / Studied / Notes / Search quick-action row pinned
 * to the bottom edge, fixed at its designed 84dp height regardless of how
 * tall the widget gets dragged — different launchers hand a "4x2" resize
 * very different real heights. The pill fills the rest of the space above
 * that row via a real layout weight (see [ContinueReadingContent]'s
 * cardsRowHeight comment for why weight, not a LocalSize-computed height,
 * is what makes this actually pin to the true bottom edge on every
 * launcher). This is now the app's only home screen widget — a separate
 * fixed-4x2 "verse of the day" widget used to exist, but was deleted once
 * this one's expanded state covered the same ground.
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
    // Measured on-device (see commit history): default 4x1 placement is
    // 416x94dp, resized to 4x2 is 416x188dp — 140dp sits with a healthy
    // ~46dp margin on both sides of that gap.
    val isExpanded = LocalSize.current.height >= 140.dp
    // Quick-action cards below are fixed at this height regardless of how
    // tall the widget gets dragged — 4x2's reference math (188dp widget,
    // minus 2x10dp outer padding, minus the pill's 74dp, minus a 10dp
    // spacer) works out to 84dp, which is what the design was previewed
    // and approved at.
    val cardsRowHeight = 84.dp
    // The pill takes the *rest* via defaultWeight() rather than a height
    // computed from LocalSize — LocalSize reflects Glance's own notion of
    // the widget's size (from appWidgetOptions), which isn't guaranteed to
    // match what the launcher's host view actually renders at on every
    // launcher. A previous version computed the pill's height from
    // LocalSize arithmetic, and on-device that left a gap between the
    // (correctly small) cards row and the widget's real bottom edge —
    // proof the two didn't agree. Real layout weight, by contrast, is
    // resolved against the actual measured RemoteViews container at
    // render time, so it's guaranteed to fill the true leftover space and
    // pin the fixed-height cards row to the true bottom edge, whatever
    // that space really is.
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .cornerRadius(24.dp)
            .padding(10.dp)
    ) {
        // Outer box paints the border color; a 1dp inset reveals it as a
        // ring around the inner pill (Glance has no Modifier.border()).
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
                modifier = GlanceModifier.fillMaxWidth().height(cardsRowHeight)
            ) {
                QuickActionCard(
                    iconRes = R.drawable.ic_widget_highlight,
                    label = "Highlighted",
                    tab = NavTab.HIGHLIGHTS,
                    palette = palette,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                QuickActionCard(
                    iconRes = R.drawable.ic_widget_check,
                    label = "Studied",
                    tab = NavTab.STUDIED,
                    palette = palette,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                QuickActionCard(
                    iconRes = R.drawable.ic_widget_notes,
                    label = "Notes",
                    tab = NavTab.NOTES,
                    palette = palette,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                QuickActionCard(
                    iconRes = R.drawable.ic_widget_search,
                    label = "Search",
                    tab = NavTab.SEARCH,
                    palette = palette,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }
}

/**
 * Quick-action card for the expanded (4x2+) layout — a rectangular, rounded
 * card (same "border ring + filled background" recipe as the pill above,
 * just squarer) rather than the small circular icon buttons this replaced,
 * so the four cards actually claim the vertical space the pill gives up by
 * no longer stretching. Icon and label intentionally share the same muted
 * tone: these are secondary shortcuts, not meant to compete with the pill's
 * accent-colored chapter reference for attention.
 */
@Composable
private fun QuickActionCard(
    iconRes: Int,
    label: String,
    tab: NavTab,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(palette.cardBorder)
            .cornerRadius(20.dp)
            .padding(1.dp)
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(palette.buttonBackground)
                .cornerRadius(19.dp)
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
                contentDescription = label,
                colorFilter = ColorFilter.tint(palette.mutedText),
                modifier = GlanceModifier.size(28.dp)
            )
            Spacer(modifier = GlanceModifier.height(12.dp))
            Text(
                text = label,
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = palette.mutedText
                ),
                maxLines = 1
            )
        }
    }
}

/** Standard Glance receiver — this is the class declared in the manifest. */
class ContinueReadingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueReadingWidget()
}
