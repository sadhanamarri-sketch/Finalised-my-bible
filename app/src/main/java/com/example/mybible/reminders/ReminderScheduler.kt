package com.example.mybible.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

private const val PREFS_NAME = "my_bible_prefs"
private const val KEY_ENABLED = "reminders_enabled"
private const val KEY_FREQUENCY = "reminders_frequency"
private const val KEY_START_HOUR = "reminders_start_hour"
private const val KEY_END_HOUR = "reminders_end_hour"
private const val KEY_ENABLED_THEMES = "reminders_enabled_themes"
private const val REQUEST_CODE_BASE = 78000

/** How far apart notifications land within the active-hours window —
 *  replaces the old hardcoded "every 3 hours." */
enum class ReminderFrequency(val label: String, val stepHours: Int) {
    GENTLE("Gentle", 4),
    REGULAR("Regular", 3),
    FREQUENT("Frequent", 2)
}

/**
 * Schedules the reading reminders. Originally matched the Capacitor app's
 * hourly `READING_REMINDER_HOURS`/`scheduleReadingReminders` (16/day);
 * reduced to a default of every 3 hours, 6am-9pm, to be less intrusive —
 * both the cadence (see [ReminderFrequency]) and the active-hours window
 * are now user-configurable in Settings instead of that fixed default.
 *
 * These are normal scheduled notifications, not exact alarms — `setAndAllowWhileIdle`
 * still wakes the device from Doze, but Android is free to batch/delay delivery by
 * up to ~15 minutes. That's a fine trade-off for a reading nudge, and it means the
 * app never needs the special "Alarms & reminders" permission (Android 12+).
 *
 * `AlarmManager` has no built-in "repeat exactly every 24h" — `setRepeating`
 * is inexact and drifts over time. Instead each alarm is one-shot, and
 * [ReminderReceiver] reschedules the same hour's alarm for +24h every time it
 * fires — a standard, drift-free pattern for daily reminders. [BootReceiver]
 * re-arms everything after a device reboot, since these alarms don't survive one.
 */
object ReminderScheduler {

    // Used only for cancellation, so that changing the active-hours window
    // or frequency can't leave orphaned alarms self-rearming forever for an
    // hour that's no longer part of the current schedule.
    private val ALL_POSSIBLE_HOURS = (0..23).toList()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun getFrequency(context: Context): ReminderFrequency {
        val stored = prefs(context).getString(KEY_FREQUENCY, null)
        return ReminderFrequency.entries.find { it.name == stored } ?: ReminderFrequency.REGULAR
    }

    fun setFrequency(context: Context, frequency: ReminderFrequency) {
        prefs(context).edit().putString(KEY_FREQUENCY, frequency.name).apply()
        if (isEnabled(context)) scheduleAll(context)
    }

    /** (startHour, endHour), both 0-23, defaulting to the original fixed
     *  6am-9pm window. */
    fun getActiveHours(context: Context): Pair<Int, Int> {
        val p = prefs(context)
        return p.getInt(KEY_START_HOUR, 6) to p.getInt(KEY_END_HOUR, 21)
    }

    fun setActiveHours(context: Context, startHour: Int, endHour: Int) {
        prefs(context).edit()
            .putInt(KEY_START_HOUR, startHour)
            .putInt(KEY_END_HOUR, endHour)
            .apply()
        if (isEnabled(context)) scheduleAll(context)
    }

    fun getEnabledThemes(context: Context): Set<ReminderTheme> {
        val stored = prefs(context).getStringSet(KEY_ENABLED_THEMES, null) ?: return ReminderTheme.entries.toSet()
        val resolved = stored.mapNotNull { name -> ReminderTheme.entries.find { it.name == name } }.toSet()
        // Guards against an empty rotation pool (e.g. every theme was
        // unchecked at some point) rather than leaving reminders silently
        // stuck with nothing to say.
        return resolved.ifEmpty { ReminderTheme.entries.toSet() }
    }

    fun setThemeEnabled(context: Context, theme: ReminderTheme, enabled: Boolean) {
        val current = getEnabledThemes(context).toMutableSet()
        if (enabled) current.add(theme) else current.remove(theme)
        // Never persist an empty set — same reasoning as the fallback in
        // getEnabledThemes, applied at write time too so the in-memory
        // state (e.g. MainViewModel's StateFlow) can't briefly show every
        // theme unchecked.
        if (current.isEmpty()) return
        prefs(context).edit().putStringSet(KEY_ENABLED_THEMES, current.map { it.name }.toSet()).apply()
    }

    /** The actual hours notifications fire at today, derived from the
     *  configured frequency + active-hours window — replaces the old
     *  hardcoded `(6..21 step 3)`. Always includes at least the start
     *  hour, even if the window is narrower than one step. */
    fun hours(context: Context): List<Int> {
        val (start, end) = getActiveHours(context)
        if (start > end) return listOf(start.coerceIn(0, 23))
        val step = getFrequency(context).stepHours
        return generateSequence(start) { it + step }.takeWhile { it <= end }.toList()
    }

    /** Persists the flag and (re)schedules or cancels the alarms to match. Does NOT request
     *  the POST_NOTIFICATIONS permission — that's the caller's job (needs an Activity). */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) scheduleAll(context) else cancelAll(context)
    }

    fun scheduleAll(context: Context) {
        cancelAll(context) // clears any stale alarms left over from a previous schedule
        hours(context).forEach { hour -> scheduleForHour(context, hour) }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        ALL_POSSIBLE_HOURS.forEach { hour ->
            alarmManager.cancel(pendingIntentFor(context, hour))
        }
    }

    fun scheduleForHour(context: Context, hour: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = nextOccurrenceMillis(hour)
        val pendingIntent = pendingIntentFor(context, hour)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun pendingIntentFor(context: Context, hour: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("hour", hour)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + hour,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextOccurrenceMillis(hour: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            // :30 rather than :00 — the user has other important
            // notifications landing on the hour, so reminders are offset
            // by half an hour to avoid piling on top of them.
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
