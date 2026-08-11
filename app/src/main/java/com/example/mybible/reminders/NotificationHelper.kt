package com.example.mybible.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.mybible.MainActivity
import com.example.mybible.R

// NOTE: channel importance is locked in at creation time — Android will not
// let the app raise or lower it later via code, only the user can via system
// Settings. This is a NEW channel id (v2) specifically so it's created fresh
// at IMPORTANCE_HIGH rather than trying to "upgrade" the old
// "bible_reading_reminders" (DEFAULT) channel, which wouldn't work for
// anyone who already has the app installed with the old channel present.
private const val CHANNEL_ID = "bible_reading_reminders_v2"

object NotificationHelper {

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reading reminders",
            NotificationManager.IMPORTANCE_HIGH // heads-up banner + sound, not just a shade entry
        ).apply {
            description = "Gentle nudges throughout the day to open the Bible"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, notificationId: Int, body: String) {
        ensureChannel(context)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("My Bible")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // pre-Oreo fallback; channel importance governs Oreo+
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // POST_NOTIFICATIONS (Android 13+) should already be granted by the
        // time this fires — MainActivity requests it when the user first
        // enables reminders — but guard here too since this can run from a
        // background AlarmManager receiver with no Activity to ask again.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.notify(notificationId, notification)
        }
    }
}
