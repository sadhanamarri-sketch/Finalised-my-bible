package com.example.mybible.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

private const val PREFS_NAME = "my_bible_prefs"

/**
 * Fires one reading reminder. Reads the current reading position straight
 * from the same SharedPreferences BibleRepository writes to (so no DB/repo
 * dependency is needed here), picks a rotating message the same way the
 * Capacitor app does (day-of-epoch + hour-slot index into the 36-message
 * list), shows the notification, then reschedules itself 24h out.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra("hour", -1)
        if (hour == -1) return

        // This hour is no longer part of the active schedule (e.g. the app updated
        // from the old hourly cadence to the current one) — don't notify, and don't
        // re-arm it, so it stops firing for good instead of self-perpetuating forever.
        if (hour !in ReminderScheduler.HOURS) return

        // A stale alarm could still fire right after the user disables
        // reminders (there's an unavoidable race between cancel() and an
        // already-in-flight alarm) — just no-op rather than notify.
        if (!ReminderScheduler.isEnabled(context)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val book = prefs.getString("last_book", "Genesis") ?: "Genesis"
        val chapter = prefs.getInt("last_chapter", 1)
        val ref = "$book $chapter"

        val slotIndex = ReminderScheduler.HOURS.indexOf(hour).coerceAtLeast(0)
        val dayIndex = (System.currentTimeMillis() / 86_400_000L).toInt()
        val messages = ReminderMessages.ALL
        val msgIndex = ((dayIndex * ReminderScheduler.HOURS.size + slotIndex) % messages.size + messages.size) % messages.size
        val body = messages[msgIndex].replace("{ref}", ref)

        NotificationHelper.show(context, notificationId = hour, body = body)

        // AlarmManager alarms are one-shot; re-arm this same hour for
        // tomorrow so it keeps firing daily without drifting.
        ReminderScheduler.scheduleForHour(context, hour)
    }
}
