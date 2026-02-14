package com.example.smartcalendar.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.smartcalendar.R

/**
 * BroadcastReceiver that handles reminder alarms.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("ReminderReceiver", "onReceive called")

        // Acquire wake lock to ensure device stays awake while handling alarm
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "SmartCalendar:ReminderWakeLock"
        )

        try {
            // Acquire wake lock for 3 minutes (enough time to handle the alarm)
            wakeLock.acquire(3 * 60 * 1000L)
            android.util.Log.d("ReminderReceiver", "Wake lock acquired")

            val eventUid = intent.getStringExtra(ReminderManager.EXTRA_EVENT_UID) ?: return
            val eventTitle = intent.getStringExtra(ReminderManager.EXTRA_EVENT_TITLE) ?: "Event"
            val eventStart = intent.getLongExtra(ReminderManager.EXTRA_EVENT_START, 0L)
            val reminderMinutes = intent.getIntExtra(ReminderManager.EXTRA_REMINDER_MINUTES, 0)
            val reminderType = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_TYPE) ?: "NOTIFICATION"

            android.util.Log.d("ReminderReceiver", "Processing reminder for: $eventTitle, type: $reminderType")

            try {
                when (reminderType) {
                    "ALARM" -> {
                        // Use full-screen intent notification for alarm
                        showFullScreenAlarm(context, eventUid, eventTitle, eventStart, reminderMinutes)
                        android.util.Log.d("ReminderReceiver", "Full-screen alarm notification shown")
                    }
                    else -> {
                        // Show notification
                        val notificationHelper = NotificationHelper(context)
                        notificationHelper.showReminderNotification(
                            eventUid = eventUid,
                            eventTitle = eventTitle,
                            eventStart = eventStart,
                            reminderMinutes = reminderMinutes
                        )
                        android.util.Log.d("ReminderReceiver", "Notification shown")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ReminderReceiver", "Error handling reminder", e)
                // Fallback to notification if alarm fails
                val notificationHelper = NotificationHelper(context)
                notificationHelper.showReminderNotification(
                    eventUid = eventUid,
                    eventTitle = eventTitle,
                    eventStart = eventStart,
                    reminderMinutes = reminderMinutes
                )
            }
        } finally {
            // Release wake lock after handling alarm
            if (wakeLock.isHeld) {
                wakeLock.release()
                android.util.Log.d("ReminderReceiver", "Wake lock released")
            }
        }
    }

    private fun showFullScreenAlarm(
        context: Context,
        eventUid: String,
        eventTitle: String,
        eventStart: Long,
        reminderMinutes: Int
    ) {
        android.util.Log.d("ReminderReceiver", "showFullScreenAlarm called")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for alarms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "alarm_channel",
                "Event Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full-screen alarms for events"
                setBypassDnd(true)
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent for the full-screen activity
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderManager.EXTRA_EVENT_UID, eventUid)
            putExtra(ReminderManager.EXTRA_EVENT_TITLE, eventTitle)
            putExtra(ReminderManager.EXTRA_EVENT_START, eventStart)
            putExtra(ReminderManager.EXTRA_REMINDER_MINUTES, reminderMinutes)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            eventUid.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification with full-screen intent
        val notification = NotificationCompat.Builder(context, "alarm_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(eventTitle)
            .setContentText("Event alarm")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .build()

        notificationManager.notify(eventUid.hashCode(), notification)
        android.util.Log.d("ReminderReceiver", "Notification with full-screen intent posted")

        // Also try to launch the activity directly as a fallback
        // This works better on some devices where full-screen intent might be restricted
        try {
            context.startActivity(fullScreenIntent)
            android.util.Log.d("ReminderReceiver", "Activity started directly as fallback")
        } catch (e: Exception) {
            android.util.Log.e("ReminderReceiver", "Failed to start activity directly", e)
        }
    }
}
