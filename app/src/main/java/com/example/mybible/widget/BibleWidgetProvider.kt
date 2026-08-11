package com.example.mybible.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.mybible.MainActivity
import com.example.mybible.R

data class WidgetVerse(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val ref: String
)

class BibleWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_VERSE = "com.example.mybible.ACTION_REFRESH_VERSE"
        private const val PREFS_NAME = "BibleWidgetPrefs"
        private const val KEY_INDEX = "current_verse_index"

        val INSPIRATIONAL_VERSES = listOf(
            WidgetVerse(
                "John", 3, 16,
                "“For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life.”",
                "— John 3:16 (KJV)"
            ),
            WidgetVerse(
                "Psalms", 23, 1,
                "“The LORD is my shepherd; I shall not want. He maketh me to lie down in green pastures: he leadeth me beside the still waters.”",
                "— Psalm 23:1-2 (KJV)"
            ),
            WidgetVerse(
                "Proverbs", 3, 5,
                "“Trust in the LORD with all thine heart; and lean not unto thine own understanding. In all thy ways acknowledge him, and he shall direct thy paths.”",
                "— Proverbs 3:5-6 (KJV)"
            ),
            WidgetVerse(
                "Philippians", 4, 13,
                "“I can do all things through Christ which strengtheneth me.”",
                "— Philippians 4:13 (KJV)"
            ),
            WidgetVerse(
                "Isaiah", 40, 31,
                "“But they that wait upon the LORD shall renew their strength; they shall mount up with wings as eagles; they shall run, and not be weary.”",
                "— Isaiah 40:31 (KJV)"
            ),
            WidgetVerse(
                "Romans", 8, 28,
                "“And we know that all things work together for good to them that love God, to them who are the called according to his purpose.”",
                "— Romans 8:28 (KJV)"
            ),
            WidgetVerse(
                "Joshua", 1, 9,
                "“Have not I commanded thee? Be strong and of a good courage; be not afraid, neither be thou dismayed: for the LORD thy God is with thee.”",
                "— Joshua 1:9 (KJV)"
            ),
            WidgetVerse(
                "Jeremiah", 29, 11,
                "“For I know the thoughts that I think toward you, saith the LORD, thoughts of peace, and not of evil, to give you an expected end.”",
                "— Jeremiah 29:11 (KJV)"
            ),
            WidgetVerse(
                "Matthew", 11, 28,
                "“Come unto me, all ye that labour and are heavy laden, and I will give you rest.”",
                "— Matthew 11:28 (KJV)"
            ),
            WidgetVerse(
                "Psalms", 119, 105,
                "“Thy word is a lamp unto my feet, and a light unto my path.”",
                "— Psalm 119:105 (KJV)"
            ),
            WidgetVerse(
                "2 Corinthians", 5, 17,
                "“Therefore if any man be in Christ, he is a new creature: old things are passed away; behold, all things are become new.”",
                "— 2 Corinthians 5:17 (KJV)"
            ),
            WidgetVerse(
                "Psalms", 46, 1,
                "“God is our refuge and strength, a very present help in trouble.”",
                "— Psalm 46:1 (KJV)"
            )
        )

        fun updateWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, BibleWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                for (appWidgetId in appWidgetIds) {
                    val provider = BibleWidgetProvider()
                    provider.updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_VERSE) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentIndex = prefs.getInt(KEY_INDEX, 0)
            val nextIndex = (currentIndex + 1) % INSPIRATIONAL_VERSES.size
            prefs.edit().putInt(KEY_INDEX, nextIndex).apply()

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, BibleWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val widgetPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val index = widgetPrefs.getInt(KEY_INDEX, 0) % INSPIRATIONAL_VERSES.size
        val verse = INSPIRATIONAL_VERSES[index]

        // Fetch last read position from main app prefs
        val mainPrefs = context.getSharedPreferences("my_bible_prefs", Context.MODE_PRIVATE)
        val lastBook = mainPrefs.getString("last_book", "Genesis") ?: "Genesis"
        val lastChapter = mainPrefs.getInt("last_chapter", 1)

        val views = RemoteViews(context.packageName, R.layout.widget_daily_verse)
        views.setTextViewText(R.id.widget_verse_text, verse.text)
        views.setTextViewText(R.id.widget_verse_reference, verse.ref)
        
        // Dynamic button text linking to last read chapter
        views.setTextViewText(R.id.btn_widget_read_now, "Continue $lastBook $lastChapter ➔")

        // PendingIntent to launch MainActivity and open the exact last read chapter
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_book", lastBook)
            putExtra("widget_chapter", lastChapter)
        }
        val pendingOpenApp = PendingIntent.getActivity(
            context,
            appWidgetId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.btn_widget_read_now, pendingOpenApp)
        views.setOnClickPendingIntent(R.id.widget_verse_click_area, pendingOpenApp)

        // PendingIntent to refresh daily verse
        val refreshIntent = Intent(context, BibleWidgetProvider::class.java).apply {
            action = ACTION_REFRESH_VERSE
        }
        val pendingRefresh = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_refresh, pendingRefresh)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
