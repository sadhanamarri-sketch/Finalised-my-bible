package com.example.mybible.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

private const val PREFS_NAME = "my_bible_prefs"
private const val KEY_ENABLED = "reminders_enabled"
private const val REQUEST_CODE_BASE = 78000

/**
 * Schedules the reading reminders (every 3 hours, 6am-9pm local time = 6 notifications/day).
 * Originally matched the Capacitor app's hourly `READING_REMINDER_HOURS`/`scheduleReadingReminders`
 * (16/day); reduced to every 3 hours to be less intrusive.
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

    val HOURS: List<Int> = (6..21 step 3).toList() // 6,9,12,15,18,21 -> every 3 hours, 6/day

    // Used only for cancellation, so that changing HOURS (e.g. the old hourly
    // cadence -> every 3 hours) can't leave orphaned alarms self-rearming forever
    // on devices that already had the previous schedule set up.
    private val ALL_POSSIBLE_HOURS = (0..23).toList()

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    /** Persists the flag and (re)schedules or cancels the alarms to match. Does NOT request
     *  the POST_NOTIFICATIONS permission — that's the caller's job (needs an Activity). */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) scheduleAll(context) else cancelAll(context)
    }

    fun scheduleAll(context: Context) {
        cancelAll(context) // clears any stale alarms left over from a previous HOURS list
        HOURS.forEach { hour -> scheduleForHour(context, hour) }
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
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
