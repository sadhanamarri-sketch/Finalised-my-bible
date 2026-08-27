package com.example.mybible.widget

import androidx.glance.action.ActionParameters

/**
 * Keys used on the Intent extras Glance attaches when actionStartActivity
 * fires. MainActivity must check for these (see MainActivity wiring note
 * in the README) and route accordingly:
 *   - EXTRA_CONTINUE_READING present -> resume last read position (the app's
 *     normal default behavior on launch already does this in most cases;
 *     this extra just makes the intent explicit / forces it)
 *   - EXTRA_OPEN_TAB present -> jump straight to that tab
 */
object WidgetActionKeys {
    val ContinueReading = ActionParameters.Key<Boolean>("widget_continue_reading")

    // Value is a NavTab enum name (e.g. "HIGHLIGHTS", "STUDIED", "NOTES",
    // "SEARCH") — used by the quick-access icon row on the home screen
    // widget to jump straight to that tab.
    val OpenTab = ActionParameters.Key<String>("widget_open_tab")

    // Plain Intent extra names (same string values as the ActionParameters
    // keys above) — this is what actually shows up on intent.extras in
    // MainActivity, since Glance serializes ActionParameters as extras.
    const val EXTRA_CONTINUE_READING = "widget_continue_reading"
    const val EXTRA_OPEN_TAB = "widget_open_tab"
}
