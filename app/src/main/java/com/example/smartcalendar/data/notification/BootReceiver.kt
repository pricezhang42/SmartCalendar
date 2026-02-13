package com.example.smartcalendar.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smartcalendar.data.repository.LocalCalendarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that reschedules all reminders after device reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule all reminders
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = LocalCalendarRepository.getInstance(context)
                    val events = repository.getAllEvents()
                    val reminderManager = ReminderManager.getInstance(context)

                    // Get reminder settings from SharedPreferences
                    val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
                    val reminderMap = mutableMapOf<String, Int?>()

                    events.forEach { event ->
                        val reminderMinutes = prefs.getInt("reminder_${event.uid}", -1)
                        if (reminderMinutes >= 0) {
                            reminderMap[event.uid] = reminderMinutes
                        }
                    }

                    reminderManager.rescheduleAllReminders(events, reminderMap)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
