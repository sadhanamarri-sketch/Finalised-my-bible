package com.example.mybible.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Scheduled alarms don't survive a reboot — re-schedule them if reminders were enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (ReminderScheduler.isEnabled(context)) {
            ReminderScheduler.scheduleAll(context)
        }
    }
}
