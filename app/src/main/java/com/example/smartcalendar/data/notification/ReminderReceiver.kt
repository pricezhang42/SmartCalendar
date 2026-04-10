package com.example.smartcalendar.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

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
                        // Start foreground service to launch full-screen alarm
                        try {
                            AlarmService.start(context, intent)
                            android.util.Log.d("ReminderReceiver", "AlarmService started for full-screen alarm")
                        } catch (e: Exception) {
                            android.util.Log.e("ReminderReceiver", "Failed to start AlarmService", e)
                            // Fallback: launch activity directly
                            try {
                                val alarmIntent = android.content.Intent(context, AlarmActivity::class.java).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    putExtras(intent)
                                }
                                context.startActivity(alarmIntent)
                            } catch (e2: Exception) {
                                android.util.Log.e("ReminderReceiver", "Fallback activity start also failed", e2)
                            }
                        }
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
}
