package com.example.mybible.widget

import androidx.glance.action.ActionParameters

/**
 * Keys used on the Intent extras Glance attaches when actionStartActivity
 * fires. MainActivity must check for these (see MainActivity wiring note
 * in the README) and route accordingly:
 *   - EXTRA_WIDGET_VERSE_BOOK/CHAPTER/VERSE present -> navigate reader there
 *   - EXTRA_WIDGET_CONTINUE present -> resume last read position (the app's
 *     normal default behavior on launch already does this in most cases;
 *     this extra just makes the intent explicit / forces it)
 */
object WidgetActionKeys {
    val VerseBook = ActionParameters.Key<String>("widget_verse_book")
    val VerseChapter = ActionParameters.Key<Int>("widget_verse_chapter")
    val VerseVerse = ActionParameters.Key<Int>("widget_verse_verse")
    val ContinueReading = ActionParameters.Key<Boolean>("widget_continue_reading")

    // Value is a NavTab enum name (e.g. "HIGHLIGHTS", "STUDIED", "NOTES",
    // "SEARCH") — used by the quick-access icon row on the home screen
    // widget to jump straight to that tab.
    val OpenTab = ActionParameters.Key<String>("widget_open_tab")

    // Plain Intent extra names (same string values as the ActionParameters
    // keys above) — this is what actually shows up on intent.extras in
    // MainActivity, since Glance serializes ActionParameters as extras.
    const val EXTRA_VERSE_BOOK = "widget_verse_book"
    const val EXTRA_VERSE_CHAPTER = "widget_verse_chapter"
    const val EXTRA_VERSE_VERSE = "widget_verse_verse"
    const val EXTRA_CONTINUE_READING = "widget_continue_reading"
    const val EXTRA_OPEN_TAB = "widget_open_tab"
}
