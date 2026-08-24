package com.example.mybible.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

private const val PREFS_NAME = "my_bible_prefs"
private const val KEY_ENABLED = "reminders_enabled"
private const val KEY_FREQUENCY = "reminders_frequency"
private const val KEY_START_MINUTES = "reminders_start_minutes"
private const val KEY_END_MINUTES = "reminders_end_minutes"
private const val KEY_ENABLED_THEMES = "reminders_enabled_themes"
private const val REQUEST_CODE_BASE = 78000
private const val UNSET = -1

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
 * [ReminderReceiver] reschedules the same slot's alarm for +24h every time it
 * fires — a standard, drift-free pattern for daily reminders. [BootReceiver]
 * re-arms everything after a device reboot, since these alarms don't survive one.
 */
object ReminderScheduler {

    // The selectable active-hours window (Settings' start/end time pickers),
    // in minutes-since-midnight, at 30-minute steps — 6:00 AM through 9:00
    // PM. MIN_GAP_MINUTES is the "end must be at least 4 hours after start"
    // rule, so even the coarsest frequency (Gentle, every 4h) always fires
    // at least twice in the window.
    const val WINDOW_START_MINUTES = 6 * 60
    const val WINDOW_END_MINUTES = 21 * 60
    const val MINUTE_STEP = 30
    const val MIN_GAP_MINUTES = 4 * 60

    // Used only for cancellation, so that changing the window or frequency
    // can't leave orphaned alarms self-rearming forever for a slot that's no
    // longer part of the current schedule. Covers every half-hour slot the
    // window could ever hold, not just the ones currently scheduled.
    private val ALL_POSSIBLE_SLOTS =
        generateSequence(WINDOW_START_MINUTES) { it + MINUTE_STEP }.takeWhile { it <= WINDOW_END_MINUTES }.toList()

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

    /** Minutes-since-midnight, or null if the user has never picked a start
     *  time. Deliberately no silent 6am default — a brand-new user (or
     *  anyone who hasn't configured this yet) sees an explicit "Select"
     *  state in Settings rather than a guessed window nobody chose; see
     *  [slotsMinutes], which schedules nothing until this is set. */
    fun getStartMinutes(context: Context): Int? =
        prefs(context).getInt(KEY_START_MINUTES, UNSET).takeIf { it != UNSET }

    /** Same as [getStartMinutes] but for the end time. Also cleared back to
     *  null whenever [setStartMinutes] moves the start late enough that the
     *  previously-chosen end no longer satisfies the 4-hour minimum gap —
     *  forcing an explicit re-pick rather than silently keeping an
     *  now-invalid window. */
    fun getEndMinutes(context: Context): Int? =
        prefs(context).getInt(KEY_END_MINUTES, UNSET).takeIf { it != UNSET }

    fun setStartMinutes(context: Context, startMinutes: Int) {
        val p = prefs(context)
        val editor = p.edit().putInt(KEY_START_MINUTES, startMinutes)
        val currentEnd = getEndMinutes(context)
        if (currentEnd != null && currentEnd < startMinutes + MIN_GAP_MINUTES) {
            editor.remove(KEY_END_MINUTES)
        }
        editor.apply()
        if (isEnabled(context)) scheduleAll(context)
    }

    fun setEndMinutes(context: Context, endMinutes: Int) {
        prefs(context).edit().putInt(KEY_END_MINUTES, endMinutes).apply()
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

    /** The actual minute-of-day slots notifications fire at today, derived
     *  from the configured frequency + active-hours window. Empty until the
     *  user has picked both a start and an end time (see [getStartMinutes]/
     *  [getEndMinutes]) — nothing is scheduled on their behalf until then. */
    fun slotsMinutes(context: Context): List<Int> {
        val start = getStartMinutes(context) ?: return emptyList()
        val end = getEndMinutes(context) ?: return emptyList()
        val stepMinutes = getFrequency(context).stepHours * 60
        return generateSequence(start) { it + stepMinutes }.takeWhile { it <= end }.toList()
    }

    /** Persists the flag and (re)schedules or cancels the alarms to match. Does NOT request
     *  the POST_NOTIFICATIONS permission — that's the caller's job (needs an Activity). */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) scheduleAll(context) else cancelAll(context)
    }

    fun scheduleAll(context: Context) {
        cancelAll(context) // clears any stale alarms left over from a previous schedule
        slotsMinutes(context).forEach { minutesOfDay -> scheduleForSlot(context, minutesOfDay) }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        ALL_POSSIBLE_SLOTS.forEach { minutesOfDay ->
            alarmManager.cancel(pendingIntentFor(context, minutesOfDay))
        }
    }

    fun scheduleForSlot(context: Context, minutesOfDay: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = nextOccurrenceMillis(minutesOfDay)
        val pendingIntent = pendingIntentFor(context, minutesOfDay)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun pendingIntentFor(context: Context, minutesOfDay: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("minutesOfDay", minutesOfDay)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + minutesOfDay,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextOccurrenceMillis(minutesOfDay: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
            set(Calendar.MINUTE, minutesOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
