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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.mybible.MainActivity
import com.example.mybible.R

/**
 * Compact home screen widget: a fixed 4x1 footprint holding only the
 * "Continue Reading" pill, sized to fill the whole widget. A separate,
 * fixed-size sibling to [VerseOfDayWidget] (4x2, with the Verse of the Day
 * card and quick-action icon row) rather than a resize target of it — a
 * live drag-resize of a Glance widget only recomposes once the drag is
 * released, and the launcher's live-crop preview mid-drag made an
 * in-place "shrink to 4x1" experience look broken no matter the layout.
 * Picking this widget from the widget picker instead gives a fixed,
 * reliable 4x1 footprint from the start.
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

@Composable
private fun ContinueReadingContent(
    palette: WidgetPalette,
    lastBook: String,
    lastChapter: Int
) {
    val context = LocalContext.current
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
                .fillMaxSize()
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
                    )
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
    }
}

/** Standard Glance receiver — this is the class declared in the manifest. */
class ContinueReadingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueReadingWidget()
}
