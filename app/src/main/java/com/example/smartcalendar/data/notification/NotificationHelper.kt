package com.example.smartcalendar.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.smartcalendar.MainActivity
import com.example.smartcalendar.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper class for creating and displaying notifications.
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    /**
     * Create notification channel (required for Android O+).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Show a reminder notification for an event.
     */
    fun showReminderNotification(
        eventUid: String,
        eventTitle: String,
        eventStart: Long,
        reminderMinutes: Int
    ) {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val startTime = timeFormat.format(Date(eventStart))

        val contentText = when (reminderMinutes) {
            0 -> "Starting now at $startTime"
            1 -> "In 1 minute at $startTime"
            else -> {
                val unit = when {
                    reminderMinutes % 10080 == 0 -> {
                        val weeks = reminderMinutes / 10080
                        if (weeks == 1) "1 week" else "$weeks weeks"
                    }
                    reminderMinutes % 1440 == 0 -> {
                        val days = reminderMinutes / 1440
                        if (days == 1) "1 day" else "$days days"
                    }
                    reminderMinutes % 60 == 0 -> {
                        val hours = reminderMinutes / 60
                        if (hours == 1) "1 hour" else "$hours hours"
                    }
                    else -> {
                        if (reminderMinutes == 1) "1 minute" else "$reminderMinutes minutes"
                    }
                }
                "In $unit at $startTime"
            }
        }

        // Intent to open the app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_EVENT_UID, eventUid)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            eventUid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(eventTitle)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        notificationManager.notify(eventUid.hashCode(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "event_reminders"
        private const val CHANNEL_NAME = "Event Reminders"
        private const val CHANNEL_DESCRIPTION = "Notifications for upcoming events"
        const val EXTRA_EVENT_UID = "event_uid"
    }
}
