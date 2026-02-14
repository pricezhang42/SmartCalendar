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

        android.util.Log.d("ReminderManager", "scheduleReminder called for event: ${event.summary}, reminderMinutes: $reminderMinutes")

        if (reminderMinutes == null) {
            android.util.Log.d("ReminderManager", "No reminder set (reminderMinutes is null)")
            return // No reminder to set
        }

        val triggerTime = event.dtStart - (reminderMinutes * 60 * 1000L)
        val currentTime = System.currentTimeMillis()

        android.util.Log.d("ReminderManager", "Event start: ${event.dtStart}, Trigger time: $triggerTime, Current time: $currentTime")

        // Don't set reminder if it's in the past
        if (triggerTime < currentTime) {
            android.util.Log.d("ReminderManager", "Trigger time is in the past, not scheduling alarm")
            return
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_UID, event.uid)
            putExtra(EXTRA_EVENT_TITLE, event.summary)
            putExtra(EXTRA_EVENT_START, event.dtStart)
            putExtra(EXTRA_REMINDER_MINUTES, reminderMinutes)
            putExtra(EXTRA_REMINDER_TYPE, event.reminderType)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.uid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setAlarmClock for ALARM type to bypass Doze mode completely
        // Use setExactAndAllowWhileIdle for NOTIFICATION type
        try {
            if (event.reminderType == "ALARM") {
                // For alarm type, use setAlarmClock which bypasses all battery optimizations
                // This ensures the alarm will always trigger, even in Doze mode
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                android.util.Log.d("ReminderManager", "========================================")
                android.util.Log.d("ReminderManager", "ALARM TYPE - AlarmClock scheduled")
                android.util.Log.d("ReminderManager", "Event: ${event.summary}")
                android.util.Log.d("ReminderManager", "Event UID: ${event.uid}")
                android.util.Log.d("ReminderManager", "Trigger time: ${java.util.Date(triggerTime)}")
                android.util.Log.d("ReminderManager", "Current time: ${java.util.Date(currentTime)}")
                android.util.Log.d("ReminderManager", "Time until trigger: ${(triggerTime - currentTime) / 1000 / 60} minutes")
                android.util.Log.d("ReminderManager", "PendingIntent hashCode: ${event.uid.hashCode()}")
                android.util.Log.d("ReminderManager", "========================================")
            } else {
                // For notification type, use exact alarm with idle exception
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        android.util.Log.d("ReminderManager", "NOTIFICATION TYPE - Exact alarm scheduled for ${java.util.Date(triggerTime)}")
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        android.util.Log.w("ReminderManager", "NOTIFICATION TYPE - Inexact alarm scheduled (no exact alarm permission)")
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    android.util.Log.d("ReminderManager", "NOTIFICATION TYPE - Exact alarm scheduled for ${java.util.Date(triggerTime)}")
                }
            }

            // Verify next alarm time
            val nextAlarm = alarmManager.nextAlarmClock
            if (nextAlarm != null) {
                android.util.Log.d("ReminderManager", "Next system alarm at: ${java.util.Date(nextAlarm.triggerTime)}")
            } else {
                android.util.Log.w("ReminderManager", "No next alarm found in system")
            }
        } catch (e: SecurityException) {
            android.util.Log.e("ReminderManager", "SecurityException scheduling alarm", e)
            // Fallback to inexact alarm if exact alarm permission is denied
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            android.util.Log.d("ReminderManager", "Fallback inexact alarm scheduled")
        } catch (e: Exception) {
            android.util.Log.e("ReminderManager", "Error scheduling alarm", e)
        }
    }

    /**
     * Cancel a reminder for an event.
     */
    fun cancelReminder(eventUid: String) {
        android.util.Log.d("ReminderManager", "Cancelling reminder for event: $eventUid")
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventUid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        android.util.Log.d("ReminderManager", "Reminder cancelled successfully")
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
        const val EXTRA_REMINDER_TYPE = "reminder_type"

        @Volatile
        private var instance: ReminderManager? = null

        fun getInstance(context: Context): ReminderManager {
            return instance ?: synchronized(this) {
                instance ?: ReminderManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
