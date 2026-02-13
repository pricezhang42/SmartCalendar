package com.example.smartcalendar.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BroadcastReceiver that handles reminder alarms.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventUid = intent.getStringExtra(ReminderManager.EXTRA_EVENT_UID) ?: return
        val eventTitle = intent.getStringExtra(ReminderManager.EXTRA_EVENT_TITLE) ?: "Event"
        val eventStart = intent.getLongExtra(ReminderManager.EXTRA_EVENT_START, 0L)
        val reminderMinutes = intent.getIntExtra(ReminderManager.EXTRA_REMINDER_MINUTES, 0)

        // Create notification
        val notificationHelper = NotificationHelper(context)
        notificationHelper.showReminderNotification(
            eventUid = eventUid,
            eventTitle = eventTitle,
            eventStart = eventStart,
            reminderMinutes = reminderMinutes
        )
    }
}
