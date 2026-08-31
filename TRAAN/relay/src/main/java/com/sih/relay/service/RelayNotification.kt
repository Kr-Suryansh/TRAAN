package com.sih.relay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Builds the persistent notification for [RelayForegroundService].
 *
 * Day 5 — the visible proof that the relay is running as a foreground service
 * (so the OS does not kill it, and the user can see the relay is active).
 *
 * Uses android.R.drawable.ic_menu_mylocation as the small icon because this
 * library module ships no app resources yet (Component C owns :app res/).
 */
object RelayNotification {

    const val CHANNEL_ID = "relay_foreground"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Relay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the disaster-relay mesh active in the background"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /**
     * Builds the foreground notification.
     *
     * The content intent reopens the app via the launcher activity (resolved from the
     * package manager) — NOT via a Service component, which would throw
     * ActivityNotFoundException when the notification is tapped.
     */
    fun build(context: Context): Notification {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
        val contentIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            null
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Disaster Relay Active")
            .setContentText("Relaying SOS messages over the nearby mesh")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }

        return builder.build()
    }
}