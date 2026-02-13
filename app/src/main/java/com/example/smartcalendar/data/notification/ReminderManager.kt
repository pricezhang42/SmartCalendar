package com.example.smartcalendar.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.smartcalendar.data.model.ICalEvent
import java.util.Calendar

/**
 * Manages event reminders and system alarms.
 */
class ReminderManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule a reminder for an event.
     * @param event The event to set reminder for
     * @param reminderMinutes Minutes before event to trigger reminder (null = no reminder)
     */
    fun scheduleReminder(event: ICalEvent, reminderMinutes: Int?) {
        // Cancel any existing reminder first
        cancelReminder(event.uid)

        if (reminderMinutes == null) {
            return // No reminder to set
        }

        val triggerTime = event.dtStart - (reminderMinutes * 60 * 1000L)

        // Don't set reminder if it's in the past
        if (triggerTime < System.currentTimeMillis()) {
            return
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_UID, event.uid)
            putExtra(EXTRA_EVENT_TITLE, event.summary)
            putExtra(EXTRA_EVENT_START, event.dtStart)
            putExtra(EXTRA_REMINDER_MINUTES, reminderMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.uid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use exact alarm for better accuracy
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ requires exact alarm permission
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    // Fallback to inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fallback to inexact alarm if exact alarm permission is denied
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    /**
     * Cancel a reminder for an event.
     */
    fun cancelReminder(eventUid: String) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventUid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * Reschedule all reminders (useful after device reboot).
     */
    suspend fun rescheduleAllReminders(events: List<ICalEvent>, reminderMinutesMap: Map<String, Int?>) {
        events.forEach { event ->
            val reminderMinutes = reminderMinutesMap[event.uid]
            scheduleReminder(event, reminderMinutes)
        }
    }

    companion object {
        const val EXTRA_EVENT_UID = "event_uid"
        const val EXTRA_EVENT_TITLE = "event_title"
        const val EXTRA_EVENT_START = "event_start"
        const val EXTRA_REMINDER_MINUTES = "reminder_minutes"

        @Volatile
        private var instance: ReminderManager? = null

        fun getInstance(context: Context): ReminderManager {
            return instance ?: synchronized(this) {
                instance ?: ReminderManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
